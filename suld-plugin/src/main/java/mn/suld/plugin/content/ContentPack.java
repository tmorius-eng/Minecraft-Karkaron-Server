package mn.suld.plugin.content;

import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.dungeon.hall.DungeonSite;
import mn.suld.api.mob.MobDefinition;
import mn.suld.api.quest.QuestDefinition;
import mn.suld.api.region.Area;
import mn.suld.api.region.RegionDefinition;
import mn.suld.api.relic.RelicDefinition;
import mn.suld.api.worldevent.WorldEventDefinition;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All SÜLD game content read from the content files ({@code /content/*.json} in the jar, or the server's copy in
 * {@code plugins/SULD/content/}): mobs, regions and areas, the dungeon ladder, the story, world events and relics.
 * Immutable; the content classes ({@link SuldContent}, {@link WorldContent}, {@link DungeonContent},
 * {@link LadderContent}, {@link QuestContent}) are views of it.
 *
 * @param mobs        every mob by id, in file order
 * @param mobRoles    mob id → role (core, wild, ladder, boss)
 * @param models      mob id → model rig id, for the mobs that have one
 * @param regionBits  region id → its discovery bit 0..15 in the player's style row (world.json "bit")
 * @param outer       ids of the outer regions (their level grows with the distance)
 * @param chapters    the story, with the EXP the rules give each chapter already filled in
 * @param sites         the dungeon gates (dungeons.json)
 * @param historicSites the historical places in the wild (content/sites.json)
 * @param campaigns     the Их Хуралдай campaigns (content/campaigns.json)
 * @param worldBosses   the world bosses (content/events.json "worldBosses")
 * @param shops         the merchants' stock (content/shops.json)
 * @param npcs          Kharkhorum's people and their talk (content/npcs.json)
 * @param sideQuests    the side quests, in file order (content/side_quests.json, docs/SIDE_QUESTS.md)
 */
public record ContentPack(
        Map<String, MobDefinition> mobs,
        Map<String, String> mobRoles,
        Map<String, String> models,
        double innerEdge,
        double worldEdge,
        List<RegionDefinition> regions,
        Map<String, Integer> regionBits,
        Set<String> outer,
        List<Area> areas,
        List<DungeonDefinition> dungeons,
        List<DungeonSite> sites,
        Map<String, DungeonContent.Completion> completions,
        Map<String, String> dungeonRegions,
        Map<String, String> dungeonWhere,
        List<QuestDefinition> chapters,
        Map<String, QuestContent.Lore> lore,
        Map<String, String> story,
        List<WorldEventDefinition> worldEvents,
        Map<String, String> eventAliases,
        List<RelicDefinition> relics,
        List<mn.suld.api.world.site.HistoricSite> historicSites,
        List<mn.suld.api.campaign.CampaignDefinition> campaigns,
        List<mn.suld.api.worldboss.WorldBossDefinition> worldBosses,
        List<mn.suld.api.shop.ShopDefinition> shops,
        List<mn.suld.api.npc.NpcDefinition> npcs,
        List<mn.suld.api.quest.SideQuestDefinition> sideQuests) {

    public ContentPack {
        mobs = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(mobs)); // keeps the file order
        mobRoles = Map.copyOf(mobRoles);
        models = Map.copyOf(models);
        regions = List.copyOf(regions);
        regionBits = Map.copyOf(regionBits);
        outer = Set.copyOf(outer);
        areas = List.copyOf(areas);
        dungeons = List.copyOf(dungeons);
        sites = List.copyOf(sites);
        completions = Map.copyOf(completions);
        dungeonRegions = Map.copyOf(dungeonRegions);
        dungeonWhere = Map.copyOf(dungeonWhere);
        chapters = List.copyOf(chapters);
        lore = Map.copyOf(lore);
        story = Map.copyOf(story);
        worldEvents = List.copyOf(worldEvents);
        eventAliases = Map.copyOf(eventAliases);
        relics = List.copyOf(relics);
        historicSites = List.copyOf(historicSites);
        campaigns = List.copyOf(campaigns);
        worldBosses = List.copyOf(worldBosses);
        shops = List.copyOf(shops);
        npcs = List.copyOf(npcs);
        sideQuests = List.copyOf(sideQuests);
    }

    /** A shop by id, or null. */
    public mn.suld.api.shop.ShopDefinition shop(String id) {
        for (mn.suld.api.shop.ShopDefinition s : shops) if (s.id().equals(id)) return s;
        return null;
    }

    /** The NPC entry that serves a city point or a dynamic role id, or null. */
    public mn.suld.api.npc.NpcDefinition npc(String pointOrRoleId) {
        if (pointOrRoleId == null) return null;
        for (mn.suld.api.npc.NpcDefinition n : npcs) if (n.serves(pointOrRoleId)) return n;
        return null;
    }

    /** A world boss by id, or null. */
    public mn.suld.api.worldboss.WorldBossDefinition worldBoss(String id) {
        for (mn.suld.api.worldboss.WorldBossDefinition b : worldBosses) if (b.id().equals(id)) return b;
        return null;
    }

    /** A Хуралдай campaign by id, or null. */
    public mn.suld.api.campaign.CampaignDefinition campaign(String id) {
        for (mn.suld.api.campaign.CampaignDefinition c : campaigns) if (c.id().equals(id)) return c;
        return null;
    }

    /** The mobs of a role, in file order. */
    public List<MobDefinition> mobsOf(String role) {
        return mobs.values().stream().filter(m -> role.equals(mobRoles.get(m.id()))).toList();
    }

    public MobDefinition mob(String id) {
        return mobs.get(id);
    }

    /** The region's discovery bit 0..15, or -1 for a region the content does not know. */
    public int regionBit(String id) {
        Integer b = regionBits.get(id);
        return b == null ? -1 : b;
    }

    public RegionDefinition region(String id) {
        for (RegionDefinition r : regions) if (r.id().equals(id)) return r;
        return null;
    }

    public DungeonDefinition dungeon(String id) {
        for (DungeonDefinition d : dungeons) if (d.id().equals(id)) return d;
        return null;
    }
}
