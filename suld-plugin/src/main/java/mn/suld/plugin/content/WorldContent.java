package mn.suld.plugin.content;

import mn.suld.api.mob.MobDefinition;
import mn.suld.api.mob.MobTier;
import mn.suld.api.region.Area;
import mn.suld.api.region.RegionDefinition;
import mn.suld.api.region.RegionShape;

import java.util.List;

/**
 * Vertical Slice 5 world content: Kharkhorum and the four wild regions around it, each with its
 * own level band, hostile mobs, loot and region items. Regions are anchored at Kharkhorum's
 * centre (bearings: 0 = north, 90 = east). Data only; rules live in suld-api.
 */
public final class WorldContent {

    private WorldContent() {
    }

    /** The outer edge of the outer ring: the world border's radius (world.border.diameter 10 000 / 2). */
    private static final double WORLD_EDGE = 5000;

    // -------------------------------------------------------------- mobs

    /** Хэрлэнгийн Тал (east, 1–8). */
    public static final MobDefinition BANDIT = new MobDefinition("mob.deeremchin", "Дээрэмчин", "PILLAGER",
            MobTier.NORMAL, 4, 20.0, 4.0, 70, "loot.deeremchin");
    /** Говь (south, 5–15). */
    public static final MobDefinition SCORPION = new MobDefinition("mob.goviin_khilents", "Говийн Хилэнц", "CAVE_SPIDER",
            MobTier.NORMAL, 7, 18.0, 5.0, 90, "loot.goviin_khilents");
    public static final MobDefinition SAND_SPIRIT = new MobDefinition("mob.elsnii_suns", "Элсний Сүнс", "HUSK",
            MobTier.NORMAL, 9, 30.0, 6.0, 120, "loot.elsnii_suns");
    /** Хангай (north, 10–20). */
    public static final MobDefinition GREY_WOLF = new MobDefinition("mob.saaral_chono", "Хангайн Саарал Чоно", "WOLF",
            MobTier.NORMAL, 12, 34.0, 7.0, 150, "loot.saaral_chono");
    public static final MobDefinition BEAR = new MobDefinition("mob.khangai_baavgai", "Хангайн Баавгай", "POLAR_BEAR",
            MobTier.ELITE, 14, 30.0, 4.0, 180, "loot.khangai_baavgai");
    /** Алтай (west, 18–30). */
    public static final MobDefinition ICE_SPIRIT = new MobDefinition("mob.altai_mosun_suns", "Алтайн Мөсөн Сүнс", "STRAY",
            MobTier.NORMAL, 19, 40.0, 8.0, 220, "loot.altai_mosun_suns");
    public static final MobDefinition GIANT = new MobDefinition("mob.altai_avarga", "Алтайн Аварга", "RAVAGER",
            MobTier.ELITE, 23, 36.0, 5.0, 300, "loot.altai_avarga");

    public static final List<MobDefinition> MOBS = List.of(BANDIT, SCORPION, SAND_SPIRIT, GREY_WOLF, BEAR, ICE_SPIRIT, GIANT);

    // -------------------------------------------------------------- loot

    // loot tables: suld-api/src/main/resources/items/loot.json (item catalog)

    // ----------------------------------------------------------- regions

    public static final RegionDefinition KHARKHORUM = new RegionDefinition("region.kharkhorum", "Хархорум",
            "Их Монгол Улсын нийслэл — аюулгүй бүс", new RegionShape.Square(51), 100, 1, 60, true, true, List.of(), 50);

    public static final RegionDefinition KHERLEN = new RegionDefinition("region.kherlen", "Хэрлэнгийн Тал",
            "Зүүн зүгийн өргөн тал — чоно, дээрэмчид", new RegionShape.Sector(0, WORLD_EDGE, 45, 135), 10, 1, 8, false, false,
            List.of(SuldContent.GOVIIN_CHONO.id(), BANDIT.id()), 100);

    public static final RegionDefinition GOBI = new RegionDefinition("region.gobi", "Говь",
            "Өмнөд зүгийн элсэн цөл — хилэнц, элсний сүнс", new RegionShape.Sector(0, WORLD_EDGE, 135, 225), 10, 5, 15, false, false,
            List.of(SCORPION.id(), SAND_SPIRIT.id()), 250);

    public static final RegionDefinition KHANGAI = new RegionDefinition("region.khangai", "Хангай",
            "Хойд зүгийн ой, уул — саарал чоно, баавгай", new RegionShape.Sector(0, WORLD_EDGE, 315, 45), 10, 10, 20, false, false,
            List.of(GREY_WOLF.id(), BEAR.id()), 400);

    public static final RegionDefinition ALTAI = new RegionDefinition("region.altai", "Алтай",
            "Баруун зүгийн мөсөн оргилууд — мөсөн сүнс, аварга", new RegionShape.Sector(0, WORLD_EDGE, 225, 315), 10, 18, 30,
            false, false, List.of(ICE_SPIRIT.id(), GIANT.id()), 600);

    public static final List<RegionDefinition> REGIONS = List.of(KHARKHORUM, KHERLEN, GOBI, KHANGAI, ALTAI);

    // ------------------------------------------------------------- areas (docs/world/AREAS.md)

