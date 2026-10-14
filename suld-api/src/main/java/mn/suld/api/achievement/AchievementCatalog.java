package mn.suld.api.achievement;

import mn.suld.api.json.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The achievements ({@code /achievements/achievements.json} in the jar, docs/ACHIEVEMENTS_AND_PASS.md). Every field is
 * checked; a file with any problem is refused with the problems listed. The plugin checks the references to its
 * content (chapters, dungeons, sites, mobs) and to the cosmetics.
 */
public final class AchievementCatalog {

    public static final String RESOURCE = "/achievements/achievements.json";
    private static final Pattern ID = Pattern.compile("ach\\.[a-z]+\\.[a-z0-9_]+");

    private final List<Achievement> all;
    private final Map<String, Achievement> byId;
    private final Map<Deed, List<Achievement>> byDeed;

    private AchievementCatalog(List<Achievement> all) {
        this.all = List.copyOf(all);
        Map<String, Achievement> ids = new LinkedHashMap<>();
        Map<Deed, List<Achievement>> deeds = new EnumMap<>(Deed.class);
        for (Achievement a : all) {
            ids.put(a.id(), a);
            deeds.computeIfAbsent(a.criterion().deed(), d -> new ArrayList<>()).add(a);
        }
        this.byId = Map.copyOf(ids);
        deeds.replaceAll((d, l) -> List.copyOf(l));
        this.byDeed = deeds;
    }

    /** A catalog's file problems: where and why. */
    public record Issue(String where, String reason) {
        @Override
        public String toString() {
            return where + ": " + reason;
        }
    }

    public record Result(AchievementCatalog catalog, List<Issue> issues) {
        public boolean ok() {
            return issues.isEmpty();
        }
    }

    public List<Achievement> all() {
        return all;
    }

    public java.util.Optional<Achievement> byId(String id) {
        return java.util.Optional.ofNullable(byId.get(id));
    }

    /** The achievements a record of {@code deed} may unlock. */
    public List<Achievement> on(Deed deed) {
        return byDeed.getOrDefault(deed, List.of());
    }

    /** Of {@code candidates}, those {@code book} now meets that are not in {@code unlocked}, in the catalog's order. */
    public static List<Achievement> newlyMet(Collection<Achievement> candidates, DeedBook book, Set<String> unlocked) {
        List<Achievement> out = new ArrayList<>();
        for (Achievement a : candidates) if (!unlocked.contains(a.id()) && book.met(a.criterion())) out.add(a);
        return out;
    }

    /** The bundled catalog. */
    public static Result bundled() {
        try (InputStream in = AchievementCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return new Result(new AchievementCatalog(List.of()), List.of(new Issue(RESOURCE, "missing from the jar")));
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new Result(new AchievementCatalog(List.of()), List.of(new Issue(RESOURCE, e.getMessage())));
        }
    }

    public static Result parse(String text) {
        List<Issue> issues = new ArrayList<>();
        List<Achievement> out = new ArrayList<>();
        Object root;
        try {
            root = Json.parse(text);
        } catch (RuntimeException e) {
            return new Result(new AchievementCatalog(List.of()), List.of(new Issue("achievements.json", "not JSON: " + e.getMessage())));
        }
        if (!(root instanceof Map<?, ?> doc) || !(doc.get("achievements") instanceof List<?> list)) {
            return new Result(new AchievementCatalog(List.of()), List.of(new Issue("achievements.json", "needs an \"achievements\" array")));
        }
        Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            String at = "achievements[" + i + "]";
            if (!(list.get(i) instanceof Map<?, ?> m)) {
                issues.add(new Issue(at, "not an object"));
                continue;
            }
            try {
                String id = text(m, "id");
                if (!ID.matcher(id).matches()) throw new IllegalArgumentException("id " + id + " is not ach.<category>.<name>");
                at = id;
                if (!seen.add(id)) throw new IllegalArgumentException("duplicate id");
                Achievement.Category cat = Achievement.Category.valueOf(text(m, "category"));
                if (!id.startsWith("ach." + cat.name().toLowerCase(java.util.Locale.ROOT) + ".")) {
                    throw new IllegalArgumentException("id must start with ach." + cat.name().toLowerCase(java.util.Locale.ROOT) + ".");
                }
                if (!(m.get("criterion") instanceof Map<?, ?> c)) throw new IllegalArgumentException("criterion missing");
                Deed deed = Deed.valueOf(text(c, "deed"));
                Criterion.Measure measure = Criterion.Measure.valueOf(text(c, "measure"));
                List<String> subjects = new ArrayList<>();
                if (c.get("subjects") instanceof List<?> ss) for (Object s : ss) subjects.add(String.valueOf(s));
                if (new java.util.HashSet<>(subjects).size() != subjects.size()) throw new IllegalArgumentException("a subject twice");
                long target = Json.integer(c.get("target"));
                Criterion criterion = new Criterion(deed, subjects, measure, target);
                if (deed.keep() == Deed.Keep.SEEN && measure == Criterion.Measure.TOTAL) {
                    throw new IllegalArgumentException("a seen deed is counted DISTINCT, not TOTAL");
                }
                int points = m.containsKey("points") ? Json.integer(m.get("points")) : 0;
                if (points < 0 || points > 5000) throw new IllegalArgumentException("points " + points);
                Achievement.Basis basis = Achievement.Basis.valueOf(text(m, "basis"));
                String source = m.get("source") instanceof String s ? s : "";
                if (basis != Achievement.Basis.INSPIRED && source.isBlank()) {
                    throw new IllegalArgumentException(basis + " needs the source it is attested in");
                }
                String reward = m.get("reward") instanceof String r && !r.isBlank() ? r : null;
                boolean hidden = Boolean.TRUE.equals(m.get("hidden"));
                out.add(new Achievement(id, text(m, "name"), text(m, "description"), cat, criterion, points, reward, basis, source, hidden));
            } catch (RuntimeException e) {
                issues.add(new Issue(at, e.getMessage()));
            }
        }
        return new Result(new AchievementCatalog(issues.isEmpty() ? out : List.of()), issues);
    }

    private static String text(Map<?, ?> m, String key) {
        if (!(m.get(key) instanceof String s) || s.isBlank()) throw new IllegalArgumentException(key + " missing");
        return s;
    }
}
