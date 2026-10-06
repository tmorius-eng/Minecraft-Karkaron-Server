package mn.suld.api.analytics;

/**
 * Canonical analytics event type keys.
 *
 * <p>Centralising these as constants keeps producers and any downstream
 * dashboard in agreement on spelling. The list mirrors the retention funnel in
 * GAME_DESIGN.md. It is not exhaustive — new keys may be emitted freely — but
 * the funnel-critical ones live here.
 */
public final class AnalyticsEventType {

    private AnalyticsEventType() {
    }

    public static final String FIRST_LOGIN = "first_login";
    public static final String CLASS_SELECTED = "class_selected";
    public static final String FIRST_MOB_KILL = "first_mob_kill";
    public static final String FIRST_ITEM = "first_item";
    public static final String FIRST_RARE_ITEM = "first_rare_item";
    public static final String FIRST_DEATH = "first_death";
    public static final String FIRST_DUNGEON = "first_dungeon";
    public static final String FIRST_BOSS = "first_boss";

    public static final String RETENTION_15M = "retention_15m";
    public static final String RETENTION_30M = "retention_30m";
    public static final String RETENTION_1H = "retention_1h";

    public static final String SESSION_START = "session_start";
    public static final String SESSION_END = "session_end";
    public static final String LOGOUT_LOCATION = "logout_location";

    public static final String LEVEL_UP = "level_up";
    public static final String EXP_GAIN = "exp_gain";
    public static final String ITEM_ECONOMY = "item_economy";
    public static final String DEATH = "death";
    public static final String BOSS_PARTICIPATION = "boss_participation";
}
