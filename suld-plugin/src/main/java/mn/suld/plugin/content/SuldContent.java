package mn.suld.plugin.content;

import mn.suld.api.dungeon.BossDefinition;
import mn.suld.api.dungeon.BossPhase;
import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemCatalogLoader;
import mn.suld.api.loot.LootTable;
import mn.suld.api.mob.MobDefinition;
import mn.suld.api.mob.MobTier;
import mn.suld.api.quest.QuestDefinition;
import mn.suld.api.quest.QuestType;
import mn.suld.api.relic.RelicDefinition;
import mn.suld.api.worldevent.WorldEventDefinition;

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

    // --- First mob ---
    public static final MobDefinition GOVIIN_CHONO = new MobDefinition(
            "mob.goviin_chono", "Говийн Чоно", "WOLF", MobTier.NORMAL,
            2, 16.0, 4.0, 50, "loot.goviin_chono");

    // --- First quest ---
    public static final QuestDefinition FIRST_HUNT = new QuestDefinition(
            "quest.first_hunt", "Анхны Ан",
            "Говийн 3 чоныг устгаж, тал нутгийг хамгаал.",
            QuestType.KILL_MOB, "mob.goviin_chono", 3, 150, 20);

    // ===== Vertical Slice 2: Хасарын Агуй (Khasar's Den) =====

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

    public static final DungeonDefinition KHASAR_DEN = new DungeonDefinition(
            "dungeon.khasar_den", "Хасарын Агуй",
            2, 1, 4,
            List.of(
                    List.of("mob.goviin_chono", "mob.goviin_chono", "mob.goviin_chono"),
                    List.of("mob.orkhon_chono", "mob.orkhon_chono", "mob.orkhon_chono", "mob.orkhon_chono")),
            KHASAR_BOSS, "loot.dungeon.khasar_den");

    /** Extra EXP / currency granted to every participant on dungeon completion. */
    public static final long KHASAR_DEN_COMPLETION_EXP = 400;
    public static final long KHASAR_DEN_COMPLETION_CURRENCY = 75;

    // ===== Vertical Slice 3: clans + world events (social progression) =====

    /** Clan EXP per SÜLD mob kill, per dungeon clear (per participant). */
    public static final long CLAN_EXP_PER_MOB_KILL = 2;
    public static final long CLAN_EXP_PER_DUNGEON_CLEAR = 150;

    /** Чонын Довтолгоо — wolves raid the steppe; the whole server has 10 minutes to repel 30. */
    public static final WorldEventDefinition WOLF_RAID = new WorldEventDefinition(
            "event.chonyn_dovtolgoo", "Чонын Довтолгоо",
            "Тал нутгийг чоно дайрлаа! Бүгдээрээ 10 минутад 30 чоно устгаарай.",
            java.util.Set.of(GOVIIN_CHONO.id(), ORKHON_CHONO.id()),
            30, 600, 300, 60, 3, 5, 200);

    public static WorldEventDefinition worldEventFor(String id) {
        return WOLF_RAID.id().equals(id) || "wolf_raid".equals(id) ? WOLF_RAID : null;
    }

    // ===== Vertical Slice 4: world-unique relics =====

    /** Хөх Сүлд — the Blue Standard, the spirit banner of the steppe. The flagship relic. */
    public static final RelicDefinition KHUKH_SULD = new RelicDefinition(
            "relic.khukh_suld", "Хөх Сүлд",
            "Мөнх тэнгэрийн хүчээр мандсан хөх сүлд — түүнийг барьсан нэгэн л тал нутгийг удирдана.",
            "minecraft:nether_star", 870100, 10, 0.25);

    /** Алтан Гэрэгэ — the Golden Paiza, the khan's seal of safe passage. */
    public static final RelicDefinition ALTAN_GEREGE = new RelicDefinition(
            "relic.altan_gerege", "Алтан Гэрэгэ",
            "Хааны тамгатай алтан гэрэгэ — эзэн нь хаана ч хүндлэгдэнэ.",
            "minecraft:nether_star", 870101, 6, 0.15);

    public static final java.util.List<RelicDefinition> RELICS = java.util.List.of(KHUKH_SULD, ALTAN_GEREGE);

    public static RelicDefinition relicFor(String key) {
        for (RelicDefinition r : RELICS) {
            if (r.key().equals(key) || r.key().equals("relic." + key)) {
                return r;
            }
        }
        return null;
    }

    public static DungeonDefinition dungeonFor(String id) {
        for (DungeonDefinition d : DungeonContent.ALL) {
            if (d.id().equals(id)) {
                return d;
            }
        }
        return null;
    }

    // ===== Items and loot: the item catalog (suld-api/src/main/resources/items, data-folder override) =====

    private static volatile ItemCatalog catalog;

    /** The live item catalog: the bundled one until the item service has loaded (and validated) the server's. */
    public static ItemCatalog items() {
        ItemCatalog c = catalog;
        if (c == null) {
            synchronized (SuldContent.class) {
                if (catalog == null) catalog = ItemCatalogLoader.load(ItemCatalogLoader.classpath()).catalog();
                c = catalog;
            }
        }
        return c;
    }

    /** Swap in a validated catalog (item service load/reload). */
    public static void items(ItemCatalog c) {
        catalog = c;
    }

    public static LootTable lootTableFor(String id) {
        return items().lootTable(id).orElse(null);
    }

    /** Mobs rendered by a model rig (docs/MODEL_RENDERER.md): mob id → rig id. */
    private static final java.util.Map<String, String> MODELS = java.util.Map.of("mob.khasar", "khasar");

    public static String modelFor(String mobId) {
        return MODELS.get(mobId);
    }

    public static MobDefinition mobFor(String id) {
        for (MobDefinition m : List.of(GOVIIN_CHONO, ORKHON_CHONO, KHASAR)) {
            if (m.id().equals(id)) {
                return m;
            }
        }
        for (MobDefinition m : WorldContent.MOBS) {
            if (m.id().equals(id)) {
                return m;
            }
        }
        for (MobDefinition m : DungeonContent.BOSSES) {
            if (m.id().equals(id)) {
                return m;
            }
        }
        return null;
    }

    /** An item definition by its permanent id. */
    public static ItemDefinition definitionFor(String id) {
        return items().item(id).orElse(null);
    }
}
