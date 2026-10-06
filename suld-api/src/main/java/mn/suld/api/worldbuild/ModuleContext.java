package mn.suld.api.worldbuild;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.IntBinaryOperator;

/**
 * Inputs for one module build: parameters from the placement, the palette for this placement, and
 * a random source seeded from the placement seed (the same placement always produces the same blocks).
 */
public final class ModuleContext {

    private final Map<String, Object> params;
    private final Palette palette;
    private final long seed;
    private final SplittableRandom random;
    private final IntBinaryOperator ground;

    public ModuleContext(Map<String, Object> params, Palette palette, long seed) {
        this(params, palette, seed, (x, z) -> 0);
    }

    /**
     * @param ground planned terrain surface height at a module-local column, relative to the module
     *               origin (0 = same level as the origin). Lets roads and lanes follow the terrain.
     */
    public ModuleContext(Map<String, Object> params, Palette palette, long seed, IntBinaryOperator ground) {
        this.params = Map.copyOf(params);
        this.palette = palette;
        this.seed = seed;
        this.random = new SplittableRandom(seed);
        this.ground = ground;
    }

    /** Planned terrain height at a local column, relative to the module origin. */
    public int ground(int x, int z) {
        return ground.applyAsInt(x, z);
    }

    public Map<String, Object> params() { return params; }
    public Palette palette() { return palette; }
    public long seed() { return seed; }
    public SplittableRandom random() { return random; }

    public int integer(String key, int fallback) {
        Object v = params.get(key);
        return v instanceof Number n ? n.intValue() : fallback;
    }

    public double number(String key, double fallback) {
        Object v = params.get(key);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    public String string(String key, String fallback) {
        Object v = params.get(key);
        return v instanceof String s ? s : fallback;
    }

    public boolean flag(String key, boolean fallback) {
        Object v = params.get(key);
        return v instanceof Boolean b ? b : fallback;
    }

    @SuppressWarnings("unchecked")
    public List<Object> list(String key) {
        Object v = params.get(key);
        return v instanceof List<?> l ? (List<Object>) l : List.of();
    }

    /** A derived, independent context for a nested module (stable per index). */
    public ModuleContext child(Map<String, Object> childParams, int index) {
        return new ModuleContext(childParams, palette, seed * 6364136223846793005L + 1442695040888963407L + index, (x, z) -> 0);
    }
}
