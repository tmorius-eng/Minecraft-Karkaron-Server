package mn.suld.plugin.anticheat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A token bucket per player for clicks in SÜLD menus (shops, cosmetics, the skill tree). Grim does not time inventory
 * clicks, and the cheat clients' ChestStealer / InventoryCleaner / AutoShop / ItemScroller modules click a whole menu
 * within a tick. A person clicks a few times a second: {@code burst} clicks pass at once, then {@code perSecond};
 * anything faster is dropped (the click is already cancelled, so nothing in the menu happens).
 */
public final class ClickRate {

    private final double burst;
    private final double perSecond;
    private final Map<UUID, double[]> buckets = new ConcurrentHashMap<>(); // {tokens, last millis, dropped}

    public ClickRate(double burst, double perSecond) {
        this.burst = burst;
        this.perSecond = perSecond;
    }

    /** True if this click may run. */
    public boolean allow(UUID player, long nowMillis) {
        double[] b = buckets.computeIfAbsent(player, k -> new double[]{burst, nowMillis, 0});
        synchronized (b) {
            b[0] = Math.min(burst, b[0] + (nowMillis - b[1]) / 1000.0 * perSecond);
            b[1] = nowMillis;
            if (b[0] >= 1) {
                b[0] -= 1;
                return true;
            }
            b[2]++;
            return false;
        }
    }

    /** Clicks dropped for the player since the last call (and resets the count). */
    public int takeDropped(UUID player) {
        double[] b = buckets.get(player);
        if (b == null) return 0;
        synchronized (b) {
            int n = (int) b[2];
            b[2] = 0;
            return n;
        }
    }

    public void forget(UUID player) {
        buckets.remove(player);
    }
}
