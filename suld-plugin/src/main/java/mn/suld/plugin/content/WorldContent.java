package mn.suld.plugin.content;

import mn.suld.api.mob.MobDefinition;
import mn.suld.api.region.Area;
import mn.suld.api.region.RegionDefinition;

import java.util.List;

/**
 * Kharkhorum, the wild regions and their named areas, and the open-world mobs — read from {@code content/world.json}
 * and {@code content/mobs.json} (docs/CONTENT_DATA.md). Regions are anchored at Kharkhorum's centre (bearings: 0 =
 * north, 90 = east). The named constants are the ids the code refers to; everything else is data.
 */
public final class WorldContent {

    private WorldContent() {
    }

    /** The outer edge of the outer ring: the world border's radius. */
    private static final double WORLD_EDGE = Content.pack().worldEdge();
    /** The home regions reach this far; beyond it lie the outer lands (levels grow with the distance). */
    public static final double INNER_EDGE = Content.pack().innerEdge();

    // -------------------------------------------------------------- mobs

    /** Хэрлэнгийн Тал (east, 1–8). */
    public static final MobDefinition BANDIT = Content.mob("mob.deeremchin");
    /** Говь (south, 5–15). */
    public static final MobDefinition SCORPION = Content.mob("mob.goviin_khilents");
    public static final MobDefinition SAND_SPIRIT = Content.mob("mob.elsnii_suns");
    /** Хангай (north, 10–20). */
    public static final MobDefinition GREY_WOLF = Content.mob("mob.saaral_chono");
    public static final MobDefinition BEAR = Content.mob("mob.khangai_baavgai");
    /** Алтай (west, 18–30). */
    public static final MobDefinition ICE_SPIRIT = Content.mob("mob.altai_mosun_suns");
    public static final MobDefinition GIANT = Content.mob("mob.altai_avarga");
    /** Алтай's high slopes (25). */
    public static final MobDefinition ALTAI_WOLF = Content.mob("mob.altai_tsasan_chono");
    /** The outer lands' edge (59): the sky palace's outer guard. */
    public static final MobDefinition SKY_GUARD = Content.mob("mob.tengeriin_kharuul");

    /** The open-world mobs of the home regions (role "wild"). */
    public static final List<MobDefinition> MOBS = Content.pack().mobsOf("wild");

    // ----------------------------------------------------------- regions

    public static final RegionDefinition KHARKHORUM = Content.pack().region("region.kharkhorum");
    public static final RegionDefinition KHERLEN = Content.pack().region("region.kherlen");
    public static final RegionDefinition GOBI = Content.pack().region("region.gobi");
    public static final RegionDefinition KHANGAI = Content.pack().region("region.khangai");
    public static final RegionDefinition ALTAI = Content.pack().region("region.altai");
    /** The outer lands (2600 to the world edge): the danger grows with the distance from Kharkhorum. */
    public static final RegionDefinition KHENTII = Content.pack().region("region.khentii");
    public static final RegionDefinition ZUUNGAR = Content.pack().region("region.zuungar");
    public static final RegionDefinition OTGON = Content.pack().region("region.otgon");
    public static final RegionDefinition KHUVSGUL = Content.pack().region("region.khuvsgul");

    public static final List<RegionDefinition> REGIONS = Content.pack().regions();

    /** An outer region ("outer": true): its level follows the distance, not its areas. */
    public static boolean outer(RegionDefinition r) {
        return Content.pack().outer().contains(r.id());
    }

    // ------------------------------------------------------------- areas (docs/world/AREAS.md)

    /** Named areas inside the wild regions, named after real places in roughly that direction from Kharkhorum. */
    public static final List<Area> AREAS = Content.pack().areas();

    /** The named area at an offset from Kharkhorum's plaza, if any. */
    public static java.util.Optional<Area> areaAt(double dx, double dz) {
        return Area.at(AREAS, dx, dz);
    }

    /**
     * The level the wild has at an offset from the plaza: the middle of the named area's band, else the region's.
     * The region spawner fits its mobs to it and the HUD rates the danger against it.
     */
    public static int localLevel(RegionDefinition r, double dx, double dz) {
        if (outer(r)) {
            // the outer lands: the danger grows with the distance from Kharkhorum (minLevel at the inner edge, maxLevel at the world's)
            double f = Math.max(0, Math.min(1, (Math.hypot(dx, dz) - INNER_EDGE) / (WORLD_EDGE - INNER_EDGE)));
            return (int) Math.round(r.minLevel() + f * (r.maxLevel() - r.minLevel()));
        }
        return areaAt(dx, dz).filter(a -> a.regionId().equals(r.id())).map(a -> (a.minLevel() + a.maxLevel()) / 2)
                .orElse((r.minLevel() + r.maxLevel()) / 2);
    }
}
