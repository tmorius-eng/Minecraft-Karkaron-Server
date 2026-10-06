package mn.suld.plugin.content;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.dungeon.BossDefinition;
import mn.suld.api.dungeon.BossPhase;
import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemStat;
import mn.suld.api.loot.LootEntry;
import mn.suld.api.loot.LootTable;
import mn.suld.api.mob.MobDefinition;
import mn.suld.api.mob.MobTier;
import mn.suld.api.quest.QuestDefinition;
import mn.suld.api.quest.QuestType;

import java.util.List;
import java.util.Map;

/**
 * Centralised, data-driven definitions for Vertical Slice 1. Kept in one place
 * (not scattered through listeners) so content is auditable and later moves to
 * config/registry files without touching gameplay code.
 */
public final class SuldContent {

    private SuldContent() {
    }

    // --- Items ---
    public static final ItemDefinition WOLF_PELT = new ItemDefinition(
            "item.chonon_arisan", "Чонын арьс", "minecraft:leather", ItemRarity.COMMON, 0,
            Map.of(), Map.of(), false);

    public static final ItemDefinition STEPPE_SABER = new ItemDefinition(
            "weapon.talyn_ild", "Талын Илд", "minecraft:iron_sword", ItemRarity.RARE, 870010,
            Map.of(ItemStat.ATTACK, 8.0, ItemStat.CRIT_CHANCE, 0.05),
            Map.of(ItemStat.ATTACK, 1.5, ItemStat.CRIT_CHANCE, 0.005), false);

    // --- First mob ---
    public static final MobDefinition GOVIIN_CHONO = new MobDefinition(
            "mob.goviin_chono", "Говийн Чоно", "WOLF", MobTier.NORMAL,
            2, 16.0, 4.0, 50, "loot.goviin_chono");

    // --- Loot table for the first mob ---
    public static final LootTable GOVIIN_CHONO_LOOT = new LootTable("loot.goviin_chono", List.of(
            new LootEntry(WOLF_PELT, 0.85, 1, 1),
            new LootEntry(STEPPE_SABER, 0.35, 2, 4)
    ));

    // --- First quest ---
    public static final QuestDefinition FIRST_HUNT = new QuestDefinition(
            "quest.first_hunt", "Анхны Ан",
            "Говийн 3 чоныг устгаж, тал нутгийг хамгаал.",
            QuestType.KILL_MOB, "mob.goviin_chono", 3, 150, 20);

    // ===== Vertical Slice 2: Хасарын Агуй (Khasar's Den) =====

    // --- Dungeon reward items ---
    public static final ItemDefinition KHASAR_FANG = new ItemDefinition(
            "weapon.khasar_soyo", "Хасарын Соёо", "minecraft:iron_sword", ItemRarity.EPIC, 0,
            Map.of(ItemStat.ATTACK, 12.0, ItemStat.CRIT_CHANCE, 0.08, ItemStat.CRIT_DAMAGE, 0.25),
            Map.of(ItemStat.ATTACK, 2.0, ItemStat.CRIT_CHANCE, 0.005), false);

    public static final ItemDefinition KHASAR_HEART = new ItemDefinition(
            "item.khasar_zurkh", "Хасарын Зүрх", "minecraft:heart_of_the_sea", ItemRarity.LEGENDARY, 0,
            Map.of(ItemStat.HEALTH, 10.0), Map.of(ItemStat.HEALTH, 1.0), true);

    public static final ItemDefinition STEPPE_TALISMAN = new ItemDefinition(
            "item.talyn_tuvshin", "Талын Түшиг", "minecraft:amethyst_shard", ItemRarity.UNCOMMON, 0,
            Map.of(ItemStat.HEALTH, 2.0), Map.of(ItemStat.HEALTH, 0.5), false);

    // --- Dungeon mobs ---
    public static final MobDefinition ORKHON_CHONO = new MobDefinition(
            "mob.orkhon_chono", "Орхоны Чоно", "WOLF", MobTier.NORMAL,
            3, 24.0, 5.0, 70, "loot.orkhon_chono");

    /** Boss base stats are multiplied by the BOSS tier (health x25, EXP x10). */
    public static final MobDefinition KHASAR = new MobDefinition(
            "mob.khasar", "Хасар — Агуйн Эзэн", "RAVAGER", MobTier.BOSS,
            5, 8.0, 0.25, 60, "loot.khasar");

