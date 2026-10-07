package mn.suld.api.worldevent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The rules of the Naadam horse race (docs/world/NAADAM.md), without Bukkit: riders pass {@code checkpoints} gates
 * in order, the first gate starts their clock, coming back to it closes the loop, and the first three home win
 * {@link #PRIZES}. A teleport or a logout puts an unfinished rider back to the start. Pure and tested.
 */
public final class HorseRace {

    public static final long[] PRIZES = {300, 200, 100};

    public enum Kind { NONE, START, CHECKPOINT, FINISH }

    /**
     * What a pass did: for CHECKPOINT {@code index} is the gate just passed (1-based, of {@code total}); for FINISH
     * {@code place} is 1-based and {@code prize} the coins (0 past third), {@code millis} the time.
     */
    public record Pass(Kind kind, int index, int total, int place, long prize, long millis) {
        static final Pass NONE = new Pass(Kind.NONE, 0, 0, 0, 0, 0);
    }

    private final int checkpoints;
    private final Map<UUID, Integer> next = new HashMap<>();
    private final Map<UUID, Long> started = new HashMap<>();
    private final Map<UUID, Long> finished = new LinkedHashMap<>();

    public HorseRace(int checkpoints) {
        if (checkpoints < 2) throw new IllegalArgumentException("a race needs at least 2 checkpoints");
        this.checkpoints = checkpoints;
    }

    /** The gate a rider must pass next (0 = the start). */
    public int nextGate(UUID rider) {
        return next.getOrDefault(rider, 0) % checkpoints;
    }

    /** The rider is at gate {@code gate} at {@code now}; counts only if it is the gate they need next. */
    public Pass pass(UUID rider, int gate, long now) {
        if (finished.containsKey(rider)) return Pass.NONE;
        int n = next.getOrDefault(rider, 0);
        if (gate != n % checkpoints) return Pass.NONE;
        if (n == 0) {
            started.put(rider, now);
            next.put(rider, 1);
            return new Pass(Kind.START, 0, checkpoints, 0, 0, 0);
        }
        long ms = now - started.get(rider);
        if (n == checkpoints) {
            finished.put(rider, ms);
            int place = finished.size();
            return new Pass(Kind.FINISH, checkpoints, checkpoints, place, place <= PRIZES.length ? PRIZES[place - 1] : 0, ms);
        }
        next.put(rider, n + 1);
        return new Pass(Kind.CHECKPOINT, n, checkpoints, 0, 0, ms);
    }

    /** A teleport or a logout mid-race: an unfinished rider starts over. True if they had started. */
    public boolean reset(UUID rider) {
        if (finished.containsKey(rider)) return false;
        next.remove(rider);
        return started.remove(rider) != null;
    }

    public boolean finished(UUID rider) {
        return finished.containsKey(rider);
    }

    /** Riders who started and have not finished, with the gate they need next. */
    public Map<UUID, Integer> riding() {
        Map<UUID, Integer> out = new LinkedHashMap<>();
        for (UUID id : started.keySet()) if (!finished.containsKey(id)) out.put(id, next.getOrDefault(id, 0));
        return out;
    }

    public int startedCount() {
        return started.size();
    }

    /** Finishers in order with their times. */
    public List<Map.Entry<UUID, Long>> results() {
        return new ArrayList<>(finished.entrySet());
    }

    /** Elapsed time of a rider who started (0 if not). */
    public long elapsed(UUID rider, long now) {
        Long s = started.get(rider);
        return s == null ? 0 : now - s;
    }
}
