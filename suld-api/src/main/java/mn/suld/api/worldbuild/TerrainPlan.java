package mn.suld.api.worldbuild;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * Terrain alignment for a city slice (Pass 1). The city surface height is the base elevation plus
 * the highest elevation zone covering a column; zones may fall off as natural slopes. Around the
 * slice bounds a feather band blends the city surface into the real terrain, so a build never ends in
 * a cliff or a trench. All heights are city-local: y = 0 is the base ground surface block.
 */
public final class TerrainPlan {

    /** Zone outline. */
    public sealed interface Shape permits Rect, Circle {
        /** Distance from the column to the shape (0 inside). */
        double distance(int x, int z);
    }

    public record Rect(int x1, int z1, int x2, int z2) implements Shape {
        public double distance(int x, int z) {
            int dx = Math.max(Math.max(x1 - x, 0), x - x2);
            int dz = Math.max(Math.max(z1 - z, 0), z - z2);
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    public record Circle(int cx, int cz, double r) implements Shape {
        public double distance(int x, int z) {
            return Math.max(0, Math.sqrt((double) (x - cx) * (x - cx) + (double) (z - cz) * (z - cz)) - r);
        }
    }

    /** @param falloff 0 = crisp edge (a module builds the retaining wall/steps); n = slope over n blocks */
    public record Zone(String id, Shape shape, int elevation, int falloff, String surface) {
    }

    /** One terrain column: surface block at {@code top}, filled below, cleared of obstacles up to {@code clearTo}. */
    public record Column(int x, int z, int top, String surface, int natural, int clearTo, boolean inside) {
    }

    private final int x1, z1, x2, z2, feather, clearance;
    private final String defaultSurface;
    private final List<Zone> zones;

    public TerrainPlan(int x1, int z1, int x2, int z2, int feather, int clearance, String defaultSurface, List<Zone> zones) {
        this.x1 = Math.min(x1, x2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.z2 = Math.max(z1, z2);
        this.feather = feather;
        this.clearance = clearance;
        this.defaultSurface = defaultSurface;
        this.zones = List.copyOf(zones);
    }

    public List<Zone> zones() { return zones; }
    public int feather() { return feather; }
    public Footprint bounds() { return new Footprint(x1, -64, z1, x2, 255, z2); }

    public boolean inside(int x, int z) {
        return x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }

    /** City surface height of a column inside the bounds. */
    public int elevation(int x, int z) {
        double best = 0;
        for (Zone zn : zones) {
            double d = zn.shape.distance(x, z);
            double e;
            if (d <= 0) e = zn.elevation;
            else if (zn.falloff > 0 && d < zn.falloff) e = zn.elevation * (1 - d / zn.falloff);
            else continue;
            if (e > best) best = e;
        }
        return (int) Math.round(best);
    }

    public String surface(int x, int z) {
        String s = defaultSurface;
        int bestElevation = Integer.MIN_VALUE;
        for (Zone zn : zones) {
            if (zn.surface != null && zn.shape.distance(x, z) <= 0 && zn.elevation >= bestElevation) {
                s = zn.surface;
                bestElevation = zn.elevation;
            }
        }
        return s;
    }

    /** All columns (bounds + feather band), given the natural terrain height per city-local column. */
    public List<Column> columns(IntBinaryOperator natural) {
        List<Column> out = new ArrayList<>();
        for (int x = x1 - feather; x <= x2 + feather; x++) {
            for (int z = z1 - feather; z <= z2 + feather; z++) {
                int nat = natural.applyAsInt(x, z);
                if (inside(x, z)) {
                    int top = elevation(x, z);
                    out.add(new Column(x, z, top, surface(x, z), nat, top + clearance, true));
                    continue;
                }
                int cx = Math.max(x1, Math.min(x2, x)), cz = Math.max(z1, Math.min(z2, z));
                double d = Math.sqrt((double) (x - cx) * (x - cx) + (double) (z - cz) * (z - cz));
                if (d > feather) continue;
                double t = d / (feather + 1.0);
                double s = t * t * (3 - 2 * t); // smoothstep
                int edge = elevation(cx, cz);
                int top = (int) Math.round(edge + (nat - edge) * s);
                if (top == nat) continue; // already matches the land
                out.add(new Column(x, z, top, defaultSurface, nat, Math.max(top, nat), false)); // feather: only reshape the ground
            }
        }
        return out;
    }
}
