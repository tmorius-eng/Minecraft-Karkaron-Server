package mn.suld.api.worldboss;

import mn.suld.api.dungeon.BossPhase;

import java.util.List;

/**
 * A world boss (content/events.json "worldBosses", docs/WORLD_BOSSES.md): a WORLD_BOSS-tier mob that rises at a
 * historic site at a scheduled hour for the whole server to fight together.
 *
 * @param mobId           the boss mob (mobs.json, tier WORLD_BOSS, role "worldboss")
 * @param siteId          the historic site it rises at (sites.json); {@code dx}/{@code dz} offset it from the site's heart
 * @param phases          its fight phases (first at 1.0, strictly descending), as a dungeon boss's
 * @param enrageSeconds   after this long the boss enrages; {@code 0} never
 * @param durationSeconds still standing after this long, it withdraws and the fight is lost
 * @param leash           blocks it may be pulled from where it rose before it walks back (no kiting it to a wall)
 * @param lootTable       rolled once per rewarded fighter (items/loot.json)
 * @param rewardExp       EXP every rewarded fighter gets at least (the level floor of Rewards.worldEventExp applies too)
 * @param rewardCoins     coins every rewarded fighter gets (the podium multiplies both)
 * @param clanBonus       clan EXP, once per clan that had a rewarded fighter
 */
public record WorldBossDefinition(String id, String mobId, String siteId, int dx, int dz, List<BossPhase> phases, int enrageSeconds,
                                  int durationSeconds, int leash, String lootTable, long rewardExp, long rewardCoins, long clanBonus) {

    public WorldBossDefinition {
        if (id == null || !id.matches("^worldboss\\.[a-z0-9_]+$")) throw new IllegalArgumentException("id must look like worldboss.some_name");
        if (mobId == null || siteId == null || lootTable == null) throw new IllegalArgumentException("mob, site and loot are required");
        if (phases == null || phases.isEmpty() || phases.get(0).healthThresholdPct() != 1.0)
            throw new IllegalArgumentException("the first phase starts at 1.0");
        for (int i = 1; i < phases.size(); i++)
            if (phases.get(i).healthThresholdPct() >= phases.get(i - 1).healthThresholdPct())
                throw new IllegalArgumentException("phases must be in strictly descending threshold order");
        if (durationSeconds < 60) throw new IllegalArgumentException("durationSeconds must be >= 60");
        if (enrageSeconds < 0 || enrageSeconds >= durationSeconds) throw new IllegalArgumentException("enrageSeconds in [0, durationSeconds)");
        if (leash < 16) throw new IllegalArgumentException("leash must be >= 16");
        if (rewardExp < 0 || rewardCoins < 0 || clanBonus < 0) throw new IllegalArgumentException("rewards must be >= 0");
        phases = List.copyOf(phases);
    }
}
