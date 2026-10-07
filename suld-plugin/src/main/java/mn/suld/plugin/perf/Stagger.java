package mn.suld.plugin.perf;

import java.util.UUID;

/**
 * Spreads per-player periodic work over the ticks of its period instead of doing every player on one tick: a player is
 * due on the tick whose index matches a hash of their UUID, so with a period of N ticks each player is handled once
 * every N ticks and the cost per tick is about players / N.
 */
public final class Stagger {

    private Stagger() {
    }

    public static boolean due(UUID player, long tick, int period) {
        return period <= 1 || Math.floorMod(player.hashCode() * 0x9E3779B1, period) == Math.floorMod(tick, period);
    }
}
