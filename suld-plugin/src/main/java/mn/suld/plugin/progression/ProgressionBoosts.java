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

    /** Timed blessings (an ovoo's, docs/world/OVOO.md): player → (until millis, bonus). */
    private final java.util.Map<UUID, double[]> blessings = new java.util.concurrent.ConcurrentHashMap<>();

    public void bless(UUID player, long untilMillis, double bonus) {
        blessings.put(player, new double[]{untilMillis, bonus});
    }

    public void forget(UUID player) {
        blessings.remove(player);
    }

    private double blessing(UUID player) {
        double[] b = blessings.get(player);
        if (b == null) return 0;
        if (System.currentTimeMillis() > b[0]) {
            blessings.remove(player);
            return 0;
        }
        return b[1];
    }

    public double bonus(UUID player) {
        return services.clans().expBonus(player) + services.relics().expBonus(player) + blessing(player);
    }

    public long apply(UUID player, long baseExp) {
        return Math.round(baseExp * (1.0 + bonus(player)));
    }
}