    public static final BossDefinition KHASAR_BOSS = new BossDefinition(KHASAR, List.of(
            new BossPhase(1.0, 1.0, "Сэрсэн"),
            new BossPhase(0.6, 1.3, "Уурласан"),
            new BossPhase(0.3, 1.6, "Галзуурсан")), 180);

    public static final LootTable ORKHON_CHONO_LOOT = new LootTable("loot.orkhon_chono", List.of(
            new LootEntry(WOLF_PELT, 0.9, 1, 1),
            new LootEntry(STEPPE_TALISMAN, 0.2, 1, 3)
    ));

    /** Boss body drop: small, the real prize is the per-player dungeon reward. */
    public static final LootTable KHASAR_LOOT = new LootTable("loot.khasar", List.of(
            new LootEntry(WOLF_PELT, 1.0, 3, 3)
    ));

    /** Rolled once per party member on completion. */
    public static final LootTable KHASAR_DEN_REWARDS = new LootTable("loot.dungeon.khasar_den", List.of(
            new LootEntry(STEPPE_TALISMAN, 1.0, 3, 5),
            new LootEntry(STEPPE_SABER, 0.6, 4, 6),
            new LootEntry(KHASAR_FANG, 0.25, 5, 7),
            new LootEntry(KHASAR_HEART, 0.06, 5, 5)
    ));

    public static final DungeonDefinition KHASAR_DEN = new DungeonDefinition(
            "dungeon.khasar_den", "Хасарын Агуй",
            2, 1, 4,
            List.of(
                    List.of("mob.goviin_chono", "mob.goviin_chono", "mob.goviin_chono"),
                    List.of("mob.orkhon_chono", "mob.orkhon_chono", "mob.orkhon_chono", "mob.orkhon_chono")),
            KHASAR_BOSS, KHASAR_DEN_REWARDS);

    /** Extra EXP / currency granted to every participant on dungeon completion. */
    public static final long KHASAR_DEN_COMPLETION_EXP = 400;
    public static final long KHASAR_DEN_COMPLETION_CURRENCY = 75;

    public static DungeonDefinition dungeonFor(String id) {
        return KHASAR_DEN.id().equals(id) ? KHASAR_DEN : null;
    }

    public static LootTable lootTableFor(String id) {
        for (LootTable t : List.of(GOVIIN_CHONO_LOOT, ORKHON_CHONO_LOOT, KHASAR_LOOT, KHASAR_DEN_REWARDS)) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    public static MobDefinition mobFor(String id) {
        for (MobDefinition m : List.of(GOVIIN_CHONO, ORKHON_CHONO, KHASAR)) {
            if (m.id().equals(id)) {
                return m;
            }
        }
        return null;
    }

    /** Resolve an item definition by id (slice-1 set; later backed by a registry). */
    public static ItemDefinition definitionFor(String id) {
        return switch (id) {
            case "item.chonon_arisan" -> WOLF_PELT;
            case "weapon.talyn_ild" -> STEPPE_SABER;
            case "weapon.surgamj_ild" -> STARTER_SABER;
            case "weapon.surgamj_num" -> STARTER_BOW;
            case "weapon.khasar_soyo" -> KHASAR_FANG;
            case "item.khasar_zurkh" -> KHASAR_HEART;
            case "item.talyn_tuvshin" -> STEPPE_TALISMAN;
            default -> null;
        };
    }

    // --- Starter equipment granted on class selection ---
    private static final ItemDefinition STARTER_SABER = new ItemDefinition(
            "weapon.surgamj_ild", "Сургамжийн Илд", "minecraft:iron_sword", ItemRarity.COMMON, 0,
            Map.of(ItemStat.ATTACK, 4.0), Map.of(), false);
    private static final ItemDefinition STARTER_BOW = new ItemDefinition(
            "weapon.surgamj_num", "Сургамжийн Нум", "minecraft:bow", ItemRarity.COMMON, 0,
            Map.of(ItemStat.ATTACK, 4.0), Map.of(), false);

    /** Starter weapon granted when a class is chosen (ranged classes get a bow). */
    public static ItemDefinition starterWeapon(PlayerClass clazz) {
        return clazz == PlayerClass.MERGEN ? STARTER_BOW : STARTER_SABER;
    }
}
