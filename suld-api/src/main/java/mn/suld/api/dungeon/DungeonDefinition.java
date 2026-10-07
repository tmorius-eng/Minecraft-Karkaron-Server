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
}
