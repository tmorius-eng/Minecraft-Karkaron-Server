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

    // The first five minutes (audit 2026-10-11 ANA-001, docs/ANALYTICS.md §Funnel)
    public static final String JOIN_ATTEMPT = "join_attempt";
    public static final String RESOURCE_PACK_STATUS = "resource_pack_status";
    public static final String TUTORIAL_STEP = "tutorial_step";
    public static final String QUEST_COMPLETE = "quest_complete";
    /** Five validated active minutes in one session (ActivePlaytime, not wall-clock). */
    public static final String ACTIVE_5M = "active_5m";

    /** Fifteen, thirty and sixty validated active minutes in one session. */
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

    // Их Хуралдай, campaigns and the chronicle (docs/KHURALDAI.md)
    public static final String VOTE_CAST = "vote_cast";
    public static final String VOTE_CLOSED = "vote_closed";
    public static final String CAMPAIGN_START = "campaign_start";
    public static final String CAMPAIGN_CONTRIBUTION = "campaign_contribution";
    public static final String CAMPAIGN_END = "campaign_end";
    public static final String CAMPAIGN_REWARD_CLAIM = "campaign_reward_claim";
    public static final String CHRONICLE_ENTRY = "chronicle_entry";
    public static final String PARTY_FIND = "party_find";
    public static final String WORLD_EFFECT_APPLIED = "world_effect_applied";
    // world bosses (docs/WORLD_BOSSES.md)
    public static final String WORLD_BOSS_SPAWN = "world_boss_spawn";
    public static final String WORLD_BOSS_END = "world_boss_end";
    // referrals (docs/REFERRAL.md): named with /urih; the invitee reached the reward level; the inviter was paid
    public static final String REFERRAL_CREATED = "referral_created";
    public static final String REFERRAL_REWARDED = "referral_rewarded";
    public static final String REFERRAL_INVITER_PAID = "referral_inviter_paid";
    // side quests (docs/SIDE_QUESTS.md): their own types, so the funnel's first-chapter step (QUEST_COMPLETE) stays the story's
    public static final String SIDE_QUEST_ACCEPT = "side_quest_accept";
    public static final String SIDE_QUEST_COMPLETE = "side_quest_complete";
    public static final String SIDE_QUEST_ABANDON = "side_quest_abandon";
    /** One of the four drinks at the Silver Tree (data: drink). */
    public static final String SILVER_TREE_DRINK = "silver_tree_drink";
}
