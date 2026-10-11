package mn.suld.plugin.prop;

import mn.suld.api.json.Json;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.region.HistoricSiteService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static 3D props in the world: the Meshy models voxelised by tools/meshy (stone turtles and the silver tree in
 * Kharkhorum, a deer stone and a stone man at their historic sites), placed from {@code content/props.json} (the
 * server's copy in {@code plugins/SULD/content/} wins). Each is one item display wearing {@code suld:prop/<id>},
 * scaled to its height and standing on the ground, with an interaction box that tells what it is. The displays are
 * not saved with the chunk: they appear when their chunk is loaded and go with it. A display has no collision of its
 * own, so the world blocks the prop fills ({@code content/prop_shapes.json}, generated with the model) are filled with
 * barrier blocks while it stands; only air is ever replaced, and only the barriers placed here are taken away again.
 */
public final class PropService implements Listener {

    public static final String TAG = "suld_prop";
    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");
    private static final TextColor STORY = TextColor.fromHexString("#E8DCC0");

    record Prop(String id, String name, String history, double height, double units, String about) {
        /** Display scale: the model is {@code units} tall at scale 1 (16 units a block). */
        float scale() {
            return (float) (height / (units / 16.0));
        }
    }

    record Place(int index, Prop prop, int[] city, String site, int[] offset, float yaw) {
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final Map<String, Prop> props = new LinkedHashMap<>();
    private final List<Place> places = new ArrayList<>();
    private final Map<Integer, Entity[]> live = new HashMap<>();
    private final Map<String, List<int[]>> shapes = new HashMap<>();
    private final Map<Integer, List<org.bukkit.block.Block>> barriers = new HashMap<>();
    private final java.util.Set<Integer> solidDone = new java.util.HashSet<>();
    private final NamespacedKey placeKey;

    public PropService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        this.placeKey = new NamespacedKey(plugin, "prop_place");
    }

    public void start() {
        try {
            load();
        } catch (RuntimeException | IOException e) {
            plugin.getLogger().warning("[props] content/props.json: " + e.getMessage() + " — no props");
            props.clear();
            places.clear();
            return;
        }
        plugin.getLogger().info("[props] " + props.size() + " props, " + places.size() + " places");
        // leftovers of an earlier run (they are not saved, but a crash can leave some in loaded chunks)
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 80L, 100L);
    }

    @SuppressWarnings("unchecked")
    private void load() throws IOException {
        String text;
        Path server = plugin.getDataFolder().toPath().resolve("content").resolve("props.json");
        if (Files.exists(server)) {
            text = Files.readString(server, StandardCharsets.UTF_8);
            plugin.getLogger().info("[props] using the server's content/props.json");
        } else {
            try (InputStream in = plugin.getResource("content/props.json")) {
                if (in == null) throw new IOException("missing");
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        Map<String, Object> root = Json.object(Json.parse(text));
        for (Object o : Json.array(root.get("props"))) {
            Map<String, Object> m = Json.object(o);
            String id = (String) m.get("id");
            if (id == null || !id.matches("[a-z][a-z0-9_]{1,40}")) throw new IllegalArgumentException("prop id " + id);
            double height = ((Number) m.get("height")).doubleValue();
            double units = ((Number) m.get("units")).doubleValue();
            if (height <= 0 || height > 32 || units <= 0 || units > 48) throw new IllegalArgumentException(id + ": height/units");
            props.put(id, new Prop(id, (String) m.get("name"), String.valueOf(m.getOrDefault("history", "INSPIRED")), height, units,
                    String.valueOf(m.getOrDefault("about", ""))));
        }
        try (InputStream in = plugin.getResource("content/prop_shapes.json")) {
            if (in != null) {
                Map<String, Object> sh = Json.object(Json.object(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8))).get("shapes"));
                for (Map.Entry<String, Object> e : sh.entrySet()) {
                    List<int[]> cells = new ArrayList<>();
                    for (Object c : Json.array(e.getValue())) {
                        List<Object> xyz = Json.array(c);
                        cells.add(new int[]{Json.integer(xyz.get(0)), Json.integer(xyz.get(1)), Json.integer(xyz.get(2))});
                    }
                    shapes.put(e.getKey(), cells);
                }
            }
        }
        int i = 0;
        for (Object o : Json.array(root.get("places"))) {
            Map<String, Object> m = Json.object(o);
            Prop p = props.get((String) m.get("prop"));
            if (p == null) throw new IllegalArgumentException("place " + i + ": unknown prop " + m.get("prop"));
            int[] city = m.get("city") == null ? null : pair(m.get("city"));
            String site = (String) m.get("site");
            if ((city == null) == (site == null)) throw new IllegalArgumentException("place " + i + ": give city or site");
            int[] off = m.get("offset") == null ? new int[]{0, 0} : pair(m.get("offset"));
            float yaw = m.get("yaw") == null ? 0f : ((Number) m.get("yaw")).floatValue();
            places.add(new Place(i++, p, city, site, off, yaw));
        }
    }

    private static int[] pair(Object o) {
        List<Object> a = Json.array(o);
        if (a.size() != 2) throw new IllegalArgumentException("expected [x, z]");
        return new int[]{Json.integer(a.get(0)), Json.integer(a.get(1))};
    }

    /** The world block column of a place, or null while it cannot be known (no city, unknown site). */
    private int[] column(World w, Place p) {
        if (p.city() != null) {
            int[] o = services.city().origin(w.getName()).orElse(null);
            return o == null ? null : new int[]{o[0] + p.city()[0], o[1] + p.city()[1]};
        }
        if (services.historicSites == null) return null;
        Location s = services.historicSites.where(p.site()).orElse(null);
        return s == null ? null : new int[]{s.getBlockX() + p.offset()[0], s.getBlockZ() + p.offset()[1]};
    }

    private void tick() {
        World w = Bukkit.getWorlds().get(0);
        for (Place p : places) {
            Entity[] e = live.get(p.index());
            if (e != null && e[0].isValid()) {
                if (!solidDone.contains(p.index())) {
                    int[] col = column(w, p);
                    if (col != null) solid(w, p, col[0], col[1]); // cells a player stood in last time
                }
                continue;
            }
            if (e != null) {
                for (Entity x : e) x.remove();
                live.remove(p.index());
            }
            int[] col = column(w, p);
            if (col == null || !w.isChunkLoaded(col[0] >> 4, col[1] >> 4)) continue;
            live.put(p.index(), spawn(w, p, col[0], col[1]));
            solid(w, p, col[0], col[1]);
        }
    }

    private Entity[] spawn(World w, Place p, int x, int z) {
        int y = ground(w, x, z) + 1;
        Location at = new Location(w, x + 0.5, y, z + 0.5, p.yaw(), 0);
        float s = p.prop().scale();
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("suld", "prop/" + p.prop().id()));
        it.setItemMeta(meta);
        ItemDisplay d = w.spawn(at, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.addScoreboardTag(TAG);
            e.setItemStack(it);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBillboard(Display.Billboard.FIXED);
            // the model stands on y = -16 units: with the NONE transform its centre (8, 8, 8) is at the entity,
            // so its foot is 1.5 blocks (times the scale) below it
            e.setTransformation(new Transformation(new Vector3f(0, 1.5f * s, 0), new AxisAngle4f(),
                    new Vector3f(s, s, s), new AxisAngle4f()));
            e.setViewRange(Math.max(1.0f, (float) p.prop().height() / 3f)); // × 64 blocks: tall props are seen from afar
            e.setShadowRadius((float) Math.min(4.0, p.prop().height() * 0.4));
            e.setShadowStrength(0.6f);
        });
        Interaction box = w.spawn(at, Interaction.class, e -> {
            e.setPersistent(false);
            e.addScoreboardTag(TAG);
            e.setInteractionWidth((float) footprint(p.prop()));
            e.setInteractionHeight((float) p.prop().height());
            e.setResponsive(true);
            e.getPersistentDataContainer().set(placeKey, org.bukkit.persistence.PersistentDataType.STRING, p.prop().id());
        });
        return new Entity[]{d, box};
    }

    /**
     * Width of the click box: the prop's own collision footprint (at most 4), so the box never reaches past the
     * barriers and swallows hits meant for a mob standing beside the statue.
     */
    private double footprint(Prop prop) {
        List<int[]> cells = shapes.get(prop.id());
        if (cells == null || cells.isEmpty()) return Math.min(4.0, Math.max(1.0, prop.height() * 0.6));
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] c : cells) {
            minX = Math.min(minX, c[0]);
            maxX = Math.max(maxX, c[0]);
            minZ = Math.min(minZ, c[2]);
            maxZ = Math.max(maxZ, c[2]);
        }
        return Math.min(4.0, Math.max(1.0, Math.min(maxX - minX + 1, maxZ - minZ + 1)));
    }

    /** The ground under a prop: the highest block, looking through the prop's own barriers (and the air between them). */
    private static int ground(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z);
        while (y > w.getMinHeight() && (w.getBlockAt(x, y, z).getType() == Material.BARRIER || w.getBlockAt(x, y, z).getType() == Material.AIR)) y--;
        return y;
    }

    /**
     * Barrier blocks in the cells the prop fills, turned with the display's yaw (90° steps): a display yaw θ turns the
     * model's +Z towards (-sin θ, cos θ). Only air becomes a barrier; the ones placed are remembered for {@link #stop}.
     */
    private void solid(World w, Place p, int x, int z) {
        List<int[]> cells = shapes.get(p.prop().id());
        if (cells == null || solidDone.contains(p.index())) return;
        int y = ground(w, x, z) + 1;
        int q = Math.floorMod(Math.round(p.yaw() / 90f), 4);
        List<org.bukkit.block.Block> placed = barriers.computeIfAbsent(p.index(), k -> new ArrayList<>());
        boolean skipped = false;
        for (int[] c : cells) {
            int dx = c[0], dz = c[2];
            for (int r = 0; r < q; r++) { // one quarter turn: (x, z) -> (-z, x)
                int t = dx;
                dx = -dz;
                dz = t;
            }
            org.bukkit.block.Block b = w.getBlockAt(x + dx, y + c[1], z + dz);
            if (b.getType() == Material.AIR && occupied(b)) {
                skipped = true; // never fill a block a player stands in: retried on the next pass
                continue;
            }
            if (b.getType() == Material.AIR) {
                b.setType(Material.BARRIER, false);
                placed.add(b);
            } else if (b.getType() == Material.BARRIER && !placed.contains(b)) {
                placed.add(b); // left by a run that did not stop cleanly: ours, taken away on stop
            }
        }
        if (!skipped) solidDone.add(p.index());
    }

    /** A player's box overlaps this block. */
    private static boolean occupied(org.bukkit.block.Block b) {
        org.bukkit.util.BoundingBox box = org.bukkit.util.BoundingBox.of(b);
        for (org.bukkit.entity.Player pl : b.getWorld().getNearbyPlayers(b.getLocation().add(0.5, 0.5, 0.5), 2.5)) {
            if (pl.getBoundingBox().overlaps(box)) return true;
        }
        return false;
    }

    @EventHandler
    public void onClick(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Interaction box) || !box.getScoreboardTags().contains(TAG)) return;
        String id = box.getPersistentDataContainer().get(placeKey, org.bukkit.persistence.PersistentDataType.STRING);
        Prop p = id == null ? null : props.get(id);
        if (p == null) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(Component.text("✦ " + p.name(), GOLD, TextDecoration.BOLD)
                .append(Component.text("  " + HistoricSiteService.label(p.history()), NamedTextColor.GRAY)));
        if (!p.about().isEmpty()) e.getPlayer().sendMessage(Component.text(p.about(), STORY));
    }

    /**
     * Staff overview: every place, where it stands and whether it is shown. A prop appears only while a player is near
     * (its chunk is loaded), so a far one says how far it is from {@code from} (null: console).
     */
    public List<String> describe(Location from) {
        World w = Bukkit.getWorlds().get(0);
        List<String> out = new ArrayList<>();
        for (Place p : places) {
            int[] col = column(w, p);
            Entity[] e = live.get(p.index());
            String state;
            if (e != null && e[0].isValid()) state = " — харагдаж байна";
            else if (col == null) state = " — байршил тодорхойгүй";
            else if (from != null && from.getWorld() == w) {
                long d = Math.round(Math.hypot(from.getX() - col[0], from.getZ() - col[1]));
                state = " — хол (" + d + " м), ойртоход гарч ирнэ";
            } else state = " — ойртоход гарч ирнэ";
            out.add(p.prop().id() + " @ " + (col == null ? "?" : col[0] + " " + col[1]) + state);
        }
        return out;
    }

    // --------------------------------------------------------------- dynamic places (campaign world effects)

    private final Map<String, Integer> dynamicIndex = new HashMap<>();
    private int nextDynamic = 100_000;

    /**
     * A campaign's world effect: prop {@code propId} appears {@code dx}/{@code dz} blocks from historic site
     * {@code siteId}, under the flag {@code key} (docs/KHURALDAI.md). Idempotent per key; false for an unknown prop.
     */
    public boolean placeDynamic(String key, String propId, String siteId, int dx, int dz, float yaw) {
        Prop prop = props.get(propId);
        if (prop == null) return false;
        if (dynamicIndex.containsKey(key)) return true;
        int index = nextDynamic++;
        dynamicIndex.put(key, index);
        places.add(new Place(index, prop, null, siteId, new int[]{dx, dz}, yaw));
        plugin.getLogger().info("[props] dynamic " + propId + " at " + siteId + " (+" + dx + ", +" + dz + ") for " + key);
        return true;
    }

    /** The flag lapsed: the prop, its click box and its barriers go. */
    public void removeDynamic(String key) {
        Integer index = dynamicIndex.remove(key);
        if (index == null) return;
        places.removeIf(p -> p.index() == index);
        Entity[] e = live.remove(index);
        if (e != null) for (Entity x : e) x.remove();
        List<org.bukkit.block.Block> bs = barriers.remove(index);
        if (bs != null) for (org.bukkit.block.Block b : bs) if (b.getType() == Material.BARRIER) b.setType(Material.AIR, false);
        solidDone.remove(index);
        plugin.getLogger().info("[props] dynamic place of " + key + " removed");
    }

    /** Where the first place of this prop stands (for /suld props tp), or null. */
    public Location where(String id) {
        World w = Bukkit.getWorlds().get(0);
        for (Place p : places) {
            if (!p.prop().id().equals(id)) continue;
            int[] col = column(w, p);
            if (col == null) return null;
            // beside the prop, on the ground, facing it
            int x = col[0] + 3, z = col[1];
            return new Location(w, x + 0.5, ground(w, x, z) + 1, z + 0.5, 90f, 0f);
        }
        return null;
    }

    public java.util.Set<String> ids() {
        return java.util.Collections.unmodifiableSet(props.keySet());
    }

    /** Removes every prop entity (plugin disable). */
    public void stop() {
        for (Entity[] e : live.values()) for (Entity x : e) x.remove();
        live.clear();
        for (List<org.bukkit.block.Block> bs : barriers.values()) {
            for (org.bukkit.block.Block b : bs) if (b.getType() == Material.BARRIER) b.setType(Material.AIR, false);
        }
        barriers.clear();
        solidDone.clear();
    }
}
