package mn.suld.api.leaderboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server leaderboards (/top). Pure ranking; storage supplies {@link Entry} rows, online players fresher ones. */
public enum Leaderboard {
    LEVEL("Түвшин"),
    COINS("Зоос");

    private final String displayName;

    Leaderboard(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** One player's standing. */
    public record Entry(UUID player, String name, int level, long expIntoLevel, long coins) {
    }

    public Comparator<Entry> order() {
        Comparator<Entry> byName = Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER);
        return switch (this) {
            case LEVEL -> Comparator.comparingInt(Entry::level).reversed()
                    .thenComparing(Comparator.comparingLong(Entry::expIntoLevel).reversed()).thenComparing(byName);
            case COINS -> Comparator.comparingLong(Entry::coins).reversed().thenComparing(byName);
        };
    }

    /**
     * The top {@code limit} of {@code rows}; when a player appears more than once the <em>last</em> row wins, so pass
     * stored rows first and live (online) rows after them.
     */
    public List<Entry> rank(Collection<Entry> rows, int limit) {
        Map<UUID, Entry> latest = new LinkedHashMap<>();
        for (Entry e : rows) latest.put(e.player(), e);
        List<Entry> out = new ArrayList<>(latest.values());
        out.sort(order());
        return out.size() > limit ? List.copyOf(out.subList(0, limit)) : List.copyOf(out);
    }
}
