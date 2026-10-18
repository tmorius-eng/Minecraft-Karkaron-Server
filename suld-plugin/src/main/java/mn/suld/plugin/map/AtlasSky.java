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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
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
 * left click on a place points the compass at it, on a tab runs it. The wheel zooms toward the cursor (from 4 × 4 map
 * pixels a block to the whole world), W/A/S/D pan by a quarter of the wall, Shift closes. Left tabs choose which places show,
 * right tabs: the whole world, back to me, zoom in, zoom out. The map never shows land the server has not generated.
 */
public final class AtlasSky implements Listener, TabExecutor {

    static final String TAG = "suld_atlas";
    static final int COLS = 5, ROWS = 5;
    /** A W / A / S / D step: whole frames, so the frames still in view keep their pictures (see {@link #frameShift}). */
    static final int PAN_FRAMES = 1;
    /**
     * The camera stands DIST blocks in front of the wall, EYE_UP above its middle (negative: below it); the stage stands
     * RISE blocks over the world's top. At 6 blocks the frame was a small picture in a sea of navy, its title under three
     * boss bars (seen on a 1.21.11 client); the boss bars now step aside while the atlas is open
     * ({@link mn.suld.plugin.ui.BossBars}), so the camera comes in until the frame's top almost touches the top of the
     * screen and the cartouche still clears the hotbar and the hearts ({@link #screenY}, AtlasFramingTest). A map pixel
     * is then a little over one screen pixel at 1080p.
     */
    static final double DIST = 5.25, EYE_UP = -0.29;
    /**
     * The atlas fits each player's own screen (owner, 2026-10-18: on a wide field of view the 70° framing was a small
     * picture in the middle). The client never tells the server its FOV, so the player gives it once (asked the first
     * time; then /map fov, the grow and shrink tabs or Ctrl + wheel) and the camera stands where the frame fills a screen
     * of that FOV exactly as it fills a 70° screen from DIST ({@link #distFor}). Past the client's 110 the atlas comes
     * closer still, larger than the screen, for weak eyes. Kept in the player's data.
     */
    static final int FOV_DEFAULT = 70, FOV_MIN = 30, FOV_CLIENT_MAX = 110, FOV_MAX = 130, FOV_STEP = 5;
    private static final NamespacedKey FOV_KEY = new NamespacedKey("suld", "atlas_fov");
    /** The /map size steps before 2026-10-18 (the camera at DIST / step): read once and turned into a FOV. */
    static final double[] OLD_SIZES = {1.0, 1.2, 1.4, 1.65, 1.9};
    private static final NamespacedKey SIZE_KEY = new NamespacedKey("suld", "atlas_size");
    /** How many times the guide opened by itself; it does for a player's first {@link #HELP_AUTO} atlases. */
    private static final NamespacedKey HELP_KEY = new NamespacedKey("suld", "atlas_help_seen");
    static final int HELP_AUTO = 3;
    /**
     * While the atlas is open: no speed bonus or flying widens the client's field of view (the client scales its FOV
     * with the movement speed: a skill-tree or potion bonus made the atlas look farther away), and nothing on the stage
     * is within reach (a close camera would otherwise touch the frames). Transient modifiers: never saved.
     */
    private static final NamespacedKey CALM = new NamespacedKey("suld", "atlas_fov"), NO_REACH = new NamespacedKey("suld", "atlas_reach");
    /** The client's default vertical field of view, and the bottom of the screen the hotbar, XP bar and hearts take at GUI scale 3. */
    static final double FOV_DEG = 70, HUD_BOTTOM = 39.0 / 360;
    static final int RISE = 144;
    static final long COMBAT_MS = 8000;
    /**
     * The zoom the atlas opens at: one block a map pixel, 640 blocks across the wall around the player. (It opened at
     * 4 blocks a pixel, where the city is a speck and most of the 2 560-block window is ungenerated parchment.)
     */
    static final int OPEN_ZOOM = 0;
    /** Panel layout (blocks, u right, v up, (0, 0) the middle of the wall), measured from the pack's textures (128 px a block). */
    static final double PX = 1.0 / 128;
    static final double FRONT_W = 784 * PX, FRONT_H = 864 * PX, FRONT_V = -12 * PX;
    static final double TAB_W = 64 * PX, TAB_H = 48 * PX, TAB_STEP = 58 * PX, TAB_U = 406 * PX;
    static final double TITLE_V = 391 * PX, CART_V = -388 * PX;
    /** Places' markers; a little larger than the pack's 24/32 px art was first shown at, so they read from the seat. */
    static final double MARKER = 28 * PX, MARKER_BIG = 38 * PX;
    /** The text: places' labels, the cartouche's line, the tooltip, the guide. A text display's line is 1/4 block at scale 1. */
    static final float LABEL_SCALE = 0.5f, CART_SCALE = 0.8f, TIP_SCALE = 0.85f, HELP_SCALE = 0.62f;
    /** A text display's font pixel (1/40 block at scale 1); the SÜLD frame's corner and the room inside it, in blocks. */
    static final double FONT_PX = 0.025, TIP_BORDER = 0.085, TIP_PAD = 0.07;
    /** The guide's frame is a big board: its gold border and corner knots drawn larger, as on a tooltip seen up close. */
    static final double HELP_BORDER = 0.16;
    static final double LINE = 0.25;
    /** A label's box: width a character, half its height, and its gap under the marker (scaled with the label). */
    static final double LABEL_CHAR = 0.045 * LABEL_SCALE / 0.42, LABEL_HALF = 0.06 * LABEL_SCALE / 0.42, LABEL_GAP = 0.07 * LABEL_SCALE / 0.42;

    /**
     * Where panel height {@code v} (blocks from the wall's middle, up) shows on the screen: −1 the bottom edge, +1 the
     * top, at the default field of view.
     */
    static double screenY(double v) {
        return (v - EYE_UP) / (DIST * Math.tan(Math.toRadians(FOV_DEG / 2)));
    }

    /** Where panel point {@code u} (blocks from the middle, right) shows across a screen of {@code aspect}: −1 left, +1 right. */
    static double screenX(double u, double aspect) {
        return u / (DIST * Math.tan(Math.toRadians(FOV_DEG / 2)) * aspect);
    }

    static final TextColor GOLD = TextColor.fromHexString("#ECBE4C"), CREAM = TextColor.fromHexString("#F4E8C8"),
            GREY = TextColor.fromHexString("#B9B3A4"), CYAN = TextColor.fromHexString("#56E2EC");
    /** The regions' names on the widest views: an old gold, on a navy plate so it reads on snow, sand and taiga alike. */
    static final TextColor REGION_INK = TextColor.fromHexString("#E8C66A");
    /**
     * The plate behind a region's name (ARGB): navy at 65%. Over the baked atlas's lightest colour (snow) the gold still
     * stands 3:1 (CartographyContentTest); the old plateless, 75%-opaque ink vanished on the Gobi's sand and the snow.
     */
    static final int REGION_PLATE = 0xA6101A30;
    /** The plate behind a place's label (ARGB). */
    static final int LABEL_PLATE = 0x99000000;
    private static final NamespacedKey RETURN = new NamespacedKey("suld", "atlas_return");
    private static final byte[] BLANK = new byte[TileComposer.PIXELS];

    /** The left tabs: which places show. A tab's tooltip says what those places are and what they give. */
    enum Filter {
        ALL("all", "Бүгд", List.of("Бүх газрыг нэг дор харуулна:", "хот, өртөө, агуй, овоо,", "түүхэн газар, аварга дайсан,",
                "таны тэмдэг.")),
        CITY("city", "Хот, өртөө", List.of("Хархорум бол аюулгүй хот:", "дэлгүүр, NPC, PvP байхгүй.", "Өртөөнд зогсоод бусад өртөө",
                "рүү " + mn.suld.plugin.npc.NpcService.TRAVEL_COST + " зоосоор аялна.")),
        DUNGEON("dungeon", "Агуй", List.of("Арван агуйн үүд.", "Агуй бүр доод түвшинтэй:", "агуй дээр заагч аваачвал",
                "түвшин, багийн хэмжээ гарна.")),
        OVOO("ovoo", "Овоо", List.of("Нутаг бүрийн овоо (40).", "Нар зөв 3 тойрвол ивээл:", "+5% EXP 30 минут.", "Анх очвол EXP авна.")),
        SITE("site", "Түүхэн газар", List.of("Түүхэн дурсгалт газрууд.", "Анх очиж үзвэл EXP авч,", "түүхийн тайлбар нээгдэнэ.")),
        BOSS("boss", "Аварга дайсан", List.of("Ертөнцийн аварга дайсан", "долоо хоногт тогтсон цагт", "гарна. Бүгдээрээ хамт ялж",
                "шагнал авна.")),
        PIN("pin", "Миний тэмдэг", List.of("Таны тавьсан тэмдгүүд", "(" + mn.suld.api.map.MapPin.MAX_PINS + " хүртэл). Тэмдэг дээр",
                "дарж чиглэх эсвэл устгана."));

        final String icon, label;
        /** What the tab's places are, a short line each (the tooltip stands on the wall). */
        final List<String> about;

        Filter(String icon, String label, List<String> about) {
            this.icon = icon;
            this.label = label;
            this.about = about;
        }

