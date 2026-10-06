package mn.suld.api.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link ConfigView} backed by nested {@link Map}s — the shape produced by any
 * YAML/JSON parser. Used in tests and as a dependency-free default, so config
 * mapping logic can be exercised without a server.
 */
public final class MapConfigView implements ConfigView {

    private final Map<String, Object> root;

    public MapConfigView(Map<String, Object> root) {
        this.root = root == null ? Map.of() : root;
    }

    @Override
    public boolean contains(String path) {
        return resolve(path) != null;
    }

    @Override
    public String getString(String path, String def) {
        Object value = resolve(path);
        return value == null ? def : String.valueOf(value);
    }

    @Override
    public int getInt(String path, int def) {
        Object value = resolve(path);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    @Override
    public long getLong(String path, long def) {
        Object value = resolve(path);
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    @Override
    public double getDouble(String path, double def) {
        Object value = resolve(path);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        Object value = resolve(path);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return def;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        Object value = resolve(path);
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(String.valueOf(o));
            }
            return out;
        }
        return List.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<ConfigView> section(String path) {
        Object value = resolve(path);
        if (value instanceof Map<?, ?> map) {
            return Optional.of(new MapConfigView((Map<String, Object>) map));
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Object resolve(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
