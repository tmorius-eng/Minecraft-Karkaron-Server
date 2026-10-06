package mn.suld.api.worldbuild;

/** Axis-aligned bounds (inclusive). */
public record Footprint(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public boolean intersects(Footprint o) {
        return minX <= o.maxX && o.minX <= maxX && minY <= o.maxY && o.minY <= maxY && minZ <= o.maxZ && o.minZ <= maxZ;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public Footprint include(int x, int y, int z) {
        return new Footprint(Math.min(minX, x), Math.min(minY, y), Math.min(minZ, z),
                Math.max(maxX, x), Math.max(maxY, y), Math.max(maxZ, z));
    }

    public static Footprint point(int x, int y, int z) {
        return new Footprint(x, y, z, x, y, z);
    }
}
