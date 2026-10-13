package mn.suld.api.chronicle;

/**
 * What the Баатрын Хроник remembers (docs/KHURALDAI.md §Хроник). A <em>first</em> is claimed once per subject; a
 * <em>record</em> is replaced when beaten; a <em>log</em> entry is simply appended.
 */
public enum ChronicleKind {
    /** subject = dungeon id; the first party to clear it */
    FIRST_DUNGEON_CLEAR(Shape.FIRST),
    /** subject = dungeon id; value = seconds; the fastest clear so far */
    FASTEST_DUNGEON_CLEAR(Shape.RECORD),
    /** subject = relic key; every claim */
    RELIC_CLAIMED(Shape.LOG),
    /** subject = site id; the first player to stand there */
    FIRST_SITE_DISCOVERY(Shape.FIRST),
    /** subject = cycle id; the campaign's outcome and its top contributors */
    CAMPAIGN_RESULT(Shape.LOG),
    /** subject = cycle id; what the Хуралдай chose */
    KHURALDAI_RESULT(Shape.LOG),
    /** subject = world boss id; the first crowd to bring it down (party = the top three) */
    FIRST_WORLD_BOSS_KILL(Shape.FIRST),
    /** subject = world boss id; value = seconds; the fastest kill so far */
    FASTEST_WORLD_BOSS_KILL(Shape.RECORD),
    /** subject = player uuid; a player who reached Тэнгэрийн Зэрэг X, «Мөнх Тэнгэрийн Элч» (once each; the monument reads them) */
    HEAVENLY_ENVOY(Shape.FIRST);

    public enum Shape { FIRST, RECORD, LOG }

    private final Shape shape;

    ChronicleKind(Shape shape) {
        this.shape = shape;
    }

    public Shape shape() {
        return shape;
    }

    public boolean isFirst() {
        return shape == Shape.FIRST;
    }

    public boolean isRecord() {
        return shape == Shape.RECORD;
    }
}
