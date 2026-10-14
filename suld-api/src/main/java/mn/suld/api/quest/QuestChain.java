package mn.suld.api.quest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * An ordered storyline: completing chapter {@code i} starts chapter {@code i + 1}. The player's single
 * {@link QuestState} is enough to know where they are — every chapter before the active one is done.
 */
public final class QuestChain {

    private final List<QuestDefinition> chapters;
    private final Map<String, Integer> index = new HashMap<>();

    /** Former chapter ids -> their current id (a chapter renamed in quests.json keeps its players where they were). */
    private final Map<String, String> aliases;

    public QuestChain(List<QuestDefinition> chapters) {
        this(chapters, Map.of());
    }

    public QuestChain(List<QuestDefinition> chapters, Map<String, String> aliases) {
        if (chapters.isEmpty()) throw new IllegalArgumentException("a chain needs at least one chapter");
        this.chapters = List.copyOf(chapters);
        for (int i = 0; i < this.chapters.size(); i++) {
            if (index.put(this.chapters.get(i).id(), i) != null) {
                throw new IllegalArgumentException("duplicate quest id: " + this.chapters.get(i).id());
            }
        }
        this.aliases = Map.copyOf(aliases);
        for (Map.Entry<String, String> a : this.aliases.entrySet()) {
            if (index.containsKey(a.getKey())) throw new IllegalArgumentException("alias " + a.getKey() + " is a current chapter id");
            if (!index.containsKey(a.getValue())) throw new IllegalArgumentException("alias " + a.getKey() + " -> unknown chapter " + a.getValue());
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
     * the successor (catch-up for players who finished a chapter before it got one); a former id → its current one.
     * An unknown non-alias id is preserved when no progression hint is available; use
     * {@link #normalise(QuestState, Predicate)} to recover it safely.
     */
    public QuestState normalise(QuestState s) {
        return normalise(s, null);
    }

    /**
     * QS-03 (audit 2026-10-14): normalises a removed/unknown chapter id without replaying chapter rewards. With a
     * {@code reached} predicate, resumes at the first chapter not yet reached; if all chapters are reached, returns
     * the final chapter as completed. Without that predicate, the unknown state is preserved because this method does
     * not have enough progression context to safely choose a replacement. Former ids are migrated before recovery.
     *
     * @param reached predicate identifying chapters already behind the player; null preserves an unknown state
     */
    public QuestState normalise(QuestState s, Predicate<QuestDefinition> reached) {
        Objects.requireNonNull(s, "state");
        if (s.questId().isEmpty()) return s.completed() ? s : first().initialState();
        String alias = aliases.get(s.questId());
        if (alias != null) return normalise(new QuestState(alias, s.progress(), s.completed()), reached);
        if (indexOf(s.questId()) < 0) {
            if (reached == null) return s;
            for (QuestDefinition d : chapters) if (!reached.test(d)) return d.initialState();
            QuestDefinition last = chapters.get(chapters.size() - 1);
            return new QuestState(last.id(), last.requiredCount(), true); // past every chapter: the story is done
        }
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
