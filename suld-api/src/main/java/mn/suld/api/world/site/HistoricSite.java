package mn.suld.api.world.site;

/**
 * A historical place of the Mongol lands set in the wild (docs/world/HISTORIC_SITES.md, content/sites.json): what it
 * is, where it stands (a bearing and distance from the Kharkhorum plaza, inside its named area) and how sure the
 * history is. The build is always a simplified game version; {@code history} labels the place, not the build.
 *
 * @param history VERIFIED (the place is known), INSPIRED (tradition or a reconstruction in its spirit) or FICTION
 */
public record HistoricSite(String id, String name, SiteKind kind, String areaId, double bearingDeg, double radius,
                           String history, String description) {

    public HistoricSite {
        if (id == null || !id.matches("site\\.[a-z0-9_]{2,40}")) throw new IllegalArgumentException("site id must look like site.some_name");
        if (radius < 120 || radius > 4900) throw new IllegalArgumentException(id + ": radius " + radius + " outside 120..4900");
    }

    /** Offset {dx, dz} from the plaza (0° = north, 90° = east). */
    public int[] offset() {
        double a = Math.toRadians(bearingDeg);
        return new int[]{(int) Math.round(Math.sin(a) * radius), (int) Math.round(-Math.cos(a) * radius)};
    }
}
