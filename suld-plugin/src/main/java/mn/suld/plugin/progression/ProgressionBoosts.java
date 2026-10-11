package mn.suld.plugin.progression;

import mn.suld.plugin.SuldServices;

import java.util.UUID;

/**
 * The single place personal EXP bonuses are combined (clan level + borne relic + blessings, and on kills the EXP % of
 * gear and tree). Bonuses add up rather than multiply, and together they give at most +50 % (progression v2,
 * {@link mn.suld.api.balance.ExpRules#BOOST_CAP}): base × (1 + min(0.5, clan + relic + blessings + gear)).
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

    /** Clan + relic + blessings, uncapped (the cap applies to the total, gear included). */
    public double rawBonus(UUID player) {
        return services.clans().expBonus(player) + services.relics().expBonus(player) + blessing(player);
    }

    public double bonus(UUID player) {
        return mn.suld.api.balance.ExpRules.capBoost(rawBonus(player));
    }

    private int serverLevel;
    private long serverLevelAt;

    /**
     * The server's median level for catch-up EXP ({@link mn.suld.api.balance.ExpRules#catchUp}): the median level of
     * the players online, recomputed at most once a minute; 0 (no catch-up) with fewer than 3 online. Main thread.
     */
    public int serverLevel() {
        long now = System.currentTimeMillis();
        if (now - serverLevelAt > 60_000) {
            serverLevelAt = now;
            java.util.List<Integer> levels = new java.util.ArrayList<>();
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                services.profiles().cached(p.getUniqueId()).ifPresent(pr -> levels.add(pr.progression().level()));
            }
            java.util.Collections.sort(levels);
            serverLevel = levels.size() < 3 ? 0 : levels.get(levels.size() / 2);
        }
        return serverLevel;
    }

    public long apply(UUID player, long baseExp) {
        return apply(player, baseExp, 0);
    }

    /** With {@code extra} more bonus (gear and tree EXP %, as a fraction) counted under the same cap. */
    public long apply(UUID player, long baseExp, double extra) {
        return Math.round(baseExp * (1.0 + mn.suld.api.balance.ExpRules.capBoost(rawBonus(player) + extra)));
    }
}
