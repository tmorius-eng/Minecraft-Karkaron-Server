package mn.suld.api.relic;

/** Block position of a relic's shrine (the lodestone players interact with). */
public record ShrineLocation(String world, int x, int y, int z) {

    public double horizontalDistance(double px, double pz) {
        double dx = px - x;
        double dz = pz - z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
