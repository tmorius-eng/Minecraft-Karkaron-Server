package mn.suld.api.achievement;

/**
 * Something a player did that the game keeps count of (docs/ACHIEVEMENTS_AND_PASS.md). Each deed is stored per subject
 * (a chapter, a dungeon, a mob, a craft…; empty for deeds without one) in one of three ways, its {@link Keep}: a count
 * that only grows, the best value seen, or simply that it happened.
 */
public enum Deed {
    /** The character level reached (best). */
    LEVEL(Keep.BEST),
    /** A story chapter completed (subject: the chapter id). */
    CHAPTER(Keep.SEEN),
    /** A dungeon cleared (subject: the dungeon id; a count). */
    DUNGEON_CLEAR(Keep.COUNT),
    /** A heroic key finished in time (subject: the dungeon id; the best key level). */
    HEROIC_TIMED(Keep.BEST),
    /** A historic site discovered (subject: the site id). */
    SITE(Keep.SEEN),
    /** An ovoo visited (subject: the ovoo id). */
    OVOO(Keep.SEEN),
    /** A region discovered (subject: the region id). */
    REGION(Keep.SEEN),
    /** A SÜLD mob killed (subject: the mob id; a count). */
    MOB_KILL(Keep.COUNT),
    /** A world boss defeated with the player among its fighters (subject: the boss id; a count). */
    WORLD_BOSS(Keep.COUNT),
    /** A weapon track's level (subject: the track; best). */
    WEAPON_MASTERY(Keep.BEST),
    /** A craft's level (subject: the craft; best). */
    CRAFT_LEVEL(Keep.BEST),
    /** A gather finished (subject: the craft; a count). */
    GATHER(Keep.COUNT),
    /** The Тэнгэрийн Зэрэг reached (best). */
    ASCENSION(Keep.BEST),
    /** How many classes are mastered (best). */
    CLASS_MASTERS(Keep.BEST),
    /** A consented fight won (a count). */
    DUEL_WIN(Keep.COUNT),
    /** «Тэнгэрийн сорил» won (a count). */
    TRIAL_WIN(Keep.COUNT),
    /** A campaign's reward tier reached (subject: the campaign id; best). */
    CAMPAIGN_TIER(Keep.BEST),
    /** A Хуралдай vote cast (a count). */
    VOTE(Keep.COUNT),
    /** A clan joined or founded (subject: the clan's id; seen, so being in it again at a join counts once). */
    CLAN_JOIN(Keep.SEEN),
    /** A daily task finished (a count). */
    DAILY_TASK(Keep.COUNT),
    /** A side quest handed in (subject: the side quest id; docs/SIDE_QUESTS.md). */
    SIDE_QUEST(Keep.SEEN);

    /** How a deed's value is kept. */
    public enum Keep {
        /** Each record adds its amount. */
        COUNT,
        /** The highest amount recorded stands. */
        BEST,
        /** Recorded once is enough (the value is 1). */
        SEEN
    }

    private final Keep keep;

    Deed(Keep keep) {
        this.keep = keep;
    }

    public Keep keep() {
        return keep;
    }

    /** The storage key of this deed for {@code subject} ({@code ""} for none): {@code DEED|subject}. */
    public String key(String subject) {
        return name() + "|" + (subject == null ? "" : subject);
    }
}
