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
 * @param outer       ids of the outer regions (their level grows with the distance)
 * @param chapters    the story, with the EXP the rules give each chapter already filled in
 */
public record ContentPack(
        Map<String, MobDefinition> mobs,
        Map<String, String> mobRoles,
        Map<String, String> models,
        double innerEdge,
        double worldEdge,
        List<RegionDefinition> regions,
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
        List<RelicDefinition> relics) {

    public ContentPack {
        mobs = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(mobs)); // keeps the file order
        mobRoles = Map.copyOf(mobRoles);
        models = Map.copyOf(models);
        regions = List.copyOf(regions);
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
    }

    /** The mobs of a role, in file order. */
    public List<MobDefinition> mobsOf(String role) {
        return mobs.values().stream().filter(m -> role.equals(mobRoles.get(m.id()))).toList();
    }

    public MobDefinition mob(String id) {
        return mobs.get(id);
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
