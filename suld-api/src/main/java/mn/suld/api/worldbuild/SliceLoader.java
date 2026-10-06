package mn.suld.api.worldbuild;

import mn.suld.api.json.Json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the machine-readable slice file ({@code assets/world/<city>/slices/<slice>.json}) and the
 * points file into a {@link CitySpec}. The format is documented in docs/world/BLUEPRINT_FORMAT.md.
 */
public final class SliceLoader {

    private SliceLoader() {
    }

    public static CitySpec load(String sliceJson, String pointsJson, Palette basePalette) {
        Map<String, Object> root = Json.object(Json.parse(sliceJson));
        Map<String, Object> bounds = Json.object(root.get("bounds"));
        int x1 = Json.integer(bounds.get("x1")), z1 = Json.integer(bounds.get("z1"));
        int x2 = Json.integer(bounds.get("x2")), z2 = Json.integer(bounds.get("z2"));

        Map<String, Object> t = Json.object(root.get("terrain"));
        List<TerrainPlan.Zone> zones = new ArrayList<>();
        for (Object o : Json.array(t.getOrDefault("zones", List.of()))) {
            Map<String, Object> z = Json.object(o);
            TerrainPlan.Shape shape;
            if (z.containsKey("rect")) {
                List<Object> r = Json.array(z.get("rect"));
                shape = new TerrainPlan.Rect(Json.integer(r.get(0)), Json.integer(r.get(1)), Json.integer(r.get(2)), Json.integer(r.get(3)));
            } else {
                List<Object> c = Json.array(z.get("circle"));
                shape = new TerrainPlan.Circle(Json.integer(c.get(0)), Json.integer(c.get(1)), ((Number) c.get(2)).doubleValue());
            }
            zones.add(new TerrainPlan.Zone((String) z.get("id"), shape, Json.integer(z.get("elevation")),
                    Json.integer(z.getOrDefault("falloff", 0L)), (String) z.get("surface")));
        }
        TerrainPlan terrain = new TerrainPlan(x1, z1, x2, z2, Json.integer(t.getOrDefault("feather", 12L)),
                Json.integer(t.getOrDefault("clearance", 32L)), (String) t.getOrDefault("surface", "minecraft:grass_block"), zones);

        Map<String, Map<String, String>> districtPalettes = new HashMap<>();
        Object dp = root.get("district_palettes");
        if (dp != null) Json.object(dp).forEach((k, v) -> districtPalettes.put(k, strings(v)));

        List<Placement> placements = new ArrayList<>();
        for (Object o : Json.array(root.get("placements"))) {
            Map<String, Object> p = Json.object(o);
            List<Object> at = Json.array(p.get("at"));
            String id = (String) p.get("id");
            Transform tr = Transform.of(Json.integer(p.getOrDefault("rotate", 0L)), Boolean.TRUE.equals(p.get("mirror")));
            Object seed = p.get("seed");
            placements.add(new Placement(id, (String) p.get("module"),
                    Json.integer(at.get(0)), Json.integer(at.get(1)), Json.integer(at.get(2)), tr,
                    p.containsKey("params") ? Json.object(p.get("params")) : Map.of(),
                    p.containsKey("palette") ? strings(p.get("palette")) : Map.of(),
                    (String) p.get("district"), Boolean.TRUE.equals(p.get("landmark")),
                    Boolean.TRUE.equals(p.get("allow_overlap")),
                    seed instanceof Number n ? n.longValue() : id.hashCode()));
        }

        List<WorldPoint> points = new ArrayList<>();
        if (pointsJson != null) {
            for (Object o : Json.array(Json.object(Json.parse(pointsJson)).get("points"))) {
                Map<String, Object> p = Json.object(o);
                points.add(new WorldPoint((String) p.get("id"), (String) p.get("type"), (String) p.get("district"),
                        Json.integer(p.get("x")), Json.integer(p.get("y")), Json.integer(p.get("z")),
                        ((Number) p.getOrDefault("yaw", 0L)).floatValue(), Boolean.TRUE.equals(p.get("required")),
                        (String) p.get("slice")));
            }
        }
        Number seed = (Number) root.getOrDefault("seed", 0L);
        return new CitySpec((String) root.get("city"), (String) root.get("slice"), seed.longValue(),
                new Footprint(x1, -64, z1, x2, 319, z2), basePalette, districtPalettes, terrain, placements, points);
    }

    private static Map<String, String> strings(Object o) {
        Map<String, String> m = new HashMap<>();
        Json.object(o).forEach((k, v) -> m.put(k, String.valueOf(v)));
        return m;
    }
}
