package mn.suld.plugin.gui;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import mn.suld.api.skill.tree.SkillAllocation;
import mn.suld.api.skill.tree.SkillAllocation.NodeState;
import mn.suld.api.skill.tree.SkillEngine;
import mn.suld.api.skill.tree.SkillNode;
import mn.suld.api.skill.tree.SkillTree;
import mn.suld.api.skill.tree.SkyLayout;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.skill.SkillText;
import mn.suld.plugin.skill.SkillTreeService;
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
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
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
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The full-screen skill tree, "Тэнгэрийн мод" (docs/SKILL_SKY.md). A server cannot open a new client screen, so the
 * tree is built in the world, in front of a locked camera, out of display entities only the owner can see:
 * <ul>
 *   <li>the player is lifted to a private stage high above their own position and seated on an invisible display, so
 *       the camera stays put; a box of panels with the night-sky backdrop rides with the seat and fills the view;</li>
 *   <li>every node is a slot frame tinted by its state with the node's icon on it, the edges are tinted quads (gold
 *       for a learned path, grey for reachable, dark for locked, red for an exclusive pair), laid out radially by
 *       {@link SkyLayout};</li>
 *   <li>the crosshair is the cursor: the node under it glows and shows its tooltip; left click learns or ranks up,
 *       right click refunds; W/A/S/D pans, the hotbar wheel zooms, Shift (dismount) closes.</li>
 * </ul>
 * Opening is refused in combat, in a dungeon run, as a soul or mid-air, and the chest map is used instead. Closing
 * (and quitting, a command, or a restart via the join hook) always returns the player to the exact spot they left.
 */
public final class SkillSky implements Listener {

    static final String TAG = "suld_skysky";
    private static final NamespacedKey RETURN = new NamespacedKey("suld", "sky_return");

    /** Blocks per layout unit, node frame size, icon size. */
    static final double SPACING = 1.0;
    static final float NODE = 0.62f;
    static final float ICON = 0.36f;
    static final float LINE = 0.055f;
    /** Camera distance to the tree plane: start, nearest, farthest (the wheel zooms between them). */
    static final double DIST0 = 6.0, DIST_MIN = 3.0, DIST_MAX = 26;
    /** Half-size of the backdrop box around the camera (blocks). */
    static final float BOX_W = 70, BOX_H = 40, BOX_D = 30;
    static final long COMBAT_MS = 8000;
    /** Tooltip: distance from the eye, text scale, line width (px); vanilla text is 0.025 blocks per pixel. */
    static final double TIP_DIST = 2.2;
    static final float TIP_SCALE = 0.36f;
    static final int TIP_WIDTH = 190;

    private static final int GOLD = 0xF2B632, WHITE = 0xF0F0F0, DIM = 0x8C8F96, DARK = 0x3C3F46, RED = 0xB03030, NAVY = 0x0B1226;
    private static final int LINE_GOLD = 0xE8A82A, LINE_OPEN = 0x9A9DA6, LINE_DARK = 0x2A2D34, LINE_RED = 0xC83030;

    private final Plugin plugin;
    private final SuldServices services;
    private final SkillMapMenu map;
    private final Predicate<UUID> isSoul;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> lastCombat = new HashMap<>();

