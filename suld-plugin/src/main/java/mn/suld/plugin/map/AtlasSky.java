package mn.suld.plugin.map;

import io.papermc.paper.datacomponent.DataComponentTypes;
import mn.suld.api.map.AtlasView;
import mn.suld.api.map.MapGeometry;
import mn.suld.api.map.TileComposer;
import mn.suld.api.region.Area;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.WorldContent;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Rotation;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * «Сүлдийн Атлас», the world map screen (docs/WORLD_MAP.md), in the sky-window engine of the menu and the skill tree:
 * the player is seated on a private stage high above where they stand and looks at a wall of 5 × 5 maps in invisible
 * glow item frames (one pooled map view per frame, {@link AtlasFrames}), the atlas frame drawn around them (display
 * quads from the pack), places and labels in front of them. Everything on the stage is visible to its owner only.
 * <p>
 * The crosshair is the cursor: the block under it is read out in the cartouche, a place under it shows its tooltip, a
 * left click on a place points the compass at it, on a tab runs it. The wheel zooms toward the cursor (from 1 block a
 * pixel to the whole world), W/A/S/D pan by a quarter of the wall, Shift closes. Left tabs choose which places show,
 * right tabs: the whole world, back to me, zoom in, zoom out. The map never shows land the server has not generated.
 */
public final class AtlasSky implements Listener, TabExecutor {

    static final String TAG = "suld_atlas";
    static final int COLS = 5, ROWS = 5;
    /** The camera stands DIST blocks in front of the wall; the stage stands RISE blocks over the world's top. */
    static final double DIST = 5.0;
    static final int RISE = 144;
    static final long COMBAT_MS = 8000;
    /** Panel layout (blocks, u right, v up, (0, 0) the middle of the wall), measured from the pack's textures (128 px a block). */
    static final double PX = 1.0 / 128;
    static final double FRONT_W = 784 * PX, FRONT_H = 864 * PX, FRONT_V = -12 * PX;
    static final double TAB_W = 64 * PX, TAB_H = 48 * PX, TAB_STEP = 58 * PX, TAB_U = 406 * PX;
    static final double TITLE_V = 391 * PX, CART_V = -388 * PX;
    static final double MARKER = 24 * PX, MARKER_BIG = 32 * PX;

    private static final TextColor GOLD = TextColor.fromHexString("#ECBE4C"), CREAM = TextColor.fromHexString("#F4E8C8"),
            GREY = TextColor.fromHexString("#B9B3A4"), CYAN = TextColor.fromHexString("#56E2EC");
    private static final NamespacedKey RETURN = new NamespacedKey("suld", "atlas_return");
    private static final byte[] BLANK = new byte[TileComposer.PIXELS];

    /** The left tabs: which places show. */
    enum Filter {
        ALL("all", "Бүгд", "Бүх газар"),
        CITY("city", "Хот, өртөө", "Хархорум, өртөөнүүд, дасгалын талбай"),
        DUNGEON("dungeon", "Агуй", "Арван агуйн үүд"),
        OVOO("ovoo", "Овоо", "Нутаг бүрийн овоо"),
        SITE("site", "Түүхэн газар", "Түүхэн дурсгалт газрууд"),
        BOSS("boss", "Аварга дайсан", "Аварга дайсны үүр"),
        PIN("pin", "Миний тэмдэг", "Таны тавьсан тэмдгүүд");

        final String icon, label, hint;

        Filter(String icon, String label, String hint) {
            this.icon = icon;
            this.label = label;
            this.hint = hint;
        }

        boolean shows(MapPois.Kind k) {
            return switch (this) {
                case ALL -> true;
                case CITY -> k == MapPois.Kind.CITY || k == MapPois.Kind.RELAY || k == MapPois.Kind.TRAINING;
                case DUNGEON -> k == MapPois.Kind.DUNGEON;
                case OVOO -> k == MapPois.Kind.OVOO;
                case SITE -> k == MapPois.Kind.SITE;
                case BOSS -> k == MapPois.Kind.BOSS;
                case PIN -> false;
            };
        }
    }

    private record Tool(String icon, String label, String hint) {
    }

    private static final List<Tool> TOOLS = List.of(new Tool("world", "Бүх ертөнц", "Бүх нутгийг нэг дор"),
            new Tool("me", "Миний байр", "Таны зогсож буй газар руу"), new Tool("plus", "Ойртуулах", "Дугуйгаар ч болно"),
            new Tool("minus", "Холдуулах", "Дугуйгаар ч болно"), new Tool("pin_add", "Тэмдэг тавих", "Удахгүй"));

