package mn.suld.plugin.content;

import mn.suld.api.clazz.PlayerClass;
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

    public static LootTable lootTableFor(String id) {
        return GOVIIN_CHONO_LOOT.id().equals(id) ? GOVIIN_CHONO_LOOT : null;
    }

    public static MobDefinition mobFor(String id) {
        return GOVIIN_CHONO.id().equals(id) ? GOVIIN_CHONO : null;
    }

    /** Resolve an item definition by id (slice-1 set; later backed by a registry). */
    public static ItemDefinition definitionFor(String id) {
        return switch (id) {
            case "item.chonon_arisan" -> WOLF_PELT;
            case "weapon.talyn_ild" -> STEPPE_SABER;
            case "weapon.surgamj_ild" -> STARTER_SABER;
            case "weapon.surgamj_num" -> STARTER_BOW;
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
