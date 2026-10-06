package mn.suld.api.worldbuild;

import java.util.Map;

/**
 * One instance of a module in the city: where (city-local coordinates of the module origin), how it
 * is turned ({@link Transform}), with which parameters and palette overrides, and its seed.
 *
 * @param landmark     a unique landmark: the validator rejects two placements with the same id
 * @param allowOverlap explicitly allowed to overwrite same-layer blocks of other placements
 */
public record Placement(String id, String module, int x, int y, int z, Transform transform,
                        Map<String, Object> params, Map<String, String> palette, String district,
                        boolean landmark, boolean allowOverlap, long seed) {

    public Placement {
        params = Map.copyOf(params);
        palette = Map.copyOf(palette);
    }

    public static Placement of(String id, String module, int x, int y, int z) {
        return new Placement(id, module, x, y, z, Transform.IDENTITY, Map.of(), Map.of(), null, false, false, id.hashCode());
    }
}
