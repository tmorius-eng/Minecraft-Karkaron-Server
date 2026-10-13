package mn.suld.plugin.training;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.plugin.SuldServices;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Warden;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The training grounds' place and things (docs/TRAINING_GROUNDS.md): «Дайчдын Дасгалын Талбай», a 40×40 yard in
 * Хархорум (plot A, west of the spawn). Everything here is an entity the plugin owns; the city is built by hand, so blocks
 * come only from staff ({@code /dasgal yard build}). In the middle stands the training Warden: no AI, so it never moves,
 * digs, roars or darkens anyone; health that is topped up after every hit, so it never dies; not saved with the world,
 * held in place by a chunk ticket, and put back by a watchdog if anything removes it. Around it: five small Wardens close
 * together (area spells), one that walks a circle (aim and spells on the move), five class stations on the north side.
 */
final class TrainingYard {

    /** Every entity of the yard. */
    static final String TAG = "suld_training";
    /** The Wardens (the training dummies). */
    static final String DUMMY = "suld_training_dummy";
    /**
     * The SÜLD mob id of the dummies: the combat code treats them as SÜLD mobs (hit formula, spells, lock-on, numbers),
     * but it is not content, so they have no loot, no EXP, no level gap and no kill of them counts.
     */
    static final String MOB_ID = "mob.dasgalyn_warden";
    /** Health in design units (MobService: real 1024, every hit divided by the rest): no hit can kill it. */
    static final double DESIGN_HEALTH = 10_000_000;
    static final int RADIUS = 20;

    enum Role { CENTRE, GROUP, MOVING }

    static final Map<PlayerClass, TextColor> COLOUR = new EnumMap<>(Map.of(
            PlayerClass.BAATAR, TextColor.fromHexString("#FF5A4A"),
            PlayerClass.MERGEN, TextColor.fromHexString("#6BE36B"),
            PlayerClass.BOO, TextColor.fromHexString("#C77DFF"),
            PlayerClass.DARKHAN, TextColor.fromHexString("#FF9A3C"),
            PlayerClass.KHULEGCHIN, TextColor.fromHexString("#5FB4FF")));

    static final Map<PlayerClass, String> ROLE = new EnumMap<>(Map.of(
            PlayerClass.BAATAR, "Хамгаалагч",
            PlayerClass.MERGEN, "Холын харваач",
            PlayerClass.BOO, "Сүнсний ид шид",
            PlayerClass.DARKHAN, "Галын дархан",
            PlayerClass.KHULEGCHIN, "Довтлогч морьтон"));

    private static final TextColor CYAN = TextColor.fromHexString("#5FE3FF"), GOLD = TextColor.fromHexString("#FFD24A");

    private final Plugin plugin;
    private final SuldServices services;
    private final java.util.function.Function<PlayerClass, ItemStack> weaponIcon;
    private Location centre;
    private final Map<String, UUID> dummies = new HashMap<>();
    private final Map<UUID, Role> roles = new HashMap<>();
    private final List<UUID> props = new ArrayList<>();
    private final Map<UUID, PlayerClass> stations = new HashMap<>();
    private final List<UUID> spinners = new ArrayList<>();
    private final List<int[]> tickets = new ArrayList<>();
    private BukkitTask watchdog, mover;
    private double angle;
    private long ticks;

    TrainingYard(Plugin plugin, SuldServices services, java.util.function.Function<PlayerClass, ItemStack> weaponIcon) {
        this.plugin = plugin;
        this.services = services;
        this.weaponIcon = weaponIcon;
    }

    /** True for one of the yard's Wardens. */
    static boolean dummy(Entity e) {
        return e != null && e.getScoreboardTags().contains(DUMMY);
    }

    Location centre() {
        return centre.clone();
    }

    /** The centre, or null before the yard is set up. */
    Location centreOrNull() {
        return centre == null ? null : centre.clone();
    }