    public SkillSky(Plugin plugin, SuldServices services, SkillMapMenu map, Predicate<UUID> isSoul) {
        this.plugin = plugin;
        this.services = services;
        this.map = map;
        this.isSoul = isSoul;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public boolean isOpen(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    private SkillTreeService st() {
        return services.skillTree();
    }

    // ------------------------------------------------------------------------------------------------ session

    private final class Session {
        final Player p;
        final Location origin;
        final boolean invulnerable;
        final SkillTree tree;
        final SkyLayout layout;
        final List<Entity> owned = new ArrayList<>();
        ItemDisplay seat;
        Location plane; // centre of the tree plane (the root)
        double eyeOff;
        double camU, camV, dist = DIST0;
        ItemDisplay[] frames, icons;
        Interaction[] hits;
        final Map<Long, ItemDisplay> lines = new HashMap<>();
        final Map<Long, Integer> lineColor = new HashMap<>();
        int[] frameColor;
        ItemDisplay glow;
        TextDisplay tooltip, header, footer;
        int hover = -1;
        boolean up, down, left, right;
        boolean moved = true, closing, ready;
        long lastClick;
        int lastSlot;
        int tipLines;
        Location tipAt;

        Session(Player p, SkillTree tree) {
            this.p = p;
            this.origin = p.getLocation().clone();
            this.invulnerable = p.isInvulnerable();
            this.tree = tree;
            this.layout = SkyLayout.of(tree);
            this.lastSlot = p.getInventory().getHeldItemSlot();
        }
    }

    /** Open the tree; false (with a reason sent) when it cannot open here and now. */
    public boolean open(Player p) {
        if (sessions.containsKey(p.getUniqueId())) return true;
        SkillTree tree = st() == null ? null : st().tree(p);
        String why = tree == null ? "Эхлээд ангиа сонго."
                : isSoul.test(p.getUniqueId()) ? "Сүнс байхдаа чадварын модыг нээх боломжгүй."
                : services.dungeons().isInAnyRun(p.getUniqueId()) ? "Агуйн дотор чадварын модыг нээх боломжгүй."
                : System.currentTimeMillis() - lastCombat.getOrDefault(p.getUniqueId(), 0L) < COMBAT_MS ? "Тулалдааны үеэр нээх боломжгүй."
                : p.isInsideVehicle() || p.isGliding() || !p.isOnGround() && !p.isFlying() ? "Газар зогсож байхдаа нээ."
                : null;
        if (why != null) {
            p.sendMessage(net.kyori.adventure.text.Component.text("ᠰ " + why, NamedTextColor.RED));
            return false;
        }
        Session s = new Session(p, tree);
        sessions.put(p.getUniqueId(), s);
        Location o = s.origin;
        p.getPersistentDataContainer().set(RETURN, PersistentDataType.STRING, o.getWorld().getName() + ";" + o.getX() + ";" + o.getY() + ";" + o.getZ()
                + ";" + o.getYaw() + ";" + o.getPitch());
        World w = o.getWorld();
        Location stage = new Location(w, Math.floor(o.getX()) + 0.5, w.getMaxHeight() + 48, Math.floor(o.getZ()) + 0.5, 0f, 0f);
        p.setInvulnerable(true);
        p.setFallDistance(0);
        p.teleport(stage);
        s.seat = w.spawn(stage, ItemDisplay.class, d -> {
            hidden(d);
            d.setTeleportDuration(3);
        });
        s.owned.add(s.seat);
        p.showEntity(plugin, s.seat);
        s.seat.addPassenger(p);
        // the eye height of a seated player is only known once seated: build the scene a tick later
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (sessions.get(p.getUniqueId()) != s || !p.isOnline()) return;
            build(s);
            s.ready = true;
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        }, 2L);
        return true;
    }

