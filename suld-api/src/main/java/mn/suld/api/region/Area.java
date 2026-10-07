package mn.suld.api.region;

import java.util.List;
import java.util.Optional;

/**
 * A named area inside a wild region (docs/world/AREAS.md): a ring segment around Kharkhorum, so the steppe east of the
 * capital is not one endless "Хэрлэнгийн Тал" but the Tuul valley, Khödöö Aral, the Onon... Regions keep the gameplay
 * (mobs, safety, quest targets); areas add the geography: a name, a level band inside the region's band, a one-time
 * discovery reward and a history note.
 *
 * @param index     the discovery bit (16..63; 0..15 belong to regions)
 * @param history   VERIFIED (a real place in that direction from Kharkhorum), INSPIRED (real, placement compressed)
 *                  or FICTION
 */
public record Area(int index, String id, String name, String regionId, double minRadius, double maxRadius,
                   double fromDeg, double toDeg, int minLevel, int maxLevel, long discoveryExp, String description,
                   String history) {

    public Area {
        if (index < 16 || index > 63) throw new IllegalArgumentException("area bit " + index + " outside 16..63");
        if (minLevel > maxLevel) throw new IllegalArgumentException(id + ": level band " + minLevel + ">" + maxLevel);
    }

    public boolean contains(double dx, double dz) {
        return new RegionShape.Sector(minRadius, maxRadius, fromDeg, toDeg).contains(dx, dz);
    }

    public String levelBand() {
        return minLevel + "–" + maxLevel;
    }

    /** The area at a point relative to the capital, if any (areas do not overlap). */
    public static Optional<Area> at(List<Area> areas, double dx, double dz) {
        for (Area a : areas) if (a.contains(dx, dz)) return Optional.of(a);
        return Optional.empty();
    }
}
