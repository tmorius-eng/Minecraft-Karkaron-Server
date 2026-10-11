package mn.suld.plugin.content;

import mn.suld.api.dungeon.BossDefinition;
import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemCatalogLoader;
import mn.suld.api.loot.LootTable;
import mn.suld.api.mob.MobDefinition;
import mn.suld.api.quest.QuestDefinition;
import mn.suld.api.relic.RelicDefinition;
import mn.suld.api.worldevent.WorldEventDefinition;

/**
 * The content entry point: the mobs, dungeons, events and relics the code names directly, and lookups by id. All of
 * it is read from the content files ({@code content/*.json}, docs/CONTENT_DATA.md); items and loot come from the item
 * catalog (suld-api/src/main/resources/items, data-folder override).
 */
public final class SuldContent {

    private SuldContent() {
    }

    // --- the first hunt and Хасарын Агуй (Khasar's Den)
    public static final MobDefinition GOVIIN_CHONO = Content.mob("mob.goviin_chono");
    public static final MobDefinition ORKHON_CHONO = Content.mob("mob.orkhon_chono");
    public static final MobDefinition KHASAR = Content.mob("mob.khasar");

    /** The story's first chapter. */
    public static final QuestDefinition FIRST_HUNT = Content.pack().chapters().stream()
            .filter(q -> q.id().equals("quest.first_hunt")).findFirst().orElseThrow();

    public static final DungeonDefinition KHASAR_DEN = Content.pack().dungeon("dungeon.khasar_den");
    public static final BossDefinition KHASAR_BOSS = KHASAR_DEN.bossDefinition();

    // --- clans (social progression)

    /** Clan EXP per SÜLD mob kill, per dungeon clear (per participant). */
    public static final long CLAN_EXP_PER_MOB_KILL = 2;
    public static final long CLAN_EXP_PER_DUNGEON_CLEAR = 150;

    // --- world events

    /** Чонын Довтолгоо — wolves raid the steppe. */
    public static final WorldEventDefinition WOLF_RAID = worldEventFor("event.chonyn_dovtolgoo");

    public static WorldEventDefinition worldEventFor(String id) {
        String key = Content.pack().eventAliases().getOrDefault(id, id);
        for (WorldEventDefinition e : Content.pack().worldEvents()) if (e.id().equals(key)) return e;
        return null;
    }

    // --- world-unique relics

    /** Хөх Сүлд — the Blue Standard (SÜLD fiction), the flagship relic. */
    public static final RelicDefinition KHUKH_SULD = relicFor("relic.khukh_suld");
    /** Алтан Гэрэгэ — the Golden Paiza. */
    public static final RelicDefinition ALTAN_GEREGE = relicFor("relic.altan_gerege");

    public static final java.util.List<RelicDefinition> RELICS = Content.pack().relics();

    public static RelicDefinition relicFor(String key) {
        for (RelicDefinition r : Content.pack().relics()) {
            if (r.key().equals(key) || r.key().equals("relic." + key)) {
                return r;
            }
        }
        return null;
    }

    public static DungeonDefinition dungeonFor(String id) {
        return Content.pack().dungeon(id);
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

    /** The model rig of a mob ("model" in mobs.json), or null (docs/MODEL_RENDERER.md). */
    public static String modelFor(String mobId) {
        return Content.pack().models().get(mobId);
    }

    public static MobDefinition mobFor(String id) {
        return id == null ? null : Content.pack().mob(id);
    }

    /** An item definition by its permanent id. */
    public static ItemDefinition definitionFor(String id) {
        return items().item(id).orElse(null);
    }
}
