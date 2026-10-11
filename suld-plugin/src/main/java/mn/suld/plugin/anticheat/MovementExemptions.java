package mn.suld.plugin.anticheat;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Who is being moved by SÜLD right now. The anticheat bridge ({@link GrimBridge}) asks this before it forgives a
 * movement flag: only a player SÜLD itself launched (a dash, a leap, a dodge) or seated (the sky skill tree) is in a
 * window, and only for as long as that movement lasts. Everything else is judged by the anticheat as usual.
 *
 * <p>Read from the anticheat's network threads, written from the main thread: lock-free maps, wall-clock windows
 * (a tick is 50 ms). No Bukkit state is touched apart from {@link #launch}, which the main thread calls.
 */
public final class MovementExemptions {

    /** One open window: until when, and why (shown by {@code /suld grim}). */
    public record Window(long untilNanos, String reason) {
    }

    /** A dash or leap is airborne for about a second and a half; the window also covers the landing. */
    public static final int LAUNCH_TICKS = 40;

    private final LongSupplier clock;
    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();
    private final Map<UUID, String> holds = new ConcurrentHashMap<>();

    public MovementExemptions() {
        this(System::nanoTime);
    }

    MovementExemptions(LongSupplier clock) {
        this.clock = clock;
    }

    /** Opens (or extends, never shortens) a window of {@code ticks} for the player. */
    public void exempt(UUID player, int ticks, String reason) {
        long until = clock.getAsLong() + ticks * 50_000_000L;
        windows.merge(player, new Window(until, reason), (old, now) -> now.untilNanos() >= old.untilNanos() ? now : old);
    }

    /** Sets the player's velocity for a SÜLD movement and opens the matching window. Main thread. */
    public void launch(Player p, Vector velocity, String reason) {
        exempt(p.getUniqueId(), LAUNCH_TICKS, reason);
        p.setVelocity(velocity);
    }

    /** An open-ended exemption (while seated on the sky tree); lasts until {@link #release}. */
    public void hold(UUID player, String reason) {
        holds.put(player, reason);
    }

    public void release(UUID player, String reason) {
        holds.remove(player, reason);
    }

    /** Why the player is exempt right now, or {@code null} if they are not. Any thread. */
    public String active(UUID player) {
        String held = holds.get(player);
        if (held != null) return held;
        Window w = windows.get(player);
        if (w == null) return null;
        if (clock.getAsLong() - w.untilNanos() < 0) return w.reason();
        windows.remove(player, w);
        return null;
    }

    /** Drops everything about a player who left. */
    public void forget(UUID player) {
        windows.remove(player);
        holds.remove(player);
    }
}
