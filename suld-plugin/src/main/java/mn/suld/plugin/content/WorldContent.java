package mn.suld.plugin.content;

import mn.suld.api.mob.MobDefinition;
import mn.suld.api.mob.MobTier;
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

    private static final double WORLD_EDGE = 3000;

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
}
