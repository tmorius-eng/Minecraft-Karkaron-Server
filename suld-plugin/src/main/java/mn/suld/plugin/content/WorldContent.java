package mn.suld.plugin.content;

import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemStat;
import mn.suld.api.loot.LootEntry;
import mn.suld.api.loot.LootTable;
import mn.suld.api.mob.MobDefinition;
import mn.suld.api.mob.MobTier;
import mn.suld.api.region.RegionDefinition;
import mn.suld.api.region.RegionShape;

import java.util.List;
import java.util.Map;

/**
 * Vertical Slice 5 world content: Kharkhorum and the four wild regions around it, each with its
 * own level band, hostile mobs, loot and region items. Regions are anchored at Kharkhorum's
 * centre (bearings: 0 = north, 90 = east). Data only; rules live in suld-api.
 */
public final class WorldContent {

    private WorldContent() {
    }

    private static final double WORLD_EDGE = 3000;

    // ------------------------------------------------------------- items

    public static final ItemDefinition SCORPION_VENOM = new ItemDefinition("item.khilentsiin_khor", "Хилэнцийн Хор",
            "minecraft:fermented_spider_eye", ItemRarity.COMMON, 0, Map.of(), Map.of(), false);
    public static final ItemDefinition GOBI_DAGGER = new ItemDefinition("weapon.govi_khutga", "Говийн Хутга",
            "minecraft:iron_sword", ItemRarity.RARE, 870013,
            Map.of(ItemStat.ATTACK, 10.0, ItemStat.CRIT_CHANCE, 0.08), Map.of(ItemStat.ATTACK, 1.5, ItemStat.CRIT_CHANCE, 0.005), false);
    public static final ItemDefinition BEAR_PELT = new ItemDefinition("item.baavgain_arisan", "Баавгайн Арьс",
            "minecraft:rabbit_hide", ItemRarity.UNCOMMON, 870041, Map.of(ItemStat.ARMOR, 2.0), Map.of(ItemStat.ARMOR, 0.5), false);
    public static final ItemDefinition KHANGAI_AXE = new ItemDefinition("weapon.khangai_sukh", "Хангайн Сүх",
            "minecraft:iron_axe", ItemRarity.RARE, 870030,
            Map.of(ItemStat.ATTACK, 13.0, ItemStat.CRIT_DAMAGE, 0.2), Map.of(ItemStat.ATTACK, 2.0), false);
    public static final ItemDefinition ICE_STONE = new ItemDefinition("item.mosun_chuluu", "Мөсөн Чулуу",
            "minecraft:prismarine_crystals", ItemRarity.RARE, 0, Map.of(ItemStat.RESOURCE, 10.0), Map.of(ItemStat.RESOURCE, 2.0), false);
    public static final ItemDefinition ALTAI_SPEAR = new ItemDefinition("weapon.altai_jad", "Алтайн Жад",
            "minecraft:trident", ItemRarity.EPIC, 0,
            Map.of(ItemStat.ATTACK, 16.0, ItemStat.CRIT_CHANCE, 0.1, ItemStat.CRIT_DAMAGE, 0.3),
            Map.of(ItemStat.ATTACK, 2.5, ItemStat.CRIT_CHANCE, 0.005), false);

    public static final List<ItemDefinition> ITEMS = List.of(SCORPION_VENOM, GOBI_DAGGER, BEAR_PELT, KHANGAI_AXE, ICE_STONE, ALTAI_SPEAR);

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

    public static final List<LootTable> LOOT = List.of(
            new LootTable("loot.deeremchin", List.of(
                    new LootEntry(SuldContent.WOLF_PELT, 0.5, 1, 1),
                    new LootEntry(SuldContent.STEPPE_SABER, 0.15, 3, 5))),
            new LootTable("loot.goviin_khilents", List.of(
                    new LootEntry(SCORPION_VENOM, 0.8, 1, 1),
                    new LootEntry(GOBI_DAGGER, 0.12, 6, 9))),
            new LootTable("loot.elsnii_suns", List.of(
                    new LootEntry(SCORPION_VENOM, 0.4, 1, 1),
                    new LootEntry(GOBI_DAGGER, 0.2, 8, 11))),
            new LootTable("loot.saaral_chono", List.of(
                    new LootEntry(SuldContent.WOLF_PELT, 0.9, 1, 1),
                    new LootEntry(KHANGAI_AXE, 0.1, 11, 14))),
            new LootTable("loot.khangai_baavgai", List.of(
                    new LootEntry(BEAR_PELT, 0.9, 12, 15),
                    new LootEntry(KHANGAI_AXE, 0.25, 13, 16))),
            new LootTable("loot.altai_mosun_suns", List.of(
                    new LootEntry(ICE_STONE, 0.6, 18, 20),
                    new LootEntry(ALTAI_SPEAR, 0.05, 18, 21))),
            new LootTable("loot.altai_avarga", List.of(
                    new LootEntry(ICE_STONE, 1.0, 20, 24),
                    new LootEntry(ALTAI_SPEAR, 0.2, 21, 25))));

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
