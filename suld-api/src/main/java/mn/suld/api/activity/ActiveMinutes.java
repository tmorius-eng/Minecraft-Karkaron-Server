package mn.suld.api.activity;

import mn.suld.api.json.Json;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Validated active minutes per category, stored with the profile ({@code active_minutes}). */
public record ActiveMinutes(Map<ActivityCategory, Long> minutes) {

    public static final int VERSION = 1;
    public static final ActiveMinutes NONE = new ActiveMinutes(Map.of());

    public ActiveMinutes {
        Map<ActivityCategory, Long> m = new EnumMap<>(ActivityCategory.class);
        if (minutes != null) minutes.forEach((k, v) -> {
            if (k != null && v != null && v > 0) m.put(k, v);
        });
        minutes = Collections.unmodifiableMap(m);
    }

    public long total() {
        return minutes.values().stream().mapToLong(Long::longValue).sum();
    }

    public long of(ActivityCategory c) {
        return minutes.getOrDefault(c, 0L);
    }

    public ActiveMinutes plus(ActivityCategory c, long n) {
        Map<ActivityCategory, Long> m = new EnumMap<>(ActivityCategory.class);
        m.putAll(minutes);
        m.merge(c, n, Long::sum);
        return new ActiveMinutes(m);
    }

    public boolean isEmpty() {
        return minutes.isEmpty();
    }

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", VERSION);
        Map<String, Object> c = new LinkedHashMap<>();
        minutes.forEach((k, v) -> c.put(k.name(), v));
        m.put("m", c);
        return Json.write(m);
    }

    public static ActiveMinutes fromJson(String json) {
        if (json == null || json.isBlank()) return NONE;
        try {
            Map<String, Object> m = Json.object(Json.parse(json));
            int v = m.get("v") instanceof Number n ? n.intValue() : -1;
            if (v != VERSION) throw new IllegalArgumentException("active minutes version " + v + " is not supported (expected " + VERSION + ")");
            Map<ActivityCategory, Long> out = new EnumMap<>(ActivityCategory.class);
            for (Map.Entry<String, Object> e : Json.object(m.getOrDefault("m", Map.of())).entrySet()) {
                out.put(ActivityCategory.valueOf(e.getKey()), ((Number) e.getValue()).longValue());
            }
            return new ActiveMinutes(out);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("broken active minutes: " + e.getMessage(), e);
        }
    }
}
