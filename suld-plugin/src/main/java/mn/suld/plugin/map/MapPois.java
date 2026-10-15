package mn.suld.plugin.map;

import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.dungeon.hall.DungeonSite;
import mn.suld.api.world.site.HistoricSite;
import mn.suld.api.worldboss.WorldBossDefinition;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.Content;
import mn.suld.plugin.dungeon.DungeonHalls;
import mn.suld.plugin.npc.NpcService;
import mn.suld.plugin.training.TrainingService;
import mn.suld.plugin.worldbuild.WorldBuildService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The atlas's places (docs/WORLD_MAP.md): where the game really puts its city, relays, dungeon gates, ovoos, historic
 * sites, world bosses and the training yard, read from the same data and services that place them in the world, so a
 * marker always stands where the thing stands. Main thread.
 */
public final class MapPois {

    /** A kind of place: one atlas tab each. */
    public enum Kind {
        CITY("Хот"),
        RELAY("Өртөө"),
        DUNGEON("Агуй"),
        OVOO("Овоо"),
        SITE("Түүхэн газар"),
        BOSS("Аварга дайсан"),
        TRAINING("Дасгалын талбай");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** One place: {@code area} and {@code region} name the land it lies in where the game knows it, else "". */
    public record Poi(String id, Kind kind, String name, int x, int z, String area, String region) {

        Poi(String id, Kind kind, String name, int x, int z, String area) {
            this(id, kind, name, x, z, area, "");
        }
    }

    private MapPois() {
    }

    /** How long a collected list of places is kept: they move only when the world is rebuilt or a site is placed. */
    public static final long CACHE_MS = 5_000;

    /**
     * {@code collect} read at most once every {@code ttlMs} ({@code clock} in ms): the minimap asked for every place in
     * the world (relays, dungeons, sites, every ovoo's nearest area) on the main thread up to five times a second per
     * player, and the atlas on every pan.
     */
    public static <T> java.util.function.Supplier<T> cached(java.util.function.Supplier<T> collect, long ttlMs, java.util.function.LongSupplier clock) {
        return new java.util.function.Supplier<>() {
            private T value;
            private long at;
            private boolean read;

            @Override
            public synchronized T get() {
                long now = clock.getAsLong();
                if (!read || now - at >= ttlMs) {
                    value = collect.get();
                    at = now;
                    read = true;
                }
                return value;
            }
        };
    }

    /** Every place in the overworld, in a stable order (kind, then the content files' order). */
    public static List<Poi> collect(SuldServices services, WorldBuildService city, DungeonHalls halls, FileConfiguration cfg) {
        World w = Bukkit.getWorlds().get(0);
        Location spawn = w.getSpawnLocation();
        List<Poi> out = new ArrayList<>();
        out.add(new Poi("city.kharkhorum", Kind.CITY, "Хархорум", spawn.getBlockX(), spawn.getBlockZ(), ""));
        if (city != null) {
            for (Map.Entry<String, String> r : NpcService.relays().entrySet()) {
                Location at = city.pointLocation(r.getKey());
                if (at != null && at.getWorld() == w) out.add(new Poi(r.getKey(), Kind.RELAY, r.getValue(), at.getBlockX(), at.getBlockZ(), "Хархорум"));
            }
        }
        if (halls != null) {
            Map<String, String> names = new HashMap<>();
            for (DungeonDefinition d : Content.pack().dungeons()) names.put(d.id(), d.displayName());
            for (DungeonSite s : Content.pack().sites()) {
                int[] xz = halls.gateXZ(s);
                out.add(new Poi(s.dungeonId(), Kind.DUNGEON, names.getOrDefault(s.dungeonId(), s.dungeonId()), xz[0], xz[1], ""));
            }
        }
        var ovoos = services.ovoos;
        if (ovoos != null) {
            Map<String, String> regionOfArea = new HashMap<>(), regionNames = new HashMap<>();
            for (mn.suld.api.region.RegionDefinition r : Content.pack().regions()) regionNames.put(r.id(), r.displayName());
            for (mn.suld.api.region.Area a : Content.pack().areas()) regionOfArea.put(a.name(), regionNames.getOrDefault(a.regionId(), ""));
            int i = 0;
            for (int[] xz : ovoos.positions()) {
                String area = ovoos.nearestName(new Location(w, xz[0], 64, xz[1]));
                out.add(new Poi("ovoo." + i++, Kind.OVOO, area == null ? "Овоо" : area + "-ийн овоо", xz[0], xz[1],
                        area == null ? "" : area, area == null ? "" : regionOfArea.getOrDefault(area, "")));
            }
        }
        Map<String, int[]> sites = new HashMap<>();
        Map<String, String> siteNames = new HashMap<>();
        var placed = services.historicSites; // where the sites stand (it follows the spawn), never recomputed here
        for (HistoricSite s : Content.pack().historicSites()) {
            Location at = placed == null ? null : placed.where(s.id()).orElse(null);
            int[] o = s.offset();
            int x = at != null ? at.getBlockX() : spawn.getBlockX() + o[0], z = at != null ? at.getBlockZ() : spawn.getBlockZ() + o[1];
            sites.put(s.id(), new int[]{x, z});
            siteNames.put(s.id(), s.name());
            out.add(new Poi(s.id(), Kind.SITE, s.name(), x, z, areaName(s.areaId())));
        }
        for (WorldBossDefinition b : Content.pack().worldBosses()) {
            int[] at = sites.get(b.siteId());
            if (at == null) continue;
            out.add(new Poi(b.id(), Kind.BOSS, mn.suld.plugin.worldboss.WorldBossService.name(b), at[0] + b.dx(), at[1] + b.dz(),
                    siteNames.getOrDefault(b.siteId(), "")));
        }
        String tw = cfg.getString("training.world", "");
        if (tw == null || tw.isBlank() || tw.equals(w.getName())) {
            out.add(new Poi("training.yard", Kind.TRAINING, "Дайчдын Дасгалын Талбай", cfg.getInt("training.x", TrainingService.DEFAULT_X),
                    cfg.getInt("training.z", TrainingService.DEFAULT_Z), "Хархорум"));
        }
        return out;
    }

    private static String areaName(String areaId) {
        if (areaId == null) return "";
        for (mn.suld.api.region.Area a : Content.pack().areas()) if (a.id().equals(areaId)) return a.name();
        return "";
    }

    /** The places as JSON (for the PNG export and outside tools): {"pois":[{"id","kind","name","x","z","area","region"}…]}. */
    public static String json(List<Poi> pois) {
        StringBuilder b = new StringBuilder("{\"pois\":[");
        for (int i = 0; i < pois.size(); i++) {
            Poi p = pois.get(i);
            if (i > 0) b.append(',');
            b.append("\n {\"id\":").append(quote(p.id())).append(",\"kind\":").append(quote(p.kind().name().toLowerCase(Locale.ROOT)))
                    .append(",\"name\":").append(quote(p.name())).append(",\"x\":").append(p.x()).append(",\"z\":").append(p.z())
                    .append(",\"area\":").append(quote(p.area())).append(",\"region\":").append(quote(p.region())).append('}');
        }
        return b.append("\n]}\n").toString();
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                default -> {
                    if (c < 0x20) b.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
}
