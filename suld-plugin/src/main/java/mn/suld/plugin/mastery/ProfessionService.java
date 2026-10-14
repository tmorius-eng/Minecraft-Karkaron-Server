package mn.suld.plugin.mastery;

import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.loot.LootDrop;
import mn.suld.api.mastery.GatherPlan;
import mn.suld.api.mastery.Profession;
import mn.suld.api.mastery.ProfessionMaterials;
import mn.suld.api.persistence.ProfessionRepository;
import mn.suld.api.region.Navigation;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.Content;
import mn.suld.plugin.hud.HudService;
import mn.suld.plugin.map.WaypointGuide;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Pages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Gate;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.SheepRegrowWoolEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Мэргэжил (docs/MASTERY_AND_PROFESSIONS.md): the four crafts in the world. Every wild area holds ore veins and herb
 * patches (display models with a click box, never blocks of the world: nothing to dig out or grief) and one herder's
 * pen of fences with horses and sheep (GatherPlan). A right click starts a two-second gather; moving away or being hit
 * breaks it. A finished gather gives the craft's material (more from levels 20 and 40), the craft's points, and spends
 * the node (or the animal) for everyone for {@link Profession#NODE_RESPAWN_SECONDS}. A node above the craft's level is
 * refused with the level it needs. {@code /mergejil} shows the four crafts, the nearest node of each (with the map's
 * waypoint to it) and the refinings (ProfessionMaterials: ore into the smith's iron and gold, wool into felt).
 *
 * <p>Points are stored like weapon mastery's (V27, additive): read at join, written every 30 s, at quit and at shutdown.
 * Node and animal entities are never saved with their chunk (placed again while the chunk is loaded and a player is
 * near); the pens are built once and kept in {@code professions.yml}. Main thread only.
 */
public final class ProfessionService implements Listener {

    static final String TAG = "suld_gather";
    private static final long FLUSH_TICKS = 20L * 30;
    /** A gather lasts this long (ticks). */
    static final int CHANNEL_TICKS = 40;
    /** How far a gatherer may drift from where they started (blocks). */
    private static final double DRIFT = 1.2;
    /** Node entities are placed while a player is this near (blocks). */
    private static final double SHOW = 112;
    private static final long RESPAWN_MS = Profession.NODE_RESPAWN_SECONDS * 1000L;
    private static final String[] TIER = {"", "I", "II", "III", "IV"};
    private static final TextColor GOLD = TextColor.fromHexString("#E8C35A"), CREAM = TextColor.fromHexString("#F4E8C8");

    private final Plugin plugin;
    private final SuldServices services;
    private final ProfessionRepository repo;
    private final NamespacedKey nodeKey;
    private final File penFile;

    // ---- storage (like WeaponMasteryService) ----
    private final Map<UUID, EnumMap<Profession, Long>> stored = new HashMap<>();
    private final Map<UUID, EnumMap<Profession, Long>> pending = new HashMap<>();
    private final Set<UUID> loaded = new HashSet<>();

    // ---- the world ----
    /** False while the city is on its way and will move the spawn (WorldBuildService.spawnSettled). */
    public volatile BooleanSupplier spawnSettled = () -> true;
    /** The places nodes keep clear of (SuldPlugin: the map's places: gates, ovoos, sites, relays, the yard). */
    public volatile Supplier<List<int[]>> keepAway = List::of;
    /** The keep-away points of the last plan: a vein, a patch or a pen that looks for ground never ends up on one. */
    private volatile List<int[]> clearOf = List.of();
    /** The map's waypoint (SuldPlugin), for «зам заах» to a node; null when the map is off. */
    public volatile WaypointGuide guide;
    private volatile List<GatherPlan.Node> nodes = List.of();
    private final Map<String, GatherPlan.Node> byId = new HashMap<>();
    private final Map<String, Live> live = new HashMap<>();
    /** A node id or a pen animal's slot ({@code pen.tuul#h1}) → when it is back (epoch ms). */
    private final Map<String, Long> spentUntil = new HashMap<>();
    /** Built pens: id → {x, y, z} of the pen's middle (professions.yml). */
    private final Map<String, int[]> pens = new HashMap<>();
    /** A pen animal's slot → its entity. */
    private final Map<String, UUID> animals = new HashMap<>();
    /** Nodes with no dry ground near their spot (this run). */
    private final Set<String> nowhere = new HashSet<>();
    private final Map<UUID, Gather> gathering = new HashMap<>();

    /** A vein or herb patch standing in a loaded chunk: where, its click box, its model parts, its label. */
    private static final class Live {
        final int x, y, z;
        final UUID hit, label;
        final List<UUID> parts;
        boolean shownSpent;

        Live(int x, int y, int z, UUID hit, List<UUID> parts, UUID label) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.hit = hit;
            this.parts = parts;
            this.label = label;
        }
    }

    /** A gather in progress. */
    private record Gather(String key, Profession craft, int tier, Location from, Location at, int[] ticks) {
    }

    public ProfessionService(Plugin plugin, SuldServices services, ProfessionRepository repo) {
        this.plugin = plugin;
        this.services = services;
        this.repo = repo;
        this.nodeKey = new NamespacedKey(plugin, "gather_node");
        this.penFile = new File(plugin.getDataFolder(), "professions.yml");
    }

    public void start() {
        YamlConfiguration st = YamlConfiguration.loadConfiguration(penFile);
        readPens(st, line -> plugin.getLogger().warning("[crafts] professions.yml: a pen line not read: " + line)).forEach((k, b) -> {
            pens.put(k, b);
            guard(k, b);
        });
        replan();
        for (Player p : Bukkit.getOnlinePlayers()) load(p.getUniqueId());
        Bukkit.getScheduler().runTaskTimer(plugin, this::flushAll, FLUSH_TICKS, FLUSH_TICKS);
        Bukkit.getScheduler().runTaskTimer(plugin, this::keep, 100L, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickGathers, 2L, 2L);
    }

    // ================================================================== storage

    private void load(UUID id) {
        repo.totals(id).whenComplete((m, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) {
                plugin.getLogger().log(Level.WARNING, "[crafts] crafts of " + id + " not read (points stay pending)", ex);
                return;
            }
            if (Bukkit.getPlayer(id) == null) return; // left before the read came back; its pending points were written at quit
            EnumMap<Profession, Long> s = new EnumMap<>(Profession.class);
            s.putAll(m);
            stored.put(id, s);
            loaded.add(id);
        }));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        load(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        gathering.remove(id);
        flush(id, true);
        stored.remove(id);
        loaded.remove(id);
    }

    private void flushAll() {
        for (UUID id : new HashSet<>(pending.keySet())) flush(id, false);
    }

    /** Writes a player's pending points; {@code leaving} writes them even before the read is back. */
    private CompletableFuture<Void> flush(UUID id, boolean leaving) {
        if (!leaving && !loaded.contains(id)) return CompletableFuture.completedFuture(null);
        EnumMap<Profession, Long> batch = pending.remove(id);
        if (batch == null || batch.isEmpty()) return CompletableFuture.completedFuture(null);
        return repo.add(id, batch, System.currentTimeMillis()).whenComplete((v, ex) -> {
            if (!plugin.isEnabled()) return; // shutdown: SuldServices waits on this write
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (ex != null) {
                    plugin.getLogger().log(Level.WARNING, "[crafts] " + id + ": points not written, kept for the next try", ex);
                    if (Bukkit.getPlayer(id) != null) batch.forEach((c, n) -> pending.computeIfAbsent(id, k -> new EnumMap<>(Profession.class)).merge(c, n, Long::sum));
                    return;
                }
                EnumMap<Profession, Long> s = stored.get(id);
                if (s != null) batch.forEach((c, n) -> s.merge(c, n, Long::sum));
            });
        });
    }

    /** Shutdown: every pending point written (SuldServices waits within its budget). */
    public CompletableFuture<Void> shutdown() {
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (UUID id : new HashSet<>(pending.keySet())) writes.add(flush(id, true));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    private long total(UUID id, Profession c) {
        EnumMap<Profession, Long> s = stored.get(id);
        EnumMap<Profession, Long> p = pending.get(id);
        return (s == null ? 0 : s.getOrDefault(c, 0L)) + (p == null ? 0 : p.getOrDefault(c, 0L));
    }

    /** A craft's state for a player whose totals are read (START until then). */
    public Profession.State state(UUID id, Profession c) {
        return loaded.contains(id) ? Profession.fromTotal(total(id, c)) : Profession.State.START;
    }

    public int level(UUID id, Profession c) {
        return state(id, c).level();
    }

    public boolean loaded(UUID id) {
        return loaded.contains(id);
    }

    /** A player's stored totals, read on the io pool (the armory's offline sheets; any thread). */
    public CompletableFuture<Map<Profession, Long>> totalsOf(UUID id) {
        return repo.totals(id);
    }

    /** Staff/QA: a craft set to the start of {@code level}. */
    public void set(UUID id, Profession c, int level) {
        long total = Profession.totalOf(new Profession.State(level, 0));
        EnumMap<Profession, Long> p = pending.get(id);
        if (p != null) p.remove(c);
        stored.computeIfAbsent(id, k -> new EnumMap<>(Profession.class)).put(c, total);
        repo.set(id, c, total, System.currentTimeMillis()).whenComplete((v, ex) -> {
            if (ex != null) plugin.getLogger().log(Level.WARNING, "[crafts] set of " + id + " not written", ex);
        });
    }

    private void earn(Player p, Profession c, int points) {
        UUID id = p.getUniqueId();
        int before = level(id, c);
        pending.computeIfAbsent(id, k -> new EnumMap<>(Profession.class)).merge(c, (long) points, Long::sum);
        int after = level(id, c);
        if (after <= before) return;
        p.sendMessage(Messages.success("⚒ " + c.displayName() + " " + after + "-р түвшин"
                + (Profession.yield(after) > Profession.yield(before) ? " · нэг цуглуулгаас " + Profession.yield(after) + " ширхэг" : "")));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        for (int t = 2; t <= Profession.MAX_TIER; t++) {
            if (Profession.tierLevel(t) > before && Profession.tierLevel(t) <= after) {
                p.sendMessage(Messages.accent("Шинэ: " + TIER[t] + " зэргийн " + nodeName(c, t).toLowerCase(Locale.ROOT) + " нээгдлээ."));
            }
        }
        for (int l = before + 1; l <= after; l++) {
            String title = Profession.title(c, l);
            if (title == null) continue;
            p.sendMessage(Component.text("✦ Шинэ цол: «" + title + "»", NamedTextColor.GOLD, TextDecoration.BOLD));
            if (l == Profession.MAX_LEVEL) Bukkit.broadcast(Component.text("✦ " + p.getName() + " — «" + title + "» боллоо!", NamedTextColor.GOLD, TextDecoration.BOLD));
        }
        services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of("profession_level", id,
                Map.<String, Object>of("craft", c.name(), "level", after)));
        services.achievements.record(p, mn.suld.api.achievement.Deed.CRAFT_LEVEL, c.name(), after);
    }

    // ================================================================== the plan

    /** The spawn moved (SuldPlugin, SpawnChangeEvent): every node stands by the new spawn, as the ovoos do. */
    public void spawnMoved() {
        replan();
    }

    private void replan() {
        World w = Bukkit.getWorlds().get(0);
        Location sp = w.getSpawnLocation();
        List<int[]> away = new ArrayList<>();
        try {
            away.addAll(keepAway.get());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "[crafts] the places to keep clear of could not be read", e);
        }
        clearOf = List.copyOf(away);
        // Kharkhorum's walls reach about 190 blocks out, into the inner areas: no node is planned inside them
        List<int[]> city = services.city().footprint(w.getName()).map(List::of).orElse(List.of());
        List<GatherPlan.Node> next = GatherPlan.plan(Content.pack().areas(), sp.getBlockX(), sp.getBlockZ(), w.getWorldBorder().getSize() / 2, away, city);
        if (next.equals(nodes)) return;
        for (Live l : live.values()) removeLive(l);
        live.clear();
        nowhere.clear();
        nodes = next;
        byId.clear();
        for (GatherPlan.Node n : next) byId.put(n.id(), n);
        plugin.getLogger().info("[crafts] " + next.size() + " gathering places by the spawn at " + sp.getBlockX() + " " + sp.getBlockZ());
    }

    /** Every node of the plan (veins, herb patches and pens). */
    public List<GatherPlan.Node> nodes() {
        return nodes;
    }

    // ================================================================== keeping the world

    private void keep() {
        if (!spawnSettled.getAsBoolean()) return;
        World w = Bukkit.getWorlds().get(0);
        long now = System.currentTimeMillis();
        for (GatherPlan.Node n : nodes) {
            if (n.kind() == GatherPlan.Kind.PEN) {
                if (activePen(n) && w.isChunkLoaded(n.x() >> 4, n.z() >> 4)) keepPen(w, n, now);
                continue;
            }
            Live l = live.get(n.id());
            if (l != null && !w.isChunkLoaded(l.x >> 4, l.z >> 4)) {
                live.remove(n.id()); // its entities left with their chunk (never saved)
                l = null;
            }
            if (l == null && !w.isChunkLoaded(n.x() >> 4, n.z() >> 4)) continue;
            if (l != null && !valid(l)) {
                removeLive(l);
                live.remove(n.id());
                l = null;
            }
            if (l == null) {
                if (nowhere.contains(n.id()) || !playerNear(w, n.x(), n.z()) || !loadedAround(w, n.x(), n.z(), DRY_SEARCH)) continue;
                l = place(w, n);
                if (l == null) {
                    nowhere.add(n.id()); // all its ground was loaded, and none of it was dry
                    continue;
                }
                live.put(n.id(), l);
            }
            show(n, l, spentUntil.getOrDefault(n.id(), 0L) > now, now);
        }
    }

    /**
     * Whether this is the spot its pen stands at, or will: the spot it was built at, or (none built yet) the first of
     * its spots (GatherPlan.PEN_ALTS) not yet found wanting this run.
     */
    private boolean activePen(GatherPlan.Node n) {
        String base = n.base();
        GatherPlan.Node first = null;
        for (int k = 0; k <= GatherPlan.PEN_ALTS; k++) {
            String id = k == 0 ? base : base + "~" + k;
            if (pens.containsKey(id)) return id.equals(n.id());
            GatherPlan.Node spot = byId.get(id);
            if (first == null && spot != null && !nowhere.contains(id)) first = spot;
        }
        return first != null && first.id().equals(n.id());
    }

    /** Whether every chunk within {@code r} blocks of (x, z) is loaded (a search there sees all its ground). */
    private static boolean loadedAround(World w, int x, int z, int r) {
        for (int cx = (x - r) >> 4; cx <= (x + r) >> 4; cx++) {
            for (int cz = (z - r) >> 4; cz <= (z + r) >> 4; cz++) if (!w.isChunkLoaded(cx, cz)) return false;
        }
        return true;
    }

    private static boolean playerNear(World w, int x, int z) {
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            if (Math.abs(l.getX() - x) < SHOW && Math.abs(l.getZ() - z) < SHOW) return true;
        }
        return false;
    }

    private static boolean valid(Live l) {
        Entity e = Bukkit.getEntity(l.hit);
        return e != null && e.isValid();
    }

    private static void removeLive(Live l) {
        List<UUID> all = new ArrayList<>(l.parts);
        all.add(l.hit);
        if (l.label != null) all.add(l.label);
        for (UUID u : all) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
    }

    /** The surface at (x, z) a node can stand on (solid, dry, not leaves), or null. */
    private static Block surface(World w, int x, int z) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return null;
        Block b = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (b.isLiquid() || !b.getType().isSolid() || b.getType() == Material.ICE || b.getType() == Material.PACKED_ICE
                || b.getType() == Material.BLUE_ICE || b.getType().name().endsWith("_LEAVES")) return null;
        Block above = b.getRelative(BlockFace.UP);
        if (above.isLiquid()) return null;
        return b;
    }

    /** The dry ground nearest the node's spot within {@code radius} blocks, or null (a lake, a river). */
    /** How far a vein or a patch keeps from the map's places when it looks for dry ground (a gate's footprint is ±8). */
    private static final int NODE_CLEAR = 12;

    private Block dryNear(World w, int x, int z, int radius) {
        Block s = offLimits(w, x, z, NODE_CLEAR, 4) ? null : surface(w, x, z);
        if (s != null) return s;
        for (int r = 2; r <= radius; r += 2) {
            for (int i = 0; i < 8 * r; i += 2) {
                double a = Math.PI * 2 * i / (8.0 * r);
                int bx = x + (int) Math.round(Math.cos(a) * r), bz = z + (int) Math.round(Math.sin(a) * r);
                if (offLimits(w, bx, bz, NODE_CLEAR, 4)) continue;
                Block b = surface(w, bx, bz);
                if (b != null) return b;
            }
        }
        return null;
    }

    /**
     * Whether (x, z) is closer than {@code clear} blocks to one of the map's places (a dungeon gate, an ovoo, a historic
     * site, a relay, the training yard, a world boss's ground) or within {@code cityMargin} blocks of Kharkhorum. The
     * plan keeps the spots clear of them; this keeps clear the ground a node or a pen moves to from its spot.
     */
    private boolean offLimits(World w, int x, int z, int clear, int cityMargin) {
        for (int[] p : clearOf) if (Math.hypot(p[0] - x, p[1] - z) < clear) return true;
        return services.city().near(w.getName(), x, z, cityMargin);
    }

    private Live place(World w, GatherPlan.Node n) {
        Block ground = dryNear(w, n.x(), n.z(), DRY_SEARCH);
        if (ground == null) {
            plugin.getLogger().info("[crafts] " + n.id() + " has no dry ground near " + n.x() + " " + n.z() + " (skipped this run)");
            return null;
        }
        int x = ground.getX(), y = ground.getY() + 1, z = ground.getZ();
        Location base = new Location(w, x + 0.5, y, z + 0.5);
        List<UUID> parts = new ArrayList<>();
        boolean vein = n.kind() == GatherPlan.Kind.VEIN;
        if (vein) {
            parts.add(block(base, ready(n), new Vector3f(-0.45f, 0f, -0.45f), 0.9f, 0.3f));
            parts.add(block(base, n.tier() % 2 == 0 ? Material.TUFF : Material.ANDESITE, new Vector3f(0.2f, 0f, -0.75f), 0.55f, -0.4f));
            parts.add(block(base, n.tier() % 2 == 0 ? Material.COBBLED_DEEPSLATE : Material.COBBLESTONE, new Vector3f(-0.85f, 0f, 0.15f), 0.5f, 0.7f));
        } else {
            Material plant = ready(n);
            parts.add(block(base, plant, new Vector3f(-0.5f, 0f, -0.5f), 1.0f, 0f));
            parts.add(block(base, plant, new Vector3f(0.05f, 0f, -0.25f), 0.8f, 0.9f));
            parts.add(block(base, plant, new Vector3f(-0.75f, 0f, 0.1f), 0.75f, -0.6f));
            parts.add(block(base, Material.FERN, new Vector3f(-0.2f, 0f, 0.15f), 0.7f, 0.4f));
        }
        Interaction hit = w.spawn(base, Interaction.class, it -> {
            it.setInteractionWidth(vein ? 1.6f : 1.4f);
            it.setInteractionHeight(vein ? 1.2f : 1.0f);
            it.setResponsive(true);
            own(it, n.id());
        });
        TextDisplay label = w.spawn(base.clone().add(0, vein ? 1.55 : 1.35, 0), TextDisplay.class, t -> {
            t.setBillboard(Display.Billboard.CENTER);
            t.setViewRange(0.2f); // about 13 blocks
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(0x66000000));
            t.text(labelText(n, false, 0));
            own(t, n.id());
        });
        return new Live(x, y, z, hit.getUniqueId(), parts, label.getUniqueId());
    }

    private BlockDisplay spawnBlock(Location at, Material m, Vector3f offset, float scale, float yaw) {
        return at.getWorld().spawn(at, BlockDisplay.class, d -> {
            d.setBlock(m.createBlockData());
            d.setTransformation(new Transformation(offset, new AxisAngle4f(yaw, 0, 1, 0), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            d.setViewRange(0.75f);
            own(d, null);
        });
    }

    private UUID block(Location at, Material m, Vector3f offset, float scale, float yaw) {
        return spawnBlock(at, m, offset, scale, yaw).getUniqueId();
    }

    private void own(Entity e, String node) {
        e.setPersistent(false);
        e.addScoreboardTag(TAG);
        if (node != null) e.getPersistentDataContainer().set(nodeKey, PersistentDataType.STRING, node);
    }

    /** The block a node shows while it can be gathered. */
    private static Material ready(GatherPlan.Node n) {
        if (n.kind() == GatherPlan.Kind.VEIN) return switch (n.tier()) {
            case 1 -> Material.IRON_ORE;
            case 2 -> Material.DEEPSLATE_IRON_ORE;
            case 3 -> Material.GOLD_ORE;
            default -> Material.DEEPSLATE_GOLD_ORE;
        };
        return n.tier() >= 3 ? Material.DANDELION : Material.ALLIUM;
    }

    /** …and once it is spent. */
    private static Material spent(GatherPlan.Node n) {
        if (n.kind() == GatherPlan.Kind.VEIN) return n.tier() % 2 == 0 ? Material.COBBLED_DEEPSLATE : Material.STONE;
        return Material.SHORT_GRASS;
    }

    private void show(GatherPlan.Node n, Live l, boolean isSpent, long now) {
        Entity label = l.label == null ? null : Bukkit.getEntity(l.label);
        if (isSpent || l.shownSpent != isSpent) {
            if (label instanceof TextDisplay t) t.text(labelText(n, isSpent, isSpent ? spentUntil.getOrDefault(n.id(), 0L) - now : 0));
        }
        if (l.shownSpent == isSpent) return;
        l.shownSpent = isSpent;
        Material main = isSpent ? spent(n) : ready(n);
        int plants = n.kind() == GatherPlan.Kind.VEIN ? 1 : 3;
        for (int i = 0; i < plants && i < l.parts.size(); i++) {
            if (Bukkit.getEntity(l.parts.get(i)) instanceof BlockDisplay d) d.setBlock(main.createBlockData());
        }
    }

    private Component labelText(GatherPlan.Node n, boolean isSpent, long leftMs) {
        Profession c = n.kind().craft();
        Component name = Component.text((n.kind() == GatherPlan.Kind.VEIN ? "⛏ " : "✿ ") + nodeName(c, n.tier()) + " · " + TIER[n.tier()],
                isSpent ? NamedTextColor.GRAY : GOLD, TextDecoration.BOLD);
        Component sub = isSpent ? Component.text("дахин " + clock(leftMs), NamedTextColor.GRAY)
                : Component.text(c.displayName() + " " + Profession.tierLevel(n.tier()) + "+ · баруун товч", CREAM);
        return name.append(Component.newline()).append(sub);
    }

    private static String clock(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    /** What a craft's nodes are called at a tier («Төмрийн судал», «Алтан судал», «Ганга», «Алтан гагнуур», …). */
    public static String nodeName(Profession c, int tier) {
        boolean fine = tier >= 3;
        return switch (c) {
            case TOMRIIN_DARKHAN -> fine ? "Алтан судал" : "Төмрийн судал";
            case EMIIN_BARIACH -> fine ? "Алтан гагнуур" : "Ганга";
            case ADUUCHIN -> "Малчны адуу";
            case ESGIICH -> "Малчны хонь";
        };
    }

    // ================================================================== the pens

    private static final int PEN_SLOTS_HORSE = GatherPlan.PEN_HORSES, PEN_SLOTS_SHEEP = GatherPlan.PEN_SHEEP;
    /** How far from its spot a vein or a patch looks for dry ground (blocks). */
    private static final int DRY_SEARCH = 24;
    /** How far from its spot a pen looks for its yard (blocks). */
    private static final int PEN_SEARCH = 64;
    /** The most a yard's ground may rise and fall (blocks); the fences stand taller where the yard is higher. */
    private static final int PEN_RANGE = 3;
    /** Beyond {@link #PEN_RANGE}, the most a yard is levelled by (blocks of fill or cut). */
    private static final int PEN_LEVEL = 8;

    private void keepPen(World w, GatherPlan.Node n, long now) {
        int[] b = pens.get(n.id());
        if (b == null) {
            if (nowhere.contains(n.id()) || !loadedAround(w, n.x(), n.z(), PEN_SEARCH + GatherPlan.PEN_HALF + 1)) return; // built when near
            b = buildPen(w, n);
            if (b == null) {
                nowhere.add(n.id()); // all its ground was loaded, and no yard there was flat and dry
                return;
            }
        }
        if (!w.isChunkLoaded(b[0] >> 4, b[2] >> 4) || !playerNear(w, b[0], b[2])) return;
        int h = GatherPlan.PEN_HALF;
        for (int i = 0; i < PEN_SLOTS_HORSE + PEN_SLOTS_SHEEP; i++) {
            boolean horse = i < PEN_SLOTS_HORSE;
            String slot = n.id() + (horse ? "#h" + i : "#s" + (i - PEN_SLOTS_HORSE));
            UUID u = animals.get(slot);
            Entity e = u == null ? null : Bukkit.getEntity(u);
            boolean isSpent = spentUntil.getOrDefault(slot, 0L) > now;
            if (e == null || !e.isValid()) {
                Location at = insidePen(w, b);
                if (at == null) continue;
                LivingEntity a = horse ? w.spawn(at, Horse.class, hz -> {
                    Horse.Color[] colours = {Horse.Color.BROWN, Horse.Color.CHESTNUT, Horse.Color.DARK_BROWN, Horse.Color.BLACK, Horse.Color.WHITE, Horse.Color.GRAY, Horse.Color.CREAMY};
                    hz.setColor(colours[ThreadLocalRandom.current().nextInt(colours.length)]);
                    hz.setStyle(Horse.Style.values()[ThreadLocalRandom.current().nextInt(Horse.Style.values().length)]);
                    hz.setTamed(false);
                    animal(hz, slot);
                }) : w.spawn(at, Sheep.class, sh -> {
                    sh.setColor(DyeColor.WHITE);
                    sh.setSheared(isSpent);
                    animal(sh, slot);
                });
                animals.put(slot, a.getUniqueId());
                continue;
            }
            Location l = e.getLocation();
            if (Math.abs(l.getX() - (b[0] + 0.5)) > h || Math.abs(l.getZ() - (b[2] + 0.5)) > h) {
                Location back = insidePen(w, b);
                if (back != null) e.teleport(back); // a gate left open: home again
            }
            if (e instanceof Sheep sh && sh.isSheared() != isSpent) sh.setSheared(isSpent);
        }
    }

    private void animal(LivingEntity a, String slot) {
        a.setPersistent(false);
        a.setRemoveWhenFarAway(false);
        a.setInvulnerable(true);
        a.addScoreboardTag(TAG);
        a.getPersistentDataContainer().set(nodeKey, PersistentDataType.STRING, slot);
        if (a instanceof org.bukkit.entity.Ageable ag) {
            ag.setAdult();
            if (ag instanceof org.bukkit.entity.Breedable br) {
                br.setBreed(false);
                br.setAgeLock(true);
            }
        }
    }

    private static Location insidePen(World w, int[] b) {
        int h = GatherPlan.PEN_HALF - 1;
        for (int t = 0; t < 12; t++) {
            int x = b[0] + ThreadLocalRandom.current().nextInt(-h + 1, h);
            int z = b[2] + ThreadLocalRandom.current().nextInt(-h + 1, h);
            Block s = ground(w, x, z);
            if (s != null && s.getRelative(BlockFace.UP).isPassable() && s.getRelative(0, 2, 0).isPassable()) return s.getLocation().add(0.5, 1, 0.5);
        }
        return null;
    }

    /**
     * A pen of spruce fences around a 9 × 9 yard, with a self-closing gate on the side that faces the spawn.
     * <ul>
     *   <li>It stands on the nearest dry square within {@link #PEN_SEARCH} blocks whose ground rises and falls at most
     *   {@link #PEN_RANGE} blocks; the fences are as many high as the yard steps up beside them.</li>
     *   <li>With no such square (a mountainside, dunes), on the most level dry square found, if it is within
     *   {@link #PEN_LEVEL} blocks: the herder levels it first, dirt into the hollows and the humps cut away, to the
     *   square's middle height.</li>
     * </ul>
     */
    private int[] buildPen(World w, GatherPlan.Node n) {
        int h = GatherPlan.PEN_HALF;
        int[] spot = null, best = null;
        int bestRange = Integer.MAX_VALUE, tried = 0, guarded = 0, rough = 0;
        outer:
        for (int r = 0; r <= PEN_SEARCH; r += r < 32 ? 4 : 8) {
            int steps = r == 0 ? 1 : Math.max(8, r); // a spot about every 6 blocks of the ring
            for (int i = 0; i < steps; i++) {
                double a = Math.PI * 2 * i / steps;
                int x = n.x() + (int) Math.round(Math.cos(a) * r), z = n.z() + (int) Math.round(Math.sin(a) * r);
                tried++;
                // never on the city, a gate, an ovoo, a site, a relay or the training yard, nor on another pen or a village
                if (offLimits(w, x, z, GatherPlan.KEEP + h + 1, h + 9) || protectedNear(w, x, z, h + 2)) {
                    guarded++;
                    continue;
                }
                int[] yard = yard(w, x, z, h + 1);
                if (yard == null) {
                    rough++; // water, ice, or more than PEN_LEVEL blocks of rise and fall
                    continue;
                }
                if (yard[1] - yard[0] <= PEN_RANGE) {
                    spot = new int[]{x, yard[0], z};
                    break outer;
                }
                if (yard[1] - yard[0] < bestRange) {
                    bestRange = yard[1] - yard[0];
                    best = new int[]{x, yard[2], z};
                }
            }
        }
        if (spot == null && best != null && bestRange <= PEN_LEVEL) {
            level(w, best[0], best[1], best[2], h + 1);
            spot = best;
        }
        if (spot == null) {
            plugin.getLogger().info("[crafts] " + n.id() + " has no dry yard near " + n.x() + " " + n.z() + " (skipped this run: "
                    + tried + " squares, " + guarded + " by a protected place, " + rough + " wet or too steep)");
            return null;
        }
        int cx = spot[0], cz = spot[2];
        Location sp = w.getSpawnLocation();
        double bx = sp.getX() - cx, bz = sp.getZ() - cz;
        // the gate: the middle of the side that faces the spawn
        int gx, gz;
        BlockFace facing;
        if (Math.abs(bx) > Math.abs(bz)) {
            gx = cx + (bx > 0 ? h : -h);
            gz = cz;
            facing = bx > 0 ? BlockFace.EAST : BlockFace.WEST;
        } else {
            gx = cx;
            gz = cz + (bz > 0 ? h : -h);
            facing = bz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        }
        for (int dx = -h; dx <= h; dx++) {
            for (int dz = -h; dz <= h; dz++) {
                if (Math.abs(dx) != h && Math.abs(dz) != h) continue;
                int x = cx + dx, z = cz + dz;
                Block top = ground(w, x, z);
                if (top == null) continue;
                int y = top.getY() + 1;
                // the yard's ground next to this post (one step in): the fence rises one block for each block it is higher
                int ix = x - Integer.signum(dx) * (Math.abs(dx) == h ? 1 : 0), iz = z - Integer.signum(dz) * (Math.abs(dz) == h ? 1 : 0);
                Block in = ground(w, ix, iz);
                int rise = in == null ? 0 : Math.max(0, Math.min(PEN_RANGE, in.getY() - top.getY()));
                Block post = w.getBlockAt(x, y, z);
                if (x == gx && z == gz) {
                    post.setType(Material.SPRUCE_FENCE_GATE, false);
                    if (post.getBlockData() instanceof Gate g) {
                        g.setFacing(facing);
                        g.setOpen(false);
                        post.setBlockData(g, false);
                    }
                    for (int k = 1; k <= rise; k++) w.getBlockAt(x, y + k, z).setType(Material.AIR, false); // the way in stays open
                } else {
                    for (int k = 0; k <= rise; k++) w.getBlockAt(x, y + k, z).setType(Material.SPRUCE_FENCE, true);
                }
            }
        }
        int[] b = {cx, spot[1], cz};
        pens.put(n.id(), b);
        guard(n.id(), b);
        savePens();
        plugin.getLogger().info("[crafts] built " + n.id() + " at " + cx + " " + spot[1] + " " + cz);
        return b;
    }

    /** Whether a protected SÜLD place (VillageProtection: ovoos, sites, pens) or a village reaches into the square at (x, z). */
    private static boolean protectedNear(World w, int x, int z, int r) {
        for (int dx = -r; dx <= r; dx += r) {
            for (int dz = -r; dz <= r; dz += r) {
                Block g = w.getHighestBlockAt(x + dx, z + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (mn.suld.plugin.worldbuild.VillageProtection.guarded(g) || mn.suld.plugin.worldbuild.VillageProtection.guarded(g.getRelative(BlockFace.UP))) return true;
            }
        }
        return false;
    }

    /**
     * The square of half-size {@code h} at (x, z): {lowest, highest, middle} ground height, or null when any of it is
     * water, ice or not loaded, or it rises and falls more than {@link #PEN_LEVEL} blocks.
     */
    private static int[] yard(World w, int x, int z, int h) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        int[] heights = new int[(2 * h + 1) * (2 * h + 1)];
        int k = 0;
        for (int dx = -h; dx <= h; dx++) {
            for (int dz = -h; dz <= h; dz++) {
                Block s = ground(w, x + dx, z + dz);
                if (s == null) return null;
                heights[k++] = s.getY();
                lo = Math.min(lo, s.getY());
                hi = Math.max(hi, s.getY());
                if (hi - lo > PEN_LEVEL) return null;
            }
        }
        java.util.Arrays.sort(heights);
        return new int[]{lo, hi, heights[heights.length / 2]};
    }

    /**
     * Levels the square of half-size {@code h} at (x, z) to {@code y}: each column is filled with dirt up to it (grass
     * on top) or cut down to it (what stood above, trees too, goes up to 8 blocks over), so the yard and its fence line
     * are flat.
     */
    private static void level(World w, int x, int y, int z, int h) {
        for (int dx = -h; dx <= h; dx++) {
            for (int dz = -h; dz <= h; dz++) {
                Block g = ground(w, x + dx, z + dz);
                if (g == null) continue;
                int top = g.getY();
                for (int yy = top + 1; yy <= y; yy++) w.getBlockAt(x + dx, yy, z + dz).setType(yy == y ? Material.GRASS_BLOCK : Material.DIRT, false);
                for (int yy = y + 1; yy <= Math.max(top, y) + 8; yy++) {
                    Block b = w.getBlockAt(x + dx, yy, z + dz);
                    if (!b.getType().isAir()) b.setType(Material.AIR, false); // a canopy over the yard goes with its trunk
                }
                if (top > y) w.getBlockAt(x + dx, y, z + dz).setType(Material.GRASS_BLOCK, false);
            }
        }
    }

    /**
     * The ground at (x, z) for a pen: the highest block that is not leaves, nor a tree's trunk, a cactus or a stalk
     * (they stand on the ground, they are not it); null on water, ice or an unloaded chunk.
     */
    private static Block ground(World w, int x, int z) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return null;
        Block b = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        while (b.getY() > w.getMinHeight() && standsOnGround(b.getType())) b = b.getRelative(BlockFace.DOWN);
        if (b.isLiquid() || !b.getType().isSolid() || b.getType() == Material.ICE || b.getType() == Material.PACKED_ICE
                || b.getType() == Material.BLUE_ICE || b.getType().name().endsWith("_LEAVES")) return null;
        if (b.getRelative(BlockFace.UP).isLiquid()) return null;
        return b;
    }

    private static boolean standsOnGround(Material m) {
        String n = m.name();
        return org.bukkit.Tag.LOGS.isTagged(m) || n.endsWith("_LEAVES") || m == Material.CACTUS || m == Material.BAMBOO
                || m == Material.SUGAR_CANE || n.endsWith("_MUSHROOM_BLOCK") || m == Material.MUSHROOM_STEM || n.endsWith("_FENCE")
                || n.endsWith("_FENCE_GATE") || n.endsWith("_WALL") || m == Material.POINTED_DRIPSTONE;
    }

    /** The fences, the gate and the yard cannot be broken or blown up (VillageProtection). */
    private static void guard(String id, int[] b) {
        int h = GatherPlan.PEN_HALF;
        mn.suld.plugin.worldbuild.VillageProtection.protect(id, b[0] - h - 1, b[1] - 3, b[2] - h - 1, b[0] + h + 1, b[1] + 6, b[2] + h + 1);
    }

    private void savePens() {
        mn.suld.plugin.persistence.AtomicYaml.write(plugin, penFile.toPath(), penYaml(pens)); // off the main thread, newest snapshot wins
    }

    /**
     * The built pens as {@code professions.yml}: one line a pen, {@code "<id> <x> <y> <z>"}, in id order. A pen's id
     * holds dots ({@code pen.tuul~2}), which a YAML path would split into nested keys, so the ids are never paths.
     */
    static YamlConfiguration penYaml(Map<String, int[]> pens) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, int[]> e : new java.util.TreeMap<>(pens).entrySet()) {
            int[] b = e.getValue();
            lines.add(e.getKey() + " " + b[0] + " " + b[1] + " " + b[2]);
        }
        YamlConfiguration st = new YamlConfiguration();
        st.set("pens", lines);
        return st;
    }

    /** The pens of a {@link #penYaml} file; a line that does not read is passed to {@code bad} and skipped. */
    static Map<String, int[]> readPens(YamlConfiguration st, java.util.function.Consumer<String> bad) {
        Map<String, int[]> out = new LinkedHashMap<>();
        for (String line : st.getStringList("pens")) {
            String[] f = line.trim().split("\\s+");
            try {
                if (f.length != 4 || !f[0].startsWith("pen.")) throw new NumberFormatException();
                out.put(f[0], new int[]{Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3])});
            } catch (NumberFormatException e) {
                bad.accept(line);
            }
        }
        return out;
    }

    /** A pen's gate closes itself two seconds after it was opened (a sheep never waits to be let out). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGate(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.SPRUCE_FENCE_GATE) return;
        Block g = e.getClickedBlock();
        if (penAt(g.getX(), g.getZ()) == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (g.getBlockData() instanceof Gate gate && gate.isOpen()) {
                gate.setOpen(false);
                g.setBlockData(gate, true);
                g.getWorld().playSound(g.getLocation(), Sound.BLOCK_FENCE_GATE_CLOSE, 0.8f, 1f);
            }
        }, 40L);
    }

    private String penAt(int x, int z) {
        int h = GatherPlan.PEN_HALF;
        for (Map.Entry<String, int[]> e : pens.entrySet()) {
            int[] b = e.getValue();
            if (Math.abs(x - b[0]) <= h && Math.abs(z - b[2]) <= h) return e.getKey();
        }
        return null;
    }

    // ================================================================== the animals stay the herder's

    private String nodeOf(Entity e) {
        return e == null || !e.getScoreboardTags().contains(TAG) ? null : e.getPersistentDataContainer().get(nodeKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAnimalHurt(EntityDamageEvent e) {
        if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onShear(PlayerShearEntityEvent e) {
        if (nodeOf(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockShear(org.bukkit.event.block.BlockShearEntityEvent e) {
        if (nodeOf(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeash(org.bukkit.event.entity.PlayerLeashEntityEvent e) {
        if (nodeOf(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTame(EntityTameEvent e) {
        if (nodeOf(e.getEntity()) != null) e.setCancelled(true);
    }

    /** A sheared pen sheep grows its wool back only when its slot is back, not by eating grass. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onRegrow(SheepRegrowWoolEvent e) {
        String slot = nodeOf(e.getEntity());
        if (slot != null && spentUntil.getOrDefault(slot, 0L) > System.currentTimeMillis()) e.setCancelled(true);
    }

    // ================================================================== gathering

    /** A right click on a node's click box or a pen animal: a gather begins. */
    @EventHandler(priority = EventPriority.LOW)
    public void onClick(PlayerInteractEntityEvent e) {
        String key = nodeOf(e.getRightClicked());
        if (key == null) return;
        e.setCancelled(true); // never a ride, a shear, a lead or a feed
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        begin(e.getPlayer(), key, e.getRightClicked());
    }

    /** A left click on a vein or a patch gathers too (it feels like mining). */
    @EventHandler(priority = EventPriority.LOW)
    public void onHitNode(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        String key = nodeOf(e.getAttacked());
        if (key == null) return;
        e.setCancelled(true);
        if (e.getAttacked() instanceof Interaction) begin(e.getPlayer(), key, e.getAttacked());
    }

    /** What a key names: the craft, the tier, and a display name. */
    private record Target(Profession craft, int tier, String name) {
    }

    private Target target(String key) {
        int hash = key.indexOf('#');
        if (hash > 0) {
            GatherPlan.Node pen = byId.get(key.substring(0, hash));
            if (pen == null) return null;
            Profession c = key.charAt(hash + 1) == 'h' ? Profession.ADUUCHIN : Profession.ESGIICH;
            return new Target(c, pen.tier(), nodeName(c, pen.tier()));
        }
        GatherPlan.Node n = byId.get(key);
        if (n == null || n.kind().craft() == null) return null;
        return new Target(n.kind().craft(), n.tier(), nodeName(n.kind().craft(), n.tier()));
    }

    private void begin(Player p, String key, Entity where) {
        UUID id = p.getUniqueId();
        Target t = target(key);
        if (t == null) return;
        if (gathering.containsKey(id)) return;
        if (!loaded.contains(id)) {
            HudService.notice(p, Component.text("Мэргэжил ачаалж байна… түр хүлээгээрэй.", NamedTextColor.GRAY));
            return;
        }
        if (services.isSoul.test(id)) {
            HudService.notice(p, Component.text("Сүнс юм цуглуулахгүй.", NamedTextColor.GRAY));
            return;
        }
        if (services.training != null && services.training.training(id)) return;
        long left = spentUntil.getOrDefault(key, 0L) - System.currentTimeMillis();
        if (left > 0) {
            HudService.notice(p, Component.text(t.name() + " хоосорсон — " + clock(left) + "-ийн дараа дахин.", NamedTextColor.GRAY));
            return;
        }
        int lvl = level(id, t.craft());
        if (!Profession.canGather(lvl, t.tier())) {
            p.sendMessage(Messages.error(TIER[t.tier()] + " зэргийн " + t.name().toLowerCase(Locale.ROOT) + ": " + t.craft().displayName() + " "
                    + Profession.tierLevel(t.tier()) + "-р түвшин хэрэгтэй (танд " + lvl + ")."));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return;
        }
        Location at = where.getLocation();
        gathering.put(id, new Gather(key, t.craft(), t.tier(), p.getLocation(), at, new int[]{0}));
        sound(p, at, t.craft());
    }

    private void tickGathers() {
        if (gathering.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Gather> en : new ArrayList<>(gathering.entrySet())) {
            UUID id = en.getKey();
            Gather g = en.getValue();
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline() || p.isDead()) {
                gathering.remove(id);
                continue;
            }
            Location l = p.getLocation();
            if (l.getWorld() != g.from().getWorld() || Math.hypot(l.getX() - g.from().getX(), l.getZ() - g.from().getZ()) > DRIFT) {
                gathering.remove(id);
                HudService.notice(p, Component.text("Хөдөлсөн тул цуглуулга тасарлаа.", NamedTextColor.GRAY));
                continue;
            }
            if (spentUntil.getOrDefault(g.key(), 0L) > now) {
                gathering.remove(id);
                HudService.notice(p, Component.text("Өөр хүн түрүүлж цуглууллаа.", NamedTextColor.GRAY));
                continue;
            }
            int t = g.ticks()[0] += 2;
            if (t % 8 == 0) {
                sound(p, g.at(), g.craft());
                int filled = Math.min(10, t * 10 / CHANNEL_TICKS);
                HudService.notice(p, Component.text(verb(g.craft()) + " ", CREAM)
                        .append(Component.text("▰".repeat(filled), GOLD)).append(Component.text("▱".repeat(10 - filled), NamedTextColor.DARK_GRAY)));
            }
            if (t >= CHANNEL_TICKS) {
                gathering.remove(id);
                finish(p, g);
            }
        }
    }

    /** Being hit breaks a gather. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || gathering.remove(p.getUniqueId()) == null) return;
        HudService.notice(p, Component.text("Цохиулсан тул цуглуулга тасарлаа.", NamedTextColor.GRAY));
    }

    private static String verb(Profession c) {
        return switch (c) {
            case TOMRIIN_DARKHAN -> "⛏ Олборлож байна";
            case EMIIN_BARIACH -> "✿ Түүж байна";
            case ADUUCHIN -> "♞ Хялгас самнаж байна";
            case ESGIICH -> "✂ Ноос хяргаж байна";
        };
    }

    private static void sound(Player p, Location at, Profession c) {
        World w = at.getWorld();
        switch (c) {
            case TOMRIIN_DARKHAN -> {
                w.playSound(at, Sound.BLOCK_STONE_HIT, 0.9f, 0.8f);
                w.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.5, 0), 6, 0.3, 0.3, 0.3, Material.STONE.createBlockData());
            }
            case EMIIN_BARIACH -> {
                w.playSound(at, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 0.7f, 1.2f);
                w.spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 0.6, 0), 3, 0.3, 0.2, 0.3, 0);
            }
            case ADUUCHIN -> w.playSound(at, Sound.ENTITY_HORSE_BREATHE, 0.8f, 1f);
            case ESGIICH -> w.playSound(at, Sound.ENTITY_SHEEP_SHEAR, 0.8f, 1f);
        }
    }

    private void finish(Player p, Gather g) {
        long now = System.currentTimeMillis();
        spentUntil.put(g.key(), now + RESPAWN_MS);
        UUID id = p.getUniqueId();
        int lvl = level(id, g.craft());
        String mat = ProfessionMaterials.gathered(g.craft(), g.tier());
        int amount = Profession.yield(lvl);
        ItemDefinition def = services.itemService().catalog().item(mat).orElse(null);
        if (def != null) {
            ItemInstance inst = services.itemService().generate(def, def.rarity(), 1, null, "gather");
            services.itemService().give(p, List.of(new LootDrop(inst, amount)), "gather");
        }
        int pts = Profession.points(lvl, g.tier());
        earn(p, g.craft(), pts);
        services.achievements.record(p, mn.suld.api.achievement.Deed.GATHER, g.craft().name(), 1);
        HudService.notice(p, Component.text("+" + amount + " " + (def == null ? mat : def.displayName()), GOLD)
                .append(Component.text(" · " + g.craft().displayName() + " +" + pts, CREAM)));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.1f);
        // the node shows it is spent at once (the keeper refreshes its clock)
        Live l = live.get(g.key());
        GatherPlan.Node n = byId.get(g.key());
        if (l != null && n != null) show(n, l, true, now);
        UUID a = animals.get(g.key());
        if (a != null && Bukkit.getEntity(a) instanceof Sheep sh) sh.setSheared(true);
    }

    // ================================================================== refining

    /** How many of each material id the player carries in the bag. */
    private Map<String, Integer> held(Player p) {
        Map<String, Integer> out = new HashMap<>();
        for (ItemStack it : p.getInventory().getStorageContents()) {
            ItemInstance ii = it == null ? null : services.items().read(it).orElse(null);
            if (ii != null) out.merge(ii.definitionId(), it.getAmount(), Integer::sum);
        }
        return out;
    }

    private void take(Player p, String materialId, int amount) {
        ItemStack[] st = p.getInventory().getStorageContents();
        for (int i = 0; i < st.length && amount > 0; i++) {
            ItemInstance ii = st[i] == null ? null : services.items().read(st[i]).orElse(null);
            if (ii == null || !ii.definitionId().equals(materialId)) continue;
            int use = Math.min(amount, st[i].getAmount());
            amount -= use;
            if (use == st[i].getAmount()) {
                p.getInventory().setItem(i, null);
            } else {
                st[i].setAmount(st[i].getAmount() - use);
                p.getInventory().setItem(i, st[i]); // written back: never rely on the array being the bag itself
            }
        }
    }

    /** Refines {@code times} times (0 = as many as the bag allows). Returns how many were done. */
    public int refine(Player p, ProfessionMaterials.Refine r, int times) {
        UUID id = p.getUniqueId();
        if (!loaded.contains(id)) {
            p.sendMessage(Messages.error("Мэргэжил ачаалж байна… түр хүлээгээд дахин."));
            return 0;
        }
        if (services.isSoul.test(id)) {
            p.sendMessage(Messages.error("Сүнс байхдаа боломжгүй."));
            return 0;
        }
        if (level(id, r.craft()) < r.level()) {
            p.sendMessage(Messages.error(r.name() + ": " + r.craft().displayName() + " " + r.level() + "-р түвшин хэрэгтэй."));
            return 0;
        }
        int can = r.times(held(p));
        int n = times <= 0 ? can : Math.min(times, can);
        if (n <= 0) {
            p.sendMessage(Messages.error(r.name() + ": " + describe(r.in()) + " хэрэгтэй."));
            return 0;
        }
        for (Map.Entry<String, Integer> e : r.in().entrySet()) take(p, e.getKey(), e.getValue() * n);
        List<LootDrop> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : r.out().entrySet()) {
            ItemDefinition def = services.itemService().catalog().item(e.getKey()).orElse(null);
            if (def == null) continue;
            out.add(new LootDrop(services.itemService().generate(def, def.rarity(), 1, null, "refine"), e.getValue() * n));
        }
        services.itemService().give(p, out, "refine");
        mn.suld.plugin.item.PlayerDataSaves.now(p); // the bag on disk at once: never the inputs back and the outputs too
        p.sendMessage(Messages.success(r.name() + " ×" + n + ": " + describe(r.out(), n)));
        p.playSound(p.getLocation(), r.craft() == Profession.TOMRIIN_DARKHAN ? Sound.BLOCK_ANVIL_USE : Sound.BLOCK_WOOL_PLACE, 0.6f, 1.2f);
        return n;
    }

    private String describe(Map<String, Integer> m) {
        return describe(m, 1);
    }

    private String describe(Map<String, Integer> m, int times) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            String name = services.itemService().catalog().item(e.getKey()).map(ItemDefinition::displayName).orElse(e.getKey());
            parts.add(e.getValue() * times + " " + name);
        }
        return String.join(", ", parts);
    }

    // ================================================================== the page

    /** The nearest node of a craft that the player's level opens, or null. */
    public GatherPlan.Node nearest(Player p, Profession c) {
        int lvl = level(p.getUniqueId(), c);
        Location l = p.getLocation();
        if (l.getWorld() != Bukkit.getWorlds().get(0)) return null;
        GatherPlan.Node best = null;
        double bestD = Double.MAX_VALUE;
        for (GatherPlan.Node n : nodes) {
            if (n.kind() == GatherPlan.Kind.PEN && !activePen(n)) continue;
            boolean mine = n.kind() == GatherPlan.Kind.PEN ? c == Profession.ADUUCHIN || c == Profession.ESGIICH : n.kind().craft() == c;
            if (!mine || !Profession.canGather(lvl, n.tier()) || nowhere.contains(n.id())) continue;
            double d = n.distanceTo(l.getBlockX(), l.getBlockZ());
            if (d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    /** {@code /mergejil}: the four crafts, their levels, the nearest place of each, and the refinings. */
    public void show(Player p) {
        UUID id = p.getUniqueId();
        List<Component> lines = new ArrayList<>();
        List<Pages.Button> buttons = new ArrayList<>();
        if (!loaded.contains(id)) lines.add(Pages.dim("Ачаалж байна… түр хүлээгээд дахин нээ."));
        for (Profession c : Profession.values()) {
            Profession.State s = state(id, c);
            long need = Profession.need(s.level());
            int bars = need <= 0 ? 10 : (int) Math.round(10.0 * s.points() / need);
            lines.add(Pages.line(c.displayName() + " " + s.level() + "/" + Profession.MAX_LEVEL + "  " + "▮".repeat(bars) + "▯".repeat(10 - bars)
                    + "  · нэгд " + Profession.yield(s.level()) + " ширхэг"));
            StringBuilder open = new StringBuilder();
            int next = 0;
            for (int t = 1; t <= Profession.MAX_TIER; t++) {
                if (Profession.canGather(s.level(), t)) open.append(open.isEmpty() ? "" : ", ").append(TIER[t]);
                else if (next == 0) next = t;
            }
            lines.add(Pages.dim("   " + c.verb() + " · нээлттэй: " + open + (next > 0 ? " · " + TIER[next] + " зэрэг " + Profession.tierLevel(next) + "-д" : "")));
            GatherPlan.Node n = nearest(p, c);
            if (n != null) {
                double dx = n.x() - p.getLocation().getX(), dz = n.z() - p.getLocation().getZ();
                lines.add(Pages.dim("   ойрын: " + nodeName(c, n.tier()) + " · " + TIER[n.tier()] + " — " + Math.round(Math.hypot(dx, dz)) + " м, "
                        + Navigation.compass(Navigation.bearing(dx, dz))));
                WaypointGuide gd = guide;
                if (gd != null) {
                    GatherPlan.Node to = n;
                    buttons.add(Pages.action("➜ " + nodeName(c, n.tier()), "Газрын зургийн замчлагч энд заана", pl -> {
                        WaypointGuide g2 = guide;
                        if (g2 == null) return;
                        g2.guide(pl, new WaypointGuide.Target(nodeName(c, to.tier()) + " · " + TIER[to.tier()], "⚒",
                                Bukkit.getWorlds().get(0).getName(), to.x() + 0.5, WaypointGuide.GROUND, to.z() + 0.5));
                    }));
                }
            }
        }
        lines.add(Pages.line("Боловсруулах (хаанаас ч):"));
        Map<String, Integer> have = held(p);
        for (ProfessionMaterials.Refine r : ProfessionMaterials.REFINES) {
            int n = r.times(have);
            boolean ok = level(id, r.craft()) >= r.level();
            lines.add(Pages.dim("   " + r.name() + ": " + describe(r.in()) + " → " + describe(r.out())
                    + (ok ? " · танд " + n + " удаа" : " · " + r.craft().displayName() + " " + r.level() + "+")));
            if (ok && n > 0) {
                buttons.add(Pages.action(r.name() + " ×1", describe(r.in()) + " → " + describe(r.out()), pl -> {
                    refine(pl, r, 1);
                    show(pl);
                }));
                if (n > 1) buttons.add(Pages.action(r.name() + " ×" + n, "Бүгдийг", pl -> {
                    refine(pl, r, 0);
                    show(pl);
                }));
            }
        }
        lines.add(Pages.dim("Тал нутгийн судал, ургамал, малчны хашаанд баруун товчоор 2 секунд цуглуулна; нэг цэг 5 минутад сэргэнэ."));
        Pages.text(p, "МЭРГЭЖИЛ", lines, buttons, null);
    }

    // ================================================================== staff and the bots

    /** {@code /suld gather list}: the nearest nodes to a player, with their state (lines for the console). */
    public List<String> describe(Player p, int max) {
        Location l = p.getLocation();
        long now = System.currentTimeMillis();
        List<GatherPlan.Node> sorted = new ArrayList<>();
        for (GatherPlan.Node n : nodes) {
            // a pen's other spots are listed once tried: the one it stands at (or will), and the ones found wanting
            if (n.kind() != GatherPlan.Kind.PEN || activePen(n) || pens.containsKey(n.id()) || nowhere.contains(n.id())) sorted.add(n);
        }
        sorted.sort(java.util.Comparator.comparingDouble(n -> n.distanceTo(l.getBlockX(), l.getBlockZ())));
        List<String> out = new ArrayList<>();
        for (GatherPlan.Node n : sorted.subList(0, Math.min(max, sorted.size()))) {
            Live lv = live.get(n.id());
            int[] pen = pens.get(n.id());
            String at = lv != null ? lv.x + " " + lv.y + " " + lv.z : pen != null ? pen[0] + " " + pen[1] + " " + pen[2] : n.x() + " ? " + n.z();
            String state = n.kind() == GatherPlan.Kind.PEN ? (pen != null ? "built" : nowhere.contains(n.id()) ? "nowhere" : "unbuilt")
                    : lv == null ? (nowhere.contains(n.id()) ? "nowhere" : "absent")
                    : spentUntil.getOrDefault(n.id(), 0L) > now ? "spent" : "ready";
            out.add("node " + n.id() + " " + n.kind().name().toLowerCase(Locale.ROOT) + " T" + n.tier() + " " + at + " " + state
                    + " " + Math.round(n.distanceTo(l.getBlockX(), l.getBlockZ())) + "m");
        }
        return out;
    }

    /** Where a node stands now (its live spot, a built pen's middle, else the plan's), or null. */
    public Location where(String nodeId) {
        GatherPlan.Node n = byId.get(nodeId);
        if (n == null) return null;
        World w = Bukkit.getWorlds().get(0);
        Live lv = live.get(nodeId);
        if (lv != null) return new Location(w, lv.x + 0.5, lv.y, lv.z + 0.5);
        int[] pen = pens.get(nodeId);
        if (pen != null) return new Location(w, pen[0] + 0.5, pen[1] + 1, pen[2] + 0.5);
        return new Location(w, n.x() + 0.5, w.getHighestBlockYAt(n.x(), n.z()) + 1, n.z() + 0.5);
    }

    /** Staff/QA: every spent node and animal is back at once. */
    public void respawnAll() {
        spentUntil.clear();
    }

    /** The material ids the crafts gather, for the guard and the sell window. */
    public static Set<String> materials() {
        return new java.util.LinkedHashSet<>(ProfessionMaterials.all());
    }

    /** For tests: the plan's node of an id. */
    GatherPlan.Node node(String id) {
        return byId.get(id);
    }

    /** The slot ids of a pen's animals ({@code pen.x#h0} … {@code #s3}). */
    static List<String> slots(String penId) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < GatherPlan.PEN_HORSES; i++) out.add(penId + "#h" + i);
        for (int i = 0; i < GatherPlan.PEN_SHEEP; i++) out.add(penId + "#s" + i);
        return out;
    }

    /** For the page's legend: the four crafts and their verbs, in order. */
    static Map<Profession, String> legend() {
        Map<Profession, String> m = new LinkedHashMap<>();
        for (Profession c : Profession.values()) m.put(c, c.verb());
        return m;
    }
}
