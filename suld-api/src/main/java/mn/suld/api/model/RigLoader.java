package mn.suld.api.model;

import mn.suld.api.json.Json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads and validates {@code rig.json} + {@code clips.json} (docs/MODEL_RENDERER.md §3). Every problem is reported;
 * a model with problems is not loaded.
 */
public final class RigLoader {

    /** A loaded model: its rig and clips by name. */
    public record Model(Rig rig, Map<String, Clip> clips) {
        public Clip clip(String name) {
            return clips.get(name);
        }
    }

    public record Result(Model model, List<String> issues) {
        public boolean ok() {
            return issues.isEmpty() && model != null;
        }
    }

    /** Clips every creature must have. */
    public static final List<String> REQUIRED_CLIPS = List.of("idle", "walk", "death");

    private RigLoader() {
    }

    public static Result load(String rigJson, String clipsJson) {
        List<String> issues = new ArrayList<>();
        Rig rig = null;
        Map<String, Clip> clips = new LinkedHashMap<>();
        try {
            rig = rig(Json.object(Json.parse(rigJson)), issues);
        } catch (RuntimeException e) {
            issues.add("rig.json: " + e.getMessage());
        }
        try {
            if (rig != null) clips = clips(Json.object(Json.parse(clipsJson)), rig, issues);
        } catch (RuntimeException e) {
            issues.add("clips.json: " + e.getMessage());
        }
        for (String r : REQUIRED_CLIPS) if (rig != null && !clips.containsKey(r)) issues.add("missing clip " + r);
        return new Result(issues.isEmpty() && rig != null ? new Model(rig, Map.copyOf(clips)) : null, issues);
    }

    private static Rig rig(Map<String, Object> m, List<String> issues) {
        String id = String.valueOf(m.get("id"));
        if (!id.matches("[a-z][a-z0-9_]*")) issues.add("rig id " + id);
        double scale = num(m.get("scale"), 1);
        double hostHeight = num(m.get("hostHeight"), 2);
        List<Rig.Bone> bones = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        for (Object o : Json.array(m.get("bones"))) {
            Map<String, Object> b = Json.object(o);
            String bid = String.valueOf(b.get("id"));
            if (index.containsKey(bid)) {
                issues.add("duplicate bone " + bid);
                continue;
            }
            Object p = b.get("parent");
            int parent = -1;
            if (p != null) {
                Integer pi = index.get(String.valueOf(p));
                if (pi == null) issues.add("bone " + bid + ": parent " + p + " must come before it");
                else parent = pi;
            }
            double bs = num(b.get("scale"), 1);
            if (!(bs > 0 && bs <= 8)) issues.add("bone " + bid + ": scale " + bs);
            index.put(bid, bones.size());
            bones.add(new Rig.Bone(bid, parent, vec(b.get("pivot"), Vec3.ZERO), vec(b.get("rest"), Vec3.ZERO),
                    Boolean.TRUE.equals(b.get("model")), bs));
        }
        if (bones.isEmpty()) issues.add("no bones");
        List<Rig.Part> parts = new ArrayList<>();
        for (Object o : Json.array(m.getOrDefault("parts", List.of()))) {
            Map<String, Object> pm = Json.object(o);
            String bone = String.valueOf(pm.get("bone"));
            if (!index.containsKey(bone)) issues.add("part on unknown bone " + bone);
            parts.add(new Rig.Part(bone, num(pm.get("width"), 1), num(pm.get("height"), 1)));
        }
        return new Rig(id, scale, hostHeight, bones, parts);
    }

    private static Map<String, Clip> clips(Map<String, Object> m, Rig rig, List<String> issues) {
        Map<String, Clip> out = new LinkedHashMap<>();
        Set<String> boneIds = new HashSet<>(rig.indices().keySet());
        for (Map.Entry<String, Object> e : Json.object(m.get("clips")).entrySet()) {
            Map<String, Object> c = Json.object(e.getValue());
            String name = e.getKey();
            int length = (int) num(c.get("length"), 0);
            if (length < 1) issues.add("clip " + name + ": length " + length);
            Map<String, Clip.Channels> chans = new HashMap<>();
            for (Map.Entry<String, Object> be : Json.object(c.getOrDefault("bones", Map.of())).entrySet()) {
                if (!boneIds.contains(be.getKey())) {
                    issues.add("clip " + name + ": unknown bone " + be.getKey());
                    continue;
                }
                Map<String, Object> ch = Json.object(be.getValue());
                chans.put(be.getKey(), new Clip.Channels(keys(ch.get("rot"), 3, name, issues), keys(ch.get("pos"), 3, name, issues),
                        keys(ch.get("scl"), 1, name, issues)));
            }
            List<Clip.Event> events = new ArrayList<>();
            for (Object ev : Json.array(c.getOrDefault("events", List.of()))) {
                List<Object> a = Json.array(ev);
                events.add(new Clip.Event((int) num(a.get(0), 0), String.valueOf(a.get(1))));
            }
            out.put(name, new Clip(name, length, Boolean.TRUE.equals(c.get("loop")), chans, events));
        }
        return out;
    }

    private static List<double[]> keys(Object o, int width, String clip, List<String> issues) {
        List<double[]> out = new ArrayList<>();
        if (o == null) return out;
        double last = Double.NEGATIVE_INFINITY;
        for (Object k : Json.array(o)) {
            List<Object> a = Json.array(k);
            if (a.size() != width + 1) {
                issues.add("clip " + clip + ": key needs " + (width + 1) + " numbers");
                continue;
            }
            double[] v = new double[width + 1];
            for (int i = 0; i <= width; i++) v[i] = num(a.get(i), Double.NaN);
            if (v[0] < last) issues.add("clip " + clip + ": keys out of order");
            last = v[0];
            out.add(v);
        }
        return out;
    }

    private static Vec3 vec(Object o, Vec3 dflt) {
        if (o == null) return dflt;
        List<Object> a = Json.array(o);
        return new Vec3(num(a.get(0), 0), num(a.get(1), 0), num(a.get(2), 0));
    }

    private static double num(Object o, double dflt) {
        return o instanceof Number n ? n.doubleValue() : dflt;
    }
}
