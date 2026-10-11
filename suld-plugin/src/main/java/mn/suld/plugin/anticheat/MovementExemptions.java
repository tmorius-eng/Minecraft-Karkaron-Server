package mn.suld.plugin.anticheat;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Who is being moved by SÜLD right now. The anticheat bridge ({@link GrimBridge}) asks this before it forgives a
 * flag: only a player SÜLD itself launched (a dash, a leap, a dodge), seated (the sky skill tree), turned (lock-on)
 * or put on / off a horse is in a window, only for as long as that lasts, and only for the checks of that kind of
 * window. Everything else is judged by the anticheat as usual.
 *
 * <p>A player has at most one window per {@link Kind}, so a lock-on during a leap keeps both. Windows are sized for
 * the player's ping ({@link #withPing}): a laggy client's movement arrives later than the server sent it.
 *
 * <p>Read from the anticheat's network threads, written from the main thread: lock-free maps, wall-clock windows
 * (a tick is 50 ms). No Bukkit state is touched apart from the {@link Player} overloads, which the main thread calls.
 */
public final class MovementExemptions {

    /** What kind of SÜLD movement a window covers; GrimBridge forgives a different check list for each. */
    public enum Kind {
        /** SÜLD moved the player: dashes, leaps, dodge, the sky-tree seat, teleport settling. */
        MOVE,
        /** The server turned the player's camera (lock-on). */
        LOCKON,
        /** The player got on or off a SÜLD horse. */
        MOUNT
    }

    /** One open window: until when, why (shown by {@code /suld grim}) and what kind. */
    public record Window(long untilNanos, String reason, Kind kind) {
    }

    /**
     * Window lengths per SÜLD movement, matched to how long it really moves the player (airtime plus the landing):
     * kept short so back-to-back casts cannot keep a window open (and GrimBridge's budget caps what is forgiven).
     */
    public static final int LAUNCH_TICKS = 40, LEAP_TICKS = 40, DISENGAGE_TICKS = 30, WINDHORSE_TICKS = 30,
            CHARGE_TICKS = 20, DASH_TICKS = 20, DODGE_TICKS = 20;
    /** At most this many extra ticks are added for a laggy client. */
    public static final int MAX_PING_TICKS = 40;

    private static final int KINDS = Kind.values().length;

    private final LongSupplier clock;
    private final Map<UUID, Window[]> windows = new ConcurrentHashMap<>();
    private final Map<UUID, Window> holds = new ConcurrentHashMap<>();

    public MovementExemptions() {
        this(System::nanoTime);
    }

    MovementExemptions(LongSupplier clock) {
        this.clock = clock;
    }

    /** Ticks a window needs for this player: {@code ticks} plus two pings (the client's answer arrives a ping late). */
    public static int withPing(int ticks, int pingMillis) {
        int extra = (int) Math.ceil(2.0 * Math.max(0, pingMillis) / 50.0);
        return ticks + Math.min(MAX_PING_TICKS, extra);
    }

    /** Opens (or extends, never shortens) a MOVE window of {@code ticks} for the player. */
    public void exempt(UUID player, int ticks, String reason) {
        exempt(player, ticks, reason, Kind.MOVE);
    }

    /** Opens (or extends, never shortens) the player's window of this kind. */
    public void exempt(UUID player, int ticks, String reason, Kind kind) {
        long until = clock.getAsLong() + ticks * 50_000_000L;
        Window w = new Window(until, reason, kind);
        windows.compute(player, (k, arr) -> {
            Window[] a = arr == null ? new Window[KINDS] : arr.clone();
            Window old = a[kind.ordinal()];
            if (old == null || until - old.untilNanos() >= 0) a[kind.ordinal()] = w;
            return a;
        });
    }

    /** Opens a window sized for the player's ping. Main thread. */
    public void exempt(Player p, int ticks, String reason, Kind kind) {
        exempt(p.getUniqueId(), withPing(ticks, p.getPing()), reason, kind);
    }

    /** Sets the player's velocity for a SÜLD movement and opens the matching window. Main thread. */
    public void launch(Player p, Vector velocity, String reason) {
        launch(p, velocity, reason, LAUNCH_TICKS);
    }

    /** {@link #launch(Player, Vector, String)} with the window length of this movement. */
    public void launch(Player p, Vector velocity, String reason, int ticks) {
        exempt(p, ticks, reason, Kind.MOVE);
        p.setVelocity(velocity);
    }

    /** An open-ended MOVE exemption (while seated on the sky tree); lasts until {@link #release}. */
    public void hold(UUID player, String reason) {
        holds.put(player, new Window(Long.MAX_VALUE, reason, Kind.MOVE));
    }

    public void release(UUID player, String reason) {
        holds.computeIfPresent(player, (k, w) -> w.reason().equals(reason) ? null : w);
    }

    /** The player's open window of this kind (a MOVE hold counts as MOVE), or {@code null}. Any thread. */
    public Window window(UUID player, Kind kind) {
        if (kind == Kind.MOVE) {
            Window held = holds.get(player);
            if (held != null) return held;
        }
        Window[] a = windows.get(player);
        Window w = a == null ? null : a[kind.ordinal()];
        if (w == null) return null;
        return clock.getAsLong() - w.untilNanos() < 0 ? w : null;
    }

    /** Why the player is exempt right now (any kind), or {@code null} if they are not. Any thread. */
    public String active(UUID player) {
        for (Kind k : Kind.values()) {
            Window w = window(player, k);
            if (w != null) return w.reason();
        }
        windows.remove(player); // all closed: drop the entry
        return null;
    }

    /** Drops everything about a player who left. */
    public void forget(UUID player) {
        windows.remove(player);
        holds.remove(player);
    }
}
