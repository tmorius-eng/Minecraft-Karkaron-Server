package mn.suld.api.config;

/**
 * World settings (Vertical Slice 5).
 *
 * @param autoBuildKharkhorum reserved for the WorldBuilder v2 (not wired yet; default off)
 * @param buildBlocksPerTick  WorldBuilder budget per server tick (keeps TPS healthy while building)
 * @param regionSpawning      natural spawning of region mobs around players
 * @param regionMobsPerPlayer target number of region mobs around each player
 */
public record WorldSettings(boolean autoBuildKharkhorum, int buildBlocksPerTick, boolean regionSpawning,
                            int regionMobsPerPlayer) {

    public WorldSettings {
        buildBlocksPerTick = Math.max(200, Math.min(50_000, buildBlocksPerTick));
        regionMobsPerPlayer = Math.max(0, Math.min(12, regionMobsPerPlayer));
    }

    public static WorldSettings defaults() {
        return new WorldSettings(false, 4000, true, 4);
    }
}
