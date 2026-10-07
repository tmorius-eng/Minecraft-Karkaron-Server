package mn.suld.api.skill.tree;

import mn.suld.api.json.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The persisted skill-tree state of one player (stored with the profile as one versioned JSON document):
 * current ranks, saved builds, points an administrator granted, and the recent respec times. Immutable.
 *
 * <p>Ranks are stored by node id ({@link SkillAllocation#encode()}), so the data files can be reordered or
 * extended without invalidating a saved build.
 */
public record SkillState(String ranks, Map<String, String> builds, String activeBuild, int granted,
                         List<Long> respecs, int version) {

    public static final int CURRENT_VERSION = 1;
    public static final int MAX_RESPEC_HISTORY = 10;
    public static final SkillState NONE = new SkillState("", Map.of(), "", 0, List.of(), CURRENT_VERSION);

    public SkillState {
        ranks = ranks == null ? "" : ranks;
        builds = Collections.unmodifiableMap(new LinkedHashMap<>(builds == null ? Map.of() : builds));
        activeBuild = activeBuild == null ? "" : activeBuild;
        granted = Math.max(0, granted);
        respecs = List.copyOf(respecs == null ? List.of() : respecs);
    }

    public SkillState withRanks(String ranks) {
        return new SkillState(ranks, builds, activeBuild, granted, respecs, version);
    }

    public SkillState withBuilds(Map<String, String> builds, String active) {
        return new SkillState(ranks, builds, active, granted, respecs, version);
    }

    public SkillState withGranted(int granted) {
        return new SkillState(ranks, builds, activeBuild, granted, respecs, version);
    }

    public SkillState withRespec(long at) {
        List<Long> next = new ArrayList<>(respecs);
        next.add(at);
        while (next.size() > MAX_RESPEC_HISTORY) next.remove(0);
        return new SkillState(ranks, builds, activeBuild, granted, next, version);
    }

    public long lastRespec() {
        return respecs.isEmpty() ? 0 : respecs.get(respecs.size() - 1);
    }

    public boolean isEmpty() {
        return equals(NONE);
    }

    // ------------------------------------------------------------------ storage

    /** The stored document, or null for the empty state (NULL column). */
    public String toJson() {
        if (isEmpty()) return null;
        StringBuilder sb = new StringBuilder("{\"v\":").append(version).append(",\"ranks\":").append(quote(ranks));
        sb.append(",\"active\":").append(quote(activeBuild)).append(",\"granted\":").append(granted).append(",\"builds\":{");
        boolean first = true;
        for (Map.Entry<String, String> e : builds.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(quote(e.getKey())).append(':').append(quote(e.getValue()));
        }
        sb.append("},\"respecs\":[");
        for (int i = 0; i < respecs.size(); i++) sb.append(i == 0 ? "" : ",").append(respecs.get(i));
        return sb.append("]}").toString();
    }

    /**
     * Read a stored document. A blank value is the empty state; anything unreadable, or written by a newer
     * version, throws so a profile is never loaded with its skills silently wiped (and then saved over).
     */
    public static SkillState fromJson(String text) {
        if (text == null || text.isBlank()) return NONE;
        try {
            Map<String, Object> o = Json.object(Json.parse(text));
            int v = o.containsKey("v") ? Json.integer(o.get("v")) : 1;
            if (v > CURRENT_VERSION) throw new IllegalArgumentException("skill data version " + v + " is newer than " + CURRENT_VERSION);
            Map<String, String> builds = new LinkedHashMap<>();
            if (o.get("builds") != null) {
                for (Map.Entry<String, Object> e : Json.object(o.get("builds")).entrySet()) builds.put(e.getKey(), String.valueOf(e.getValue()));
            }
            List<Long> respecs = new ArrayList<>();
            if (o.get("respecs") != null) {
                for (Object x : Json.array(o.get("respecs"))) respecs.add(((Number) x).longValue());
            }
            return new SkillState(o.get("ranks") == null ? "" : String.valueOf(o.get("ranks")), builds,
                    o.get("active") == null ? "" : String.valueOf(o.get("active")),
                    o.get("granted") == null ? 0 : Json.integer(o.get("granted")), respecs, CURRENT_VERSION);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("unreadable skill data: " + e.getMessage(), e);
        }
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
