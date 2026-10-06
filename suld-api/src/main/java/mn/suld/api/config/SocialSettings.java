package mn.suld.api.config;

/**
 * Clan and world-event settings (Vertical Slice 3).
 *
 * @param clanCreateCost          coins charged to found a clan (a currency sink, not real money)
 * @param worldEventsEnabled      whether world events start automatically
 * @param worldEventIntervalMin   minutes between automatic events
 * @param worldEventMinPlayers    minimum online players with a class for an automatic start
 */
public record SocialSettings(long clanCreateCost, boolean worldEventsEnabled,
                             int worldEventIntervalMin, int worldEventMinPlayers) {

    public SocialSettings {
        clanCreateCost = Math.max(0, clanCreateCost);
        worldEventIntervalMin = Math.max(5, worldEventIntervalMin);
        worldEventMinPlayers = Math.max(1, worldEventMinPlayers);
    }

    public static SocialSettings defaults() {
        return new SocialSettings(500, true, 45, 1);
    }
}
