package mn.suld.plugin.map;

import mn.suld.api.death.SoulBeacon;
import mn.suld.api.map.MapPin;
import mn.suld.api.map.ScreenProjection;
import mn.suld.api.map.WaypointHud;
import mn.suld.api.style.PlayerSetting;
import mn.suld.plugin.SuldServices;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The waypoint markers on the game screen (docs/WORLD_MAP.md), like a client minimap mod's: the waypoint the player
 * follows, their nearest pins and the place they last fell each get a card, «A · Агуй» over «28.1 м», seen through
 * blocks and by the player only. A mark in view stands in the world on the line from the eye to it (at the place
 * within {@value WaypointHud#NEAR} blocks), so the client's own camera puts it on the right pixel every frame at any
 * field of view or window size; a mark behind or beside the view becomes a small card at the edge of the screen with an
 * arrow toward it, placed by {@link ScreenProjection} for the look the server knows. The layout is {@link WaypointHud}'s.
 * <p>
 * The waypoint always shows; pins and the death follow the «Дэлгэцийн тэмдэг» setting. Hidden in the sky windows,
 * while dead, as a spectator and in another world. State is read every tick, so a waypoint set, a pin added or
 * removed on the atlas or by command shows or goes on the next tick. Main thread.
 */
public final class ScreenMarkers implements Listener {

    static final String TAG = WaypointGuide.TAG;
    /** A card is moved only when its spot has changed by more than this (blocks): standing still sends nothing. */
    static final double MOVE_EPS = 0.01;
    /** The distance on a card is rewritten at most this often (ticks), unless the card changes kind. */
    static final int TEXT_TICKS = 4;
    static final TextColor GOLD = TextColor.fromHexString("#ECBE4C");
    static final TextColor GREY = TextColor.fromHexString("#C8D0E0");
    /** The death's card colour (pale bone). */
    static final int DEATH_COLOUR = 0xD9D4C7;

    private static final class Card {
        TextDisplay entity;
        boolean inView;
        Component text;
        int textTick = Integer.MIN_VALUE / 2;
        double x = Double.NaN, y, z;
        double scale = -1;
        String arrow;
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final PinService pins;
    private final WaypointGuide guide;
    private final Map<UUID, Map<String, Card>> cards = new HashMap<>();
    /** Set by SuldPlugin: true while the player is in a sky window (atlas, menu, skill tree). */
    public volatile Predicate<Player> busy = p -> false;

    public ScreenMarkers(Plugin plugin, SuldServices services, PinService pins, WaypointGuide guide) {
        this.plugin = plugin;
        this.services = services;
        this.pins = pins;
        this.guide = guide;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 1L);
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(cards.keySet())) hideAll(id);
        cards.clear();
    }

    /** The keys of the marks the player sees now. */
    public Set<String> showing(UUID player) {
        Map<String, Card> m = cards.get(player);
        return m == null ? Set.of() : Set.copyOf(m.keySet());
    }

    private void tick() {
        int now = Bukkit.getCurrentTick();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR || busy.test(p)) {
                if (cards.containsKey(id)) hideAll(id);
                continue;
            }
            update(p, now);
        }
        cards.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    /** The marks the player may see in their world now, before {@link WaypointHud#choose}. */
    private List<WaypointHud.Mark> marks(Player p) {
        String world = p.getWorld().getName();
        List<WaypointHud.Mark> all = new ArrayList<>();
        WaypointGuide.Target t = guide.target(p.getUniqueId());
        if (t != null && t.world().equals(world)) all.add(waypointMark(t, pinOf(p, t)));
        if (!services.styles().setting(p.getUniqueId(), PlayerSetting.SCREEN_MARKERS)) return all;
        // pins live in the overworld
        if (p.getWorld() == Bukkit.getWorlds().get(0)) for (MapPin pin : pins.pins(p.getUniqueId())) all.add(pinMark(pin));
        var beacons = services.soulBeacons;
        SoulBeacon b = beacons == null ? null : beacons.of(p.getUniqueId());
        if (b != null && b.world().equals(world)) all.add(deathMark(b.x() + 0.5, b.y(), b.z() + 0.5));
        return all;
    }

    private MapPin pinOf(Player p, WaypointGuide.Target t) {
        if (t.pin() == null) return null;
        for (MapPin pin : pins.pins(p.getUniqueId())) if (pin.id().equals(t.pin())) return pin;
        return null;
    }

    /** The waypoint's mark: a pin's letter and colour when it leads to a pin, else its own glyph in gold. */
    static WaypointHud.Mark waypointMark(WaypointGuide.Target t, MapPin pin) {
        String letter = pin != null ? WaypointHud.initial(pin.name()) : t.glyph();
        int colour = pin != null ? WaypointHud.colourOf(pin.colour()) : 0xECBE4C;
        return new WaypointHud.Mark("waypoint", WaypointHud.Kind.WAYPOINT, letter, t.name(), colour, t.x(), t.y(), t.z());
    }

    static WaypointHud.Mark pinMark(MapPin pin) {
        return new WaypointHud.Mark(pin.id().toString(), WaypointHud.Kind.PIN, WaypointHud.initial(pin.name()), pin.name(),
                WaypointHud.colourOf(pin.colour()), pin.x() + 0.5, pin.y(), pin.z() + 0.5);
    }

    static WaypointHud.Mark deathMark(double x, double y, double z) {
        return new WaypointHud.Mark("death", WaypointHud.Kind.DEATH, "☠", "Сүүлд унасан газар", DEATH_COLOUR, x, y, z);
    }

    /**
     * The key of the mark the waypoint already stands for, so it is not shown twice: its pin, or the death when it leads
     * to the death's block.
     */
    static String sameAs(WaypointGuide.Target t, List<WaypointHud.Mark> all) {
        if (t == null) return null;
        if (t.pin() != null) return t.pin().toString();
        for (WaypointHud.Mark m : all) {
            if (m.kind() == WaypointHud.Kind.DEATH && Math.floor(m.x()) == Math.floor(t.x()) && Math.floor(m.z()) == Math.floor(t.z())) return m.key();
        }
        return null;
    }

    private void update(Player p, int now) {
        UUID id = p.getUniqueId();
        List<WaypointHud.Mark> all = marks(p);
        Location feet = p.getLocation();
        List<WaypointHud.Mark> chosen = WaypointHud.choose(all, feet.getX(), feet.getY(), feet.getZ(), WaypointHud.MAX_MARKS,
                sameAs(guide.target(id), all));
        Map<String, Card> mine = cards.computeIfAbsent(id, k -> new HashMap<>());
        if (chosen.isEmpty()) {
            if (!mine.isEmpty()) hideAll(id);
            return;
        }
        World w = p.getWorld();
        // a place picked on the map has no height: the ground there once its chunk is loaded, else the player's height
        List<WaypointHud.Mark> resolved = new ArrayList<>(chosen.size());
        for (WaypointHud.Mark m : chosen) resolved.add(Double.isNaN(m.y()) ? withGround(w, m) : m);
        Location eye = p.getEyeLocation();
        ScreenProjection.Camera cam = ScreenProjection.Camera.standard(eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch());
        Set<String> wasInView = new HashSet<>();
        for (Map.Entry<String, Card> e : mine.entrySet()) if (e.getValue().inView) wasInView.add(e.getKey());
        List<WaypointHud.Placement> layout = WaypointHud.layout(cam, feet.getX(), feet.getY(), feet.getZ(), resolved, wasInView);
        Set<String> keep = new HashSet<>();
        for (WaypointHud.Placement pl : layout) {
            keep.add(pl.mark().key());
            show(p, mine.computeIfAbsent(pl.mark().key(), k -> new Card()), pl, now);
        }
        mine.entrySet().removeIf(e -> {
            if (keep.contains(e.getKey())) return false;
            remove(e.getValue());
            return true;
        });
    }

    private static WaypointHud.Mark withGround(World w, WaypointHud.Mark m) {
        int bx = (int) Math.floor(m.x()), bz = (int) Math.floor(m.z());
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return m;
        return new WaypointHud.Mark(m.key(), m.kind(), m.letter(), m.name(), m.colour(), m.x(), w.getHighestBlockYAt(bx, bz) + 1, m.z());
    }

    private void show(Player p, Card c, WaypointHud.Placement pl, int now) {
        World w = p.getWorld();
        Location at = new Location(w, pl.x(), pl.y(), pl.z());
        boolean kindChanged = c.entity == null || c.inView != pl.inView();
        boolean textDue = kindChanged || !java.util.Objects.equals(c.arrow, pl.arrow()) || now - c.textTick >= TEXT_TICKS;
        Component text = textDue ? card(pl) : c.text;
        // a card turning from its place to the edge (or back) starts afresh rather than glide across the view
        if (kindChanged) remove(c);
        if (c.entity == null || !c.entity.isValid() || c.entity.getWorld() != w) {
            remove(c);
            Component first = text;
            c.entity = w.spawn(at, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.addScoreboardTag(TAG);
                d.setBillboard(Display.Billboard.CENTER);
                d.setSeeThrough(true);
                d.setShadowed(true);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setLineWidth(400);
                d.setTeleportDuration(2);
                d.setViewRange(4f);
                d.text(first);
            });
            p.showEntity(plugin, c.entity);
            c.x = pl.x();
            c.y = pl.y();
            c.z = pl.z();
            c.text = text;
            c.textTick = now;
            c.scale = -1;
            c.arrow = pl.arrow();
        } else {
            if (Math.abs(c.x - pl.x()) > MOVE_EPS || Math.abs(c.y - pl.y()) > MOVE_EPS || Math.abs(c.z - pl.z()) > MOVE_EPS) {
                c.entity.teleport(at);
                c.x = pl.x();
                c.y = pl.y();
                c.z = pl.z();
            }
            if (textDue && !text.equals(c.text)) {
                c.entity.text(text);
                c.text = text;
            }
            if (textDue) c.textTick = now;
            c.arrow = pl.arrow();
        }
        if (kindChanged || c.scale < 0) {
            c.entity.setBackgroundColor(background(pl.mark(), pl.inView()));
        }
        if (Math.abs(c.scale - pl.scale()) > 0.01) {
            float s = (float) pl.scale();
            c.entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s), new Quaternionf()));
            c.scale = pl.scale();
        }
        c.inView = pl.inView();
    }

    /**
     * A card's text. In view: «A · Агуй» in the mark's colour (the waypoint's name bold) over «28.1 м». At the edge, one
     * short line: «⬅ A 28.1 м», the waypoint's with its name.
     */
    static Component card(WaypointHud.Placement pl) {
        WaypointHud.Mark m = pl.mark();
        TextColor colour = TextColor.color(m.colour());
        boolean waypoint = m.kind() == WaypointHud.Kind.WAYPOINT;
        String meters = WaypointHud.meters(pl.distance());
        if (pl.inView()) {
            return Component.text(m.letter(), colour, TextDecoration.BOLD)
                    .append(Component.text(" · " + m.name(), waypoint ? GOLD : NamedTextColor.WHITE).decoration(TextDecoration.BOLD, waypoint))
                    .append(Component.newline())
                    .append(Component.text(meters, GREY));
        }
        Component c = Component.text(pl.arrow() + " ", NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.text(m.letter(), colour, TextDecoration.BOLD));
        if (waypoint) c = c.append(Component.text(" " + m.name(), GOLD).decoration(TextDecoration.BOLD, false));
        return c.append(Component.text(" " + meters, GREY).decoration(TextDecoration.BOLD, false));
    }

    /** The card's backing: dark navy, a little stronger for the waypoint and at the edge (over bright sky). */
    static Color background(WaypointHud.Mark m, boolean inView) {
        int alpha = m.kind() == WaypointHud.Kind.WAYPOINT ? 150 : inView ? 100 : 130;
        return Color.fromARGB(alpha, 10, 16, 32);
    }

    private static void remove(Card c) {
        if (c.entity != null && c.entity.isValid()) c.entity.remove();
        c.entity = null;
    }

    private void hideAll(UUID id) {
        Map<String, Card> mine = cards.remove(id);
        if (mine == null) return;
        for (Card c : mine.values()) remove(c);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hideAll(e.getPlayer().getUniqueId());
    }

    /** A card left behind by a crash: never kept. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (var en : e.getEntities()) if (en.getScoreboardTags().contains(TAG)) en.remove();
    }
}
