package mn.suld.plugin.gui;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Glyphs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The «Командын Өргөө» windows as a sky screen, like the skill tree (docs/UI_HUB.md): the player is seated on a
 * private stage high above where they stand, and the page is built in front of the locked camera out of display
 * entities only they can see. Text is real text (TextDisplay), so nothing depends on the client's GUI scale, the
 * way the chest-title art did. The crosshair is the cursor: the card under it lights up and shows its tooltip, a left
 * click runs it, W / A / S / D pan, the wheel zooms, Shift closes.
 * <p>
 * Every page is still a {@link HubMenu.Spec}: {@link HubMenu#open} hands it here. A click that opens another hub page
 * rebuilds the scene in place; a click that does anything else (a chest window, a dialog, a teleport) closes the sky
 * first view-wise and returns the player where they stood. In combat, a dungeon run, mid-air or a vehicle the chest
 * window opens instead.
 */
public final class MenuSky implements Listener {

    static final String TAG = "suld_menusky";
    /** The panel stands PLANE blocks ahead of the stage; the camera sits DIST0 from it, the wheel zooms DIST_MIN … DIST_MAX. */
    static final double PAN_U = 5.0, PAN_V = 3.2;
    static final double PLANE = 7.5, DIST0 = 3.2, DIST_MIN = 1.8, DIST_MAX = 7.0, ZOOM_STEP = 0.45;
    static final long COMBAT_MS = 8000;
    static final float BOX_W = 40, BOX_H = 24, BOX_D = 16;

    // layout on the panel plane, in blocks: u to the right, v up, (0,0) straight ahead of the eye
    static final double TAB_V = 1.95, TAB_W = 1.12, TAB_H = 0.62, TAB_GAP = 0.08;
    static final double CARD_W = 4.5, CARD_H = 0.66, CARD_GAP_U = 0.3, CARD_TOP = 1.1, CARD_STEP = 0.78;
    static final double FOOT_V = -2.25, FOOT_H = 0.5;

    private static final int NAVY = 0x0B1226, PANEL = 0x111A33, CARD = 0x18264A, CARD_HOVER = 0x2B5E86, CARD_ON = 0x6B5414,
            TAB = 0x1A2440, TAB_ON = 0x2B6E8E, BUTTON = 0x24304F, CLOSE = 0x6E1E24, GOLD_RIM = 0xC9962E;
    private static final TextColor CYAN = TextColor.fromHexString("#56E2EC"), GOLD = TextColor.fromHexString("#ECBE4C"),
            GREY = TextColor.fromHexString("#A9B3C6");
    private static final NamespacedKey RETURN = new NamespacedKey("suld", "menusky_return");

    private static MenuSky instance;

    /** The running sky screen (null in tests and before enable). */
    public static MenuSky instance() {
        return instance;
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> lastCombat = new HashMap<>();

    /** One clickable area of the page. */
    private record Hit(double u, double v, double w, double h, Runnable click, Component name, List<Component> lore, ItemDisplay bg, int rgb, int hover) {
        boolean contains(double x, double y) {
            return Math.abs(x - u) <= w / 2 && Math.abs(y - v) <= h / 2;
        }
    }

    private final class Session {
        final Player p;
        final Location origin;
        final boolean invulnerable;
        final List<Entity> stage = new ArrayList<>(), page = new ArrayList<>();
        final List<Hit> hits = new ArrayList<>();
        ItemDisplay seat;
        Location plane;
        double eyeOff;
        TextDisplay tooltip;
        HubMenu.Spec spec;
        List<HubMenu.Tab> tabs;
        int hover = -1;
        boolean closing, ready, capturing;
        HubMenu.Spec captured;
        List<HubMenu.Tab> capturedTabs;
        long lastClick;
        double dist = DIST0, camU, camV;
        boolean up, down, left, right, moved;

        Session(Player p) {
            this.p = p;
            this.origin = p.getLocation().clone();
            this.invulnerable = p.isInvulnerable();
        }
    }

    public MenuSky(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void start() {
        instance = this;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public boolean isOpen(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    /**
     * Show {@code spec}: rebuilt in place when the sky is already open (or captured during a click), opened when the
     * player may leave the ground now; false = the caller opens the chest window instead.
     */
    public boolean show(Player p, HubMenu.Spec spec, List<HubMenu.Tab> tabs) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null) {
            if (s.capturing) {
                s.captured = spec;
                s.capturedTabs = tabs;
            } else if (s.ready) {
                buildPage(s, spec, tabs);
            } else {
                s.spec = spec;
                s.tabs = tabs;
            }
            return true;
        }
        if (blocked(p) != null) return false;
        open(p, spec, tabs);
        return true;
    }

    /** Why the sky cannot open now (the chest window opens instead), or null. */
    private String blocked(Player p) {
        if (services.dungeons().isInAnyRun(p.getUniqueId())) return "dungeon";
        if (System.currentTimeMillis() - lastCombat.getOrDefault(p.getUniqueId(), 0L) < COMBAT_MS) return "combat";
        // falling (the return teleport would cancel the fall): the client's on-ground flag alone is too strict (slabs,
        // a step, just after joining)
        if (p.isInsideVehicle() || p.isGliding() || !p.isFlying() && (p.getFallDistance() > 1.5f || p.getVelocity().getY() < -0.6)) return "air";
        if (p.isDead()) return "dead";
        return null;
    }

    private void open(Player p, HubMenu.Spec spec, List<HubMenu.Tab> tabs) {
        Session s = new Session(p);
        s.spec = spec;
        s.tabs = tabs;
        sessions.put(p.getUniqueId(), s);
        p.closeInventory();
        Location o = s.origin;
        p.getPersistentDataContainer().set(RETURN, PersistentDataType.STRING, o.getWorld().getName() + ";" + o.getX() + ";" + o.getY() + ";" + o.getZ()
                + ";" + o.getYaw() + ";" + o.getPitch());
        World w = o.getWorld();
        // a stage of its own, away from the skill tree's (that one stands +48 over the same column)
        Location stage = new Location(w, Math.floor(o.getX()) + 0.5, w.getMaxHeight() + 96, Math.floor(o.getZ()) + 0.5, 0f, 0f);
        p.setInvulnerable(true);
        p.setFallDistance(0);
        services.movement.hold(p.getUniqueId(), "menu sky seat");
        p.teleport(stage);
        s.seat = w.spawn(stage, ItemDisplay.class, d -> {
            hidden(d);
            d.setTeleportDuration(3);
        });
        s.stage.add(s.seat);
        p.showEntity(plugin, s.seat);
        s.seat.addPassenger(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (sessions.get(p.getUniqueId()) != s || !p.isOnline()) return;
            buildStage(s);
            moveCamera(s);
            buildPage(s, s.spec, s.tabs);
            s.ready = true;
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        }, 2L);
    }

    public void close(Player p) {
        close(p, true);
    }

    /** Close; {@code goBack} false when the player was already moved away (a travel card teleported them). */
    private void close(Player p, boolean goBack) {
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) return;
        s.closing = true;
        for (Entity e : s.page) if (e.isValid()) e.remove();
        for (Entity e : s.stage) if (e.isValid()) e.remove();
        if (p.isInsideVehicle()) p.leaveVehicle();
        if (goBack) p.teleport(s.origin);
        p.setFallDistance(0);
        p.setInvulnerable(s.invulnerable);
        services.movement.release(p.getUniqueId(), "menu sky seat");
        services.movement.exempt(p.getUniqueId(), 20, "menu sky return");
        p.getPersistentDataContainer().remove(RETURN);
    }

    public void shutdown() {
        for (Session s : new ArrayList<>(sessions.values())) close(s.p);
        instance = null;
    }

    // ------------------------------------------------------------------------------------------------ scene

    private void hidden(Display d) {
        d.setPersistent(false);
        d.setVisibleByDefault(false);
        d.addScoreboardTag(TAG);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setShadowRadius(0);
        d.setViewRange(2f);
    }

    private static ItemStack quad(String model, int rgb) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("suld", "entity/skytree/" + model));
        it.setItemMeta(meta);
        if (rgb >= 0) it.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addColor(Color.fromRGB(rgb)).build());
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

    /** The seat (and the camera on it) {@code dist} blocks in front of the panel; the box rides along. */
    private void moveCamera(Session s) {
        s.moved = false;
        Location cam = s.plane.clone().add(-s.camU, s.camV - s.eyeOff, -s.dist);
        cam.setYaw(s.p.getLocation().getYaw());
        cam.setPitch(s.p.getLocation().getPitch());
        s.seat.teleport(cam, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    /** The box around the camera: navy everywhere, the panel in front. Built once per opening. */
    private void buildStage(Session s) {
        Player p = s.p;
        s.eyeOff = p.getEyeLocation().getY() - s.seat.getLocation().getY();
        s.plane = s.seat.getLocation().clone().add(0, s.eyeOff, PLANE);
        float hx = BOX_W, hy = BOX_H, hz = BOX_D, ey = (float) s.eyeOff;
        Quaternionf ry = new Quaternionf().rotateY((float) (Math.PI / 2)), rx = new Quaternionf().rotateX((float) (Math.PI / 2));
        // the far wall stays behind the panel (not a passenger: the seat moves when the player zooms)
        rect(s, s.stage, 0, 0, hx * 2, hy * 2, 0x060A16, -0.6);
        backdrop(s, "line", NAVY, new Vector3f(hx, ey, 0), ry, new Vector3f(hz * 2, hy * 2, 1));
        backdrop(s, "line", NAVY, new Vector3f(-hx, ey, 0), ry, new Vector3f(hz * 2, hy * 2, 1));
        backdrop(s, "line", NAVY, new Vector3f(0, ey + hy, 0), rx, new Vector3f(hx * 2, hz * 2, 1));
        backdrop(s, "line", 0x070B18, new Vector3f(0, ey - hy, 0), rx, new Vector3f(hx * 2, hz * 2, 1));
        backdrop(s, "line", NAVY, new Vector3f(0, ey, -hz), new Quaternionf(), new Vector3f(hx * 2, hy * 2, 1));
        // the window: a gold rim behind a navy panel
        rect(s, s.stage, 0, 0.05, 10.4, 6.5, GOLD_RIM, -0.02);
        rect(s, s.stage, 0, 0.05, 10.25, 6.35, PANEL, 0.0);
        s.tooltip = own(s, s.stage, p.getWorld().spawn(at(s, 0, 0, 0.6), TextDisplay.class, t -> {
            hidden(t);
            t.setBillboard(Display.Billboard.FIXED);
            t.setBackgroundColor(Color.fromARGB(235, 10, 14, 30));
            t.setLineWidth(260);
            t.setAlignment(TextDisplay.TextAlignment.LEFT);
            t.setShadowed(true);
            t.setRotation(180f, 0f);
            t.text(Component.empty());
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.72f), new Quaternionf()));
        }));
    }

    private void backdrop(Session s, String model, int rgb, Vector3f at, Quaternionf rot, Vector3f scale) {
        ItemDisplay d = own(s, s.stage, s.p.getWorld().spawn(s.seat.getLocation(), ItemDisplay.class, e -> {
            hidden(e);
            e.setItemStack(quad(model, rgb));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setViewRange(4f);
            e.setTransformation(new Transformation(at, rot, scale, new Quaternionf()));
        }));
        s.seat.addPassenger(d);
    }

    /** A flat tinted rectangle on the panel (towardViewer: small offsets keep layers in order). */
    private ItemDisplay rect(Session s, List<Entity> into, double u, double v, double w, double h, int rgb, double towardViewer) {
        return own(s, into, s.p.getWorld().spawn(at(s, u, v, towardViewer), ItemDisplay.class, d -> {
            hidden(d);
            d.setItemStack(quad("line", rgb));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f((float) w, (float) h, 1), new Quaternionf()));
        }));
    }

    /** Text facing the viewer, centred on (u, v); {@code width} in text pixels before scaling. */
    private TextDisplay text(Session s, double u, double v, Component c, float scale, int width) {
        Location l = at(s, u, v, 0.08);
        return own(s, s.page, s.p.getWorld().spawn(l, TextDisplay.class, t -> {
            hidden(t);
            t.setBillboard(Display.Billboard.FIXED);
            t.setRotation(180f, 0f);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setShadowed(true);
            t.setLineWidth(width);
            t.text(c.font() == null ? c.font(mn.suld.plugin.ui.StyleFormat.HD) : c);
            // a TextDisplay grows up from its origin: drop it by half a line per line so (u, v) is its middle
            int lines = 1 + (int) PlainLines.count(c);
            t.setTransformation(new Transformation(new Vector3f(0, (float) (-0.125 * scale * lines), 0), new Quaternionf(),
                    new Vector3f(scale), new Quaternionf()));
        }));
    }

    private ItemDisplay icon(Session s, double u, double v, ItemStack item, float size) {
        return own(s, s.page, s.p.getWorld().spawn(at(s, u, v, 0.1), ItemDisplay.class, d -> {
            hidden(d);
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(size, size, 0.02f), new Quaternionf()));
        }));
    }

    private static String pageTitle(String page) {
        if (Glyphs.PAGE_PROFILE.equals(page)) return "Тоглогчийн профайл";
        if (Glyphs.PAGE_HELP.equals(page)) return "Тусламж";
        if (Glyphs.PAGE_RANKUP.equals(page)) return "Цол · Rankup";
        if (Glyphs.PAGE_SHOP.equals(page)) return "Дэлгүүр · 1/2";
        if (Glyphs.PAGE_SHOP2.equals(page)) return "Дэлгүүр · 2/2";
        if (Glyphs.PAGE_TRAVEL.equals(page)) return "Аялал";
        if (Glyphs.PAGE_SETTINGS.equals(page)) return "Тохиргоо";
        return "Командын Өргөө";
    }

    /** The page itself: header, tabs, cards, footer. Removed and rebuilt on every page change. */
    private void buildPage(Session s, HubMenu.Spec spec, List<HubMenu.Tab> tabs) {
        for (Entity e : s.page) if (e.isValid()) e.remove();
        s.page.clear();
        s.hits.clear();
        s.hover = -1;
        s.spec = spec;
        s.tabs = tabs;
        s.tooltip.text(Component.empty());
        s.tooltip.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));

        // header
        text(s, 0, 2.78, Component.text("SÜLD", GOLD, TextDecoration.BOLD).append(Component.text("  ◆  ", CYAN))
                .append(Component.text(pageTitle(spec.page()).toUpperCase(), GOLD, TextDecoration.BOLD)), 1.25f, 400);
        if (spec.dynamic() != null) text(s, 0, 2.42, spec.dynamic().colorIfAbsent(GREY), 0.7f, 400);

        // tabs
        double tabsW = HubLayout.TABS * TAB_W + (HubLayout.TABS - 1) * TAB_GAP;
        for (int i = 0; i < Math.min(tabs.size(), HubLayout.TABS); i++) {
            HubMenu.Tab t = tabs.get(i);
            boolean active = i == spec.activeTab();
            double u = -tabsW / 2 + TAB_W / 2 + i * (TAB_W + TAB_GAP);
            int rgb = active ? TAB_ON : TAB;
            ItemDisplay bg = rect(s, s.page, u, TAB_V, TAB_W, TAB_H, rgb, 0.03);
            text(s, u, TAB_V, Component.text(t.label(), active ? NamedTextColor.WHITE : GOLD, TextDecoration.BOLD), 0.62f, 100);
            Consumer<Player> open = t.open();
            Runnable click = open == null || active ? null : () -> open.accept(s.p);
            List<Component> lore = List.of(Component.text(t.hint(), GREY),
                    Component.text(active ? "Та энд байна" : open == null ? "Удахгүй" : "Дарж нээх", active ? NamedTextColor.GRAY : CYAN));
            s.hits.add(new Hit(u, TAB_V, TAB_W, TAB_H, click, Component.text(t.label(), GOLD, TextDecoration.BOLD), lore, bg, rgb,
                    click == null ? rgb : CARD_HOVER));
        }

        // cards: two columns, four rows
        for (int i = 0; i < Math.min(spec.cards().size(), HubLayout.CARDS); i++) {
            HubMenu.Card c = spec.cards().get(i);
            if (c == null) continue;
            double u = (i % 2 == 0 ? -1 : 1) * (CARD_W / 2 + CARD_GAP_U / 2), v = CARD_TOP - (i / 2) * CARD_STEP;
            boolean on = (spec.cardMask() >> i & 1) != 0;
            if (on) rect(s, s.page, u, v, CARD_W + 0.08, CARD_H + 0.08, GOLD_RIM, 0.02);
            int rgb = on ? CARD_ON : CARD;
            ItemDisplay bg = rect(s, s.page, u, v, CARD_W, CARD_H, rgb, 0.03);
            boolean hasIcon = c.item() != null;
            if (hasIcon) icon(s, u - CARD_W / 2 + 0.38, v, c.item(), 0.5f);
            double tu = hasIcon ? u + 0.3 : u;
            Component body = c.name();
            if (!c.lore().isEmpty()) body = body.append(Component.newline()).append(c.lore().get(0).colorIfAbsent(GREY));
            text(s, tu, v, body, 0.8f, (int) ((CARD_W - (hasIcon ? 0.9 : 0.3)) / 0.8 / 0.025));
            Menu.Click click = c.click();
            s.hits.add(new Hit(u, v, CARD_W, CARD_H, click == null ? null : () -> click.accept(s.p, ClickType.LEFT), c.name(), c.lore(), bg, rgb,
                    click == null ? rgb : CARD_HOVER));
        }

        // the ninth tile (stats, tutorial, badges ...): a small info button in the header's corner
        if (spec.ornament() != null) {
            HubMenu.Card o = spec.ornament();
            double u = 4.45, v = 2.7;
            ItemDisplay bg = rect(s, s.page, u, v, 0.9, 0.6, BUTTON, 0.03);
            text(s, u, v, Component.text("ℹ", CYAN, TextDecoration.BOLD), 1.2f, 40);
            Menu.Click click = o.click();
            s.hits.add(new Hit(u, v, 0.9, 0.6, click == null ? null : () -> click.accept(s.p, ClickType.LEFT), o.name(), o.lore(), bg, BUTTON, CARD_HOVER));
        }

        // footer: back, close, page arrows (large and labelled: the old window hid them)
        if (spec.back() != null) {
            Consumer<Player> back = spec.back();
            button(s, -4.0, FOOT_V, 1.9, "‹ Буцах", BUTTON, () -> back.accept(s.p), "Өмнөх цонх руу.");
        }
        button(s, 0, FOOT_V, 2.2, "✕ Хаах", CLOSE, () -> close(s.p), "Цонхыг хаана (Shift).");
        if (spec.prev() != null) {
            Consumer<Player> prev = spec.prev();
            button(s, 2.55, FOOT_V, 1.9, "‹ Өмнөх", BUTTON, () -> prev.accept(s.p), "Өмнөх хуудас.");
        }
        if (spec.next() != null) {
            Consumer<Player> next = spec.next();
            button(s, 4.0, FOOT_V, 1.9, "Дараах ›", TAB_ON, () -> next.accept(s.p), "Дараагийн хуудас.");
        }
        text(s, 0, -2.85, Component.text("Хараагаар заагаад зүүн товш · W A S D: гүйлгэх · Дугуй: томруулах · Shift: хаах", GREY), 0.6f, 420);
    }

    private void button(Session s, double u, double v, double w, String label, int rgb, Runnable click, String hint) {
        ItemDisplay bg = rect(s, s.page, u, v, w, FOOT_H, rgb, 0.03);
        text(s, u, v, Component.text(label, NamedTextColor.WHITE, TextDecoration.BOLD), 0.75f, 160);
        s.hits.add(new Hit(u, v, w, FOOT_H, click, Component.text(label, GOLD, TextDecoration.BOLD), List.of(Component.text(hint, GREY)),
                bg, rgb, CARD_HOVER));
    }

    // ------------------------------------------------------------------------------------------------ input

    private void tick() {
        for (Session s : new ArrayList<>(sessions.values())) {
            if (!s.ready) continue;
            if (!s.p.isOnline() || !s.seat.isValid()) {
                close(s.p);
                continue;
            }
            // W / A / S / D pan the camera over the window, like the skill tree
            double step = 0.1 + 0.02 * s.dist;
            if (s.up) s.camV = Math.min(PAN_V, s.camV + step);
            if (s.down) s.camV = Math.max(-PAN_V, s.camV - step);
            if (s.left) s.camU = Math.max(-PAN_U, s.camU - step);
            if (s.right) s.camU = Math.min(PAN_U, s.camU + step);
            if (s.up || s.down || s.left || s.right) s.moved = true;
            if (s.moved && Bukkit.getCurrentTick() % 2 == 0) moveCamera(s);
            hover(s);
        }
    }

    private void hover(Session s) {
        Location eye = s.p.getEyeLocation();
        Vector dir = eye.getDirection();
        int best = -1;
        if (dir.getZ() > 0.05) {
            double t = (s.plane.getZ() - eye.getZ()) / dir.getZ();
            double u = s.plane.getX() - (eye.getX() + dir.getX() * t), v = eye.getY() + dir.getY() * t - s.plane.getY();
            for (int i = 0; i < s.hits.size(); i++) if (s.hits.get(i).contains(u, v)) best = i;
        }
        if (best == s.hover) return;
        if (s.hover >= 0 && s.hover < s.hits.size()) {
            Hit old = s.hits.get(s.hover);
            if (old.bg().isValid()) old.bg().setItemStack(quad("line", old.rgb()));
        }
        s.hover = best;
        if (best < 0) {
            s.tooltip.text(Component.empty());
            s.tooltip.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            return;
        }
        Hit h = s.hits.get(best);
        if (h.bg().isValid()) h.bg().setItemStack(quad("line", h.hover()));
        Component tip = h.name();
        for (Component l : h.lore()) tip = tip.append(Component.newline()).append(l.colorIfAbsent(GREY));
        s.tooltip.text(tip);
        s.tooltip.setBackgroundColor(Color.fromARGB(235, 10, 14, 30));
        int lines = 1 + h.lore().size();
        // under the hovered thing, or above it in the lower half, so it never runs off the window
        double tv = h.v() > -0.3 ? h.v() - h.h() / 2 - 0.12 : h.v() + h.h() / 2 + 0.12 + 0.155 * lines;
        double tu = Math.max(-3.4, Math.min(3.4, h.u()));
        s.tooltip.teleport(at(s, tu, tv - 0.155 * lines, 0.4));
        if (h.click() != null) s.p.playSound(s.p.getLocation(), Sound.UI_BUTTON_CLICK, 0.2f, 1.9f);
    }

    private void click(Session s) {
        long now = System.currentTimeMillis();
        if (s.hover < 0 || s.hover >= s.hits.size() || now - s.lastClick < 200) return;
        Hit h = s.hits.get(s.hover);
        if (h.click() == null) return;
        s.lastClick = now;
        s.p.playSound(s.p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        Location before = s.p.getLocation();
        s.capturing = true;
        s.captured = null;
        try {
            h.click().run();
        } finally {
            s.capturing = false;
        }
        if (!sessions.containsKey(s.p.getUniqueId())) return; // the click closed the window (✕ Хаах)
        if (s.captured != null) {
            buildPage(s, s.captured, s.capturedTabs);
            return;
        }
        // the click did something else: let it happen where the player really stands
        afterOther(s, before);
    }

    /** A click opened a chest window, a dialog or moved the player: leave the sky without undoing it. */
    private void afterOther(Session s, Location before) {
        Player p = s.p;
        Location now = p.getLocation();
        boolean moved = now.getWorld() != before.getWorld() || now.distanceSquared(before) > 16;
        Inventory top = p.getOpenInventory().getTopInventory();
        boolean window = top.getType() != org.bukkit.event.inventory.InventoryType.CRAFTING
                && top.getType() != org.bukkit.event.inventory.InventoryType.CREATIVE;
        if (!moved && !window) {
            // a dialog or a chat answer: the sky stays out of the way of neither, close it underneath
            close(p, true);
            return;
        }
        close(p, !moved);
        if (window && !moved) Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) p.openInventory(top);
        });
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
        if (sessions.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    /** The wheel zooms: the camera moves toward the panel or away from it. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onWheel(PlayerItemHeldEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s == null || !s.ready) return;
        e.setCancelled(true);
        int d = e.getNewSlot() - e.getPreviousSlot();
        if (d > 4) d -= 9;
        if (d < -4) d += 9;
        double to = Math.max(DIST_MIN, Math.min(DIST_MAX, s.dist + d * ZOOM_STEP));
        if (to == s.dist) return;
        s.dist = to;
        moveCamera(s);
    }

    /** W / A / S / D pan (held keys; the tick moves the camera). */
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
        String cmd = e.getMessage().toLowerCase(java.util.Locale.ROOT);
        // a page command typed in the sky (/menu, /shop ...) turns the page; anything else runs from where they stood
        if (cmd.startsWith("/menu") || cmd.startsWith("/shop") || cmd.startsWith("/profile") || cmd.startsWith("/rankup")
                || cmd.startsWith("/travel") || cmd.startsWith("/settings") || cmd.equals("/help")) return;
        close(p);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && sessions.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && sessions.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent e) {
        long now = System.currentTimeMillis();
        if (e.getEntity() instanceof Player p) lastCombat.put(p.getUniqueId(), now);
        if (e.getDamager() instanceof Player p) lastCombat.put(p.getUniqueId(), now);
        if (e.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player p) lastCombat.put(p.getUniqueId(), now);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        close(e.getPlayer());
        lastCombat.remove(e.getPlayer().getUniqueId());
    }

    /** A crash or restart while the window was open: the player logs back in where they stood, not on the stage. */
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
            for (Session s : sessions.values()) if (s.page.contains(en) || s.stage.contains(en)) live = true;
            if (!live) en.remove();
        }
    }

    /** Newlines in a component tree (for centring a TextDisplay on its middle line). */
    static final class PlainLines {
        static long count(Component c) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
            return plain.chars().filter(ch -> ch == '\n').count();
        }
    }
}
