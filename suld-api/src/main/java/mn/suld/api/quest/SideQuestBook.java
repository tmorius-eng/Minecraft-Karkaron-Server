package mn.suld.api.quest;

import mn.suld.api.clazz.PlayerClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The side quests of the content (docs/SIDE_QUESTS.md) and the rules for taking, advancing and handing them in. Pure:
 * the plugin's SideQuestService keeps the players' records and pays the rewards around it.
 */
public final class SideQuestBook {

    /** How many side quests a player may carry at once. */
    public static final int MAX_ACTIVE = 3;

    /** Why a player may (OPEN) or may not take a side quest now, checked in this order. */
    public enum Gate { OPEN, DONE, ACTIVE, LEVEL, CLASS, REQUIRES_CHAPTER, REQUIRES_QUEST, LIMIT }

    private final Map<String, SideQuestDefinition> byId = new LinkedHashMap<>();

    public SideQuestBook(List<SideQuestDefinition> quests) {
        for (SideQuestDefinition d : quests == null ? List.<SideQuestDefinition>of() : quests) {
            if (byId.putIfAbsent(d.id(), d) != null) throw new IllegalArgumentException("duplicate side quest " + d.id());
        }
    }

    /** In file order. */
    public List<SideQuestDefinition> all() {
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    public Optional<SideQuestDefinition> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public int size() {
        return byId.size();
    }

    /** The side quests this NPC gives (and takes back), in file order. */
    public List<SideQuestDefinition> offeredBy(String npcId) {
        List<SideQuestDefinition> out = new ArrayList<>();
        for (SideQuestDefinition d : byId.values()) if (d.giverNpcId().equals(npcId)) out.add(d);
        return out;
    }

    /**
     * Whether a player may take {@code d} now.
     *
     * @param chapterDone whether a story chapter ({@code quest.*}) is finished
     * @param mine        the player's records by quest id
     */
    public Gate gate(SideQuestDefinition d, int level, PlayerClass cls, Predicate<String> chapterDone, Map<String, SideQuestRecord> mine) {
        SideQuestRecord r = mine.get(d.id());
        if (r != null && r.done()) return Gate.DONE;
        if (r != null && r.active()) return Gate.ACTIVE;
        if (level < d.minLevel()) return Gate.LEVEL;
        if (!d.openTo(cls)) return Gate.CLASS;
        String unmet = firstUnmet(d, chapterDone, mine);
        if (unmet != null) return unmet.startsWith("squest.") ? Gate.REQUIRES_QUEST : Gate.REQUIRES_CHAPTER;
        if (activeCount(mine) >= MAX_ACTIVE) return Gate.LIMIT;
        return Gate.OPEN;
    }

    /** The first requirement still unmet, in the file's order (a chapter not finished, a side quest not handed in), or null. */
    public static String firstUnmet(SideQuestDefinition d, Predicate<String> chapterDone, Map<String, SideQuestRecord> mine) {
        for (String req : d.requires()) {
            if (req.startsWith("squest.")) {
                SideQuestRecord q = mine.get(req);
                if (q == null || !q.done()) return req;
            } else if (!chapterDone.test(req)) {
                return req;
            }
        }
        return null;
    }

    public static int activeCount(Map<String, SideQuestRecord> mine) {
        int n = 0;
        for (SideQuestRecord r : mine.values()) if (r.active()) n++;
        return n;
    }

    /**
     * The Mongolian line for a refusal (null for OPEN); {@code chapterTitle} names a story chapter by its id, and a
     * requirement refusal names the first requirement still unmet ({@code chapterDone}, {@code mine} as for gate).
     */
    public String refusal(Gate g, SideQuestDefinition d, java.util.function.Function<String, String> chapterTitle,
                          Predicate<String> chapterDone, Map<String, SideQuestRecord> mine) {
        return switch (g) {
            case OPEN -> null;
            case DONE -> "Энэ эрлийг аль хэдийн дуусгасан.";
            case ACTIVE -> "Энэ эрэл аль хэдийн таны дэвтэрт байна.";
            case LEVEL -> d.minLevel() + "-р түвшинд хүрээд ав.";
            case CLASS -> "Энэ эрэл " + classNames(d) + " ангид зориулагдсан.";
            case REQUIRES_CHAPTER, REQUIRES_QUEST -> requirementLine(firstUnmet(d, chapterDone, mine), chapterTitle);
            case LIMIT -> "Нэг дор " + MAX_ACTIVE + "-аас олон нэмэлт эрэл авч болохгүй — нэгийг нь дуусга эсвэл орхи.";
        };
    }

    /** The line for an unmet requirement, worded by what it is (a side quest of this book, or a story chapter). */
    private String requirementLine(String req, java.util.function.Function<String, String> chapterTitle) {
        if (req == null) return "Шаардлага нь биелсэн байна — дахин оролдоно уу.";
        if (req.startsWith("squest.")) return "Эхлээд «" + byId(req).map(SideQuestDefinition::title).orElse(req) + "» эрлийг дуусга.";
        return "Эхлээд түүхийн бүлэг дуусга: «" + chapterTitle.apply(req) + "».";
    }

    private static String classNames(SideQuestDefinition d) {
        List<String> names = new ArrayList<>();
        for (PlayerClass c : PlayerClass.values()) if (d.classes().contains(c)) names.add(c.displayName());
        return String.join(", ", names);
    }

    /**
     * An objective event on one record. KILL and CRAFT add up, COLLECT is what the bag holds now (it can fall),
     * the rest (a place, a dungeon, a clue, an ovoo) are met at once. Never past the count; a DONE record never moves.
     */
    public static SideQuestRecord advance(SideQuestRecord r, SideQuestDefinition d, QuestType type, String target, long value) {
        if (r == null || d == null || !r.active() || !d.id().equals(r.questId()) || d.type() != type || !d.targetId().equals(target)) return r;
        int need = d.requiredCount();
        int p = switch (type) {
            case KILL_MOB, CRAFT_ITEM -> (int) Math.min(need, (long) r.progress() + Math.max(0, value));
            case COLLECT_ITEM -> (int) Math.max(0, Math.min(need, value));
            default -> need;
        };
        return p == r.progress() ? r : r.withProgress(p);
    }
}
