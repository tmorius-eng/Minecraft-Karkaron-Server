package mn.suld.api.worldbuild;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Pure block-state string utilities: parse {@code minecraft:id[k=v,...]}, transform direction-bearing
 * properties under a {@link Transform}, and re-serialize. Handles facing, axis, rotation (banners,
 * signs, skulls), door hinge, stair shape, chest type, and directional connection keys
 * (north/east/south/west on fences, walls, panes, vines, redstone).
 */
public final class BlockStates {

    private BlockStates() {
    }

    public static String id(String block) {
        int b = block.indexOf('[');
        return b < 0 ? block : block.substring(0, b);
    }

    public static Map<String, String> props(String block) {
        Map<String, String> m = new LinkedHashMap<>();
        int b = block.indexOf('[');
        if (b < 0) return m;
        for (String kv : block.substring(b + 1, block.length() - 1).split(",")) {
            int eq = kv.indexOf('=');
            if (eq > 0) m.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        return m;
    }

    public static String build(String id, Map<String, String> props) {
        if (props.isEmpty()) return id;
        StringJoiner j = new StringJoiner(",", id + "[", "]");
        props.forEach((k, v) -> j.add(k + "=" + v));
        return j.toString();
    }

    public static String with(String block, String key, String value) {
        Map<String, String> p = props(block);
        p.put(key, value);
        return build(id(block), p);
    }

    public static String transform(String block, Transform t) {
        if (t.equals(Transform.IDENTITY) || block.indexOf('[') < 0) return block;
        Map<String, String> in = props(block);
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : in.entrySet()) {
            String k = e.getKey(), v = e.getValue();
            switch (k) {
                case "facing" -> out.put(k, cardinal(v) ? t.apply(Facing.valueOf(v.toUpperCase())).id() : v);
                case "axis" -> out.put(k, t.swapsAxes() && !v.equals("y") ? (v.equals("x") ? "z" : "x") : v);
                case "rotation" -> out.put(k, String.valueOf(t.applyRotation16(Integer.parseInt(v))));
                case "hinge" -> out.put(k, t.mirrorX() ? (v.equals("left") ? "right" : "left") : v);
                case "shape" -> out.put(k, t.mirrorX() ? mirrorShape(v) : v);
                case "type" -> out.put(k, t.mirrorX() && (v.equals("left") || v.equals("right")) ? (v.equals("left") ? "right" : "left") : v);
                case "north", "east", "south", "west" -> {
                    String nk = t.apply(Facing.valueOf(k.toUpperCase())).id();
                    out.put(nk, v);
                }
                default -> out.put(k, v);
            }
        }
        return build(id(block), out);
    }

    private static boolean cardinal(String v) {
        return v.equals("north") || v.equals("east") || v.equals("south") || v.equals("west");
    }

    private static String mirrorShape(String v) {
        return switch (v) {
            case "inner_left" -> "inner_right";
            case "inner_right" -> "inner_left";
            case "outer_left" -> "outer_right";
            case "outer_right" -> "outer_left";
            default -> v; // straight, rail shapes (north_south...) untouched for our modules
        };
    }
}