        /** The tooltip's last line: what a click does. */
        String use() {
            return this == ALL ? "Дарж: бүгдийг харуулах" : "Дарж: зөвхөн эдгээрийг харуулах";
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

    /** A right tab: its name, what it does (a short line each) and how to use it (the tooltip's last line). */
    record Tool(String icon, String label, List<String> about, String use) {
    }

    static final List<Tool> TOOLS = List.of(
            new Tool("world", "Бүх ертөнц", List.of("Газрын зургийг холдуулж", "бүх нутгийг нэг дор харуулна."), "Дарж харах"),
            new Tool("me", "Миний байр", List.of("Таны зогсож буй газар руу", "газрын зургийг буцаана."), "Дарж буцах"),
            new Tool("plus", "Ойртуулах", List.of("Газрын зургийг томруулж,", "нарийн хэсгийг харуулна."), "Дарах · эсвэл хулганы дугуй"),
            new Tool("minus", "Холдуулах", List.of("Газрын зургийг жижигрүүлж,", "өргөн нутгийг харуулна."), "Дарах · эсвэл хулганы дугуй"),
            new Tool("pin_add", "Тэмдэг тавих", List.of("Үүнийг дараад газрын зураг", "дээр дарвал тэр газарт", "тэмдэг тавигдана."),
                    "Дахин дарвал болино"),
            new Tool("grow", "Атлас томруулах", List.of("Атласыг нүдэнд ойртуулна:", "бичиг, тэмдэг томорно.", "Тохиргоо тань хадгалагдана.",
                    "Яг тааруулах: /map fov"), "Дарах · эсвэл Ctrl + дугуй"),
            new Tool("shrink", "Атлас жижигрүүлэх", List.of("Атлас дэлгэцэнд багтахгүй", "бол холдуулна.", "Тохиргоо тань хадгалагдана."),
                    "Дарах · эсвэл Ctrl + дугуй"));

    /** The guide: its title and its lines (key, what it does), shown on the SÜLD frame in the middle of the map. */
    static final String HELP_TITLE = "СҮЛДИЙН АТЛАС — ЗААВАР";
    static final List<String[]> HELP = List.of(
            new String[]{"Хулгана", "заагч аваачвал тайлбар гарна"},
            new String[]{"Зүүн товч", "туг дарах · тэмдэг тавих"},
            new String[]{"Дугуй", "зураг ойртуулах · холдуулах"},
            new String[]{"Ctrl + Дугуй", "атласыг томруулах · багасгах"},
            new String[]{"W A S D", "газрын зураг гүйлгэх"},
            new String[]{"Зүүн туг", "газрын төрлөөр шүүх"},
            new String[]{"Баруун туг", "ертөнц · байр · ± · атлас"},
            new String[]{"/map fov", "атласыг FOV-доо тааруулах"},
            new String[]{"Space", "заавар нээх · хаах"},
            new String[]{"Shift", "атлас хаах"});
    static final String HELP_CLOSE = "Товших эсвэл Space — заавар хаах";

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
        /** The player's own weather before the atlas (null: the world's); the atlas is always clear. */
        org.bukkit.WeatherType weather;
        final List<Entity> stage = new ArrayList<>();
        /** The places' markers, labels and region names: kept from one redraw to the next, moved instead of spawned again. */
        final MarkPool<Entity> marks = new MarkPool<>();
        final List<Hit> tabs = new ArrayList<>(), places = new ArrayList<>();
        /** The party mates' markers and their hits (redrawn every second when one moved). */
        final MarkPool<Entity> mateMarks = new MarkPool<>();
        /** The wall's item frames by position (row × COLS + col), and the map view each one shows (a pan slides them). */
        final GlowItemFrame[] wall = new GlowItemFrame[COLS * ROWS];
        int[] viewAt = identityWall();
        final List<Hit> mateHits = new ArrayList<>();
        int matesKey;
        ItemDisplay seat;
        Location plane;
        double eyeOff;
        TextDisplay tooltip, cartouche, title;
        AtlasView view;
        Filter filter = Filter.ALL;
        Hit hover;
        String lastCursor = "";
        int gen;
        boolean ready, closing, dirty, pinMode, up, down, left, right;
        /** The latest death's {x, z} (read when the atlas opens), or null. */
        int[] death;
        int heldTicks;
        long lastClick;
        /** The tooltip's SÜLD frame (rebuilt for each thing hovered) and the guide (its frame and its text). */
        final List<Entity> tipFrame = new ArrayList<>(), help = new ArrayList<>();
        boolean jump;
        /** The field of view the atlas is fitted to ({@link #fovOf}) and the camera's distance from the wall for it. */
        int fov = FOV_DEFAULT;
        double dist = DIST;
        /** The sprint key (Ctrl) held: the wheel sizes the atlas instead of zooming the map. */
        boolean sprint;
        /** Flying when the atlas opened (given back on close). */
        boolean wasFlying;

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
    /** Set by SuldPlugin: the players' pins and the waypoint guide. */
    public volatile PinService pins;
    public volatile WaypointGuide guide;
    /** Set by SuldPlugin: turns the minimap setting (the /map minimap shortcut). */
    public volatile java.util.function.Consumer<Player> toggleMinimap = p -> { };
    /** Set by SuldPlugin: turns the «Дэлгэцийн тэмдэг» setting (the /map markers shortcut). */
    public volatile java.util.function.Consumer<Player> toggleScreenMarkers = p -> { };
    private static final net.kyori.adventure.text.event.ClickCallback.Options ONCE =
            net.kyori.adventure.text.event.ClickCallback.Options.builder().uses(1).lifetime(java.time.Duration.ofMinutes(5)).build();

    public AtlasSky(Plugin plugin, SuldServices services, MapService map) {
        this.plugin = plugin;
        this.services = services;
        this.map = map;
        this.frames = new AtlasFrames(plugin, COLS * ROWS);
    }

    public void start() {
        frames.start();
        map.tiles().listen((zoom, tx, tz) -> Bukkit.getScheduler().runTask(plugin, () -> tileChanged(zoom, tx, tz)));
        map.tiles().onReset(() -> Bukkit.getScheduler().runTask(plugin, () -> sessions.values().forEach(s -> s.dirty = true)));
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
        if (args.length > 0) {
            sub(p, args);
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
        List<String> subs = List.of("pin", "pins", "unpin", "guide", "stop", "minimap", "markers", "fov", "size");
        if (args.length == 1) return subs.stream().filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && (args[0].equalsIgnoreCase("fov") || args[0].equalsIgnoreCase("size"))) {
            return List.of("70", "80", "90", "100", "110");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("unpin") || args[0].equalsIgnoreCase("guide")) && sender instanceof Player p && pins != null) {
            List<String> out = new ArrayList<>();
            for (mn.suld.api.map.MapPin pin : pins.pins(p.getUniqueId())) out.add(pin.name());
            return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    /** {@code /map pin [нэр] | pins | unpin <нэр|#> | guide <нэр|#|x z> | stop | minimap | markers}. */
    private void sub(Player p, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        String rest = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).strip();
        switch (sub) {
            case "pin" -> {
                if (p.getWorld() != Bukkit.getWorlds().get(0)) {
                    p.sendMessage(Messages.error("Тэмдэг зөвхөн Их Монголын нутагт тавигдана."));
                    return;
                }
                Location l = p.getLocation();
                pins.add(p, rest, l.getBlockX(), l.getBlockY(), l.getBlockZ(), pin -> refreshFor(p));
            }
            case "pins" -> listPins(p);
            case "unpin" -> {
                var pin = mn.suld.api.map.MapPin.find(pins.pins(p.getUniqueId()), rest);
                if (pin.isEmpty()) {
                    p.sendMessage(Messages.error("Ийм тэмдэг алга: /map pins"));
                    return;
                }
                var gone = pin.get();
                pins.remove(p, gone, () -> unpinned(p, gone));
            }
            case "guide" -> {
                String[] xz = rest.split("\\s+");
                if (xz.length == 2 && xz[0].matches("-?\\d+") && xz[1].matches("-?\\d+")) {
                    int x = Integer.parseInt(xz[0]), z = Integer.parseInt(xz[1]);
                    guide.guide(p, new WaypointGuide.Target("x " + x + ", z " + z, "◆", Bukkit.getWorlds().get(0).getName(), x + 0.5, WaypointGuide.GROUND, z + 0.5));
                    return;
                }
                var pin = mn.suld.api.map.MapPin.find(pins.pins(p.getUniqueId()), rest);
                if (pin.isEmpty()) {
                    p.sendMessage(Messages.error("Ийм тэмдэг алга: /map pins, эсвэл /map guide <x> <z>"));
                    return;
                }
                guidePin(p, pin.get());
            }
            case "stop" -> {
                guide.stop(p);
                p.sendMessage(Messages.success("Чиглэл зогслоо."));
            }
            case "minimap" -> toggleMinimap.accept(p);
            case "markers" -> toggleScreenMarkers.accept(p);
            case "fov", "size", "хэмжээ" -> setFov(p, rest);
            default -> p.sendMessage(Messages.info("/map — газрын зураг · /map pin [нэр] · /map pins · /map unpin <нэр> · /map guide <нэр|x z> · /map stop · /map minimap · /map markers · /map fov [" + FOV_MIN + "-" + FOV_MAX + "]"));
        }
    }

    /** The camera's distance from the wall that shows the frame on a {@code fov}° screen as DIST shows it on a 70° one. */
    static double distFor(int fov) {
        return DIST * Math.tan(Math.toRadians(FOV_DEG / 2)) / Math.tan(Math.toRadians(clampFov(fov) / 2.0));
    }

    static int clampFov(int fov) {
        return Math.max(FOV_MIN, Math.min(FOV_MAX, fov));
    }

    /** An old /map size step (0-based) as the FOV its framing matched, to the nearest 5°: 70, 80, 90, 100, 105. */
    static int fovForSize(int step) {
        double k = OLD_SIZES[Math.max(0, Math.min(OLD_SIZES.length - 1, step))];
        double fov = 2 * Math.toDegrees(Math.atan(k * Math.tan(Math.toRadians(FOV_DEG / 2))));
        return clampFov((int) Math.round(fov / FOV_STEP) * FOV_STEP);
    }

    /** How the chat and the action bar name a fit. */
    static String fitLabel(int fov) {
        return fov <= FOV_CLIENT_MAX ? "Атласын хэмжээ: FOV " + fov + "-д тааруулсан" : "Атласын хэмжээ: дэлгэцээс том (" + fov + ")";
    }

    /** True once the player has a fit (their own, or an old /map size step); until then the atlas asks for it. */
    private static boolean hasFit(Player p) {
        var pdc = p.getPersistentDataContainer();
        return pdc.has(FOV_KEY, PersistentDataType.INTEGER) || pdc.has(SIZE_KEY, PersistentDataType.INTEGER);
    }

    /** The player's fit: their FOV, an old /map size step turned into one (once), or the default. */
    private static int fovOf(Player p) {
        var pdc = p.getPersistentDataContainer();
        Integer v = pdc.get(FOV_KEY, PersistentDataType.INTEGER);
        if (v == null) {
            Integer old = pdc.get(SIZE_KEY, PersistentDataType.INTEGER);
            if (old == null) return FOV_DEFAULT;
            v = fovForSize(old);
            pdc.set(FOV_KEY, PersistentDataType.INTEGER, v);
            pdc.remove(SIZE_KEY);
        }
        return clampFov(v);
    }

    /**
     * {@code /map fov}: the window that asks for the client's FOV; {@code /map fov 95}: that fit ({@value #FOV_MIN}–{@value
     * #FOV_MAX}; 1–5 are the old /map size steps). An open atlas moves its camera at once.
     */
    private void setFov(Player p, String arg) {
        if (arg.isEmpty()) {
            askFov(p);
            return;
        }
        int v = arg.matches("\\d{1,3}") ? Integer.parseInt(arg) : -1;
        if (v >= 1 && v <= OLD_SIZES.length) v = fovForSize(v - 1);
        if (v < FOV_MIN || v > FOV_MAX) {
            p.sendMessage(Messages.error("/map fov " + FOV_MIN + "–" + FOV_MAX + " (Minecraft-ийн тань FOV: Normal = 70, Quake Pro = 110)"));
            return;
        }
        applyFov(p, v);
        p.sendMessage(Messages.success(fitLabel(v) + ". Өөрчлөх: /map fov, эсвэл атлас дотор Ctrl + дугуй."));
    }

    /** Keeps the fit and, while the atlas is open, glides its camera there. */
    private void applyFov(Player p, int fov) {
        int v = clampFov(fov);
        p.getPersistentDataContainer().set(FOV_KEY, PersistentDataType.INTEGER, v);
        Session s = sessions.get(p.getUniqueId());
        if (s != null && s.ready) fit(s, v);
    }

    /** One step of the grow and shrink tabs and of Ctrl + wheel ({@code dFov} above 0: closer, larger). */
    private void resize(Session s, int dFov) {
        int next = clampFov(s.fov + dFov);
        if (next == s.fov) {
            s.p.sendActionBar(Component.text(dFov > 0 ? "Атлас хамгийн том хэмжээндээ байна" : "Атлас хамгийн жижиг хэмжээндээ байна", GREY));
            return;
        }
        s.p.getPersistentDataContainer().set(FOV_KEY, PersistentDataType.INTEGER, next);
        fit(s, next);
    }

    private void fit(Session s, int fov) {
        s.fov = fov;
        s.dist = distFor(fov);
        placeCamera(s);
        s.p.sendActionBar(Component.text(fitLabel(fov) + " · Ctrl + дугуй", GOLD));
    }

    /**
     * «Атласыг дэлгэцэндээ тааруулах»: the client's FOV (Options → FOV), so the atlas fills this player's screen. Asked
     * the first time the atlas opens; {@code /map fov} brings it back. A dialog is drawn on the screen, not in the world,
     * so it reads the same at any FOV.
     */
    private void askFov(Player p) {
        int now = Math.min(FOV_CLIENT_MAX, fovOf(p));
        io.papermc.paper.dialog.Dialog d = io.papermc.paper.dialog.Dialog.create(f -> f.empty()
                .base(io.papermc.paper.registry.data.dialog.DialogBase.builder(Component.text("Атласыг дэлгэцэндээ тааруулах", GOLD, TextDecoration.BOLD))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(Component.text(
                                "Minecraft-ийн тань FOV хэд вэ? Options → FOV (Normal = 70, Quake Pro = 110).\n\n"
                                        + "Сервер үүнийг өөрөө мэдэж чадахгүй. Нэг удаа сонговол атлас таны дэлгэцийг яг дүүргэнэ. "
                                        + "Дараа нь атлас дотор Ctrl + дугуй, эсвэл баруун талын доод хоёр тугаар томруулж, "
                                        + "жижигрүүлнэ.", CREAM), 320)))
                        .inputs(List.of(io.papermc.paper.registry.data.dialog.input.DialogInput.numberRange("fov", Component.text("FOV", GOLD),
                                FOV_MIN, FOV_CLIENT_MAX).step(1f).initial((float) now).width(300).build()))
                        .build())
                .type(io.papermc.paper.registry.data.dialog.type.DialogType.confirmation(
                        io.papermc.paper.registry.data.dialog.ActionButton.create(Component.text("Хадгалах ✔", NamedTextColor.GREEN, TextDecoration.BOLD),
                                null, 120, io.papermc.paper.registry.data.dialog.action.DialogAction.customClick((view, who) -> {
                                    Float v = view.getFloat("fov");
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        if (v == null || !(who instanceof Player pl) || !pl.isOnline()) return;
                                        int fov = clampFov(Math.round(v));
                                        applyFov(pl, fov);
                                        pl.sendMessage(Messages.success(fitLabel(fov) + ". Өөрчлөх: /map fov, эсвэл атлас дотор Ctrl + дугуй."));
                                    });
                                }, ONCE)),
                        io.papermc.paper.registry.data.dialog.ActionButton.create(Component.text("Дараа"), null, 100, null))));
        p.showDialog(d);
    }

    private void listPins(Player p) {
        List<mn.suld.api.map.MapPin> list = pins.pins(p.getUniqueId());
        if (list.isEmpty()) {
            p.sendMessage(Messages.info("Тэмдэг алга. Тавих: /map pin <нэр>, эсвэл газрын зураг дээрх «Тэмдэг тавих»."));
            return;
        }
        p.sendMessage(Messages.accent("◆ Миний тэмдэг (" + list.size() + "/" + mn.suld.api.map.MapPin.MAX_PINS + ")"));
        Location l = p.getLocation();
        for (int i = 0; i < list.size(); i++) {
            var pin = list.get(i);
            long d = Math.round(Math.hypot(pin.x() + 0.5 - l.getX(), pin.z() + 0.5 - l.getZ()));
            int n = i + 1;
            p.sendMessage(Component.text(n + ". ", GREY).append(Component.text(pin.name(), CREAM, TextDecoration.BOLD))
                    .append(Component.text("  x " + pin.x() + ", z " + pin.z() + " · " + d + " м  ", GREY))
                    .append(Component.text("[Чиглэх]", CYAN).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/map guide " + n)))
                    .append(Component.text(" "))
                    .append(Component.text("[Устгах]", NamedTextColor.RED).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/map unpin " + n))));
        }
    }

    private void guidePin(Player p, mn.suld.api.map.MapPin pin) {
        guide.guide(p, new WaypointGuide.Target(pin.name(), "◆", Bukkit.getWorlds().get(0).getName(), pin.x() + 0.5, pin.y(), pin.z() + 0.5, pin.id()));
        p.sendMessage(Messages.success("◆ «" + pin.name() + "» руу чиглэлээ. Зогсоох: /map stop"));
    }

    /** A pin was removed: the atlas drops its marker and a waypoint leading to it ends (no stale bar, label or compass). */
    private void unpinned(Player p, mn.suld.api.map.MapPin pin) {
        refreshFor(p);
        if (guide != null) guide.pinRemoved(p, pin.id());
    }

    /**
     * The player's waypoint was set, stopped or reached (WaypointGuide's listener): an open atlas moves its «Чиглэл» flag
     * on the next tick (not inside the click that set it).
     */
    public void waypointChanged(Player p) {
        if (!sessions.containsKey(p.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> refreshFor(p));
    }

    /** The player's atlas, if open, shows their pins and their waypoint again. */
    private void refreshFor(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null && s.ready) placeMarkers(s);
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
        boolean ask = !hasFit(p);
        s.fov = fovOf(p);
        s.dist = distFor(s.fov);
        sessions.put(p.getUniqueId(), s);
        p.closeInventory();
        Location o = s.origin;
        p.getPersistentDataContainer().set(RETURN, PersistentDataType.STRING, o.getWorld().getName() + ";" + o.getX() + ";" + o.getY() + ";" + o.getZ()
                + ";" + o.getYaw() + ";" + o.getPitch());
        World w = o.getWorld();
        Location stage = new Location(w, Math.floor(o.getX()) + 0.5, w.getMaxHeight() + RISE, Math.floor(o.getZ()) + 0.5, 0f, 0f);
        p.setInvulnerable(true);
        p.setFallDistance(0);
        // no rain or snow falling across the map (the stage stands in the open sky)
        s.weather = p.getPlayerWeather();
        p.setPlayerWeather(org.bukkit.WeatherType.CLEAR);
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
        s.view = new AtlasView(COLS, ROWS, o.getX(), o.getZ(), OPEN_ZOOM).clamp(g);
        String home = w.getName();
        services.deathRepository().recent(p.getUniqueId(), 1).whenComplete((list, err) -> {
            if (err != null || list == null || list.isEmpty() || !plugin.isEnabled()) return;
            var last = list.get(0);
            if (!home.equals(last.world())) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                s.death = new int[]{last.x(), last.z()};
                if (s.ready && sessions.get(p.getUniqueId()) == s) placeMarkers(s);
            });
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (sessions.get(p.getUniqueId()) != s || !p.isOnline()) return;
            calm(s);
            build(s);
            s.ready = true;
            redraw(s);
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.0f);
            // the guide opens by itself for a player's first atlases; Space brings it back any time
            int seen = p.getPersistentDataContainer().getOrDefault(HELP_KEY, PersistentDataType.INTEGER, 0);
            if (seen < HELP_AUTO) {
                p.getPersistentDataContainer().set(HELP_KEY, PersistentDataType.INTEGER, seen + 1);
                showHelp(s);
            }
            // the first atlas asks for the client's FOV once (the default is kept even if the window is just closed)
            if (ask) {
                p.getPersistentDataContainer().set(FOV_KEY, PersistentDataType.INTEGER, s.fov);
                askFov(p);
            }
        }, 2L);
    }

    /**
     * The client widens its field of view with the player's movement speed (and while flying): a skill-tree or potion
     * speed bonus made the atlas look farther away. While it is open the speed is exactly the walking speed (one
     * MULTIPLY_TOTAL modifier on top of every other), flying is off, and nothing on the stage is within reach.
     */
    private void calm(Session s) {
        Player p = s.p;
        calmSpeed(p);
        for (Attribute a : new Attribute[]{Attribute.ENTITY_INTERACTION_RANGE, Attribute.BLOCK_INTERACTION_RANGE}) {
            AttributeInstance r = p.getAttribute(a);
            if (r == null) continue;
            r.removeModifier(NO_REACH);
            r.addTransientModifier(new AttributeModifier(NO_REACH, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
        s.wasFlying = p.isFlying();
        if (s.wasFlying) p.setFlying(false);
    }

    /** The movement speed back to the plain walking speed (again after a skill or potion changed it while open). */
    private static void calmSpeed(Player p) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        double walk = p.getWalkSpeed() / 2.0; // Bukkit's walk speed is twice the client's
        if (Math.abs(speed.getValue() - walk) < 1e-6) return;
        speed.removeModifier(CALM);
        double now = speed.getValue();
        if (now > 0 && walk > 0 && Math.abs(walk / now - 1) > 1e-4) {
            speed.addTransientModifier(new AttributeModifier(CALM, walk / now - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    private void uncalm(Session s) {
        Player p = s.p;
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(CALM);
        for (Attribute a : new Attribute[]{Attribute.ENTITY_INTERACTION_RANGE, Attribute.BLOCK_INTERACTION_RANGE}) {
            AttributeInstance r = p.getAttribute(a);
            if (r != null) r.removeModifier(NO_REACH);
        }
        if (s.wasFlying && p.getAllowFlight()) p.setFlying(true);
    }

    public void close(Player p) {
        close(p, true);
    }

    private void close(Player p, boolean goBack) {
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) return;
        s.closing = true;
        for (Entity e : s.marks.clear()) if (e.isValid()) e.remove();
        for (Entity e : s.mateMarks.clear()) if (e.isValid()) e.remove();
        for (Entity e : s.stage) if (e.isValid()) e.remove();
        for (Entity e : s.tipFrame) if (e.isValid()) e.remove();
        for (Entity e : s.help) if (e.isValid()) e.remove();
        uncalm(s);
        frames.close(p.getUniqueId());
        if (p.isInsideVehicle()) p.leaveVehicle();
        if (goBack) p.teleport(s.origin);
        p.setFallDistance(0);
        p.setInvulnerable(s.invulnerable);
        if (s.weather == null) p.resetPlayerWeather();
        else p.setPlayerWeather(s.weather);
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

    /** Shows {@code e} to the session's player only; {@code into} (null: a {@link MarkPool} keeps it) lists it for removal. */
    private <T extends Entity> T own(Session s, List<Entity> into, T e) {
        if (into != null) into.add(e);
        s.p.showEntity(plugin, e);
        return e;
    }

    private ItemDisplay picture(Session s, List<Entity> into, String model, double u, double v, double w, double h, double towardViewer, float turn) {
        return own(s, into, s.p.getWorld().spawn(at(s, u, v, towardViewer), ItemDisplay.class, d -> {
            hidden(d);
            d.setItemStack(quad(model));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(pictureShape(w, h, turn));
        }));
    }

    private static Transformation pictureShape(double w, double h, float turn) {
        return new Transformation(new Vector3f(), new Quaternionf().rotateZ(turn), new Vector3f((float) w, (float) h, 1), new Quaternionf());
    }

    private static Transformation textShape(float scale) {
        return new Transformation(new Vector3f(0, (float) (-0.125 * scale), 0), new Quaternionf(), new Vector3f(scale), new Quaternionf());
    }

    /**
     * A picture kept in {@code pool} under {@code key}: the one from the last redraw is moved and refitted (one position
     * packet), only a new one is spawned.
     */
    private ItemDisplay mark(Session s, MarkPool<Entity> pool, String key, String model, double u, double v, double w, double h,
                             double towardViewer, float turn) {
        String k = pool.unique(key);
        if (pool.take(k) instanceof ItemDisplay d && d.isValid()) {
            ItemStack it = quad(model);
            if (!it.equals(d.getItemStack())) d.setItemStack(it);
            Transformation shape = pictureShape(w, h, turn);
            if (!shape.equals(d.getTransformation())) d.setTransformation(shape);
            move(d, at(s, u, v, towardViewer));
            return d;
        }
        ItemDisplay d = picture(s, null, model, u, v, w, h, towardViewer, turn);
        pool.put(k, d);
        return d;
    }

    /** A text kept in {@code pool} under {@code key}, as {@link #mark} keeps a picture; {@code shape} null = the usual one. */
    private TextDisplay label(Session s, MarkPool<Entity> pool, String key, double u, double v, Component c, float scale, int width,
                              int background, double towardViewer, Transformation shape) {
        String k = pool.unique(key);
        Transformation t = shape != null ? shape : textShape(scale);
        if (pool.take(k) instanceof TextDisplay d && d.isValid()) {
            Component text = c.font() == null ? c.font(mn.suld.plugin.ui.StyleFormat.HD) : c;
            if (!text.equals(d.text())) d.text(text);
            Color bg = Color.fromARGB(background);
            if (!bg.equals(d.getBackgroundColor())) d.setBackgroundColor(bg);
            if (d.getLineWidth() != width) d.setLineWidth(width);
            if (!t.equals(d.getTransformation())) d.setTransformation(t);
            move(d, at(s, u, v, towardViewer));
            return d;
        }
        TextDisplay d = text(s, null, u, v, c, scale, width, background, towardViewer);
        if (shape != null) d.setTransformation(shape);
        pool.put(k, d);
        return d;
    }

    /** Moves a kept mark to {@code to}, keeping the way it faces (a text display is turned to face the camera). */
    private static void move(Entity e, Location to) {
        Location now = e.getLocation();
        if (now.getWorld() == to.getWorld() && now.distanceSquared(to) < 1e-8) return;
        to.setYaw(now.getYaw());
        to.setPitch(now.getPitch());
        e.teleport(to);
    }

    /** Every position showing its own map view: position i shows view i (a new atlas, before any pan). */
    static int[] identityWall() {
        int[] v = new int[COLS * ROWS];
        for (int i = 0; i < v.length; i++) v[i] = i;
        return v;
    }

    /**
     * The whole frames the picture slid by from {@code before} to {@code after} ({+columns east, +rows south}), or null
     * when it did not slide by whole frames that leave some of the wall in view (a zoom, a pan the world's edge cut
     * short, a jump across the world).
     */
    static int[] frameShift(AtlasView before, AtlasView after) {
        if (before.zoom() != after.zoom() || before.cols() != after.cols() || before.rows() != after.rows()) return null;
        int span = before.blocksPerFrame();
        int dx = after.left() - before.left(), dz = after.top() - before.top();
        if (dx % span != 0 || dz % span != 0) return null;
        int dc = dx / span, dr = dz / span;
        if (dc == 0 && dr == 0 || Math.abs(dc) >= before.cols() || Math.abs(dr) >= before.rows()) return null;
        return new int[]{dc, dr};
    }

    /**
     * The map view each wall position shows after the picture slid by ({@code dc}, {@code dr}) frames. Position (c, r)
     * takes the view that was at (c + dc, r + dr): that view already holds the picture now wanted there, so
     * {@link AtlasFrames#show} sends nothing for it. Only the positions the slide uncovers get new pictures, in the views
     * that slid off the far side.
     */
    static int[] slid(int[] viewAt, int cols, int rows, int dc, int dr) {
        int[] out = new int[viewAt.length];
        boolean[] used = new boolean[viewAt.length];
        java.util.Arrays.fill(out, -1);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int sc = c + dc, sr = r + dr;
                if (sc < 0 || sc >= cols || sr < 0 || sr >= rows) continue;
                int view = viewAt[sr * cols + sc];
                out[r * cols + c] = view;
                used[view] = true;
            }
        }
        java.util.ArrayDeque<Integer> free = new java.util.ArrayDeque<>();
        for (int view : viewAt) if (!used[view]) free.add(view);
        for (int i = 0; i < out.length; i++) if (out[i] < 0) out[i] = free.poll();
        return out;
    }

    /**
     * Hands each wall position the map view {@link #slid} gives it; the item frames switch maps the client already holds.
     * Returns the positions the slide uncovered: their views still hold the pictures that slid off the far side.
     */
    private boolean[] slideWall(Session s, int dc, int dr) {
        int[] next = slid(s.viewAt, COLS, ROWS, dc, dr);
        for (int pos = 0; pos < next.length; pos++) {
            if (next[pos] == s.viewAt[pos]) continue;
            GlowItemFrame f = s.wall[pos];
            if (f != null && f.isValid()) f.setItem(mapItem(frames.view(next[pos])), false);
        }
        s.viewAt = next;
        return uncovered(COLS, ROWS, dc, dr);
    }

    /** The wall positions a slide by ({@code dc}, {@code dr}) frames uncovers: what they show was off the wall before. */
    static boolean[] uncovered(int cols, int rows, int dc, int dr) {
        boolean[] out = new boolean[cols * rows];
        for (int pos = 0; pos < out.length; pos++) {
            int c = pos % cols + dc, r = pos / cols + dr;
            out[pos] = c < 0 || c >= cols || r < 0 || r >= rows;
        }
        return out;
    }

    private static ItemStack mapItem(org.bukkit.map.MapView view) {
        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        org.bukkit.inventory.meta.MapMeta mm = (org.bukkit.inventory.meta.MapMeta) mapItem.getItemMeta();
        mm.setMapView(view);
        mapItem.setItemMeta(mm);
        return mapItem;
    }

    private TextDisplay text(Session s, List<Entity> into, double u, double v, Component c, float scale, int width, int background) {
        return text(s, into, u, v, c, scale, width, background, 0.06);
    }

    private TextDisplay text(Session s, List<Entity> into, double u, double v, Component c, float scale, int width, int background,
                             double towardViewer) {
        return own(s, into, s.p.getWorld().spawn(at(s, u, v, towardViewer), TextDisplay.class, t -> {
            hidden(t);
            t.setBillboard(Display.Billboard.FIXED);
            t.setRotation(180f, 0f);
            t.setBackgroundColor(Color.fromARGB(background));
            t.setShadowed(true);
            t.setLineWidth(width);
            t.text(c.font() == null ? c.font(mn.suld.plugin.ui.StyleFormat.HD) : c);
            t.setTransformation(textShape(scale));
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
        int bz = (int) Math.floor(st.getZ() + s.dist);
        // an invisible frame draws its map flush with the block behind it (the client's item frame renderer: 0.5
        // from the block centre instead of a visible frame's 0.4375), one map pixel's depth (1/128) in front of it;
        // the parchment, the frame art and the labels are placed against that plane (seen on a 1.21.11 client)
        double faceZ = bz + 1 - 1.0 / 128;
        s.plane = new Location(w, bx + 0.5, by + 0.5, faceZ - 0.004);
        placeCamera(s);
        // the map wall: column 0 is on the viewer's left (world +x, the viewer faces +z)
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int frame = row * COLS + col;
                Location at = new Location(w, bx + (COLS / 2 - col), by + (ROWS / 2 - row), bz);
                ItemStack mapItem = mapItem(frames.view(s.viewAt[frame]));
                s.wall[frame] = own(s, s.stage, w.spawn(at, GlowItemFrame.class, f -> {
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
        room(s, w);
        picture(s, s.stage, "back", 0, 0, COLS, ROWS, -0.03, 0f);
        picture(s, s.stage, "front", 0, FRONT_V, FRONT_W, FRONT_H, 0.012, 0f);
        s.title = text(s, s.stage, 0, TITLE_V, Component.text("СҮЛДИЙН АТЛАС", GOLD, TextDecoration.BOLD), 1.2f, 300, 0);
        s.cartouche = text(s, s.stage, 0, CART_V, Component.empty(), CART_SCALE, 420, 0);
        s.tooltip = text(s, s.stage, 0, 0, Component.empty(), TIP_SCALE, 1000, 0, TIP_Z + 0.012);
        s.tooltip.setAlignment(TextDisplay.TextAlignment.LEFT);
        // tabs
        Filter[] fs = Filter.values();
        double top = (fs.length - 1) * TAB_STEP / 2;
        for (int i = 0; i < fs.length; i++) {
            Filter f = fs[i];
            double v = top - i * TAB_STEP;
            String model = "tab_" + f.icon;
            ItemDisplay bg = picture(s, s.stage, s.filter == f ? model + "_on" : model, -TAB_U, v, TAB_W, TAB_H, 0.02, 0f);
            s.tabs.add(new Hit(-TAB_U, v, TAB_W, TAB_H, () -> setFilter(s, f), Component.text(f.label, GOLD, TextDecoration.BOLD),
                    tipLines(f.about, f.use()), bg, model, model + "_hover"));
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
                case "plus" -> () -> zoom(s, -1, 0, 0);
                case "minus" -> () -> zoom(s, 1, 0, 0);
                case "pin_add" -> () -> togglePinMode(s);
                case "grow" -> () -> resize(s, FOV_STEP);
                case "shrink" -> () -> resize(s, -FOV_STEP);
                default -> null;
            };
            s.tabs.add(new Hit(TAB_U, v, TAB_W, TAB_H, click, Component.text(t.label(), GOLD, TextDecoration.BOLD),
                    tipLines(t.about(), t.use()), bg, model, click == null ? model : model + "_hover"));
        }
    }

    /**
     * The seat, and the camera on it, {@code s.dist} in front of the wall and EYE_UP from its middle: at the player's
     * fit the frame stands on their screen as on a 70° screen from DIST. A new fit glides it there (the seat's
     * teleport duration).
     */
    private void placeCamera(Session s) {
        Location cam = s.plane.clone().add(0, EYE_UP - s.eyeOff, 0.004 - s.dist);
        cam.setYaw(s.p.getLocation().getYaw());
        cam.setPitch(s.p.getLocation().getPitch());
        s.seat.teleport(cam); // passengers always ride along (Paper 1.21.10+)
    }

    /** A tooltip's lines under its name: what the thing is (cream) and, last, what a click does (cyan). */
    static List<Component> tipLines(List<String> about, String use) {
        List<Component> out = new ArrayList<>();
        for (String l : about) out.add(Component.text(l, CREAM));
        if (use != null) out.add(Component.text(use, CYAN));
        return out;
    }

    /** A navy box around the camera and the wall, so looking away from the atlas shows no sky; deep enough for every fit. */
    private void room(Session s, World w) {
        ItemStack navy = new ItemStack(Material.PAPER);
        ItemMeta meta = navy.getItemMeta();
        meta.setItemModel(new NamespacedKey("suld", "entity/skytree/line"));
        navy.setItemMeta(meta);
        navy.setData(DataComponentTypes.CUSTOM_MODEL_DATA, io.papermc.paper.datacomponent.item.CustomModelData.customModelData()
                .addColor(Color.fromRGB(0x0B1226)).build());
        double cx = s.plane.getX(), cy = s.plane.getY(), z0 = s.plane.getZ() - distFor(FOV_MIN) - 1.5, z1 = s.plane.getZ() + 0.7, hw = 9, hh = 6;
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
        int[] shift = frameShift(s.view, v);
        s.view = v;
        // a whole-frame pan: the frames still in view keep their pictures
        redraw(s, shift != null ? slideWall(s, shift[0], shift[1]) : null);
    }

    /**
     * One zoom step ({@code dz} −1 closer, +1 farther) toward panel point (u, v), shared by the wheel and the tabs. The
     * new scale is read out on the action bar; at either end the step is refused out loud instead of doing nothing.
     */
    private void zoom(Session s, int dz, double u, double v) {
        AtlasView next = s.view.zoomAt(s.view.zoom() + dz, u, v, map.geometry());
        if (next.zoom() == s.view.zoom()) {
            s.p.sendActionBar(Component.text(dz < 0 ? "Хамгийн ойр (" + zoomLabel(s.view.zoom()) + ")" : "Хамгийн хол (" + zoomLabel(s.view.zoom()) + ")", GREY));
            return;
        }
        setView(s, next);
        s.p.sendActionBar(Component.text("Zoom " + zoomLabel(next.zoom()), GOLD));
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
        redraw(s, null);
    }

    /**
     * {@link #redraw(Session)} after a slide: {@code uncovered} (null: none) are the positions whose views still hold the
     * pictures that slid off. With every tile at hand (land drawn before) the new strip goes out in this tick, with the
     * slide; while a tile is read they show blank, never a stale strip of the far side.
     */
    private void redraw(Session s, boolean[] uncovered) {
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
                        wanted.add(tiles.tile(v.tileZoom(), tx, tz));
                        keys.add(new int[]{col, row, tx, tz});
                    }
                }
            }
        }
        Runnable paint = () -> {
            if (sessions.get(s.p.getUniqueId()) != s || s.gen != gen) return; // a newer view is on its way
            byte[][] out = new byte[COLS * ROWS][];
            for (int i = 0; i < keys.size(); i++) {
                int[] k = keys.get(i);
                CompletableFuture<byte[]> f = wanted.get(i);
                byte[] tile = f.isCompletedExceptionally() ? null : f.getNow(null);
                if (tile == null) s.dirty = true; // a region read timed out: asked again within a second, not left blank
                int frame = k[1] * COLS + k[0];
                if (out[frame] == null) out[frame] = new byte[TileComposer.PIXELS];
                if (tile != null) v.copy(tile, k[2], k[3], k[0], k[1], out[frame]);
            }
            for (int f = 0; f < out.length; f++) frames.show(s.p, s.viewAt[f], out[f] == null ? BLANK : out[f]);
        };
        CompletableFuture<Void> all = CompletableFuture.allOf(wanted.toArray(CompletableFuture[]::new));
        if (all.isDone()) {
            paint.run();
        } else {
            if (uncovered != null) {
                for (int f = 0; f < uncovered.length; f++) if (uncovered[f]) frames.show(s.p, s.viewAt[f], BLANK);
            }
            all.whenComplete((x, err) -> {
                if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, paint);
            });
        }
        placeMarkers(s);
        cursor(s, true);
    }

    /** A tile changed (a chunk was read): redraw the atlases that show it. */
    private void tileChanged(int zoom, int tx, int tz) {
        for (Session s : sessions.values()) {
            if (!s.ready || s.view.tileZoom() != zoom) continue;
            int[] a = s.view.tilesOf(0, 0), b = s.view.tilesOf(COLS - 1, ROWS - 1);
            if (tx >= a[0] && tx <= b[2] && tz >= a[1] && tz <= b[3]) s.dirty = true;
        }
    }

    // ------------------------------------------------------------------------------------------------ places

    /**
     * The places, the player's pins and latest death, the waypoint flag, the regions' names and the player's own arrow.
     * Every mark is asked for by a stable key: the ones from the last redraw are moved, only new ones are spawned, and
     * the ones no longer on the wall are removed (a pan used to remove and spawn the whole set every 6 ticks).
     */
    private void placeMarkers(Session s) {
        s.marks.begin();
        try {
            placeAll(s);
        } finally {
            for (Entity e : s.marks.end()) if (e.isValid()) e.remove();
        }
    }

    private void placeAll(Session s) {
        s.places.clear();
        if (s.hover != null && !s.tabs.contains(s.hover)) s.hover = null;
        AtlasView v = s.view;
        int zoom = v.zoom();
        List<double[]> boxes = new ArrayList<>(); // labels already placed (u0, v0, u1, v1)
        List<mn.suld.api.map.RegionLabels.Box> taken = new ArrayList<>(); // markers and labels: the regions' names keep off them
        List<MapPois.Poi> pois = new ArrayList<>(map.pois.get());
        pois.sort((a, b) -> Integer.compare(rank(b.kind()), rank(a.kind())));
        for (MapPois.Poi poi : pois) {
            if (!s.filter.shows(poi.kind()) || !visibleAt(poi.kind(), zoom)) continue;
            double u = v.u(poi.x() + 0.5), vv = v.v(poi.z() + 0.5);
            if (Math.abs(u) > COLS / 2.0 - 0.05 || Math.abs(vv) > ROWS / 2.0 - 0.05) continue;
            boolean big = poi.kind() == MapPois.Kind.CITY || poi.kind() == MapPois.Kind.DUNGEON || poi.kind() == MapPois.Kind.BOSS;
            double size = big ? MARKER_BIG : MARKER;
            mark(s, s.marks, "poi:" + poiKey(poi), "marker_" + poi.kind().name().toLowerCase(Locale.ROOT), u, vv, size, size, 0.03, 0f);
            taken.add(mn.suld.api.map.RegionLabels.Box.around(u, vv, size / 2, size / 2));
            Runnable click = () -> point(s, poi);
            s.places.add(new Hit(u, vv, size * 1.3, size * 1.3, click, Component.text(poi.name(), GOLD, TextDecoration.BOLD), describe(s, poi), null, null, null));
            if (labelled(poi.kind(), zoom)) {
                double lw = Math.max(0.3, poi.name().length() * LABEL_CHAR), lv = vv - size / 2 - LABEL_GAP;
                double[] box = {u - lw / 2, lv - LABEL_HALF, u + lw / 2, lv + LABEL_HALF};
                boolean free = Math.abs(box[0]) < COLS / 2.0 && Math.abs(box[2]) < COLS / 2.0 && box[1] > -ROWS / 2.0;
                for (double[] b : boxes) if (box[0] < b[2] && b[0] < box[2] && box[1] < b[3] && b[1] < box[3]) free = false;
                if (free) {
                    boxes.add(box);
                    taken.add(new mn.suld.api.map.RegionLabels.Box(box[0], box[1], box[2], box[3]));
                    label(s, s.marks, "poi-label:" + poiKey(poi), u, lv, Component.text(poi.name(), CREAM), LABEL_SCALE, 300, LABEL_PLATE, 0.06, null);
                }
            }
        }
        // the player's own pins and the latest death
        if (pins != null && (s.filter == Filter.ALL || s.filter == Filter.PIN)) {
            for (mn.suld.api.map.MapPin pin : pins.pins(s.p.getUniqueId())) {
                double u = v.u(pin.x() + 0.5), vv = v.v(pin.z() + 0.5);
                if (Math.abs(u) > COLS / 2.0 - 0.05 || Math.abs(vv) > ROWS / 2.0 - 0.05) continue;
                mark(s, s.marks, "pin:" + pin.id(), "marker_pin", u, vv, MARKER_BIG, MARKER_BIG, 0.032, 0f);
                taken.add(mn.suld.api.map.RegionLabels.Box.around(u, vv, MARKER_BIG / 2, MARKER_BIG / 2));
                taken.add(mn.suld.api.map.RegionLabels.Box.around(u, vv - MARKER_BIG / 2 - LABEL_GAP, Math.max(0.3, pin.name().length() * LABEL_CHAR) / 2, LABEL_HALF));
                long d = Math.round(Math.hypot(pin.x() + 0.5 - s.origin.getX(), pin.z() + 0.5 - s.origin.getZ()));
                s.places.add(new Hit(u, vv, MARKER_BIG * 1.3, MARKER_BIG * 1.3, () -> pinDialog(s, pin), Component.text("◆ " + pin.name(), GOLD, TextDecoration.BOLD),
                        List.of(Component.text("Миний тэмдэг · " + d + " м", GREY), Component.text("Дарж: чиглэх эсвэл устгах", CYAN)), null, null, null));
                label(s, s.marks, "pin-label:" + pin.id(), u, vv - MARKER_BIG / 2 - LABEL_GAP, Component.text(pin.name(), CREAM), LABEL_SCALE, 300, 0x99000000, 0.06, null);
            }
        }
        if (s.death != null && s.filter == Filter.ALL) {
            double u = v.u(s.death[0] + 0.5), vv = v.v(s.death[1] + 0.5);
            if (Math.abs(u) <= COLS / 2.0 - 0.05 && Math.abs(vv) <= ROWS / 2.0 - 0.05) {
                mark(s, s.marks, "death", "marker_death", u, vv, MARKER_BIG, MARKER_BIG, 0.032, 0f);
                taken.add(mn.suld.api.map.RegionLabels.Box.around(u, vv, MARKER_BIG / 2, MARKER_BIG / 2));
                taken.add(mn.suld.api.map.RegionLabels.Box.around(u, vv - MARKER_BIG / 2 - LABEL_GAP, "Сүүлд унасан газар".length() * LABEL_CHAR / 2, LABEL_HALF));
                int[] at = s.death;
                s.places.add(new Hit(u, vv, MARKER_BIG * 1.3, MARKER_BIG * 1.3, () -> guide.guide(s.p, new WaypointGuide.Target("Сүүлд унасан газар", "☠",
                        s.origin.getWorld().getName(), at[0] + 0.5, WaypointGuide.GROUND, at[1] + 0.5)), Component.text("☠ Сүүлд унасан газар", GOLD, TextDecoration.BOLD),
                        List.of(Component.text("x " + at[0] + ", z " + at[1], GREY), Component.text("Дарж чиглэх", CYAN)), null, null, null));
                label(s, s.marks, "death-label", u, vv - MARKER_BIG / 2 - LABEL_GAP, Component.text("Сүүлд унасан газар", CREAM), LABEL_SCALE, 300, 0x99000000, 0.06, null);
            }
        }
        waypointFlag(s, taken);
        if (zoom >= REGION_NAMES_ZOOM) regionNames(s, taken);
        placeMates(s);
        // where the player stood, and which way they looked
        double pu = v.u(s.origin.getX()), pv = v.v(s.origin.getZ());
        if (v.onWall(pu, pv)) {
            // the arrow points up (north) unturned; yaw 0 looks south: turn clockwise on screen by yaw + 180°
            float turn = (float) Math.toRadians(s.origin.getYaw() + 180);
            mark(s, s.marks, "player", "marker_player", pu, pv, MARKER_BIG, MARKER_BIG, 0.035, turn);
        }
    }

    /**
     * The waypoint on the wall: a gold «▼ Чиглэл · 412 м» over it, the same waypoint the screen cards, the minimap's gold
     * diamond and the bar show. A waypoint to typed coordinates or a mate's spot, which has no marker of its own, also
     * gets a pin marker.
     */
    private void waypointFlag(Session s, List<mn.suld.api.map.RegionLabels.Box> taken) {
        WaypointGuide.Target t = guide == null ? null : guide.target(s.p.getUniqueId());
        if (t == null || !t.world().equals(s.origin.getWorld().getName())) return;
        AtlasView v = s.view;
        double u = v.u(t.x()), vv = v.v(t.z());
        if (Math.abs(u) > COLS / 2.0 - 0.05 || Math.abs(vv) > ROWS / 2.0 - 0.05) return;
        boolean marked = t.pin() != null || s.death != null && Math.floor(t.x()) == s.death[0] && Math.floor(t.z()) == s.death[1];
        for (MapPois.Poi poi : map.pois.get()) marked |= poi.x() + 0.5 == t.x() && poi.z() + 0.5 == t.z();
        if (!marked) mark(s, s.marks, "waypoint", "marker_pin", u, vv, MARKER, MARKER, 0.031, 0f);
        String meters = mn.suld.api.map.WaypointHud.meters(mn.suld.api.map.ScreenProjection.distance(s.origin.getX(), s.origin.getY(),
                s.origin.getZ(), t.x(), t.y(), t.z()));
        String line = "▼ Чиглэл · " + meters;
        double lv = vv + MARKER_BIG / 2 + LABEL_GAP + LABEL_HALF;
        taken.add(new mn.suld.api.map.RegionLabels.Box(u - line.length() * LABEL_CHAR / 2, lv - LABEL_HALF, u + line.length() * LABEL_CHAR / 2, lv + LABEL_HALF));
        label(s, s.marks, "waypoint-label", u, lv, Component.text(line, GOLD, TextDecoration.BOLD), LABEL_SCALE, 300, 0xB0101A30, 0.07, null);
    }

    /** From this zoom (8 blocks a pixel) out the atlas writes the regions' names across their lands. */
    static final int REGION_NAMES_ZOOM = 3;

    /** The regions' letters on the wall: a capital's mean width and a line's height at {@link #REGION_SCALE} (1/40 block a pixel). */
    static final float REGION_SCALE = 0.62f;
    static final mn.suld.api.map.RegionLabels.Metrics REGION_METRICS =
            new mn.suld.api.map.RegionLabels.Metrics(7 * 0.025 * REGION_SCALE, 10 * 0.025 * REGION_SCALE);

    /**
     * The regions' names across their lands, as an atlas names its countries: at the two widest zooms, faint gold
     * capitals behind the places and their labels, where {@link mn.suld.api.map.RegionLabels} puts them: the middle of
     * each wild region's ring and arc when free, else the nearest free spot of the region; never over another name, the
     * places' markers or labels ({@code taken}), always wholly on the wall; a long name of two words in two lines. The
     * city keeps its own label.
     */
    private void regionNames(Session s, List<mn.suld.api.map.RegionLabels.Box> taken) {
        World w = s.p.getWorld();
        Location sp = w.getSpawnLocation();
        double edge = w.getWorldBorder().getSize() / 2 - 100;
        List<mn.suld.api.map.RegionLabels.Region> regions = new ArrayList<>();
        for (mn.suld.api.region.RegionDefinition r : WorldContent.REGIONS) {
            if (!(r.shape() instanceof mn.suld.api.region.RegionShape.Sector sec)) continue;
            regions.add(new mn.suld.api.map.RegionLabels.Region(r.displayName(), sec.minRadius(), sec.maxRadius(), sec.fromDeg(), sec.toDeg()));
        }
        for (mn.suld.api.map.RegionLabels.Label l : mn.suld.api.map.RegionLabels.place(regions, s.view, sp.getX(), sp.getZ(), edge, REGION_METRICS, taken)) {
            // two lines: the text grows up from its foot, so its middle goes down a half line a line more
            Transformation shape = l.lines().size() > 1 ? new Transformation(new Vector3f(0, (float) (-0.125 * REGION_SCALE * l.lines().size()), 0),
                    new Quaternionf(), new Vector3f(REGION_SCALE), new Quaternionf()) : null;
            label(s, s.marks, "region:" + l.text(), l.u(), l.v(), Component.text(l.text().toUpperCase(java.util.Locale.ROOT), REGION_INK,
                    TextDecoration.BOLD), REGION_SCALE, 400, REGION_PLATE, 0.02, shape);
        }
    }

    /**
     * The party's other members where they are now, light blue arrows the way they look with their names, under every
     * tab (they are not a kind of place); a click starts the waypoint to where the mate stands. Placed with the
     * places, and again every second when one moved a block or turned.
     */
    private void placeMates(Session s) {
        s.mateMarks.begin();
        try {
            placeMateMarks(s);
        } finally {
            for (Entity e : s.mateMarks.end()) if (e.isValid()) e.remove();
        }
    }

    private void placeMateMarks(Session s) {
        s.places.removeAll(s.mateHits);
        if (s.hover != null && s.mateHits.contains(s.hover)) s.hover = null;
        s.mateHits.clear();
        s.matesKey = MinimapService.matesKey(services, s.p);
        AtlasView v = s.view;
        for (Player m : MinimapService.mates(services, s.p)) {
            Location l = m.getLocation();
            double u = v.u(l.getX()), vv = v.v(l.getZ());
            if (!v.onWall(u, vv)) continue;
            mark(s, s.mateMarks, "mate:" + m.getUniqueId(), "marker_party", u, vv, MARKER, MARKER, 0.034, (float) Math.toRadians(l.getYaw() + 180));
            long d = Math.round(Math.hypot(l.getX() - s.origin.getX(), l.getZ() - s.origin.getZ()));
            String name = m.getName(), world = l.getWorld().getName();
            double x = l.getX(), z = l.getZ();
            Hit h = new Hit(u, vv, MARKER * 1.4, MARKER * 1.4, () -> {
                guide.guide(s.p, new WaypointGuide.Target(name, "◆", world, x, WaypointGuide.GROUND, z));
                s.p.sendActionBar(Component.text("◆ Чиглэл: " + name, GOLD));
            }, Component.text("◆ " + name, CYAN, TextDecoration.BOLD),
                    List.of(Component.text("Багийн гишүүн · " + d + " м", GREY), Component.text("Дарж чиглэх (одоо байгаа газар нь)", CYAN)), null, null, null);
            s.mateHits.add(h);
            s.places.add(h);
            label(s, s.mateMarks, "mate-label:" + m.getUniqueId(), u, vv - MARKER / 2 - LABEL_GAP, Component.text(name, CYAN), LABEL_SCALE, 300, 0x99000000, 0.06, null);
        }
    }

    /** A place's key in the mark pool: the same place on the next redraw keeps its marker and label. */
    private static String poiKey(MapPois.Poi poi) {
        return poi.kind() + ":" + poi.x() + "," + poi.z() + ":" + poi.name();
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
        for (String l : about(poi)) out.add(Component.text(l, CREAM));
        double d = Math.hypot(poi.x() + 0.5 - s.origin.getX(), poi.z() + 0.5 - s.origin.getZ());
        out.add(Component.text(Math.round(d) + " м · x " + poi.x() + ", z " + poi.z(), GREY));
        out.add(Component.text("Дарж чиглэх (луужин, зам заагч)", CYAN));
        return out;
    }

    /** What a place is for, a short line or two between its kind and its distance. */
    static List<String> about(MapPois.Poi poi) {
        return switch (poi.kind()) {
            case CITY -> List.of("Аюулгүй хот: дэлгүүр, NPC,", "тулаан ба PvP байхгүй.");
            case RELAY -> List.of("Энд зогсоод бусад өртөө рүү", mn.suld.plugin.npc.NpcService.TRAVEL_COST + " зоосоор аялна.");
            case TRAINING -> List.of("Аль ч ангиа 60-р түвшинд", "туршиж дасгал хийнэ (/dasgal).");
            case DUNGEON -> dungeonLines(poi.id());
            case OVOO -> List.of("Нар зөв 3 тойрвол ивээл:", "+5% EXP 30 минут, өдөрт нэг.");
            case SITE -> List.of("Анх очиж үзвэл EXP авч,", "түүхийн тайлбар нээгдэнэ.");
            case BOSS -> List.of("Долоо хоногт тогтсон цагт", "гарна: бүгдээрээ хамт ялна.");
        };
    }

    /** A dungeon's level band and party (its gate says the same), and how to go in. */
    private static List<String> dungeonLines(String id) {
        for (mn.suld.api.dungeon.DungeonDefinition d : mn.suld.plugin.content.Content.pack().dungeons()) {
            if (!d.id().equals(id)) continue;
            int party = d.recommendedParty();
            return List.of("Түвшин " + d.minLevel() + "–" + d.maxLevel() + " · " + (party <= 1 ? "ганцаараа ч болно" : party + " хүнтэй баг"),
                    "Хаалгыг нь дарж орно.");
        }
        return List.of("Хаалгыг нь дарж орно.");
    }

    /** Makes a place the player's waypoint (the guide's bar and label, and the compass). */
    private void point(Session s, MapPois.Poi poi) {
        guide.guide(s.p, new WaypointGuide.Target(poi.name(), "◆", s.origin.getWorld().getName(), poi.x() + 0.5, WaypointGuide.GROUND, poi.z() + 0.5));
        s.p.sendActionBar(Component.text("◆ Чиглэл: " + poi.name(), GOLD));
    }

    private void togglePinMode(Session s) {
        s.pinMode = !s.pinMode;
        for (Hit h : s.tabs) {
            if (!"tab_pin_add".equals(h.model()) || !h.bg().isValid()) continue;
            h.bg().setItemStack(quad(s.pinMode ? h.model() + "_on" : h.model()));
        }
        s.p.sendActionBar(Component.text(s.pinMode ? "◆ Тэмдэг тавих газраа газрын зураг дээр дарна уу" : "Тэмдэг тавихаа болилоо", GOLD));
    }

    /** A pin at block (x, z) of the map: its name is asked in a dialog over the atlas. */
    private void pinAt(Session s, int x, int z) {
        Player p = s.p;
        World w = s.origin.getWorld();
        int y = w.isChunkLoaded(x >> 4, z >> 4) ? w.getHighestBlockYAt(x, z) + 1 : 64;
        String def = mn.suld.api.map.MapPin.defaultName(pins.pins(p.getUniqueId()));
        io.papermc.paper.dialog.Dialog d = io.papermc.paper.dialog.Dialog.create(f -> f.empty()
                .base(io.papermc.paper.registry.data.dialog.DialogBase.builder(Component.text("◆ Шинэ тэмдэг", GOLD, TextDecoration.BOLD))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(
                                Component.text(place(x, z), GREY), 300)))
                        .inputs(List.of(io.papermc.paper.registry.data.dialog.input.DialogInput.text("name", Component.text("Нэр", GREY))
                                .width(300).maxLength(mn.suld.api.map.MapPin.MAX_NAME).initial(def).build()))
                        .build())
                .type(io.papermc.paper.registry.data.dialog.type.DialogType.confirmation(
                        io.papermc.paper.registry.data.dialog.ActionButton.create(Component.text("Хадгалах ◆", NamedTextColor.GREEN, TextDecoration.BOLD),
                                null, 120, io.papermc.paper.registry.data.dialog.action.DialogAction.customClick((view, who) -> {
                                    String name = view.getText("name");
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        if (who instanceof Player pl && pl.isOnline()) pins.add(pl, name, x, y, z, pin -> refreshFor(pl));
                                    });
                                }, ONCE)),
                        io.papermc.paper.registry.data.dialog.ActionButton.create(Component.text("Болих"), null, 100, null))));
        s.pinMode = false;
        togglePinModeOff(s);
        p.showDialog(d);
    }

    private void togglePinModeOff(Session s) {
        for (Hit h : s.tabs) if ("tab_pin_add".equals(h.model()) && h.bg().isValid()) h.bg().setItemStack(quad(h.model()));
    }

    /** A pin's own window: guide to it or remove it. */
    private void pinDialog(Session s, mn.suld.api.map.MapPin pin) {
        Player p = s.p;
        io.papermc.paper.dialog.Dialog d = io.papermc.paper.dialog.Dialog.create(f -> f.empty()
                .base(io.papermc.paper.registry.data.dialog.DialogBase.builder(Component.text("◆ " + pin.name(), GOLD, TextDecoration.BOLD))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(Component.text(place(pin.x(), pin.z()), GREY), 300)))
                        .build())
                .type(io.papermc.paper.registry.data.dialog.type.DialogType.multiAction(List.of(
                        io.papermc.paper.registry.data.dialog.ActionButton.create(Component.text("Чиглэх ◆", CYAN, TextDecoration.BOLD), null, 100,
                                io.papermc.paper.registry.data.dialog.action.DialogAction.customClick((view, who) -> Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (who instanceof Player pl && pl.isOnline()) guidePin(pl, pin);
                                }), ONCE)),
                        io.papermc.paper.registry.data.dialog.ActionButton.create(Component.text("Устгах ✕", NamedTextColor.RED), null, 100,
                                io.papermc.paper.registry.data.dialog.action.DialogAction.customClick((view, who) -> Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (who instanceof Player pl && pl.isOnline()) pins.remove(pl, pin, () -> unpinned(pl, pin));
                                }), ONCE))))
                        .columns(2)
                        .exitAction(io.papermc.paper.registry.data.dialog.ActionButton.create(Component.text("Болих"), null, 100, null))
                        .build()));
        p.showDialog(d);
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
            line = "Zoom " + zoomLabel(s.view.zoom()) + " · Space: заавар · Shift: хаах";
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

    static String zoomLabel(int zoom) {
        return switch (zoom) {
            case -2 -> "4:1";
            case -1 -> "2:1";
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

    /** How far in front of the wall the tooltip's frame and the guide's frame stand (the tooltip over the guide). */
    static final double HELP_Z = 0.07, TIP_Z = 0.1;

    /**
     * The tooltip of the thing under the cursor, in the SÜLD tooltip frame (the same navy-and-gold frame every tooltip
     * in the game wears) right beside it, toward the middle of the wall: its name in bold gold, its lines in bold cream.
     */
    private void showTooltip(Session s, Hit h) {
        for (Entity e : s.tipFrame) if (e.isValid()) e.remove();
        s.tipFrame.clear();
        if (h == null) {
            s.tooltip.text(Component.empty());
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(plain(h.name()));
        Component tip = Component.text(lines.get(0), GOLD, TextDecoration.BOLD);
        for (Component l : h.lore()) {
            String line = plain(l);
            lines.add(line);
            // every line bold in its own colour: cream what it is, grey where, cyan what a click does
            tip = tip.append(Component.newline()).append(Component.text(line, l.color() != null ? l.color() : CREAM, TextDecoration.BOLD));
        }
        double[] box = box(lines, TIP_SCALE);
        double w = box[0], ht = box[1];
        // beside the thing, on the side toward the wall's middle, and wholly on the wall
        double side = h.u() > 0 ? -1 : 1;
        double cu = h.u() + side * (h.w() / 2 + 0.06 + w / 2);
        double half = COLS / 2.0 + 0.4;
        cu = Math.max(-half + w / 2, Math.min(half - w / 2, cu));
        double cv = Math.max(-ROWS / 2.0 + ht / 2, Math.min(ROWS / 2.0 - ht / 2, h.v()));
        panel(s, s.tipFrame, cu, cv, w, ht, TIP_Z);
        s.tooltip.text(tip.font(mn.suld.plugin.ui.StyleFormat.HD));
        // moved, keeping its yaw 180: teleport(at(...)) gave it the plane's yaw 0 and turned the one-sided text away from
        // the camera, an empty frame on every tooltip (owner's screenshot, 2026-10-18)
        move(s.tooltip, at(s, cu, cv - (lines.size() - 1) * LINE * TIP_SCALE / 2, TIP_Z + 0.012));
    }

    private static String plain(Component c) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
    }

    /**
     * The SÜLD frame's size (w, h in blocks) around these lines of bold HD text at {@code scale}: the widest line
     * (gui.HubLayout's widths of the HD font, one pixel more a letter for the bold) and a line's height each, with room.
     */
    static double[] box(List<String> lines, float scale) {
        int px = 0;
        for (String l : lines) px = Math.max(px, mn.suld.plugin.gui.HubLayout.textWidth(l) + l.length());
        double w = px * FONT_PX * scale + 2 * (TIP_BORDER * 0.6 + TIP_PAD);
        double h = lines.size() * LINE * scale + 2 * (TIP_BORDER * 0.5 + TIP_PAD);
        return new double[]{w, h};
    }

    /**
     * The SÜLD tooltip frame (tools/map/atlas_art.py: the pack's suld:mongol nine-slice, cut in nine) as a w × h box
     * centred at (u, v): the corners keep their size, the edges and the middle stretch.
     */
    private void panel(Session s, List<Entity> into, double u, double v, double w, double h, double towardViewer) {
        panel(s, into, u, v, w, h, towardViewer, TIP_BORDER);
    }

    private void panel(Session s, List<Entity> into, double u, double v, double w, double h, double towardViewer, double border) {
        double b = Math.min(border, Math.min(w, h) / 2);
        double[] us = {u - w / 2 + b / 2, u, u + w / 2 - b / 2}, vs = {v + h / 2 - b / 2, v, v - h / 2 + b / 2};
        double[] ws = {b, w - 2 * b, b}, hs = {b, h - 2 * b, b};
        String[] names = {"tl", "t", "tr", "l", "m", "r", "bl", "b", "br"};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (ws[c] <= 0 || hs[r] <= 0) continue;
                // the middle a hair behind the edges and corners, so the gold line is never hidden under the navy
                double z = towardViewer + (r == 1 && c == 1 ? 0 : 0.002);
                picture(s, into, "tip_" + names[r * 3 + c], us[c], vs[r], ws[c] + (c == 1 ? 0.002 : 0), hs[r] + (r == 1 ? 0.002 : 0), z, 0f);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the guide

    private void toggleHelp(Session s) {
        if (s.help.isEmpty()) showHelp(s);
        else hideHelp(s);
    }

    /** The guide: what every key and tab does, in bold Mongolian on the SÜLD frame in the middle of the map. */
    private void showHelp(Session s) {
        hideHelp(s);
        List<String> plainLines = helpLines();
        Component body = Component.text(HELP_TITLE, GOLD, TextDecoration.BOLD).append(Component.newline());
        for (String[] l : HELP) {
            body = body.append(Component.newline()).append(Component.text(l[0], CYAN, TextDecoration.BOLD))
                    .append(Component.text(" — " + l[1], CREAM, TextDecoration.BOLD));
        }
        body = body.append(Component.newline()).append(Component.newline())
                .append(Component.text(HELP_CLOSE, GREY, TextDecoration.BOLD));
        double[] box = box(plainLines, HELP_SCALE);
        double cv = 0.1;
        panel(s, s.help, 0, cv, box[0] + HELP_BORDER, box[1] + HELP_BORDER, HELP_Z, HELP_BORDER);
        TextDisplay t = text(s, s.help, 0, cv - (plainLines.size() - 1) * LINE * HELP_SCALE / 2, body.font(mn.suld.plugin.ui.StyleFormat.HD),
                HELP_SCALE, 1000, 0, HELP_Z + 0.012);
        t.setAlignment(TextDisplay.TextAlignment.LEFT);
        s.p.playSound(s.p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.3f);
    }

    /** The guide's lines as plain text, as shown (title, a blank line, the keys, a blank line, how to close it). */
    static List<String> helpLines() {
        List<String> out = new ArrayList<>();
        out.add(HELP_TITLE);
        out.add("");
        for (String[] l : HELP) out.add(l[0] + " — " + l[1]);
        out.add("");
        out.add(HELP_CLOSE);
        return out;
    }

    private void hideHelp(Session s) {
        for (Entity e : s.help) if (e.isValid()) e.remove();
        s.help.clear();
    }

    // ------------------------------------------------------------------------------------------------ input

    private void tick() {
        for (Session s : new ArrayList<>(sessions.values())) {
            if (!s.ready) continue;
            if (!s.p.isOnline() || !s.seat.isValid()) {
                close(s.p);
                continue;
            }
            // W / A / S / D: one whole frame at once, again every 6 ticks while held. A whole frame lets the frames still in
            // view keep their pictures (setView slides them): only the uncovered row or column is drawn and sent
            boolean any = s.up || s.down || s.left || s.right;
            if (any) {
                if (s.heldTicks % 6 == 0) {
                    double du = (s.right ? 1 : 0) - (s.left ? 1 : 0), dv = (s.up ? 1 : 0) - (s.down ? 1 : 0);
                    setView(s, s.view.pan(du * PAN_FRAMES, dv * PAN_FRAMES, map.geometry()));
                }
                s.heldTicks++;
            } else {
                s.heldTicks = 0;
            }
            if (s.dirty && Bukkit.getCurrentTick() % 20 == 0) {
                s.dirty = false;
                redraw(s);
            } else if (Bukkit.getCurrentTick() % 20 == 10 && MinimapService.matesKey(services, s.p) != s.matesKey) {
                placeMates(s); // a party mate moved: only their markers
            }
            if (Bukkit.getCurrentTick() % 2 == 0) cursor(s, false);
            if (Bukkit.getCurrentTick() % 20 == 5) calmSpeed(s.p);
        }
    }

    private void click(Session s) {
        long now = System.currentTimeMillis();
        if (now - s.lastClick < 200) return;
        s.lastClick = now;
        if (!s.help.isEmpty()) { // the first click puts the guide away
            hideHelp(s);
            return;
        }
        cursor(s, false);
        Hit h = s.hover;
        if (s.pinMode && (h == null || !s.tabs.contains(h))) {
            double[] a = aim(s);
            if (a != null && s.view.onWall(a[0], a[1])) {
                s.p.playSound(s.p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                pinAt(s, (int) Math.floor(s.view.worldX(a[0])), (int) Math.floor(s.view.worldZ(a[1])));
            }
            return;
        }
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

    /** The wheel zooms toward the cursor; with Ctrl (the sprint key) held it sizes the atlas instead. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onWheel(PlayerItemHeldEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s == null || !s.ready) return;
        e.setCancelled(true);
        int d = e.getNewSlot() - e.getPreviousSlot();
        if (d > 4) d -= 9;
        if (d < -4) d += 9;
        if (d == 0) return;
        if (s.sprint) { // wheel up (the slot before) = larger, like the map's zoom
            resize(s, d < 0 ? FOV_STEP : -FOV_STEP);
            return;
        }
        double[] a = aim(s);
        double u = a != null && s.view.onWall(a[0], a[1]) ? a[0] : 0, v = a != null && s.view.onWall(a[0], a[1]) ? a[1] : 0;
        // wheel down (next slot) = farther, like a web map
        zoom(s, Integer.signum(d), u, v);
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
        s.sprint = in.isSprint();
        // Space: the guide, open or away (on the press, not while held)
        boolean jump = in.isJump();
        if (jump && !s.jump && s.ready) toggleHelp(s);
        s.jump = jump;
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
        if (isAtlasCommand(e.getMessage())) return; // the command closes it itself
        close(p);
    }

    /** The /map command under any of its names (plugin.yml aliases, the suld: prefix), so it toggles instead of closing and reopening. */
    static final java.util.Set<String> LABELS = java.util.Set.of("map", "maps", "atlas", "gazar", "газар");

    static boolean isAtlasCommand(String message) {
        String m = message.strip();
        if (!m.startsWith("/")) return false;
        int sp = m.indexOf(' ');
        String label = (sp < 0 ? m.substring(1) : m.substring(1, sp)).toLowerCase(Locale.ROOT);
        if (label.startsWith("suld:")) label = label.substring(5);
        return LABELS.contains(label);
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
            for (Session s : sessions.values()) if (s.marks.holds(en) || s.mateMarks.holds(en) || s.stage.contains(en)) live = true;
            if (!live) en.remove();
        }
    }
}