    /** A thing under the cursor: a tab or a place. */
    private record Hit(double u, double v, double w, double h, Runnable click, Component name, List<Component> lore, ItemDisplay bg, String model,
                       String hoverModel) {
        boolean contains(double x, double y) {
            return Math.abs(x - u) <= w / 2 && Math.abs(y - v) <= h / 2;
        }
    }

    private final class Session {
        final Player p;
        final Location origin;
        final boolean invulnerable;
        final List<Entity> stage = new ArrayList<>(), marks = new ArrayList<>();
        final List<Hit> tabs = new ArrayList<>(), places = new ArrayList<>();
        ItemDisplay seat;
        Location plane;
        double eyeOff;
        TextDisplay tooltip, cartouche, title;
        AtlasView view;
        Filter filter = Filter.ALL;
        Hit hover;
        String lastCursor = "";
        int gen;
        boolean ready, closing, dirty, up, down, left, right;
        int heldTicks;
        long lastClick;

        Session(Player p) {
            this.p = p;
            this.origin = p.getLocation().clone();
            this.invulnerable = p.isInvulnerable();
        }
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final MapService map;
    private final AtlasFrames frames;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> lastCombat = new HashMap<>();
    /** Set by SuldPlugin: true while another sky window is open for the player (it is closed first). */
    public volatile java.util.function.Consumer<Player> closeOthers = p -> { };

    public AtlasSky(Plugin plugin, SuldServices services, MapService map) {
        this.plugin = plugin;
        this.services = services;
        this.map = map;
        this.frames = new AtlasFrames(plugin, COLS * ROWS);
    }

    public void start() {
        frames.start();
        map.tiles().listen((zoom, tx, tz) -> Bukkit.getScheduler().runTask(plugin, () -> tileChanged(zoom, tx, tz)));
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public boolean isOpen(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    // ------------------------------------------------------------------------------------------------ the command

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Messages.error("Тоглогч л газрын зураг нээнэ."));
            return true;
        }
        if (isOpen(p)) {
            close(p);
            return true;
        }
        String why = blocked(p);
        if (why != null) {
            p.sendMessage(Messages.error(why));
            return true;
        }
        open(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }

    /** Why the atlas cannot open now, or null. */
    private String blocked(Player p) {
        if (p.getWorld() != Bukkit.getWorlds().get(0)) return "Газрын зураг зөвхөн Их Монголын нутагт нээгдэнэ.";
        if (services.dungeons().isInAnyRun(p.getUniqueId())) return "Агуй дотор газрын зураг нээгдэхгүй.";
        if (System.currentTimeMillis() - lastCombat.getOrDefault(p.getUniqueId(), 0L) < COMBAT_MS) return "Тулалдаанаас гараад нээнэ үү.";
        if (p.isInsideVehicle() || p.isGliding() || !p.isFlying() && (p.getFallDistance() > 1.5f || p.getVelocity().getY() < -0.6)) {
            return "Газар дээр зогсож байхдаа нээнэ үү.";
        }
        if (p.isDead()) return "Одоо нээх боломжгүй.";
        return null;
    }

    // ------------------------------------------------------------------------------------------------ open / close

