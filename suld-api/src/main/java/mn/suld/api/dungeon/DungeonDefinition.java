package mn.suld.api.dungeon;

import java.util.List;

/**
 * Data-driven definition for a dungeon instance. Contains all static data about
 * a dungeon: wave composition (mob loot tables describe which mobs are in each
 * wave), the boss, and the reward table rolled on completion.
 *
 * @param id             stable id (e.g. "dungeon.khasar_den")
 * @param displayName    Mongolian display name
 * @param minLevel       minimum player level to enter
 * @param minPartySize   minimum party size
 * @param maxPartySize   maximum party size
 * @param waveSpawnIds   ordered list of mob ids for each wave
 *                       (each entry is a list of mob ids to spawn in that wave)
 * @param bossDefinition boss definition (non-null)
 * @param rewardTableId  id of the loot table (item catalog) rolled once per player on completion
 */
public record DungeonDefinition(
        String id,
        String displayName,
        int minLevel,
        int minPartySize,
        int maxPartySize,
        List<List<String>> waveSpawnIds,
        BossDefinition bossDefinition,
        String rewardTableId) {

    public DungeonDefinition {
        waveSpawnIds = waveSpawnIds == null ? List.of() : List.copyOf(waveSpawnIds);
    }

    public int totalWaves() { return waveSpawnIds.size(); }

    /** How far above its boss's level a dungeon's reward items may roll (dungeons outside the ladder). */
    public static final int REWARD_LEVEL_SPAN = 5;

    /** Highest level this dungeon's rewards are worth: the ladder's band, else boss level + {@link #REWARD_LEVEL_SPAN}. */
    public int maxLevel() {
        mn.suld.api.balance.DungeonLadder.Rung r = mn.suld.api.balance.DungeonLadder.rung(id);
        return r != null ? r.max() : Math.max(minLevel, bossDefinition.mob().level() + REWARD_LEVEL_SPAN);
    }

    /**
     * The level reward items roll at for a player of {@code playerLevel}: their own level clamped to the dungeon's
     * band (min..max). A level-60 player farming the first dungeon gets that dungeon's gear, not level-60 gear.
     */
    public int rewardLevel(int playerLevel) {
        return mn.suld.api.balance.DungeonRules.lootLevel(minLevel, maxLevel(), Math.max(1, playerLevel));
    }

    /** Party size the boss is tuned for (1 outside the ladder). */
    public int recommendedParty() {
        mn.suld.api.balance.DungeonLadder.Rung r = mn.suld.api.balance.DungeonLadder.rung(id);
        return r == null ? 1 : r.party();
    }
}
