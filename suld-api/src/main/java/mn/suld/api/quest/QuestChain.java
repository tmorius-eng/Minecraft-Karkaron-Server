package mn.suld.api.quest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An ordered storyline: completing chapter {@code i} starts chapter {@code i + 1}. The player's single
 * {@link QuestState} is enough to know where they are — every chapter before the active one is done.
 */
public final class QuestChain {

    private final List<QuestDefinition> chapters;
    private final Map<String, Integer> index = new HashMap<>();

    public QuestChain(List<QuestDefinition> chapters) {
        if (chapters.isEmpty()) throw new IllegalArgumentException("a chain needs at least one chapter");
        this.chapters = List.copyOf(chapters);
        for (int i = 0; i < this.chapters.size(); i++) {
            if (index.put(this.chapters.get(i).id(), i) != null) {
                throw new IllegalArgumentException("duplicate quest id: " + this.chapters.get(i).id());
            }
        }
    }

    public List<QuestDefinition> chapters() {
        return chapters;
    }

    public int size() {
        return chapters.size();
    }

    public QuestDefinition first() {
        return chapters.get(0);
    }

    public Optional<QuestDefinition> byId(String id) {
        Integer i = index.get(id);
        return i == null ? Optional.empty() : Optional.of(chapters.get(i));
    }

    /** Zero-based chapter number, or -1 if the id is not in this chain. */
    public int indexOf(String id) {
        return index.getOrDefault(id, -1);
    }

    /** The chapter after {@code id}; empty if {@code id} is the last chapter or unknown. */
    public Optional<QuestDefinition> next(String id) {
        int i = indexOf(id);
        return i < 0 || i + 1 >= chapters.size() ? Optional.empty() : Optional.of(chapters.get(i + 1));
    }

    /**
     * Where a player stands, normalised: no quest yet → the first chapter; a completed chapter that has a successor →
     * the successor (catch-up for players who finished a chapter before it got one); an unknown id → unchanged.
     */
    public QuestState normalise(QuestState s) {
        Objects.requireNonNull(s, "state");
        if (s.questId().isEmpty()) return s.completed() ? s : first().initialState();
        if (!s.completed()) return s;
        return next(s.questId()).map(QuestDefinition::initialState).orElse(s);
    }

    /** Number of chapters finished in this state. */
    public int completedCount(QuestState s) {
        int i = indexOf(s.questId());
        if (i < 0) return 0;
        return s.completed() ? i + 1 : i;
    }
}