    private void open(Player p) {
        closeOthers.accept(p);
        Session s = new Session(p);
        sessions.put(p.getUniqueId(), s);
        p.closeInventory();
        Location o = s.origin;
        p.getPersistentDataContainer().set(RETURN, PersistentDataType.STRING, o.getWorld().getName() + ";" + o.getX() + ";" + o.getY() + ";" + o.getZ()
                + ";" + o.getYaw() + ";" + o.getPitch());
        World w = o.getWorld();
        Location stage = new Location(w, Math.floor(o.getX()) + 0.5, w.getMaxHeight() + RISE, Math.floor(o.getZ()) + 0.5, 0f, 0f);
        p.setInvulnerable(true);
        p.setFallDistance(0);
        services.movement.hold(p.getUniqueId(), "atlas seat");
        p.teleport(stage);
        s.seat = w.spawn(stage, ItemDisplay.class, d -> {
            hidden(d);
            d.setTeleportDuration(2);
        });
        s.stage.add(s.seat);
        p.showEntity(plugin, s.seat);
        s.seat.addPassenger(p);
        frames.open(p.getUniqueId());
        MapGeometry g = map.geometry();
        s.view = new AtlasView(COLS, ROWS, o.getX(), o.getZ(), 2).clamp(g);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (sessions.get(p.getUniqueId()) != s || !p.isOnline()) return;
            build(s);
            s.ready = true;
            redraw(s);
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.0f);
        }, 2L);
    }

    public void close(Player p) {
        close(p, true);
    }

    private void close(Player p, boolean goBack) {
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) return;
        s.closing = true;
        for (Entity e : s.marks) if (e.isValid()) e.remove();
        for (Entity e : s.stage) if (e.isValid()) e.remove();
        frames.close(p.getUniqueId());
        if (p.isInsideVehicle()) p.leaveVehicle();
        if (goBack) p.teleport(s.origin);
        p.setFallDistance(0);
        p.setInvulnerable(s.invulnerable);
        services.movement.release(p.getUniqueId(), "atlas seat");
        services.movement.exempt(p.getUniqueId(), 20, "atlas return");
        p.getPersistentDataContainer().remove(RETURN);
    }

    public void shutdown() {
        for (Session s : new ArrayList<>(sessions.values())) close(s.p);
    }

    // ------------------------------------------------------------------------------------------------ the stage

    private void hidden(Display d) {
        d.setPersistent(false);
        d.setVisibleByDefault(false);
        d.addScoreboardTag(TAG);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setShadowRadius(0);
        d.setViewRange(2f);
    }

    private static ItemStack quad(String model) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("suld", "entity/atlas/" + model));
        it.setItemMeta(meta);
        return it;
    }

    /** World position of a panel point (the viewer faces +Z, so screen-right is world -X). */
    private Location at(Session s, double u, double v, double towardViewer) {
        return s.plane.clone().add(-u, v, -towardViewer);
    }

    private <T extends Entity> T own(Session s, List<Entity> into, T e) {
        into.add(e);
        s.p.showEntity(plugin, e);
        return e;
    }

    private ItemDisplay picture(Session s, List<Entity> into, String model, double u, double v, double w, double h, double towardViewer, float turn) {
        return own(s, into, s.p.getWorld().spawn(at(s, u, v, towardViewer), ItemDisplay.class, d -> {
            hidden(d);
            d.setItemStack(quad(model));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateZ(turn), new Vector3f((float) w, (float) h, 1), new Quaternionf()));
        }));
    }

    private TextDisplay text(Session s, List<Entity> into, double u, double v, Component c, float scale, int width, int background) {
        return own(s, into, s.p.getWorld().spawn(at(s, u, v, 0.06), TextDisplay.class, t -> {
            hidden(t);
            t.setBillboard(Display.Billboard.FIXED);
            t.setRotation(180f, 0f);
            t.setBackgroundColor(Color.fromARGB(background));
            t.setShadowed(true);
            t.setLineWidth(width);
            t.text(c.font() == null ? c.font(mn.suld.plugin.ui.StyleFormat.HD) : c);
            t.setTransformation(new Transformation(new Vector3f(0, (float) (-0.125 * scale), 0), new Quaternionf(), new Vector3f(scale), new Quaternionf()));
        }));
    }

    /** The wall, the frame, the tabs, the title and the cartouche. Built once per opening. */
    private void build(Session s) {
        Player p = s.p;
        s.eyeOff = p.getEyeLocation().getY() - s.seat.getLocation().getY();
        World w = p.getWorld();
        Location st = s.seat.getLocation();
        // the frames hang on block faces: the wall's middle is a block centre, the camera sits level with it
        int bx = (int) Math.floor(st.getX());
        int by = (int) Math.floor(st.getY() + s.eyeOff);
        int bz = (int) Math.floor(st.getZ() + DIST);
        double faceZ = bz + 15.0 / 16;
        s.plane = new Location(w, bx + 0.5, by + 0.5, faceZ - 0.004);
        Location cam = new Location(w, bx + 0.5, by + 0.5 - s.eyeOff, faceZ - DIST, p.getLocation().getYaw(), p.getLocation().getPitch());
        s.seat.teleport(cam, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
        // the map wall: column 0 is on the viewer's left (world +x, the viewer faces +z)
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int frame = row * COLS + col;
                Location at = new Location(w, bx + (COLS / 2 - col), by + (ROWS / 2 - row), bz);
                ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
                org.bukkit.inventory.meta.MapMeta mm = (org.bukkit.inventory.meta.MapMeta) mapItem.getItemMeta();
                mm.setMapView(frames.view(frame));
                mapItem.setItemMeta(mm);
                own(s, s.stage, w.spawn(at, GlowItemFrame.class, f -> {
                    f.setFacingDirection(BlockFace.NORTH, true);
                    f.setPersistent(false);
                    f.setVisibleByDefault(false);
                    f.addScoreboardTag(TAG);
                    f.setVisible(false);
                    f.setFixed(true);
                    f.setInvulnerable(true);
                    f.setSilent(true);
                    f.setItem(mapItem, false);
                    f.setRotation(Rotation.NONE);
                }));
            }
        }
        // a dark room around the camera and the wall, the parchment behind the maps, the frame in front of them
        room(s, w, cam);
        picture(s, s.stage, "back", 0, 0, COLS, ROWS, -0.03, 0f);
        picture(s, s.stage, "front", 0, FRONT_V, FRONT_W, FRONT_H, 0.012, 0f);
        s.title = text(s, s.stage, 0, TITLE_V, Component.text("СҮЛДИЙН АТЛАС", GOLD, TextDecoration.BOLD), 1.2f, 300, 0);
        s.cartouche = text(s, s.stage, 0, CART_V, Component.empty(), 0.7f, 420, 0);
        s.tooltip = text(s, s.stage, 0, 0, Component.empty(), 0.6f, 240, 0);
        // tabs
        Filter[] fs = Filter.values();
        double top = (fs.length - 1) * TAB_STEP / 2;
        for (int i = 0; i < fs.length; i++) {
            Filter f = fs[i];
            double v = top - i * TAB_STEP;
            String model = "tab_" + f.icon;
            ItemDisplay bg = picture(s, s.stage, s.filter == f ? model + "_on" : model, -TAB_U, v, TAB_W, TAB_H, 0.02, 0f);
            s.tabs.add(new Hit(-TAB_U, v, TAB_W, TAB_H, () -> setFilter(s, f), Component.text(f.label, GOLD, TextDecoration.BOLD),
                    List.of(Component.text(f.hint, GREY)), bg, model, model + "_hover"));
        }
        top = (TOOLS.size() - 1) * TAB_STEP / 2;
        for (int i = 0; i < TOOLS.size(); i++) {
            Tool t = TOOLS.get(i);
            double v = top - i * TAB_STEP;
            String model = "tab_" + t.icon();
            ItemDisplay bg = picture(s, s.stage, model, TAB_U, v, TAB_W, TAB_H, 0.02, 0f);
            Runnable click = switch (t.icon()) {
                case "world" -> () -> setView(s, new AtlasView(COLS, ROWS, 0, 0, AtlasView.wholeWorldZoom(COLS, ROWS, map.geometry())).clamp(map.geometry()));
                case "me" -> () -> setView(s, s.view.centreOn(s.origin.getX(), s.origin.getZ(), map.geometry()));
                case "plus" -> () -> setView(s, s.view.zoomAt(s.view.zoom() - 1, 0, 0, map.geometry()));
                case "minus" -> () -> setView(s, s.view.zoomAt(s.view.zoom() + 1, 0, 0, map.geometry()));
                default -> null;
            };
            s.tabs.add(new Hit(TAB_U, v, TAB_W, TAB_H, click, Component.text(t.label(), GOLD, TextDecoration.BOLD),
                    List.of(Component.text(t.hint(), GREY)), bg, model, click == null ? model : model + "_hover"));
        }
    }

    /** A navy box around the camera and the wall, so looking away from the atlas shows no sky. */
    private void room(Session s, World w, Location cam) {
        ItemStack navy = new ItemStack(Material.PAPER);
        ItemMeta meta = navy.getItemMeta();
        meta.setItemModel(new NamespacedKey("suld", "entity/skytree/line"));
        navy.setItemMeta(meta);
        navy.setData(DataComponentTypes.CUSTOM_MODEL_DATA, io.papermc.paper.datacomponent.item.CustomModelData.customModelData()
                .addColor(Color.fromRGB(0x0B1226)).build());
        double cx = s.plane.getX(), cy = s.plane.getY(), z0 = cam.getZ() - 1.5, z1 = s.plane.getZ() + 0.7, hw = 9, hh = 6;
        double mz = (z0 + z1) / 2, d = z1 - z0;
        Quaternionf ry = new Quaternionf().rotateY((float) (Math.PI / 2)), rx = new Quaternionf().rotateX((float) (Math.PI / 2));
        wall(s, w, navy, new Location(w, cx, cy, z1), new Quaternionf(), new Vector3f((float) (2 * hw), (float) (2 * hh), 1));
        wall(s, w, navy, new Location(w, cx, cy, z0), new Quaternionf(), new Vector3f((float) (2 * hw), (float) (2 * hh), 1));
        wall(s, w, navy, new Location(w, cx + hw, cy, mz), ry, new Vector3f((float) d, (float) (2 * hh), 1));
        wall(s, w, navy, new Location(w, cx - hw, cy, mz), ry, new Vector3f((float) d, (float) (2 * hh), 1));
        wall(s, w, navy, new Location(w, cx, cy + hh, mz), rx, new Vector3f((float) (2 * hw), (float) d, 1));
        wall(s, w, navy, new Location(w, cx, cy - hh, mz), rx, new Vector3f((float) (2 * hw), (float) d, 1));
    }

    private void wall(Session s, World w, ItemStack item, Location at, Quaternionf rot, Vector3f scale) {
        own(s, s.stage, w.spawn(at, ItemDisplay.class, d -> {
            hidden(d);
            d.setViewRange(4f);
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(), rot, scale, new Quaternionf()));
        }));
    }

    // ------------------------------------------------------------------------------------------------ the view

    private void setView(Session s, AtlasView v) {
        if (v.equals(s.view)) return;
        s.view = v;
        redraw(s);
    }

    private void setFilter(Session s, Filter f) {
        if (s.filter == f) return;
        s.filter = f;
        for (Hit h : s.tabs) {
            boolean left = h.u() < 0;
            if (!left) continue;
            Filter hf = Filter.values()[s.tabs.indexOf(h)];
            if (h.bg().isValid()) h.bg().setItemStack(quad(hf == f ? h.model() + "_on" : h.model()));
        }
        placeMarkers(s);
    }

    /** Recomposes every frame of the wall from the map's tiles (on the workers), then shows it; and moves the places. */
    private void redraw(Session s) {
        int gen = ++s.gen;
        AtlasView v = s.view;
        MapTiles tiles = map.tiles();
        List<CompletableFuture<byte[]>> wanted = new ArrayList<>();
        List<int[]> keys = new ArrayList<>();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int[] t = v.tilesOf(col, row);
                for (int tz = t[1]; tz <= t[3]; tz++) {
                    for (int tx = t[0]; tx <= t[2]; tx++) {
                        wanted.add(tiles.tile(v.zoom(), tx, tz));
                        keys.add(new int[]{col, row, tx, tz});
                    }
                }
            }
        }
        CompletableFuture.allOf(wanted.toArray(CompletableFuture[]::new)).whenComplete((x, err) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sessions.get(s.p.getUniqueId()) != s || s.gen != gen) return; // a newer view is on its way
                byte[][] out = new byte[COLS * ROWS][];
                for (int i = 0; i < keys.size(); i++) {
                    int[] k = keys.get(i);
                    CompletableFuture<byte[]> f = wanted.get(i);
                    byte[] tile = f.isCompletedExceptionally() ? null : f.getNow(null);
                    int frame = k[1] * COLS + k[0];
                    if (out[frame] == null) out[frame] = new byte[TileComposer.PIXELS];
                    if (tile != null) v.copy(tile, k[2], k[3], k[0], k[1], out[frame]);
                }
                for (int f = 0; f < out.length; f++) frames.show(s.p, f, out[f] == null ? BLANK : out[f]);
            });
        });
        placeMarkers(s);
        cursor(s, true);
    }

    /** A tile changed (a chunk was read): redraw the atlases that show it. */
    private void tileChanged(int zoom, int tx, int tz) {
        for (Session s : sessions.values()) {
            if (!s.ready || s.view.zoom() != zoom) continue;
            int[] a = s.view.tilesOf(0, 0), b = s.view.tilesOf(COLS - 1, ROWS - 1);
            if (tx >= a[0] && tx <= b[2] && tz >= a[1] && tz <= b[3]) s.dirty = true;
        }
    }

    // ------------------------------------------------------------------------------------------------ places

    private void placeMarkers(Session s) {
        for (Entity e : s.marks) if (e.isValid()) e.remove();
        s.marks.clear();
        s.places.clear();
        if (s.hover != null && !s.tabs.contains(s.hover)) s.hover = null;
        AtlasView v = s.view;
        int zoom = v.zoom();
        List<double[]> boxes = new ArrayList<>(); // labels already placed (u0, v0, u1, v1)
        List<MapPois.Poi> pois = new ArrayList<>(map.pois.get());
        pois.sort((a, b) -> Integer.compare(rank(b.kind()), rank(a.kind())));
        for (MapPois.Poi poi : pois) {
            if (!s.filter.shows(poi.kind()) || !visibleAt(poi.kind(), zoom)) continue;
            double u = v.u(poi.x() + 0.5), vv = v.v(poi.z() + 0.5);
            if (Math.abs(u) > COLS / 2.0 - 0.05 || Math.abs(vv) > ROWS / 2.0 - 0.05) continue;
            boolean big = poi.kind() == MapPois.Kind.CITY || poi.kind() == MapPois.Kind.DUNGEON || poi.kind() == MapPois.Kind.BOSS;
            double size = big ? MARKER_BIG : MARKER;
            picture(s, s.marks, "marker_" + poi.kind().name().toLowerCase(Locale.ROOT), u, vv, size, size, 0.03, 0f);
            Runnable click = () -> point(s, poi);
            s.places.add(new Hit(u, vv, size * 1.3, size * 1.3, click, Component.text(poi.name(), GOLD, TextDecoration.BOLD), describe(s, poi), null, null, null));
            if (labelled(poi.kind(), zoom)) {
                double lw = Math.max(0.3, poi.name().length() * 0.045), lv = vv - size / 2 - 0.07;
                double[] box = {u - lw / 2, lv - 0.06, u + lw / 2, lv + 0.06};
                boolean free = Math.abs(box[0]) < COLS / 2.0 && Math.abs(box[2]) < COLS / 2.0 && box[1] > -ROWS / 2.0;
                for (double[] b : boxes) if (box[0] < b[2] && b[0] < box[2] && box[1] < b[3] && b[1] < box[3]) free = false;
                if (free) {
                    boxes.add(box);
                    text(s, s.marks, u, lv, Component.text(poi.name(), CREAM), 0.42f, 300, 0x99000000);
                }
            }
        }
        // where the player stood, and which way they looked
        double pu = v.u(s.origin.getX()), pv = v.v(s.origin.getZ());
        if (v.onWall(pu, pv)) {
            // the arrow points up (north) unturned; yaw 0 looks south: turn clockwise on screen by yaw + 180°
            float turn = (float) Math.toRadians(s.origin.getYaw() + 180);
            picture(s, s.marks, "marker_player", pu, pv, MARKER_BIG, MARKER_BIG, 0.035, turn);
        }
    }

    private static int rank(MapPois.Kind k) {
        return switch (k) {
            case CITY -> 6;
            case DUNGEON -> 5;
            case BOSS -> 4;
            case TRAINING -> 3;
            case RELAY -> 2;
            case SITE -> 1;
            case OVOO -> 0;
        };
    }

    private static boolean visibleAt(MapPois.Kind k, int zoom) {
        return switch (k) {
            case RELAY, TRAINING -> zoom <= 1;
            default -> true;
        };
    }

    private static boolean labelled(MapPois.Kind k, int zoom) {
        return switch (k) {
            case CITY, DUNGEON, BOSS -> true;
            case SITE -> zoom <= 2;
            case RELAY, TRAINING, OVOO -> zoom <= 1;
        };
    }

    private List<Component> describe(Session s, MapPois.Poi poi) {
        List<Component> out = new ArrayList<>();
        out.add(Component.text(poi.kind().label() + (poi.area().isEmpty() ? "" : " · " + poi.area()), GREY));
        double d = Math.hypot(poi.x() + 0.5 - s.origin.getX(), poi.z() + 0.5 - s.origin.getZ());
        out.add(Component.text(Math.round(d) + " м · x " + poi.x() + ", z " + poi.z(), GREY));
        out.add(Component.text("Дарж луужингаа энд заалгах", CYAN));
        return out;
    }

    /** Points the player's compass at a place. */
    private void point(Session s, MapPois.Poi poi) {
        s.p.setCompassTarget(new Location(s.origin.getWorld(), poi.x() + 0.5, 64, poi.z() + 0.5));
        s.p.sendActionBar(Component.text("Луужин: " + poi.name(), GOLD));
        s.p.playSound(s.p.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 0.8f, 1.2f);
    }

    // ------------------------------------------------------------------------------------------------ the cursor

    /** Where the crosshair meets the wall, as panel (u, v), or null. */
    private double[] aim(Session s) {
        Location eye = s.p.getEyeLocation();
        Vector dir = eye.getDirection();
        if (dir.getZ() <= 0.05) return null;
        double t = (s.plane.getZ() - eye.getZ()) / dir.getZ();
        return new double[]{s.plane.getX() - (eye.getX() + dir.getX() * t), eye.getY() + dir.getY() * t - s.plane.getY()};
    }

    private void cursor(Session s, boolean force) {
        double[] a = aim(s);
        Hit best = null;
        if (a != null) {
            for (Hit h : s.tabs) if (h.contains(a[0], a[1])) best = h;
            if (best == null) for (Hit h : s.places) if (h.contains(a[0], a[1])) best = h;
        }
        if (best != s.hover || force) {
            if (s.hover != null && s.hover.bg() != null && s.hover.bg().isValid() && s.tabs.contains(s.hover)) restoreTab(s, s.hover);
            s.hover = best;
            if (best != null && best.bg() != null && best.bg().isValid() && best.hoverModel() != null) best.bg().setItemStack(quad(best.hoverModel()));
            showTooltip(s, best);
            if (best != null && best.click() != null && !force) s.p.playSound(s.p.getLocation(), Sound.UI_BUTTON_CLICK, 0.2f, 1.9f);
        }
        String line;
        if (a != null && s.view.onWall(a[0], a[1])) {
            int x = (int) Math.floor(s.view.worldX(a[0])), z = (int) Math.floor(s.view.worldZ(a[1]));
            line = place(x, z);
        } else {
            line = "Zoom " + zoomLabel(s.view.zoom()) + " · Дугуй: томруулах · W A S D: гүйлгэх · Shift: хаах";
        }
        if (!line.equals(s.lastCursor) || force) {
            s.lastCursor = line;
            s.cartouche.text(Component.text(line, CREAM).font(mn.suld.plugin.ui.StyleFormat.HD));
        }
    }

    private void restoreTab(Session s, Hit h) {
        int i = s.tabs.indexOf(h);
        boolean on = i >= 0 && i < Filter.values().length && Filter.values()[i] == s.filter;
        h.bg().setItemStack(quad(on ? h.model() + "_on" : h.model()));
    }

    private static String zoomLabel(int zoom) {
        return switch (zoom) {
            case 0 -> "1:1";
            case 1 -> "1:2";
            case 2 -> "1:4";
            case 3 -> "1:8";
            default -> "1:16";
        };
    }

    /** "x 123 · z -456 · Туулын Хөндий · Lv 5–12" for a block. */
    private String place(int x, int z) {
        Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        StringBuilder b = new StringBuilder("x ").append(x).append(" · z ").append(z);
        if (services.city().contains(Bukkit.getWorlds().get(0).getName(), x, z)) return b.append(" · Хархорум").toString();
        Optional<Area> area = WorldContent.areaAt(x - spawn.getX(), z - spawn.getZ());
        area.ifPresent(a -> b.append(" · ").append(a.name()).append(" · Lv ").append(a.minLevel()).append("–").append(a.maxLevel()));
        return b.toString();
    }

    private void showTooltip(Session s, Hit h) {
        if (h == null) {
            s.tooltip.text(Component.empty());
            s.tooltip.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            return;
        }
        Component tip = h.name();
        for (Component l : h.lore()) tip = tip.append(Component.newline()).append(l);
        s.tooltip.text(tip.font(mn.suld.plugin.ui.StyleFormat.HD));
        s.tooltip.setBackgroundColor(Color.fromARGB(230, 14, 22, 44));
        int lines = 1 + h.lore().size();
        double tu = Math.max(-2.2, Math.min(2.2, h.u() + (h.u() < 0 ? 0.9 : -0.9) * (Math.abs(h.u()) > 3 ? 1 : 0)));
        double tv = h.v() > -1.0 ? h.v() - h.h() / 2 - 0.08 - 0.15 * lines : h.v() + h.h() / 2 + 0.08;
        s.tooltip.teleport(at(s, tu, tv, 0.08));
    }

    // ------------------------------------------------------------------------------------------------ input

    private void tick() {
        for (Session s : new ArrayList<>(sessions.values())) {
            if (!s.ready) continue;
            if (!s.p.isOnline() || !s.seat.isValid()) {
                close(s.p);
                continue;
            }
            // W / A / S / D: a quarter of the wall at once, again every 6 ticks while held
            boolean any = s.up || s.down || s.left || s.right;
            if (any) {
                if (s.heldTicks % 6 == 0) {
                    double du = (s.right ? 1 : 0) - (s.left ? 1 : 0), dv = (s.up ? 1 : 0) - (s.down ? 1 : 0);
                    setView(s, s.view.pan(du * COLS / 4.0, dv * ROWS / 4.0, map.geometry()));
                }
                s.heldTicks++;
            } else {
                s.heldTicks = 0;
            }
            if (s.dirty && Bukkit.getCurrentTick() % 20 == 0) {
                s.dirty = false;
                redraw(s);
            }
            if (Bukkit.getCurrentTick() % 2 == 0) cursor(s, false);
        }
    }

    private void click(Session s) {
        long now = System.currentTimeMillis();
        if (now - s.lastClick < 200) return;
        s.lastClick = now;
        cursor(s, false);
        Hit h = s.hover;
        if (h == null || h.click() == null) return;
        s.p.playSound(s.p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        h.click().run();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwing(PlayerAnimationEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s == null || e.getAnimationType() != PlayerAnimationType.ARM_SWING || !s.ready) return;
        click(s);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (sessions.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (sessions.containsKey(e.getPlayer().getUniqueId()) || e.getRightClicked().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    /** The wheel zooms toward the cursor. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onWheel(PlayerItemHeldEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s == null || !s.ready) return;
        e.setCancelled(true);
        int d = e.getNewSlot() - e.getPreviousSlot();
        if (d > 4) d -= 9;
        if (d < -4) d += 9;
        if (d == 0) return;
        double[] a = aim(s);
        double u = a != null && s.view.onWall(a[0], a[1]) ? a[0] : 0, v = a != null && s.view.onWall(a[0], a[1]) ? a[1] : 0;
        // wheel down (next slot) = farther, like a web map
        setView(s, s.view.zoomAt(s.view.zoom() + Integer.signum(d), u, v, map.geometry()));
    }

    @EventHandler
    public void onInput(org.bukkit.event.player.PlayerInputEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s == null) return;
        var in = e.getInput();
        s.up = in.isForward();
        s.down = in.isBackward();
        s.left = in.isLeft();
        s.right = in.isRight();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.closing) return;
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> close(p));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!sessions.containsKey(p.getUniqueId())) return;
        String cmd = e.getMessage().toLowerCase(Locale.ROOT);
        if (cmd.equals("/map") || cmd.startsWith("/map ") || cmd.equals("/atlas")) return; // the command closes it itself
        close(p);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && sessions.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent e) {
        if (e.getEntity().getScoreboardTags().contains(mn.suld.plugin.clazz.ClassTrialService.TAG) || e.getEntity().getScoreboardTags().contains("suld_training_dummy")) return;
        long now = System.currentTimeMillis();
        if (e.getEntity() instanceof Player p) lastCombat.put(p.getUniqueId(), now);
        if (e.getDamager() instanceof Player p) lastCombat.put(p.getUniqueId(), now);
        if (e.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player p) lastCombat.put(p.getUniqueId(), now);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFrameBreak(HangingBreakEvent e) {
        if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        close(e.getPlayer());
        lastCombat.remove(e.getPlayer().getUniqueId());
    }

    /** A crash or restart while the atlas was open: the player logs back in where they stood, not on the stage. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String ret = p.getPersistentDataContainer().get(RETURN, PersistentDataType.STRING);
        if (ret == null) return;
        p.getPersistentDataContainer().remove(RETURN);
        String[] f = ret.split(";");
        World w = f.length == 6 ? Bukkit.getWorld(f[0]) : null;
        if (w == null) return;
        try {
            Location l = new Location(w, Double.parseDouble(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3]),
                    Float.parseFloat(f[4]), Float.parseFloat(f[5]));
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.teleport(l);
                p.setFallDistance(0);
                p.setInvulnerable(false);
            });
        } catch (NumberFormatException ignored) {
            // a damaged value: the player stays where the server put them
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (!en.getScoreboardTags().contains(TAG)) continue;
            boolean live = false;
            for (Session s : sessions.values()) if (s.marks.contains(en) || s.stage.contains(en)) live = true;
            if (!live) en.remove();
        }
    }
}
