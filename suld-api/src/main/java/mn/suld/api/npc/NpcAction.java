package mn.suld.api.npc;

/** What an NPC's action button does (content/npcs.json {@code action}); the behaviour stays in the plugin. */
public enum NpcAction {
    /** The class stones: pick a class, later the Академи. */
    CLASS,
    /** The guide's tutorial window. */
    TUTORIAL,
    /** The story's quest book. */
    QUESTS,
    /** A shop of content/shops.json ({@code shop} names it). */
    SHOP,
    /** The smith: repair and the class armour. */
    SMITH,
    /** The relay: the travel page. */
    TRAVEL,
    /** The sky shrine: the blessing, the Тэнгэрийн Зэрэг rite at the cap. */
    SHRINE,
    /** The side quests this NPC gives («Нэмэлт эрэл»). */
    SIDE_QUESTS,
    /** The Их Хуралдай window (a campaign's steward). */
    KHURALDAI,
    /** Talk only. */
    NONE
}
