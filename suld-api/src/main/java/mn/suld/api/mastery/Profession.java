package mn.suld.api.mastery;

/**
 * The four steppe crafts (directive 2026-10-13 Phase 4, docs/MASTERY_AND_PROFESSIONS.md). Each gathers from its own
 * kind of node in the wilderness and levels 1–{@link #MAX_LEVEL}; a level opens better node tiers and bigger yields
 * ({@link #canGather}, {@link #yield}). The crafts are VERIFIED steppe trades; their numbers are the game's.
 */
public enum Profession {

    /** Iron and copper from the rock: the smith's metal. */
    TOMRIIN_DARKHAN("Төмрийн Дархан", "хад чулуунаас төмөр, зэсийн хүдэр олборлоно", Source.ORE),
    /** Healing plants of the steppe (ганга, чацаргана, алтан гагнуур). */
    EMIIN_BARIACH("Эмийн Бариач", "талын эмийн ургамал түүнэ", Source.HERB),
    /** The herds: horsehair and mare's milk from the wild herds of the steppe. */
    ADUUCHIN("Хүлэгч / Адуучин", "зэрлэг адуунаас хялгас, гүүний сүү авна", Source.HERD),
    /** Wool from the sheep, beaten into felt. */
    ESGIICH("Эсгийч", "хонины ноос хяргаж, эсгий хийнэ", Source.WOOL);

    /** The kind of node a craft gathers from. */
    public enum Source { ORE, HERB, HERD, WOOL }

    public static final int MAX_LEVEL = 50;
    /** Node tiers (the region bands of the world: Хэрлэн … the outer lands). */
    public static final int MAX_TIER = 4;
    /** A gathered node comes back after this long (for everyone). */
    public static final int NODE_RESPAWN_SECONDS = 300;

    private final String displayName;
    private final String verb;
    private final Source source;

    Profession(String displayName, String verb, Source source) {
        this.displayName = displayName;
        this.verb = verb;
        this.source = source;
    }

    public String displayName() {
        return displayName;
    }

    /** What the craft does, for its card («хад чулуунаас төмөр … олборлоно»). */
    public String verb() {
        return verb;
    }

    public Source source() {
        return source;
    }

    /** The craft that gathers from {@code s}. */
    public static Profession of(Source s) {
        for (Profession p : values()) if (p.source == s) return p;
        throw new IllegalArgumentException("no craft for " + s);
    }

    /** Points level {@code level} needs to reach the next: round(50 · L^1.4); 0 at the top. */
    public static long need(int level) {
        if (level >= MAX_LEVEL) return 0;
        return Math.round(50 * Math.pow(Math.max(1, level), 1.4));
    }

    /** The level a node tier opens at: 1, 13, 25, 37. */
    public static int tierLevel(int tier) {
        return 1 + 12 * (Math.max(1, Math.min(MAX_TIER, tier)) - 1);
    }

    public static boolean canGather(int level, int tier) {
        return level >= tierLevel(tier);
    }

    /** Points one gather of a tier gives: 10 per tier, halved once the craft is two tiers past it (24 levels). */
    public static int points(int level, int tier) {
        int t = Math.max(1, Math.min(MAX_TIER, tier));
        int base = 10 * t;
        return level >= tierLevel(t) + 24 ? base / 2 : base;
    }

    /** Items one gather yields: 1, +1 from level 20, +1 from level 40. */
    public static int yield(int level) {
        return 1 + (level >= 20 ? 1 : 0) + (level >= 40 ? 1 : 0);
    }

    /** The level and progress that {@code total} points ever earned make. */
    public static State fromTotal(long total) {
        return State.START.add(total);
    }

    /** The points ever earned that make {@code s} (the inverse of {@link #fromTotal}, below the top). */
    public static long totalOf(State s) {
        long t = s.points();
        for (int l = 1; l < s.level(); l++) t += need(l);
        return t;
    }

    /** The levels that bring a title: «Шавь» at 10, «Мэргэжилтэн» at 25, «Мастер» at 50. */
    public static final int[] TITLE_LEVELS = {10, 25, 50};

    /** The title a level brings («Төмрийн Дарханы Шавь» …), or null. */
    public static String title(Profession p, int level) {
        String rank = switch (level) {
            case 10 -> "Шавь";
            case 25 -> "Мэргэжилтэн";
            case 50 -> "Мастер";
            default -> null;
        };
        return rank == null ? null : p.genitive() + " " + rank;
    }

    /** The craft's name in the genitive, for its titles («Төмрийн Дарханы», «Эмийн Бариачийн», «Адуучны», «Эсгийчийн»). */
    public String genitive() {
        return switch (this) {
            case TOMRIIN_DARKHAN -> "Төмрийн Дарханы";
            case EMIIN_BARIACH -> "Эмийн Бариачийн";
            case ADUUCHIN -> "Адуучны";
            case ESGIICH -> "Эсгийчийн";
        };
    }

    /** A craft's level and its points toward the next one ({@link WeaponMastery.State}'s twin, with this curve). */
    public record State(int level, long points) {

        public static final State START = new State(1, 0);

        public State {
            level = Math.max(1, Math.min(MAX_LEVEL, level));
            points = level >= MAX_LEVEL ? 0 : Math.max(0, points);
        }

        public State add(long pts) {
            if (pts <= 0 || level >= MAX_LEVEL) return this;
            int l = level;
            long p = points + pts;
            while (l < MAX_LEVEL && p >= need(l)) {
                p -= need(l);
                l++;
            }
            return new State(l, p);
        }
    }
}
