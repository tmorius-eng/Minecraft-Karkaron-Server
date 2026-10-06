package mn.suld.api.style;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The SÜLD rank ladder, after the decimal army of the Mongol Empire (арав → зуу → мянга → түмэн).
 * A player climbs it with {@code /rankup}: each step needs a character level and costs SÜLD coins.
 * The rank shows as a badge in chat, the TAB list and above the head. Staff badges are separate
 * ({@link StaffBadge}).
 */
public enum Rank {
    ARD("ard", "Ард", "АРД", "#B8C0CC", 1, 0, "Энгийн малчин ард. Аян эндээс эхэлнэ."),
    TSEREG("tsereg", "Цэрэг", "ЦЭРЭГ", "#6BD06B", 5, 150, "Хааны цэрэгт элссэн дайчин."),
    ARAVT("aravt", "Аравтын дарга", "АРАВТ", "#4FD2D2", 10, 400, "Арван дайчныг удирдагч."),
    ZUUT("zuut", "Зуутын дарга", "ЗУУТ", "#4F8BFF", 18, 1_000, "Зуун дайчны манлай."),
    MYANGAT("myangat", "Мянгатын ноён", "МЯНГАТ", "#B06BFF", 26, 2_500, "Мянган цэргийн ноён."),
    TUMEN("tumen", "Түмэний ноён", "ТҮМЭН", "#FF9A3C", 35, 6_000, "Арван мянган цэргийн их ноён."),
    NOYON("noyon", "Их ноён", "НОЁН", "#FF5A5A", 45, 14_000, "Хааны зөвлөлийн их ноён."),
    KHAAN("khaan", "Хаан", "ХААН", "#FFD24A", 60, 35_000, "Мөнх тэнгэрийн хүчинд — хаан ширээ.");

    private final String id;
    private final String displayName;
    private final String badgeLabel;
    private final String color;
    private final int requiredLevel;
    private final long cost;
    private final String lore;

    Rank(String id, String displayName, String badgeLabel, String color, int requiredLevel, long cost, String lore) {
        this.id = id;
        this.displayName = displayName;
        this.badgeLabel = badgeLabel;
        this.color = color;
        this.requiredLevel = requiredLevel;
        this.cost = cost;
        this.lore = lore;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    /** Uppercase label drawn on the badge glyph. */
    public String badgeLabel() { return badgeLabel; }
    /** Hex colour, e.g. {@code #FFD24A}. */
    public String color() { return color; }
    public int requiredLevel() { return requiredLevel; }
    public long cost() { return cost; }
    public String lore() { return lore; }

    public static final List<Rank> LADDER = List.of(values());

    public Optional<Rank> next() {
        return ordinal() + 1 < LADDER.size() ? Optional.of(LADDER.get(ordinal() + 1)) : Optional.empty();
    }

    public static Rank byId(String id) {
        if (id == null) return ARD;
        String k = id.toLowerCase(Locale.ROOT);
        for (Rank r : values()) if (r.id.equals(k)) return r;
        return ARD;
    }

    /** Why a rank-up is or is not possible. */
    public enum Check { OK, MAX_RANK, LEVEL_TOO_LOW, NOT_ENOUGH_COINS }

    /** Pure decision for {@code /rankup}: the next rank needs its level and its cost in coins. */
    public static Check canRankUp(Rank current, int level, long coins) {
        Rank next = current.next().orElse(null);
        if (next == null) return Check.MAX_RANK;
        if (level < next.requiredLevel) return Check.LEVEL_TOO_LOW;
        if (coins < next.cost) return Check.NOT_ENOUGH_COINS;
        return Check.OK;
    }
}
