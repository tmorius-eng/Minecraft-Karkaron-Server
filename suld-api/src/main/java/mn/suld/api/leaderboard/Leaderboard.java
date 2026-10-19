package mn.suld.api.leaderboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server leaderboards (/top). Pure ranking; storage supplies {@link Entry} rows and, for the live boards, online
 * players' cached profiles supply fresher ones. The score boards are read from the database only: distinct dungeons
 * cleared (V17 marks), units given to the latest Их Хуралдай campaign (V18 totals), achievements unlocked (V28).
 */
public enum Leaderboard {
    LEVEL("Түвшин", "", true),
    COINS("Зоос", "зоос", true),
    DUNGEON_CLEARS("Агуй", "агуй", false),
    KHURALDAI("Хуралдайн аян", "нэгж", false),
    ACHIEVEMENTS("Амжилт", "амжилт", false);

    private final String displayName;
    private final String unit;
    private final boolean live;

    Leaderboard(String displayName, String unit, boolean live) {
        this.displayName = displayName;
        this.unit = unit;
        this.live = live;
    }

    public String displayName() {
        return displayName;
    }

    /** The word after a score board's number («12 агуй»); empty for the level board. */
    public String unit() {
        return unit;
    }

    /** True when online players' cached profiles are merged over the stored rows (level, coins); the score boards are stored rows only. */
    public boolean live() {
        return live;
    }

    /** One player's standing; {@code score} is the score board's number (0 on the live boards). */
    public record Entry(UUID player, String name, int level, long expIntoLevel, long coins, long score) {
        public Entry(UUID player, String name, int level, long expIntoLevel, long coins) {
            this(player, name, level, expIntoLevel, coins, 0);
        }
    }

    public Comparator<Entry> order() {
        Comparator<Entry> byName = Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER);
        return switch (this) {
            case LEVEL -> Comparator.comparingInt(Entry::level).reversed()
                    .thenComparing(Comparator.comparingLong(Entry::expIntoLevel).reversed()).thenComparing(byName);
            case COINS -> Comparator.comparingLong(Entry::coins).reversed().thenComparing(byName);
            default -> Comparator.comparingLong(Entry::score).reversed()
                    .thenComparing(Comparator.comparingInt(Entry::level).reversed()).thenComparing(byName);
        };
    }

    /**
     * The top {@code limit} of {@code rows}; when a player appears more than once the <em>last</em> row wins, so pass
     * stored rows first and live (online) rows after them. A score board drops rows that scored nothing.
     */
    public List<Entry> rank(Collection<Entry> rows, int limit) {
        Map<UUID, Entry> latest = new LinkedHashMap<>();
        for (Entry e : rows) if (live || e.score() > 0) latest.put(e.player(), e);
        List<Entry> out = new ArrayList<>(latest.values());
        out.sort(order());
        return out.size() > limit ? List.copyOf(out.subList(0, limit)) : List.copyOf(out);
    }
}
