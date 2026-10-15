package mn.suld.plugin.map;

import mn.suld.api.dungeon.hall.DungeonSite;
import mn.suld.api.map.Cartography;
import mn.suld.api.region.Area;
import mn.suld.api.region.RegionDefinition;
import mn.suld.api.region.RegionShape;
import mn.suld.api.world.site.HistoricSite;
import mn.suld.plugin.content.ContentPack;

import java.util.ArrayList;
import java.util.List;

/**
 * The world's layout as the baked cartography reads it (docs/WORLD_MAP.md, «Baked cartography»), from the loaded
 * content: the regions' and areas' sectors (world.json), the capital's square, and the dungeon gates and historic sites
 * the caravan roads lead to (their offsets from the spawn, the same the game places them by). Pure: no Bukkit call.
 */
final class CartoContent {

    private CartoContent() {
    }

    static Cartography.Layout layout(ContentPack pack, double spawnX, double spawnZ) {
        double cityHalf = 0;
        List<Cartography.Land> regions = new ArrayList<>();
        for (RegionDefinition r : pack.regions()) {
            if (r.shape() instanceof RegionShape.Sector s) {
                regions.add(new Cartography.Land(r.id(), s.minRadius(), s.maxRadius(), s.fromDeg(), s.toDeg()));
            } else if (r.shape() instanceof RegionShape.Square sq && r.safeZone()) {
                cityHalf = Math.max(cityHalf, sq.half());
            }
        }
        List<Cartography.Land> areas = new ArrayList<>();
        for (Area a : pack.areas()) areas.add(new Cartography.Land(a.id(), a.minRadius(), a.maxRadius(), a.fromDeg(), a.toDeg()));
        List<Cartography.Spot> spots = new ArrayList<>();
        for (DungeonSite s : pack.sites()) spots.add(new Cartography.Spot(s.offset()[0], s.offset()[1]));
        for (HistoricSite s : pack.historicSites()) spots.add(new Cartography.Spot(s.offset()[0], s.offset()[1]));
        return new Cartography.Layout(spawnX, spawnZ, cityHalf, regions, areas, spots);
    }
}
