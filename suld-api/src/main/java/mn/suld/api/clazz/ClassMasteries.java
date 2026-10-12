package mn.suld.api.clazz;

import mn.suld.api.classgear.ClassGear;
import mn.suld.api.json.Json;
import mn.suld.api.skill.tree.SkillState;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Each class's own progress while another class is played (docs/CLASS_CHANGE.md): its skill tree (allocation, saved
 * builds) and its class gear (armour level, tier, enhancement, armour mastery, the identity of its soulbound pieces and
 * weapon). The class being played keeps its progress in the profile itself ({@code skillState}, {@code classGear});
 * this holds only the others. Immutable; stored as one JSON document ({@code suld_profiles.class_mastery}, V22).
 */
public record ClassMasteries(Map<PlayerClass, Saved> saved, Trial trial) {

    public static final ClassMasteries NONE = new ClassMasteries(Map.of(), null);
    static final int VERSION = 1;

    /** One class's stored progress. */
    public record Saved(SkillState skills, ClassGear gear, long lastPlayedAt) {
        public Saved {
            skills = skills == null ? SkillState.NONE : skills;
            gear = gear == null ? ClassGear.NONE : gear;
        }
    }

    /**
     * A class trial in progress (docs/CLASS_CHANGE.md «Сорилт»): the class the player really plays, the trial class's
     * stored progress before the trial (null = never played), the cooldown stamp, and the coins and level at the
     * start, which the end never lets grow. Stored with the profile, so a crash or a disconnect ends it on the next
     * join instead of leaving the player in the trial class.
     */
    public record Trial(PlayerClass from, PlayerClass to, Saved toBefore, long lastChangeBefore, long startedAt,
                        long coinsAtStart, int levelAtStart, long expAtStart) {
    }

    public ClassMasteries(Map<PlayerClass, Saved> saved) {
        this(saved, null);
    }

    public ClassMasteries {
        EnumMap<PlayerClass, Saved> m = new EnumMap<>(PlayerClass.class);
        if (saved != null) m.putAll(saved);
        saved = java.util.Collections.unmodifiableMap(m);
    }

    public Optional<Saved> of(PlayerClass c) {
        return Optional.ofNullable(saved.get(c));
    }

    /** {@code c}'s progress stored (replacing an older one). */
    public ClassMasteries with(PlayerClass c, Saved s) {
        EnumMap<PlayerClass, Saved> m = new EnumMap<>(PlayerClass.class);
        m.putAll(saved);
        m.put(c, s);
        return new ClassMasteries(m, trial);
    }

    /** {@code c} taken out (it is the class being played again). */
    public ClassMasteries without(PlayerClass c) {
        if (!saved.containsKey(c)) return this;
        EnumMap<PlayerClass, Saved> m = new EnumMap<>(PlayerClass.class);
        m.putAll(saved);
        m.remove(c);
        return new ClassMasteries(m, trial);
    }

    public ClassMasteries withTrial(Trial t) {
        return new ClassMasteries(saved, t);
    }

    public boolean isEmpty() {
        return saved.isEmpty() && trial == null;
    }

    /** The stored document, or null when empty (NULL column). */
    public String toJson() {
        if (isEmpty()) return null;
        Map<String, Object> classes = new LinkedHashMap<>();
        for (Map.Entry<PlayerClass, Saved> e : saved.entrySet()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("skills", e.getValue().skills().toJson());
            o.put("gear", e.getValue().gear().toJson());
            o.put("at", e.getValue().lastPlayedAt());
            classes.put(e.getKey().id(), o);
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("v", VERSION);
        doc.put("classes", classes);
        if (trial != null) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("from", trial.from().id());
            t.put("to", trial.to().id());
            if (trial.toBefore() != null) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("skills", trial.toBefore().skills().toJson());
                o.put("gear", trial.toBefore().gear().toJson());
                o.put("at", trial.toBefore().lastPlayedAt());
                t.put("toBefore", o);
            }
            t.put("lastChange", trial.lastChangeBefore());
            t.put("at", trial.startedAt());
            t.put("coins", trial.coinsAtStart());
            t.put("level", trial.levelAtStart());
            t.put("exp", trial.expAtStart());
            doc.put("trial", t);
        }
        return Json.write(doc);
    }

    private static Saved saved(Map<String, Object> o) {
        return new Saved(SkillState.fromJson(o.get("skills") == null ? null : String.valueOf(o.get("skills"))),
                ClassGear.fromJson(o.get("gear") == null ? null : String.valueOf(o.get("gear"))), num(o.get("at")));
    }

    private static PlayerClass clazz(Object id) {
        return PlayerClass.byId(String.valueOf(id)).orElseThrow(() -> new IllegalArgumentException("unknown class " + id));
    }

    private static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    /** Read a stored document; anything unreadable (or newer) throws, so a load never wipes a class's progress. */
    public static ClassMasteries fromJson(String text) {
        if (text == null || text.isBlank() || text.equals("{}")) return NONE;
        try {
            Map<String, Object> doc = Json.object(Json.parse(text));
            int v = doc.containsKey("v") ? Json.integer(doc.get("v")) : 1;
            if (v > VERSION) throw new IllegalArgumentException("class mastery version " + v + " is newer than " + VERSION);
            EnumMap<PlayerClass, Saved> m = new EnumMap<>(PlayerClass.class);
            Object classes = doc.get("classes");
            if (classes != null) {
                for (Map.Entry<String, Object> e : Json.object(classes).entrySet()) {
                    PlayerClass c = PlayerClass.byId(e.getKey())
                            .orElseThrow(() -> new IllegalArgumentException("unknown class " + e.getKey()));
                    m.put(c, saved(Json.object(e.getValue())));
                }
            }
            Trial trial = null;
            if (doc.get("trial") != null) {
                Map<String, Object> t = Json.object(doc.get("trial"));
                trial = new Trial(clazz(t.get("from")), clazz(t.get("to")),
                        t.get("toBefore") == null ? null : saved(Json.object(t.get("toBefore"))), num(t.get("lastChange")),
                        num(t.get("at")), num(t.get("coins")), (int) num(t.get("level")), num(t.get("exp")));
            }
            return new ClassMasteries(m, trial);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("unreadable class mastery: " + e.getMessage(), e);
        }
    }
}