    /**
     * Named areas inside the four wild regions: three rings (to 700, 1500 and the world edge) split in two halves per
     * region, named after real places in roughly that direction from Kharkhorum (compressed to fit the map).
     */
    public static final List<Area> AREAS = List.of(
            new Area(16, "area.tuul", "Туулын Хөндий", "region.kherlen", 51, 700, 45, 90, 1, 3, 60, "Туул голын бургастай хөндий", "VERIFIED"),
            new Area(17, "area.kherlen", "Хэрлэнгийн Тал", "region.kherlen", 51, 700, 90, 135, 1, 3, 60, "Хэрлэн голын өргөн тал", "VERIFIED"),
            new Area(18, "area.burkhan_khaldun", "Бурхан Халдуны Бэл", "region.kherlen", 700, 1500, 45, 90, 3, 6, 120, "Хэнтийн ариун уулын бэл", "VERIFIED"),
            new Area(19, "area.khodoo_aral", "Хөдөө Арал", "region.kherlen", 700, 1500, 90, 135, 3, 6, 120, "Хэрлэн, Цэнхэрийн бэлчир — их хуралдайн газар", "VERIFIED"),
            new Area(20, "area.onon", "Онон Голын Хөндий", "region.kherlen", 1500, WORLD_EDGE, 45, 90, 6, 8, 200, "Онон гол — Дэлүүн Болдогийн нутаг", "VERIFIED"),
            new Area(21, "area.buir", "Буйр Нуурын Тал", "region.kherlen", 1500, WORLD_EDGE, 90, 135, 6, 8, 200, "Татаруудын нутаг байсан алс зүүн тал", "INSPIRED"),
            new Area(22, "area.ongi", "Онгийн Гол", "region.gobi", 51, 700, 135, 180, 5, 8, 60, "Говь руу урсах Онгийн гол", "VERIFIED"),
            new Area(23, "area.taats", "Таацын Хөндий", "region.gobi", 51, 700, 180, 225, 5, 8, 60, "Таацын голын хуурай хөндий", "VERIFIED"),
            new Area(24, "area.omnii_govi", "Өмнийн Говь", "region.gobi", 700, 1500, 135, 180, 8, 12, 120, "Өмнөд говийн хайрган тал", "VERIFIED"),
            new Area(25, "area.gurvan_saikhan", "Говь Гурван Сайхан", "region.gobi", 700, 1500, 180, 225, 8, 12, 120, "Говийн гурван сайхан нуруу", "VERIFIED"),
            new Area(26, "area.galba", "Галбын Говь", "region.gobi", 1500, WORLD_EDGE, 135, 180, 12, 15, 200, "Галбын элсэн говь", "VERIFIED"),
            new Area(27, "area.tangut", "Тангудын Хил", "region.gobi", 1500, WORLD_EDGE, 180, 225, 12, 15, 200, "Тангуд улсын хил рүү тэмүүлэх зам", "INSPIRED"),
            new Area(28, "area.orkhon", "Орхоны Хөндий", "region.khangai", 51, 700, 0, 45, 10, 13, 60, "Орхон голын урсгал доош — Хархорумын хойд хөндий", "VERIFIED"),
            new Area(29, "area.khar_balgas", "Хар Балгас", "region.khangai", 51, 700, 315, 360, 10, 13, 60, "Уйгурын эртний хотын балгас", "VERIFIED"),
            new Area(30, "area.selenge", "Сэлэнгэ Мөрөн", "region.khangai", 700, 1500, 0, 45, 13, 17, 120, "Хойд зүг урсах их мөрөн", "VERIFIED"),
            new Area(31, "area.ider", "Идэрийн Гол", "region.khangai", 700, 1500, 315, 360, 13, 17, 120, "Ойт уулсын дундах Идэр гол", "VERIFIED"),
            new Area(32, "area.merkit", "Мэргидийн Тайга", "region.khangai", 1500, WORLD_EDGE, 0, 45, 17, 20, 200, "Мэргид аймгийн байсан хойд ой", "INSPIRED"),
            new Area(33, "area.khuvsgul", "Хөвсгөл Нуур", "region.khangai", 1500, WORLD_EDGE, 315, 360, 17, 20, 200, "Хойт зүгийн их цэнгэг нуур", "VERIFIED"),
            new Area(34, "area.tamir", "Тамирын Гол", "region.altai", 51, 700, 270, 315, 18, 22, 60, "Хойд, Өмнөд Тамирын бэлчир", "VERIFIED"),
            new Area(35, "area.khangai_range", "Хангайн Нуруу", "region.altai", 51, 700, 225, 270, 18, 22, 60, "Хархорумаас баруун өмнөх их нуруу", "VERIFIED"),
            new Area(36, "area.zavkhan", "Завхан Гол", "region.altai", 700, 1500, 270, 315, 22, 26, 120, "Баруун зүгийн Завхан гол", "VERIFIED"),
            new Area(37, "area.otgontenger", "Отгонтэнгэр", "region.altai", 700, 1500, 225, 270, 22, 26, 120, "Хангайн хамгийн өндөр цаст оргил", "VERIFIED"),
            new Area(38, "area.kharkhiraa", "Хархираа Уул", "region.altai", 1500, WORLD_EDGE, 270, 315, 26, 30, 200, "Увсын мөсөн оргилууд", "VERIFIED"),
            new Area(39, "area.naiman", "Найманы Нутаг", "region.altai", 1500, WORLD_EDGE, 225, 270, 26, 30, 200, "Найман аймгийн байсан Алтайн нутаг", "INSPIRED"));

    /** The named area at an offset from Kharkhorum's plaza, if any. */
    public static java.util.Optional<Area> areaAt(double dx, double dz) {
        return Area.at(AREAS, dx, dz);
    }

    /**
     * The level the wild has at an offset from the plaza: the middle of the named area's band, else the region's.
     * The region spawner fits its mobs to it and the HUD rates the danger against it.
     */
    public static int localLevel(RegionDefinition r, double dx, double dz) {
        return areaAt(dx, dz).filter(a -> a.regionId().equals(r.id())).map(a -> (a.minLevel() + a.maxLevel()) / 2)
                .orElse((r.minLevel() + r.maxLevel()) / 2);
    }
}
