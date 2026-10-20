package mn.suld.plugin.progression;

import mn.suld.api.balance.TimedBoosts;
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
     * Timed boosts by kind and source (an ovoo's EXP blessing, docs/world/OVOO.md; a Silver Tree drink): within a
     * kind the strongest live source counts, so two EXP blessings never add up (api TimedBoosts).
     */
    private final java.util.Map<UUID, TimedBoosts> blessings = new java.util.concurrent.ConcurrentHashMap<>();

    /** An ovoo's EXP blessing. */
    public void bless(UUID player, long untilMillis, double bonus) {
        bless(player, TimedBoosts.Kind.EXP, "ovoo", untilMillis, bonus);
    }

    public void bless(UUID player, TimedBoosts.Kind kind, String source, long untilMillis, double bonus) {
        blessings.computeIfAbsent(player, k -> new TimedBoosts()).put(kind, source, untilMillis, bonus);
    }

    /** The strongest live boost of {@code kind} (a fraction; LOOT in percent points), or 0. */
    public double bonus(UUID player, TimedBoosts.Kind kind) {
        TimedBoosts b = blessings.get(player);
        return b == null ? 0 : b.strongest(kind, System.currentTimeMillis());
    }

    /** When {@code source}'s boost of {@code kind} ends (0: none live). */
    public long until(UUID player, TimedBoosts.Kind kind, String source) {
        TimedBoosts b = blessings.get(player);
        return b == null ? 0 : b.until(kind, source, System.currentTimeMillis());
    }

    /** Quit: the boosts are kept in the player file by their sources and put back at the next join. */
    public void forget(UUID player) {
        blessings.remove(player);
    }

    private double blessing(UUID player) {
        return bonus(player, TimedBoosts.Kind.EXP);
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