    public void close(Player p) {
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) return;
        s.closing = true;
        for (Entity e : s.owned) if (e.isValid()) e.remove();
        if (p.isInsideVehicle()) p.leaveVehicle();
        p.teleport(s.origin);
        p.setFallDistance(0);
        p.setInvulnerable(s.invulnerable);
        p.getPersistentDataContainer().remove(RETURN);
    }

    public void shutdown() {
        for (Session s : new ArrayList<>(sessions.values())) close(s.p);
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

    private <T extends Entity> T own(Session s, T e) {
        s.owned.add(e);
        s.p.showEntity(plugin, e);
        return e;
    }

    private static ItemStack quad(String model, int rgb) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("suld", "entity/skytree/" + model));
        it.setItemMeta(meta);
        if (rgb >= 0) it.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addColor(Color.fromRGB(rgb)).build());
        return it;
    }

    /** World position of a layout point on the tree plane (the viewer faces +Z, so screen-right is world -X). */
    private Location at(Session s, double u, double v, double towardViewer) {
        return s.plane.clone().add(-u * SPACING, v * SPACING, -towardViewer);
    }

    private void build(Session s) {
        Player p = s.p;
        World w = p.getWorld();
        s.eyeOff = p.getEyeLocation().getY() - s.seat.getLocation().getY();
        // the root sits low on the screen: the branches grow up into the view
        s.camV = 1.9;
        s.plane = s.seat.getLocation().clone().add(0, s.eyeOff, DIST0);
        s.plane.setY(s.plane.getY() - s.camV * SPACING);

        // backdrop box riding with the seat: the art in front, dark navy everywhere else
        backdrop(s, "bg", -1, new Vector3f(0, (float) s.eyeOff, BOX_D), new Quaternionf(), new Vector3f(BOX_W * 2, BOX_H * 2, 1));
        float hx = BOX_W, hy = BOX_H, hz = BOX_D;
        Quaternionf ry = new Quaternionf().rotateY((float) (Math.PI / 2)), rx = new Quaternionf().rotateX((float) (Math.PI / 2));
        backdrop(s, "line", NAVY, new Vector3f(hx, (float) s.eyeOff, 0), ry, new Vector3f(hz * 2, hy * 2, 1));
        backdrop(s, "line", NAVY, new Vector3f(-hx, (float) s.eyeOff, 0), ry, new Vector3f(hz * 2, hy * 2, 1));
        backdrop(s, "line", NAVY, new Vector3f(0, (float) s.eyeOff + hy, 0), rx, new Vector3f(hx * 2, hz * 2, 1));
        backdrop(s, "line", 0x070B18, new Vector3f(0, (float) s.eyeOff - hy, 0), rx, new Vector3f(hx * 2, hz * 2, 1));
        backdrop(s, "line", NAVY, new Vector3f(0, (float) s.eyeOff, -hz), new Quaternionf(), new Vector3f(hx * 2, hy * 2, 1));

        // header and footer: standalone billboards that moveCamera() keeps in front of the eye. NOT seat passengers:
        // a billboarded display applies its translation in the camera-facing frame, where +Z points back at the camera,
        // so a passenger at "z = 2.4" was drawn behind the player's eyes and never seen
        s.header = own(s, w.spawn(s.seat.getLocation(), TextDisplay.class, t -> {
            hidden(t);
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setShadowed(true);
            t.setLineWidth(400);
            t.setTeleportDuration(3);
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.42f), new Quaternionf()));
        }));
        s.footer = own(s, w.spawn(s.seat.getLocation(), TextDisplay.class, t -> {
            hidden(t);
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(150, 8, 6, 20));
            t.setLineWidth(320);
            t.setTeleportDuration(3);
            t.setShadowed(true);
            t.text(Component.text("Зүүн товш: нээх   ·   Баруун товш: буцаах", NamedTextColor.WHITE).append(Component.newline())
                    .append(Component.text("W A S D: гүйлгэх   ·   Дугуй: томруулах   ·   Shift: гарах", NamedTextColor.WHITE)));
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.3f), new Quaternionf()));
        }));

        int n = s.tree.nodes().size();
        s.frames = new ItemDisplay[n];
        s.icons = new ItemDisplay[n];
        s.hits = new Interaction[n];
        s.frameColor = new int[n];
        java.util.Arrays.fill(s.frameColor, -2);

        // edges first (drawn behind the frames)
        for (SkillNode a : s.tree.nodes()) {
            for (int b : s.tree.neighbours(a.index())) if (b > a.index()) line(s, a.index(), b);
            for (int b : s.tree.exclusives(a.index())) {
                // a red line only between near nodes; far rivals are named in the tooltip (long red lines cluttered the sky)
                if (b > a.index() && !s.tree.linked(a.index(), b) && s.layout.of(a).distance(s.layout.of(s.tree.node(b))) < 1.8) line(s, a.index(), b);
            }
        }
        for (SkillNode node : s.tree.nodes()) {
            SkyLayout.Pos pos = s.layout.of(node);
            int i = node.index();
            float size = node.root() || node.keystone() || node.capstone() ? NODE * 1.2f : node.satellite() ? NODE * 0.72f : NODE;
            s.frames[i] = own(s, w.spawn(at(s, pos.u(), pos.v(), 0), ItemDisplay.class, d -> {
                hidden(d);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(size, size, 1), new Quaternionf()));
            }));
            s.icons[i] = own(s, w.spawn(at(s, pos.u(), pos.v(), 0.04), ItemDisplay.class, d -> {
                hidden(d);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(size / NODE * ICON), new Quaternionf()));
            }));
            // a click target exactly over the slot (left click = learn, right click = refund)
            s.hits[i] = own(s, w.spawn(at(s, pos.u(), pos.v() - size / 2 / SPACING, 0.05), Interaction.class, it -> {
                it.setPersistent(false);
                it.setVisibleByDefault(false);
                it.addScoreboardTag(TAG);
                it.setInteractionWidth(size);
                it.setInteractionHeight(size);
                it.setResponsive(true);
            }));
        }
        s.glow = own(s, w.spawn(s.plane, ItemDisplay.class, d -> {
            hidden(d);
            d.setItemStack(new ItemStack(Material.AIR));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(NODE * 1.7f, NODE * 1.7f, 1), new Quaternionf()));
        }));
        // the tooltip follows the cursor: a standalone billboard kept TIP_DIST blocks from the eye, just right of the
        // hovered node (followTooltip), so it reads the same at every zoom and is always where the player looks
        s.tooltip = own(s, w.spawn(s.seat.getLocation(), TextDisplay.class, t -> {
            hidden(t);
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setLineWidth(TIP_WIDTH);
            t.setAlignment(TextDisplay.TextAlignment.LEFT);
            t.setShadowed(true);
            t.setTeleportDuration(1);
            t.text(Component.empty());
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(TIP_SCALE), new Quaternionf()));
        }));
        refresh(s);
        moveCamera(s);
    }

    private void backdrop(Session s, String model, int rgb, Vector3f at, Quaternionf rot, Vector3f scale) {
        ItemDisplay d = own(s, s.p.getWorld().spawn(s.seat.getLocation(), ItemDisplay.class, e -> {
            hidden(e);
            e.setItemStack(quad(model, rgb));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setViewRange(4f);
            e.setTransformation(new Transformation(at, rot, scale, new Quaternionf()));
        }));
        s.seat.addPassenger(d);
    }

    private static long key(int a, int b) {
        return ((long) Math.min(a, b) << 32) | Math.max(a, b);
    }

    private void line(Session s, int a, int b) {
        SkyLayout.Pos pa = s.layout.of(s.tree.node(a)), pb = s.layout.of(s.tree.node(b));
        double du = (pb.u() - pa.u()) * SPACING, dv = (pb.v() - pa.v()) * SPACING;
        double len = Math.hypot(du, dv) - NODE * 0.75;
        if (len <= 0.02) return;
        // the quad's local X goes along the edge: world dx = -du (screen-right is -X)
        float angle = (float) Math.atan2(dv, -du);
        Location mid = at(s, (pa.u() + pb.u()) / 2, (pa.v() + pb.v()) / 2, -0.03);
        ItemDisplay d = own(s, s.p.getWorld().spawn(mid, ItemDisplay.class, e -> {
            hidden(e);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f(angle, 0, 0, 1)),
                    new Vector3f((float) len, LINE, 1), new Quaternionf()));
        }));
        s.lines.put(key(a, b), d);
    }

    // ------------------------------------------------------------------------------------------------ state → visuals

    private static int frameRgb(NodeState st) {
        return switch (st) {
            case MAXED, UNLOCKED -> GOLD;
            case AVAILABLE -> WHITE;
            case NEEDS_POINTS, LEVEL_LOCKED, PREREQUISITE_MISSING -> DIM;
            case EXCLUDED -> RED;
            case LOCKED -> DARK;
        };
    }

    /** Re-tint everything after the allocation changed (only what changed is re-sent). */
    private void refresh(Session s) {
        Player p = s.p;
        SkillAllocation a = st().allocation(p);
        if (a == null) return;
        SkillEngine.Context ctx = st().context(p);
        int avail = st().available(p);
        NodeState[] state = new NodeState[s.tree.nodes().size()];
        for (SkillNode n : s.tree.nodes()) {
            int i = n.index();
            state[i] = a.state(n, ctx.level(), avail);
            boolean visible = a.visible(n);
            int rgb = visible ? frameRgb(state[i]) : -1;
            if (rgb != s.frameColor[i]) {
                s.frameColor[i] = rgb;
                s.frames[i].setItemStack(visible ? quad("frame", rgb) : new ItemStack(Material.AIR));
                ItemStack icon;
                if (!visible) {
                    icon = new ItemStack(Material.AIR);
                } else {
                    Material m = n.secret() && !a.unlocked(n) ? Material.ENDER_EYE : Material.matchMaterial(n.icon());
                    icon = new ItemStack(m == null || !m.isItem() ? Material.PAPER : m);
                    ItemMeta meta = icon.getItemMeta();
                    if (meta != null) {
                        meta.setEnchantmentGlintOverride(state[i] == NodeState.UNLOCKED || state[i] == NodeState.MAXED);
                        icon.setItemMeta(meta);
                    }
                    if (a.rank(n) > 1) icon.setAmount(Math.min(64, a.rank(n)));
                }
                s.icons[i].setItemStack(icon);
            }
        }
        for (Map.Entry<Long, ItemDisplay> e : s.lines.entrySet()) {
            int x = (int) (e.getKey() >> 32), y = (int) (long) e.getKey();
            SkillNode nx = s.tree.node(x), ny = s.tree.node(y);
            int rgb;
            if (!a.visible(nx) || !a.visible(ny)) rgb = -1;
            else if (s.tree.exclusiveLink(x, y)) rgb = LINE_RED;
            else if (a.unlocked(nx) && a.unlocked(ny)) rgb = LINE_GOLD;
            else if (a.unlocked(nx) || a.unlocked(ny)) rgb = LINE_OPEN;
            else rgb = LINE_DARK;
            Integer old = s.lineColor.put(e.getKey(), rgb);
            if (old == null || old != rgb) e.getValue().setItemStack(rgb < 0 ? new ItemStack(Material.AIR) : quad("line", rgb));
        }
        long learned = s.tree.nodes().stream().filter(n -> !n.root() && a.unlocked(n)).count();
        s.header.text(Component.text("✦ ", TextColor.color(GOLD))
                .append(Component.text("Тэнгэрийн мод · " + s.tree.clazz().displayName(), TextColor.color(GOLD), TextDecoration.BOLD))
                .append(Component.text(" ✦", TextColor.color(GOLD)))
                .append(Component.newline())
                .append(Component.text("Чөлөөт оноо: ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(avail), avail > 0 ? NamedTextColor.GREEN : NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text("   Сурсан: ", NamedTextColor.GRAY)).append(Component.text(learned + "/" + (s.tree.nodes().size() - 1), NamedTextColor.WHITE))
                .append(Component.text("   Түвшин: ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(ctx.level()), NamedTextColor.WHITE)));
        if (s.hover >= 0) showTooltip(s, s.hover, true);
    }

    private void showTooltip(Session s, int i, boolean force) {
        Player p = s.p;
        SkillNode n = s.tree.node(i);
        SkillAllocation a = st().allocation(p);
        if (a == null) return;
        SkillEngine.Context ctx = st().context(p);
        NodeState state = a.state(n, ctx.level(), st().available(p));
        Component text;
        if (n.secret() && !a.unlocked(n)) {
            text = Component.text("???", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD).append(Component.newline())
                    .append(Component.text("Нууц чадвар — замыг нь нээж ол.", NamedTextColor.GRAY));
        } else {
            TextColor c = state == NodeState.UNLOCKED || state == NodeState.MAXED ? TextColor.color(GOLD) : NamedTextColor.WHITE;
            text = Component.text((n.keystone() ? "★ " : n.capstone() ? "✦ " : "") + n.name(), c, TextDecoration.BOLD);
            List<Component> lines = map.tooltip(p, s.tree, a, n, state, false);
            // the chest map's last line explains its own clicks; the sky shows its controls in the footer
            int keep = lines.size();
            if (!n.root() && keep > 0) keep--;
            for (int k = 0; k < keep; k++) text = text.append(Component.newline()).append(lines.get(k));
        }
        s.tooltip.text(text);
        s.tipLines = 1 + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(text).split("\n", -1).length - 1;
        s.tooltip.setBackgroundColor(Color.fromARGB(236, 20, 10, 38));
        s.tipAt = null;
        followTooltip(s);
        SkyLayout.Pos pos = s.layout.of(n);
        s.glow.teleport(at(s, pos.u(), pos.v(), -0.01));
        s.glow.setItemStack(quad("glow", state == NodeState.UNLOCKED || state == NodeState.MAXED ? GOLD : WHITE));
    }

    /**
     * Keep the tooltip next to the cursor: TIP_DIST blocks from the eye on the line to the hovered node, shifted right
     * by half its width plus a margin, top edge just above the node (a text display's origin is its bottom centre).
     */
    private void followTooltip(Session s) {
        if (s.hover < 0) return;
        SkyLayout.Pos pos = s.layout.of(s.tree.node(s.hover));
        Location eye = s.p.getEyeLocation();
        Vector d = at(s, pos.u(), pos.v(), 0).toVector().subtract(eye.toVector());
        if (d.lengthSquared() < 1e-6) return;
        d.normalize();
        Vector up = new Vector(0, 1, 0);
        Vector right = d.clone().crossProduct(up);
        if (right.lengthSquared() < 1e-6) right = new Vector(-1, 0, 0);
        right.normalize();
        double px = 0.025 * TIP_SCALE;
        double w = TIP_WIDTH * px, h = (s.tipLines * 10 + 2) * px;
        Location to = eye.clone().add(d.multiply(TIP_DIST)).add(right.multiply(w / 2 + 0.18)).subtract(0, h - 0.12, 0);
        if (s.tipAt != null && s.tipAt.distanceSquared(to) < 1e-4) return;
        s.tipAt = to;
        s.tooltip.teleport(to);
    }

    private void hideTooltip(Session s) {
        s.tooltip.text(Component.empty());
        s.tooltip.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        s.glow.setItemStack(new ItemStack(Material.AIR));
    }

    // ------------------------------------------------------------------------------------------------ input

    private void tick() {
        for (Session s : new ArrayList<>(sessions.values())) {
            if (!s.ready) continue;
            if (!s.p.isOnline() || !s.seat.isValid()) {
                close(s.p);
                continue;
            }
            double step = 0.12 + 0.012 * s.dist; // farther = faster pan
            double[] ext = s.layout.extent();
            if (s.up) s.camV = Math.min(ext[1] + 1, s.camV + step);
            if (s.down) s.camV = Math.max(-ext[1] - 1, s.camV - step);
            if (s.left) s.camU = Math.max(-ext[0] - 1, s.camU - step);
            if (s.right) s.camU = Math.min(ext[0] + 1, s.camU + step);
            if (s.up || s.down || s.left || s.right) s.moved = true;
            if (s.moved && Bukkit.getCurrentTick() % 2 == 0) moveCamera(s);
            hover(s);
            followTooltip(s);
        }
    }

    private void moveCamera(Session s) {
        s.moved = false;
        Location cam = at(s, s.camU, s.camV, s.dist);
        cam.subtract(0, s.eyeOff, 0);
        cam.setYaw(0);
        cam.setPitch(0);
        s.seat.teleport(cam, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
        // header and footer stay in front of the eye (the camera looks +Z: world offsets)
        Location eye = cam.clone().add(0, s.eyeOff, 0);
        s.header.teleport(eye.clone().add(0, 1.0, 2.4));
        s.footer.teleport(eye.clone().add(0, -1.3, 2.4));
    }

    /** The crosshair as a cursor: intersect the look ray with the tree plane and take the nearest node under it. */
    private void hover(Session s) {
        Location eye = s.p.getEyeLocation();
        Vector dir = eye.getDirection();
        int best = -1;
        if (dir.getZ() > 0.05) {
            double t = (s.plane.getZ() - eye.getZ()) / dir.getZ();
            double x = eye.getX() + dir.getX() * t, y = eye.getY() + dir.getY() * t;
            double u = (s.plane.getX() - x) / SPACING, v = (y - s.plane.getY()) / SPACING;
            SkillAllocation a = st().allocation(s.p);
            double bestD = NODE * 0.55 / SPACING;
            for (SkillNode n : s.tree.nodes()) {
                if (a != null && !a.visible(n)) continue;
                SkyLayout.Pos p = s.layout.of(n);
                double d = Math.hypot(p.u() - u, p.v() - v);
                if (d < bestD) {
                    bestD = d;
                    best = n.index();
                }
            }
        }
        if (best == s.hover) return;
        s.hover = best;
        if (best < 0) {
            hideTooltip(s);
        } else {
            showTooltip(s, best, false);
            s.p.playSound(s.p.getLocation(), Sound.UI_BUTTON_CLICK, 0.25f, 1.8f);
        }
    }

    private void click(Session s, boolean refund) {
        long now = System.currentTimeMillis();
        if (s.hover < 0 || now - s.lastClick < 180) return;
        s.lastClick = now;
        SkillNode n = s.tree.node(s.hover);
        if (n.root()) return;
        var c = refund ? st().refund(s.p, n) : st().unlock(s.p, n);
        if (c.ok()) {
            s.p.playSound(s.p.getLocation(), refund ? Sound.BLOCK_AMETHYST_BLOCK_BREAK : Sound.ENTITY_PLAYER_LEVELUP, 0.8f, refund ? 0.8f : 1.6f);
            services.hud().toast(s.p, Component.text(refund ? "↩ " + n.name() + " буцаагдлаа" : "✔ " + n.name() + " нээгдлээ",
                    refund ? NamedTextColor.YELLOW : NamedTextColor.GREEN, TextDecoration.BOLD));
        } else {
            s.p.playSound(s.p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
            services.hud().toast(s.p, Component.text(SkillText.why(c), NamedTextColor.RED, TextDecoration.BOLD));
        }
        refresh(s);
    }

    @EventHandler
    public void onInput(PlayerInputEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s == null) return;
        var in = e.getInput();
        s.up = in.isForward();
        s.down = in.isBackward();
        s.left = in.isLeft();
        s.right = in.isRight();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwing(PlayerAnimationEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s == null || e.getAnimationType() != PlayerAnimationType.ARM_SWING || !s.ready) return;
        click(s, false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRightClick(PlayerInteractEntityEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s == null) return;
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !s.ready) return;
        if (e.getRightClicked() instanceof Interaction hit) {
            for (int i = 0; i < s.hits.length; i++) if (s.hits[i] == hit) s.hover = i;
        }
        click(s, true);
    }

    /** Left-clicking an Interaction is an "attack": never let it reach combat code. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHitInteraction(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && sessions.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onWheel(PlayerItemHeldEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s == null) return;
        int d = e.getNewSlot() - e.getPreviousSlot();
        if (d > 4) d -= 9;
        if (d < -4) d += 9;
        s.dist = Math.max(DIST_MIN, Math.min(DIST_MAX, s.dist + d * 1.1));
        s.moved = true;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.closing) return;
        // Shift: the vanilla dismount closes the tree (and returns the player where they stood)
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> close(p));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        // any command (a teleport, /spawn...) runs from where the player really is
        if (sessions.containsKey(e.getPlayer().getUniqueId())) close(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && sessions.containsKey(p.getUniqueId())) e.setCancelled(true);
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

    /** A crash or restart while the tree was open: the player logs back in where they stood, not on the stage. */
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
            for (Session s : sessions.values()) if (s.owned.contains(en)) live = true;
            if (!live) en.remove();
        }
    }
}