    /** Inside the yard (with {@code margin} blocks more on every side), on the yard's world. */
    boolean inside(Location l, double margin) {
        if (centre == null || l == null || !l.getWorld().equals(centre.getWorld())) return false;
        double r = RADIUS + margin;
        return Math.abs(l.getX() - centre.getX()) <= r && Math.abs(l.getZ() - centre.getZ()) <= r && Math.abs(l.getY() - centre.getY()) <= 24;
    }

    /** Where a trainee arrives: the south gate, looking north at the Warden. */
    Location entrance() {
        Location l = ground(centre.getX(), centre.getZ() + RADIUS - 4);
        l.setYaw(180f);
        l.setPitch(0f);
        return l;
    }

    /** The station a click on {@code e} belongs to, or null. */
    PlayerClass stationOf(Entity e) {
        return e == null ? null : stations.get(e.getUniqueId());
    }

    Role role(Entity e) {
        return e == null ? null : roles.get(e.getUniqueId());
    }

    /** The highest block that stands at x/z, plus one (the pedestal if staff built one, else the grass). */
    private Location ground(double x, double z) {
        World w = centre.getWorld();
        int y = w.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z), HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        if (Math.abs(y - centre.getY()) > 12) y = centre.getBlockY(); // a roof or a hole: the configured height
        return new Location(w, x, y, z);
    }

    // ------------------------------------------------------------------------------------------------ lifecycle

    void start(Location at) {
        stop();
        this.centre = at.clone();
        World w = centre.getWorld();
        int cx = centre.getBlockX() >> 4, cz = centre.getBlockZ() >> 4, span = (RADIUS >> 4) + 1;
        for (int x = cx - span; x <= cx + span; x++) {
            for (int z = cz - span; z <= cz + span; z++) {
                w.getChunkAt(x, z);
                if (w.addPluginChunkTicket(x, z, plugin)) tickets.add(new int[]{x, z});
            }
        }
        // anything a crash left behind (the entities are not saved, but a chunk saved mid-tick may carry one)
        for (Entity e : w.getNearbyEntities(centre, RADIUS + 8, 24, RADIUS + 8)) if (e.getScoreboardTags().contains(TAG)) e.remove();
        spawnAll();
        watchdog = Bukkit.getScheduler().runTaskTimer(plugin, this::respawnMissing, 100L, 100L);
        mover = Bukkit.getScheduler().runTaskTimer(plugin, this::move, 1L, 1L);
    }

    void stop() {
        if (watchdog != null) watchdog.cancel();
        if (mover != null) mover.cancel();
        watchdog = mover = null;
        for (UUID id : dummies.values()) remove(id);
        for (UUID id : props) remove(id);
        dummies.clear();
        roles.clear();
        props.clear();
        stations.clear();
        spinners.clear();
        if (centre != null) for (int[] t : tickets) centre.getWorld().removePluginChunkTicket(t[0], t[1], plugin);
        tickets.clear();
    }

    private static void remove(UUID id) {
        Entity e = Bukkit.getEntity(id);
        if (e != null) e.remove();
    }

    private void spawnAll() {
        // the centre Warden on its pedestal, facing the gate; a giant: ×1.6
        put("centre", Role.CENTRE, ground(centre.getX() + 0.5, centre.getZ() + 0.5), 0f, 1.6, true);
        // the group: five small Wardens close together, west of the centre
        for (int i = 0; i < 5; i++) {
            double a = Math.PI * 2 * i / 5;
            put("group" + i, Role.GROUP, ground(centre.getX() - 10.5 + Math.cos(a) * 1.7, centre.getZ() + 3.5 + Math.sin(a) * 1.7), -90f, 0.6, false);
        }
        Location walk = moverAt(angle);
        put("moving", Role.MOVING, walk, walk.getYaw(), 0.85, false);
        label(ground(centre.getX() - 10.5, centre.getZ() + 3.5).add(0, 2.4, 0), Component.text("Бүлэг бай", CYAN, TextDecoration.BOLD),
                Component.text("олон дайсныг нэг дор: AoE ид шид", NamedTextColor.WHITE));
        label(ground(centre.getX() + 11.5, centre.getZ() + 3.5).add(0, 3.2, 0), Component.text("Хөдөлгөөнт бай", CYAN, TextDecoration.BOLD),
                Component.text("хөдөлж буй дайсанд онилох", NamedTextColor.WHITE));
        // the gate board
        label(ground(centre.getX() + 0.5, centre.getZ() + RADIUS - 1.5).add(0, 2.6, 0),
                Component.text("ДАЙЧДЫН ДАСГАЛЫН ТАЛБАЙ", GOLD, TextDecoration.BOLD),
                Component.text("Анги сонго → 60-р түвшин, дээд хуяг, зэвсэг", NamedTextColor.WHITE),
                Component.text("Warden-д дайрч хохирол, DPS, идэвхгүй чадвараа хар", NamedTextColor.WHITE),
                Component.text("Гарахад бүх зүйл буцна · /dasgal", NamedTextColor.GRAY));
        // Мэргэн's range marks south of the Warden
        for (int m : new int[]{10, 15}) {
            label(ground(centre.getX() + 0.5, centre.getZ() + 0.5 + m).add(0, 0.25, 0), Component.text(m + " м", NamedTextColor.YELLOW, TextDecoration.BOLD));
        }
        // the class stations along the north side
        PlayerClass[] classes = PlayerClass.values();
        for (int i = 0; i < classes.length; i++) {
            PlayerClass c = classes[i];
            Location at = ground(centre.getX() + 0.5 + (i - 2) * 6, centre.getZ() + 0.5 - (RADIUS - 5));
            Interaction hit = at.getWorld().spawn(at, Interaction.class, it -> {
                it.setInteractionWidth(1.6f);
                it.setInteractionHeight(2.6f);
                it.setResponsive(true);
                own(it);
            });
            stations.put(hit.getUniqueId(), c);
            props.add(hit.getUniqueId());
            label(at.clone().add(0, 3.0, 0), Component.text(c.displayName().toUpperCase(java.util.Locale.ROOT), COLOUR.get(c), TextDecoration.BOLD),
                    Component.text(ROLE.get(c), NamedTextColor.WHITE), Component.text("дарж турш", NamedTextColor.GRAY));
            ItemStack icon = weaponIcon.apply(c);
            if (icon != null) {
                ItemDisplay d = at.getWorld().spawn(at.clone().add(0, 1.5, 0), ItemDisplay.class, it -> {
                    it.setItemStack(icon);
                    it.setBillboard(Display.Billboard.FIXED);
                    it.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.1f, 1.1f, 1.1f), new AxisAngle4f()));
                    own(it);
                });
                props.add(d.getUniqueId());
                spinners.add(d.getUniqueId());
            }
        }
    }

    private void own(Entity e) {
        e.setPersistent(false);
        e.addScoreboardTag(TAG);
    }

    private void label(Location at, Component... lines) {
        Component text = Component.empty();
        for (int i = 0; i < lines.length; i++) text = text.append(i == 0 ? lines[i] : Component.newline().append(lines[i]));
        Component t = text;
        TextDisplay d = at.getWorld().spawn(at, TextDisplay.class, it -> {
            it.text(t);
            it.setBillboard(Display.Billboard.CENTER);
            it.setShadowed(true);
            it.setAlignment(TextDisplay.TextAlignment.CENTER);
            it.setBackgroundColor(Color.fromARGB(110, 10, 14, 24));
            own(it);
        });
        props.add(d.getUniqueId());
    }

    private void put(String key, Role role, Location at, float yaw, double scale, boolean named) {
        at.setYaw(yaw);
        Warden w = at.getWorld().spawn(at, Warden.class, e -> {
            e.setAI(false);
            e.setSilent(true);
            e.setPersistent(false);
            e.setRemoveWhenFarAway(false);
            // collidable stays on: a non-collidable entity is not pickable, and arrows would fly through it
            e.addScoreboardTag(TAG);
            e.addScoreboardTag(DUMMY);
            services.mobs().tagAs(e, MOB_ID);
            e.customName(Component.text(role == Role.CENTRE ? "Дасгалын Warden" : role == Role.GROUP ? "Бүлгийн бай" : "Хөдөлгөөнт бай", CYAN, TextDecoration.BOLD)
                    .append(Component.text(" ∞", GOLD)));
            e.setCustomNameVisible(named);
            var sc = e.getAttribute(Attribute.SCALE);
            if (sc != null) sc.setBaseValue(scale);
            var kb = e.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (kb != null) kb.setBaseValue(1);
        });
        services.mobs().resize(w, DESIGN_HEALTH);
        w.setHealth(mn.suld.plugin.mob.MobService.maxHealth(w));
        dummies.put(key, w.getUniqueId());
        roles.put(w.getUniqueId(), role);
    }

    /** Full health again (after every hit; a hit can never take the real 1024 down: it is divided by 9 766). */
    static void topUp(LivingEntity d) {
        double max = mn.suld.plugin.mob.MobService.maxHealth(d);
        if (d.isValid() && !d.isDead() && d.getHealth() < max) d.setHealth(max);
    }

    /** Anything missing comes back: the watchdog, every five seconds. */
    private void respawnMissing() {
        if (centre == null || !centre.getWorld().isChunkLoaded(centre.getBlockX() >> 4, centre.getBlockZ() >> 4)) return;
        boolean missing = false;
        for (UUID id : dummies.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e == null || !e.isValid() || e.isDead()) missing = true;
            else if (e instanceof LivingEntity le) {
                topUp(le);
                if (le.getFireTicks() > 200) le.setFireTicks(200);
            }
        }
        for (UUID id : props) {
            Entity e = Bukkit.getEntity(id);
            if (e == null || !e.isValid()) missing = true;
        }
        if (missing) {
            plugin.getLogger().info("[training] the yard's entities were missing: put back");
            Location at = centre;
            stop();
            start(at);
        }
    }

    /** A point of the walking Warden's circle (radius 3.5, east of the centre), facing along it. */
    private Location moverAt(double a) {
        Location l = ground(centre.getX() + 11.5 + Math.cos(a) * 3.5, centre.getZ() + 3.5 + Math.sin(a) * 3.5);
        // moving along (−sin a, cos a): yaw = atan2(−dx, dz) = a
        l.setYaw((float) Math.toDegrees(a % (Math.PI * 2)));
        return l;
    }

    /** The walking Warden (about 2.2 blocks a second), and the station weapons turning slowly. */
    private void move() {
        ticks++;
        UUID id = dummies.get("moving");
        Entity e = id == null ? null : Bukkit.getEntity(id);
        if (e != null && e.isValid()) {
            angle += 2.2 / 3.5 / 20.0;
            e.teleport(moverAt(angle));
        }
        if (ticks % 40 == 0) {
            float rot = (float) ((ticks / 40 % 4) * Math.PI / 2);
            for (UUID s : spinners) {
                if (!(Bukkit.getEntity(s) instanceof ItemDisplay d)) continue;
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(40);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(rot + (float) (Math.PI / 2), 0, 1, 0),
                        new Vector3f(1.1f, 1.1f, 1.1f), new AxisAngle4f()));
            }
        }
    }

    /** The dummies standing now (for the stats of a trainee and the bot's checks). */
    List<LivingEntity> dummies() {
        List<LivingEntity> out = new ArrayList<>();
        for (UUID id : dummies.values()) if (Bukkit.getEntity(id) instanceof LivingEntity le && le.isValid()) out.add(le);
        return out;
    }
}
