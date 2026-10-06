package mn.suld.api.worldbuild;

import java.util.List;
import java.util.Map;

/**
 * Everything needed to compile one city slice: bounds, base palette and per-district overrides,
 * terrain plan, module placements, gameplay points and the city seed.
 */
public record CitySpec(String city, String slice, long seed, Footprint bounds, Palette palette,
                       Map<String, Map<String, String>> districtPalettes, TerrainPlan terrain,
                       List<Placement> placements, List<WorldPoint> points) {

    public CitySpec {
        districtPalettes = Map.copyOf(districtPalettes);
        placements = List.copyOf(placements);
        points = List.copyOf(points);
    }
}
