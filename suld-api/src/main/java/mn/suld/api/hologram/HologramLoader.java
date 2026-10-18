package mn.suld.api.hologram;

import mn.suld.api.json.Json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads and checks content/holograms.json (docs/CONTENT_DATA.md). Every problem is listed with its path; a file with
 * any problem gives no set, and the plugin then shows the bundled holograms instead.
 */
public final class HologramLoader {

    public static final String FILE = "holograms.json";
    public static final int FORMAT = 1;
    /** The labels the game's code asks for by id: they can be edited, not removed or renamed. */
    public static final List<String> REQUIRED_LABELS = List.of("dungeon.gate", "site.plate", "training.gate", "training.group",
            "training.range", "training.station", "profession.node", "profession.vein", "soul.beacon");
    /** The live values a board can show, and the variants its lines need for each state of them. */
    public static final Map<String, List<String>> SOURCES = Map.of(
            "envoys", List.of("names", "empty"),
            "khuraldai", List.of("ballot", "campaign", "idle"),
            "chronicle", List.of("lines", "empty"));

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z0-9_]+)*");
    private static final Pattern TAG = Pattern.compile("[a-z0-9_]{1,40}");
    private static final Pattern COLOR = Pattern.compile("#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})");

    public record Issue(String path, String message) {
        @Override
        public String toString() {
            return FILE + ": " + (path.isEmpty() ? "" : path + ": ") + message;
        }
    }

    public record Result(HologramSet set, List<Issue> issues) {
        public boolean ok() {
            return set != null && issues.isEmpty();
        }
    }

    private HologramLoader() {
    }

    public static Result load(String json) {
        List<Issue> issues = new ArrayList<>();
        Map<String, Object> root;
        try {
            root = Json.object(Json.parse(json));
        } catch (RuntimeException e) {
            issues.add(new Issue("", "not valid JSON: " + e.getMessage()));
            return new Result(null, issues);
        }
        if (!(root.get("format") instanceof Number n) || n.intValue() != FORMAT) issues.add(new Issue("format", "format must be " + FORMAT));

        List<HologramBoard> boards = new ArrayList<>();
        Map<String, HologramBoard> byId = new HashMap<>();
        if (!(root.get("boards") instanceof List<?> list)) {
            issues.add(new Issue("boards", "a list of boards is required"));
        } else {
            for (int i = 0; i < list.size(); i++) {
                HologramBoard b = board("boards[" + i + "]", list.get(i), issues);
                if (b == null) continue;
                if (byId.putIfAbsent(b.id(), b) != null) {
                    issues.add(new Issue("boards[" + i + "].id", "a second board " + b.id()));
                    continue;
                }
                boards.add(b);
            }
        }
        for (HologramBoard b : boards) {
            if (b.placement() != HologramBoard.Placement.ABOVE) continue;
            if (!byId.containsKey(b.above())) {
                issues.add(new Issue("boards." + b.id() + ".above", "no board " + b.above()));
                continue;
            }
            Set<String> seen = new HashSet<>();
            for (HologramBoard c = b; c != null && c.placement() == HologramBoard.Placement.ABOVE; c = byId.get(c.above())) {
                if (!seen.add(c.id())) {
                    issues.add(new Issue("boards." + b.id() + ".above", "the boards stand on each other in a ring"));
                    break;
                }
            }
        }

        Map<String, HologramLabel> labels = new LinkedHashMap<>();
        if (!(root.get("labels") instanceof Map<?, ?> lm)) {
            issues.add(new Issue("labels", "an object of labels is required"));
        } else {
            for (Map.Entry<?, ?> e : lm.entrySet()) {
                String id = String.valueOf(e.getKey());
                String at = "labels." + id;
                if (!ID.matcher(id).matches()) {
                    issues.add(new Issue(at, "a label id looks like dungeon.gate"));
                    continue;
                }
                HologramLabel l = label(at, id, e.getValue(), issues);
                if (l != null) labels.put(id, l);
            }
        }
        for (String r : REQUIRED_LABELS) {
            if (!labels.containsKey(r)) issues.add(new Issue("labels", "label " + r + " is used by the game and must stay"));
        }
        return new Result(issues.isEmpty() ? new HologramSet(boards, labels) : null, issues);
    }

    private static HologramBoard board(String at, Object o, List<Issue> issues) {
        Map<String, Object> m = object(at, o, issues);
        if (m == null) return null;
        String id = str(m, "id");
        if (id == null || !ID.matcher(id).matches()) {
            issues.add(new Issue(at + ".id", "a board id looks like guide.classes"));
            return null;
        }
        at = "boards." + id;
        String place = str(m, "place");
        HologramBoard.Placement p = switch (place == null ? "offset" : place) {
            case "offset" -> HologramBoard.Placement.OFFSET;
            case "arc" -> HologramBoard.Placement.ARC;
            case "above" -> HologramBoard.Placement.ABOVE;
            default -> null;
        };
        if (p == null) {
            issues.add(new Issue(at + ".place", "offset, arc or above"));
            return null;
        }
        String anchor = str(m, "anchor"), above = str(m, "above");
        double[] off;
        if (p == HologramBoard.Placement.ABOVE) {
            if (above == null) issues.add(new Issue(at + ".above", "the id of the board it stands on"));
            off = new double[]{0, num(at + ".gap", m, "gap", 0.3, 0, 8, issues), 0};
        } else {
            if (anchor == null || anchor.isBlank()) issues.add(new Issue(at + ".anchor", "a city point (spawn, event.plaza …) or city"));
            off = vec(at + ".offset", m.get("offset"), issues);
            if (off == null) return null;
        }
        double radius = num(at + ".radius", m, "radius", 7.5, 1, 32, issues);
        HologramStyle style = style(at, m, issues);
        String source = str(m, "source");
        if (source != null && !SOURCES.containsKey(source)) issues.add(new Issue(at + ".source", "one of " + SOURCES.keySet()));
        Map<String, List<String>> variants = variants(at, m, issues);
        if (source != null && SOURCES.containsKey(source)) {
            for (String v : SOURCES.get(source)) {
                if (!variants.containsKey(v)) issues.add(new Issue(at + ".variants", "a " + source + " board needs the variant " + v));
            }
        } else if (source == null && !variants.containsKey(HologramBoard.TEXT)) {
            issues.add(new Issue(at + ".text", "the board's lines"));
        }
        Map<String, String> items = new LinkedHashMap<>();
        if (m.get("items") != null) {
            Map<String, Object> im = object(at + ".items", m.get("items"), issues);
            if (im != null) {
                for (Map.Entry<String, Object> e : im.entrySet()) {
                    if (e.getValue() instanceof String s && s.indexOf('\n') < 0) items.put(e.getKey(), s);
                    else issues.add(new Issue(at + ".items." + e.getKey(), "one line of text"));
                }
            }
        }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        if (m.get("headers") != null) {
            Map<String, Object> hm = object(at + ".headers", m.get("headers"), issues);
            if (hm != null) {
                for (Map.Entry<String, Object> e : hm.entrySet()) {
                    List<String> l = lines(at + ".headers." + e.getKey(), e.getValue(), issues);
                    if (l != null) headers.put(e.getKey(), l);
                }
            }
        }
        String tag = str(m, "tag");
        if (tag != null && !TAG.matcher(tag).matches()) issues.add(new Issue(at + ".tag", "a tag looks like suld_guide"));
        boolean clear = bool(at + ".clear", m, "clear", p != HologramBoard.Placement.ABOVE, issues);
        boolean ground = bool(at + ".ground", m, "ground", false, issues);
        boolean sparkle = bool(at + ".sparkle", m, "sparkle", false, issues);
        return new HologramBoard(id, anchor, p, off[0], off[1], off[2], radius, above, clear, ground, sparkle, tag, style, source,
                variants, items, headers);
    }

    private static HologramLabel label(String at, String id, Object o, List<Issue> issues) {
        Map<String, Object> m = object(at, o, issues);
        if (m == null) return null;
        double dy = num(at + ".dy", m, "dy", 0, -16, 32, issues);
        HologramStyle style = style(at, m, issues);
        Map<String, List<String>> variants = variants(at, m, issues);
        if (variants.isEmpty()) issues.add(new Issue(at + ".text", "the label's lines"));
        return new HologramLabel(id, dy, style, variants);
    }

    private static HologramStyle style(String at, Map<String, Object> m, List<Issue> issues) {
        HologramStyle d = HologramStyle.DEFAULT;
        HologramStyle.Billboard billboard = d.billboard();
        String bb = str(m, "billboard");
        if (bb != null) {
            try {
                billboard = HologramStyle.Billboard.valueOf(bb.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                issues.add(new Issue(at + ".billboard", "center, vertical, horizontal or fixed"));
            }
        }
        HologramStyle.Align align = d.align();
        String al = str(m, "align");
        if (al != null) {
            try {
                align = HologramStyle.Align.valueOf(al.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                issues.add(new Issue(at + ".align", "center, left or right"));
            }
        }
        int background = d.background();
        Object bg = m.get("background");
        if (bg != null) {
            if ("none".equals(bg)) background = 0;
            else if (bg instanceof String s && COLOR.matcher(s).matches()) background = argb(s);
            else issues.add(new Issue(at + ".background", "#AARRGGBB, #RRGGBB or none"));
        }
        float scale = (float) num(at + ".scale", m, "scale", d.scale(), 0.05, 8, issues);
        int lineWidth = (int) num(at + ".lineWidth", m, "lineWidth", d.lineWidth(), 10, 1000, issues);
        float viewRange = (float) num(at + ".viewRange", m, "viewRange", d.viewRange(), 0.05, 16, issues);
        boolean shadow = bool(at + ".shadow", m, "shadow", d.shadow(), issues);
        return new HologramStyle(billboard, scale, background, lineWidth, align, shadow, viewRange);
    }

    /** {@code "#AARRGGBB"} or {@code "#RRGGBB"} (opaque) as ARGB. */
    static int argb(String s) {
        long v = Long.parseLong(s.substring(1), 16);
        return (int) (s.length() == 7 ? 0xFF000000L | v : v);
    }

    /** {@code text} (the fixed lines) and {@code variants} (lines by variant), as one map. */
    private static Map<String, List<String>> variants(String at, Map<String, Object> m, List<Issue> issues) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (m.get("text") != null) {
            List<String> l = lines(at + ".text", m.get("text"), issues);
            if (l != null) out.put(HologramBoard.TEXT, l);
        }
        if (m.get("variants") != null) {
            Map<String, Object> vm = object(at + ".variants", m.get("variants"), issues);
            if (vm != null) {
                for (Map.Entry<String, Object> e : vm.entrySet()) {
                    List<String> l = lines(at + ".variants." + e.getKey(), e.getValue(), issues);
                    if (l != null) out.put(e.getKey(), l);
                }
            }
        }
        return out;
    }

    private static List<String> lines(String at, Object o, List<Issue> issues) {
        if (!(o instanceof List<?> l) || l.isEmpty()) {
            issues.add(new Issue(at, "a list of lines"));
            return null;
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i) instanceof String s && s.indexOf('\n') < 0) out.add(s);
            else issues.add(new Issue(at + "[" + i + "]", "one line of text (a new line is a new entry)"));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(String at, Object o, List<Issue> issues) {
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        issues.add(new Issue(at, "an object"));
        return null;
    }

    private static String str(Map<String, Object> m, String key) {
        return m.get(key) instanceof String s ? s : null;
    }

    private static double num(String at, Map<String, Object> m, String key, double def, double min, double max, List<Issue> issues) {
        Object o = m.get(key);
        if (o == null) return def;
        if (!(o instanceof Number n) || n.doubleValue() < min || n.doubleValue() > max) {
            issues.add(new Issue(at, "a number from " + min + " to " + max));
            return def;
        }
        return n.doubleValue();
    }

    private static boolean bool(String at, Map<String, Object> m, String key, boolean def, List<Issue> issues) {
        Object o = m.get(key);
        if (o == null) return def;
        if (o instanceof Boolean b) return b;
        issues.add(new Issue(at, "true or false"));
        return def;
    }

    private static double[] vec(String at, Object o, List<Issue> issues) {
        if (o instanceof List<?> l && l.size() == 3 && l.stream().allMatch(x -> x instanceof Number)) {
            return new double[]{((Number) l.get(0)).doubleValue(), ((Number) l.get(1)).doubleValue(), ((Number) l.get(2)).doubleValue()};
        }
        issues.add(new Issue(at, "[x, y, z] in blocks"));
        return null;
    }
}
