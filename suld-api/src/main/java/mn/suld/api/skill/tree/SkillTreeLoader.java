package mn.suld.api.skill.tree;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.json.Json;
import mn.suld.api.skill.Spell;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the skill data files ({@code <class>.json} plus the shared {@code universal.json}) and validates every
 * field. A problem is reported with the exact file, field path and reason; nothing is silently skipped, and a
 * class with any problem gets no tree (the server keeps the previous one on a reload).
 */
public final class SkillTreeLoader {

    private SkillTreeLoader() {
    }

    public static final int FORMAT_VERSION = 1;
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]{0,23}");

    public record Issue(String file, String path, String message) {
        @Override
        public String toString() {
            return file + ": " + path + ": " + message;
        }
    }

    /** Where data files come from (the jar, or the server's data folder). */
    public interface Source {
        /** The file's text; throws {@link NoSuchFileException} if there is none. */
        String read(String name) throws IOException;
    }

    public static Source classpath() {
        return name -> {
            try (InputStream in = SkillTreeLoader.class.getResourceAsStream("/skills/" + name)) {
                if (in == null) throw new NoSuchFileException("/skills/" + name);
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        };
    }

    public static Source directory(Path dir) {
        return name -> Files.readString(dir.resolve(name), StandardCharsets.UTF_8);
    }

    public record Result(Map<PlayerClass, SkillTree> trees, List<Issue> issues) {
        public boolean ok() { return issues.isEmpty(); }
    }

    public static String fileOf(PlayerClass c) { return c.id() + ".json"; }

    public static Result loadAll(Source source) {
        Map<PlayerClass, SkillTree> trees = new EnumMap<>(PlayerClass.class);
        List<Issue> issues = new ArrayList<>();
        Parsed universal = parseFile(source, "universal.json", null, issues);
        for (PlayerClass c : PlayerClass.values()) {
            List<Issue> mine = new ArrayList<>();
            Parsed own = parseFile(source, fileOf(c), c, mine);
            if (own != null && universal != null && mine.isEmpty()) {
                try {
                    trees.put(c, build(c, own, universal));
                } catch (IllegalArgumentException e) {
                    mine.add(new Issue(fileOf(c), "tree", e.getMessage()));
                }
            }
            issues.addAll(mine);
        }
        return new Result(trees, issues);
    }

    // ------------------------------------------------------------------ parsing

    private record Parsed(List<RawNode> nodes, List<String[]> edges, List<String[]> exclusive) {
    }

    private record RawNode(String id, String name, String description, SkillCategory category, String icon, int x, int y,
                           int cost, int maxRank, int level, List<SkillNode.Req> requires, List<Effect> effects,
                           List<String> tags, boolean keystone, boolean capstone, boolean hidden, boolean secret, int version) {
    }

    private static SkillTree build(PlayerClass c, Parsed own, Parsed universal) {
        List<SkillNode> nodes = new ArrayList<>();
        List<String[]> edges = new ArrayList<>(own.edges());
        edges.addAll(universal.edges());
        for (Parsed p : List.of(own, universal)) {
            for (RawNode r : p.nodes()) {
                nodes.add(new SkillNode(nodes.size(), r.id(), r.name(), r.description(), r.category(), r.icon(), r.x(), r.y(),
                        r.cost(), r.maxRank(), r.level(), r.requires(), r.effects(), r.tags(), r.keystone(), r.capstone(),
                        r.hidden(), r.secret(), r.version()));
            }
        }
        List<String[]> ex = new ArrayList<>(own.exclusive());
        ex.addAll(universal.exclusive());
        return new SkillTree(c, nodes, edges, ex);
    }

    private static Parsed parseFile(Source source, String file, PlayerClass clazz, List<Issue> issues) {
        String text;
        try {
            text = source.read(file);
        } catch (IOException e) {
            issues.add(new Issue(file, "file", "cannot read: " + e.getMessage()));
            return null;
        }
        Map<String, Object> root;
        try {
            root = Json.object(Json.parse(text));
        } catch (RuntimeException e) {
            issues.add(new Issue(file, "$", "not valid JSON: " + e.getMessage()));
            return null;
        }
        int before = issues.size();
        Object ver = root.get("version");
        if (!(ver instanceof Number n) || n.intValue() != FORMAT_VERSION) {
            issues.add(new Issue(file, "version", "must be " + FORMAT_VERSION));
        }
        String expected = clazz == null ? "universal" : clazz.id();
        if (!expected.equals(root.get("class"))) issues.add(new Issue(file, "class", "must be \"" + expected + "\""));
        List<RawNode> nodes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        if (!(root.get("nodes") instanceof List<?> list) || list.isEmpty()) {
            issues.add(new Issue(file, "nodes", "must be a non-empty array"));
        } else {
            for (int i = 0; i < list.size(); i++) {
                RawNode rn = parseNode(file, "nodes[" + i + "]", list.get(i), clazz, issues);
                if (rn != null && !ids.add(rn.id())) {
                    issues.add(new Issue(file, "nodes[" + i + "].id", "duplicate id '" + rn.id() + "'"));
                }
                if (rn != null) nodes.add(rn);
            }
        }
        List<String[]> edges = pairs(file, "edges", root.get("edges"), clazz != null, issues);
        List<String[]> exclusive = pairs(file, "exclusive", root.get("exclusive"), false, issues);
        return issues.size() > before ? null : new Parsed(nodes, edges, exclusive);
    }

    private static List<String[]> pairs(String file, String field, Object value, boolean required, List<Issue> issues) {
        List<String[]> out = new ArrayList<>();
        if (value == null) {
            if (required) issues.add(new Issue(file, field, "missing"));
            return out;
        }
        if (!(value instanceof List<?> l)) {
            issues.add(new Issue(file, field, "must be an array of [id, id] pairs"));
            return out;
        }
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i) instanceof List<?> p && p.size() == 2 && p.get(0) instanceof String a && p.get(1) instanceof String b) {
                out.add(new String[]{a, b});
            } else {
                issues.add(new Issue(file, field + "[" + i + "]", "must be [\"id\", \"id\"]"));
            }
        }
        return out;
    }

    private static RawNode parseNode(String file, String at, Object o, PlayerClass clazz, List<Issue> issues) {
        if (!(o instanceof Map<?, ?> raw)) {
            issues.add(new Issue(file, at, "must be an object"));
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) raw;
        int before = issues.size();
        String id = str(file, at, m, "id", true, issues);
        if (id != null && !ID.matcher(id).matches()) issues.add(new Issue(file, at + ".id", "'" + id + "' must match [a-z][a-z0-9_]{0,23}"));
        String name = str(file, at, m, "name", true, issues);
        if (name != null && (name.isBlank() || name.length() > 32)) issues.add(new Issue(file, at + ".name", "must be 1-32 characters"));
        String description = str(file, at, m, "description", false, issues);
        String icon = str(file, at, m, "icon", true, issues);
        if (icon != null && !icon.matches("[A-Z][A-Z0-9_]{1,63}")) issues.add(new Issue(file, at + ".icon", "'" + icon + "' is not an item id like IRON_SWORD"));
        SkillCategory category = null;
        String cat = str(file, at, m, "category", true, issues);
        if (cat != null) category = enumOf(SkillCategory.class, cat, file, at + ".category", issues);
        int x = intIn(file, at, m, "x", 0, 15, true, 0, issues);
        int y = intIn(file, at, m, "y", 0, 15, true, 0, issues);
        boolean root = "root".equals(id);
        int cost = intIn(file, at, m, "cost", root ? 0 : 1, 10, true, 0, issues);
        int maxRank = intIn(file, at, m, "maxRank", 1, 5, false, 1, issues);
        int level = intIn(file, at, m, "level", 1, 100, false, 1, issues);
        List<SkillNode.Req> requires = new ArrayList<>();
        if (m.get("requires") != null) {
            if (m.get("requires") instanceof List<?> l) {
                for (int i = 0; i < l.size(); i++) {
                    if (l.get(i) instanceof Map<?, ?> rm && rm.get("node") instanceof String rn && rm.get("rank") instanceof Number rk) {
                        requires.add(new SkillNode.Req(rn, rk.intValue()));
                    } else {
                        issues.add(new Issue(file, at + ".requires[" + i + "]", "must be {\"node\": id, \"rank\": n}"));
                    }
                }
            } else {
                issues.add(new Issue(file, at + ".requires", "must be an array"));
            }
        }
        List<Effect> effects = new ArrayList<>();
        if (m.get("effects") == null) {
            if (!root) issues.add(new Issue(file, at + ".effects", "missing"));
        } else if (m.get("effects") instanceof List<?> l) {
            if (l.isEmpty() && !root) issues.add(new Issue(file, at + ".effects", "a node needs at least one effect"));
            for (int i = 0; i < l.size(); i++) {
                Effect e = parseEffect(file, at + ".effects[" + i + "]", l.get(i), clazz, issues);
                if (e != null) effects.add(e);
            }
        } else {
            issues.add(new Issue(file, at + ".effects", "must be an array"));
        }
        List<String> tags = new ArrayList<>();
        if (m.get("tags") instanceof List<?> l) for (Object t : l) tags.add(String.valueOf(t));
        boolean keystone = bool(m, "keystone"), capstone = bool(m, "capstone"), hidden = bool(m, "hidden"), secret = bool(m, "secret");
        if (keystone && effects.stream().noneMatch(e -> e instanceof Effect.Keystone)) issues.add(new Issue(file, at + ".keystone", "a keystone node needs a keystone effect"));
        if (effects.stream().anyMatch(e -> e instanceof Effect.Keystone) && !keystone) issues.add(new Issue(file, at + ".keystone", "a node with a keystone effect must set keystone: true"));
        if (capstone && effects.stream().noneMatch(e -> e instanceof Effect.UnlockUltimate)) issues.add(new Issue(file, at + ".capstone", "a capstone node needs an ultimate effect"));
        int version = intIn(file, at, m, "nodeVersion", 1, 1000, false, 1, issues);
        if (issues.size() > before) return null;
        return new RawNode(id, name, description, category, icon, x, y, cost, maxRank, level, requires, effects, tags, keystone, capstone, hidden, secret, version);
    }

    private static Effect parseEffect(String file, String at, Object o, PlayerClass clazz, List<Issue> issues) {
        if (!(o instanceof Map<?, ?> raw)) {
            issues.add(new Issue(file, at, "must be an object"));
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) raw;
        int before = issues.size();
        String type = str(file, at, m, "type", true, issues);
        if (type == null) return null;
        Effect result = null;
        switch (type) {
            case "stat" -> {
                StatKey k = enumOf(StatKey.class, str(file, at, m, "key", true, issues), file, at + ".key", issues);
                double v = num(file, at, m, "value", -1000, 1000, issues);
                if (k != null && issues.size() == before) result = new Effect.Stat(k, v);
            }
            case "mod" -> {
                Spell s = enumOf(Spell.class, str(file, at, m, "spell", true, issues), file, at + ".spell", issues);
                ModKey k = enumOf(ModKey.class, str(file, at, m, "key", true, issues), file, at + ".key", issues);
                double v = num(file, at, m, "value", -1000, 1000, issues);
                if (s != null && k != null && issues.size() == before) {
                    if (clazz != null && s.clazz() != clazz) issues.add(new Issue(file, at + ".spell", s + " belongs to " + s.clazz().id() + ", not " + clazz.id()));
                    else if (!SpellMods.supports(s, k)) issues.add(new Issue(file, at + ".key", s + " does not support " + k));
                    else result = new Effect.SpellMod(s, k, v);
                }
            }
            case "proc" -> {
                TriggerEvent ev = enumOf(TriggerEvent.class, str(file, at, m, "event", true, issues), file, at + ".event", issues);
                ProcKind k = enumOf(ProcKind.class, str(file, at, m, "kind", true, issues), file, at + ".kind", issues);
                double chance = num(file, at, m, "chance", 1, 100, issues);
                double a = m.containsKey("a") ? num(file, at, m, "a", -1000, 1000, issues) : 0;
                double b = m.containsKey("b") ? num(file, at, m, "b", -1000, 1000, issues) : 0;
                double cd = m.containsKey("cooldown") ? num(file, at, m, "cooldown", 0, 3600, issues) : 0;
                if (ev != null && k != null && issues.size() == before) result = new Effect.Proc(ev, chance, k, a, b, cd);
            }
            case "ultimate" -> {
                Ultimate u = enumOf(Ultimate.class, str(file, at, m, "ultimate", true, issues), file, at + ".ultimate", issues);
                if (u != null && issues.size() == before) {
                    if (clazz != null && u.clazz() != clazz) issues.add(new Issue(file, at + ".ultimate", u + " belongs to " + u.clazz().id()));
                    else result = new Effect.UnlockUltimate(u);
                }
            }
            case "keystone" -> {
                KeystoneKind k = enumOf(KeystoneKind.class, str(file, at, m, "kind", true, issues), file, at + ".kind", issues);
                if (k != null && issues.size() == before) {
                    if (clazz != null && k.clazz() != clazz) issues.add(new Issue(file, at + ".kind", k + " belongs to " + k.clazz().id()));
                    else result = new Effect.Keystone(k);
                }
            }
            default -> issues.add(new Issue(file, at + ".type", "unknown effect type '" + type + "' (stat, mod, proc, ultimate, keystone)"));
        }
        return result;
    }

    // ------------------------------------------------------------------ field helpers

    private static String str(String file, String at, Map<String, Object> m, String key, boolean required, List<Issue> issues) {
        Object v = m.get(key);
        if (v == null) {
            if (required) issues.add(new Issue(file, at + "." + key, "missing"));
            return null;
        }
        if (!(v instanceof String s)) {
            issues.add(new Issue(file, at + "." + key, "must be text"));
            return null;
        }
        return s;
    }

    private static int intIn(String file, String at, Map<String, Object> m, String key, int min, int max, boolean required,
                             int dflt, List<Issue> issues) {
        Object v = m.get(key);
        if (v == null) {
            if (required) issues.add(new Issue(file, at + "." + key, "missing"));
            return dflt;
        }
        if (!(v instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())) {
            issues.add(new Issue(file, at + "." + key, "must be a whole number"));
            return dflt;
        }
        int i = n.intValue();
        if (i < min || i > max) issues.add(new Issue(file, at + "." + key, i + " is outside " + min + ".." + max));
        return i;
    }

    private static double num(String file, String at, Map<String, Object> m, String key, double min, double max, List<Issue> issues) {
        Object v = m.get(key);
        if (!(v instanceof Number n)) {
            issues.add(new Issue(file, at + "." + key, v == null ? "missing" : "must be a number"));
            return 0;
        }
        double d = n.doubleValue();
        if (Double.isNaN(d) || d < min || d > max) issues.add(new Issue(file, at + "." + key, d + " is outside " + min + ".." + max));
        return d;
    }

    private static boolean bool(Map<String, Object> m, String key) {
        return Boolean.TRUE.equals(m.get(key));
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String value, String file, String path, List<Issue> issues) {
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            StringBuilder sb = new StringBuilder();
            for (E c : type.getEnumConstants()) sb.append(sb.length() == 0 ? "" : ", ").append(c.name());
            issues.add(new Issue(file, path, "unknown value '" + value + "' (one of " + sb + ")"));
            return null;
        }
    }
}
