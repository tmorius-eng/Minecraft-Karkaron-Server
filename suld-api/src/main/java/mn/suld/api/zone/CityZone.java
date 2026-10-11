package mn.suld.api.zone;

/**
 * "Is this position inside Kharkhorum?" — the single source of truth every system asks
 * (protection, dungeons, world events, mob spawning, PvP). Coordinates are world block coordinates.
 */
public interface CityZone {

    /** Inside the city bounds (walls included). */
    boolean contains(String world, int x, int z);

    /** Inside the city or within {@code margin} blocks of it. */
    boolean near(String world, int x, int z, int margin);

    /** The world block {x, z} of the city plan's origin (its layout coordinates are relative to it), if built. */
    default java.util.Optional<int[]> origin(String world) {
        return java.util.Optional.empty();
    }

    CityZone NONE = new CityZone() {
        public boolean contains(String world, int x, int z) { return false; }
        public boolean near(String world, int x, int z, int margin) { return false; }
    };
}
