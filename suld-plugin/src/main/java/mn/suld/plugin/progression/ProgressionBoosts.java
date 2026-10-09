package mn.suld.plugin.progression;

import mn.suld.plugin.SuldServices;

import java.util.UUID;

/**
 * The single place personal EXP bonuses are combined (clan level + borne relic). Bonuses add
 * up rather than multiply, so stacking stays predictable: base × (1 + clan + relic).
 */
public final class ProgressionBoosts {

    private final SuldServices services;

    public ProgressionBoosts(SuldServices services) {
        this.services = services;
    }

    /**
     * Timed blessings by source (an ovoo's, docs/world/OVOO.md; others later): player → source → (until
     * millis, bonus). Different sources add up; a new blessing from the same source replaces the old one.
     */
    private final java.util.Map<UUID, java.util.Map<String, double[]>> blessings = new java.util.concurrent.ConcurrentHashMap<>();

    public void bless(UUID player, long untilMillis, double bonus) {
        bless(player, "ovoo", untilMillis, bonus);
    }

    public void bless(UUID player, String source, long untilMillis, double bonus) {
        blessings.computeIfAbsent(player, k -> new java.util.concurrent.ConcurrentHashMap<>()).put(source, new double[]{untilMillis, bonus});
    }

    public void forget(UUID player) {
        blessings.remove(player);
    }

    private double blessing(UUID player) {
        java.util.Map<String, double[]> mine = blessings.get(player);
        if (mine == null) return 0;
        long now = System.currentTimeMillis();
        mine.values().removeIf(b -> now > b[0]);
        double sum = 0;
        for (double[] b : mine.values()) sum += b[1];
        return sum;
    }

    public double bonus(UUID player) {
        return services.clans().expBonus(player) + services.relics().expBonus(player) + blessing(player);
    }

    public long apply(UUID player, long baseExp) {
        return Math.round(baseExp * (1.0 + bonus(player)));
    }
}
