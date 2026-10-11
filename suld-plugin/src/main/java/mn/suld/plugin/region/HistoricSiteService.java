package mn.suld.plugin.region;

import mn.suld.api.progression.ExpSource;
import mn.suld.api.region.Area;
import mn.suld.api.world.site.HistoricSite;
import mn.suld.api.world.site.SiteKind;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.Content;
import mn.suld.plugin.content.WorldContent;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.worldbuild.VillageProtection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The historical places of the Mongol lands in the wild (content/sites.json, docs/world/HISTORIC_SITES.md). Each is
 * built from its {@link SiteKind} when its chunks are first loaded by a player (never a forced load), on a levelled pad
 * at the ground's median height, remembered in sites.yml (rebuilt when its kind's build changes) and protected
 * ({@link VillageProtection#protect}). A name plate floats over it; a player's first visit shows its story with its
 * history label and pays a landmark's EXP.
 */
public final class HistoricSiteService {

    private static final long FIRST_VISIT_EXP = 60;
    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");

    private record Placed(HistoricSite site, int x, int z) {
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final File stateFile;
    private final NamespacedKey seenKey, plateKey;
    private final List<Placed> sites = new ArrayList<>();
    /** id → x, y, z, build version. */
    private final Map<String, int[]> built = new HashMap<>();
    private final Map<String, UUID> plates = new HashMap<>();

    public HistoricSiteService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        this.stateFile = new File(plugin.getDataFolder(), "sites.yml");
        this.seenKey = new NamespacedKey(plugin, "sites_seen");
        this.plateKey = new NamespacedKey(plugin, "site_plate");
    }

    /** A build's version: changes when its blocks change, so an edited kind is rebuilt. */
    static int version(SiteKind k) {
        return k.blocks().hashCode();
    }

    public void start() {
        World w = Bukkit.getWorlds().get(0);
        Location sp = w.getSpawnLocation();
        for (HistoricSite s : Content.pack().historicSites()) {
            int[] o = s.offset();
            sites.add(new Placed(s, sp.getBlockX() + o[0], sp.getBlockZ() + o[1]));
        }
        YamlConfiguration st = YamlConfiguration.loadConfiguration(stateFile);
        for (String k : st.getKeys(false)) {
            built.put(k, new int[]{st.getInt(k + ".x"), st.getInt(k + ".y"), st.getInt(k + ".z"), st.getInt(k + ".v")});
        }
        for (Placed p : sites) {
            int[] b = built.get(p.site().id());
            if (b != null) guard(p.site(), b);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::buildLoaded, 220L, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::visit, 60L, 20L);
    }

    /** Where each site stands: id → {x, z}, and whether it is built yet. */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Placed p : sites) {
            int[] b = built.get(p.site().id());
            out.add(p.site().name() + " (" + p.site().kind().label() + ", " + p.site().history() + ") " + p.x() + " " + p.z()
                    + (b == null ? " — хараахан баригдаагүй" : " — баригдсан y=" + b[1]));
        }
        return out;
    }

    /** The location of a site by its id (short or full), for staff teleports. */
    public Location location(String id) {
        for (Placed p : sites) {
            if (p.site().id().equals(id) || p.site().id().equals("site." + id)) {
                World w = Bukkit.getWorlds().get(0);
                int[] b = built.get(p.site().id());
                int y = b != null ? b[1] : w.getHighestBlockYAt(p.x(), p.z()) + 1;
                return new Location(w, p.x() + 0.5, y + 1, p.z() + p.site().kind().radius() + 2.5, 180, 0);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------ building

    private void buildLoaded() {
        World w = Bukkit.getWorlds().get(0);
        for (Placed p : sites) {
            SiteKind k = p.site().kind();
            int[] b = built.get(p.site().id());
            boolean current = b != null && b[0] == p.x() && b[2] == p.z() && b[3] == version(k);
            if (current) {
                plate(w, p, b[1]);
                continue;
            }
            if (!footprintLoaded(w, p.x(), p.z(), k.radius() + 1)) continue; // built when someone is near
            int y = b != null && b[0] == p.x() && b[2] == p.z() ? b[1] : groundLevel(w, p.x(), p.z(), k.radius()) + 1;
            build(w, p.x(), y, p.z(), k);
            int[] rec = {p.x(), y, p.z(), version(k)};
            built.put(p.site().id(), rec);
            guard(p.site(), rec);
            save();
            plugin.getLogger().info("[sites] built " + p.site().id() + " at " + p.x() + " " + y + " " + p.z());
        }
    }

    private static boolean footprintLoaded(World w, int x, int z, int r) {
        for (int dx = -r; dx <= r; dx += Math.max(1, r)) {
            for (int dz = -r; dz <= r; dz += Math.max(1, r)) if (!w.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) return false;
        }
        return true;
    }

    /** The median surface height over the footprint (trees and plants ignored), so the pad cuts and fills least. */
    private static int groundLevel(World w, int x, int z, int r) {
        List<Integer> hs = new ArrayList<>();
        for (int dx = -r; dx <= r; dx += Math.max(1, r / 2)) {
            for (int dz = -r; dz <= r; dz += Math.max(1, r / 2)) hs.add(w.getHighestBlockYAt(x + dx, z + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES));
        }
        hs.sort(Integer::compare);
        return Math.max(w.getSeaLevel(), hs.get(hs.size() / 2));
    }

    /**
     * A level pad (grass over dirt) the size of the footprint at the base height, cleared above to the build's
     * height, then the build. Physics off, so nothing falls or updates mid-build.
     */
    private void build(World w, int x, int y, int z, SiteKind k) {
        int r = k.radius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 0; dy <= k.height() + 2; dy++) {
                    Block bl = w.getBlockAt(x + dx, y + dy, z + dz);
                    if (!bl.getType().isAir()) bl.setType(Material.AIR, false);
                }
                w.getBlockAt(x + dx, y - 1, z + dz).setType(Material.GRASS_BLOCK, false);
                for (int d = 2; d <= 6; d++) {
                    Block under = w.getBlockAt(x + dx, y - d, z + dz);
                    if (under.isPassable() || under.isLiquid()) under.setType(Material.DIRT, false);
                    else break;
                }
            }
        }
        for (SiteKind.Block sb : k.blocks()) {
            BlockData data = blockData(sb.block());
            if (data != null) w.getBlockAt(x + sb.x(), y + sb.y(), z + sb.z()).setBlockData(data, false);
        }
    }

    private final Map<String, BlockData> dataCache = new HashMap<>();

    private BlockData blockData(String s) {
        return dataCache.computeIfAbsent(s, key -> {
            try {
                return Bukkit.createBlockData(key);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[sites] unknown block " + key);
                return null;
            }
        });
    }

    /** The pad and the build cannot be broken, built over, burned or blown up. */
    private static void guard(HistoricSite s, int[] b) {
        int r = s.kind().radius() + 1;
        VillageProtection.protect(s.id(), b[0] - r, b[1] - 6, b[2] - r, b[0] + r, b[1] + s.kind().height() + 3, b[2] + r);
    }

    /** A name plate over the site while its chunk is loaded (not saved with the chunk; respawned). */
    private void plate(World w, Placed p, int y) {
        if (!w.isChunkLoaded(p.x() >> 4, p.z() >> 4)) return;
        UUID id = plates.get(p.site().id());
        Entity e = id == null ? null : Bukkit.getEntity(id);
        if (e != null && e.isValid()) return;
        Location at = new Location(w, p.x() + 0.5, y + p.site().kind().height() + 1.6, p.z() + 0.5);
        for (Entity old : w.getNearbyEntities(at, 2, 3, 2)) {
            if (old instanceof TextDisplay && old.getPersistentDataContainer().has(plateKey)) old.remove();
        }
        TextDisplay t = w.spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.text(Component.text(p.site().name(), GOLD, TextDecoration.BOLD).append(Component.newline())
                    .append(Component.text(p.site().kind().label() + " · " + label(p.site().history()), NamedTextColor.GRAY)));
            d.getPersistentDataContainer().set(plateKey, PersistentDataType.STRING, p.site().id());
        });
        plates.put(p.site().id(), t.getUniqueId());
    }

    static String label(String history) {
        return switch (history) {
            case "VERIFIED" -> "Түүхэн газар";
            case "INSPIRED" -> "Түүхийг сэдэвлэсэн";
            default -> "Домог";
        };
    }

    // ------------------------------------------------------------------------------------------------ visiting

    private void visit() {
        World w = Bukkit.getWorlds().get(0);
        for (Player pl : w.getPlayers()) {
            for (Placed p : sites) {
                int[] b = built.get(p.site().id());
                if (b == null) continue;
                double reach = p.site().kind().radius() + 6;
                Location l = pl.getLocation();
                if (Math.abs(l.getX() - p.x()) > reach || Math.abs(l.getZ() - p.z()) > reach || Math.abs(l.getY() - b[1]) > 16) continue;
                Set<String> seen = seen(pl);
                if (seen.contains(p.site().id())) continue;
                seen.add(p.site().id());
                pl.getPersistentDataContainer().set(seenKey, PersistentDataType.STRING, String.join(",", seen));
                discovered(pl, p.site());
            }
        }
    }

    private Set<String> seen(Player p) {
        String s = p.getPersistentDataContainer().get(seenKey, PersistentDataType.STRING);
        Set<String> out = new HashSet<>();
        if (s != null && !s.isEmpty()) for (String id : s.split(",")) out.add(id);
        return out;
    }

    private void discovered(Player p, HistoricSite s) {
        p.showTitle(Title.title(Component.text(s.name(), GOLD, TextDecoration.BOLD),
                Component.text(s.kind().label() + " · " + label(s.history()), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofMillis(800))));
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.7f, 0.8f);
        p.sendMessage(Component.text("✦ " + s.name(), GOLD, TextDecoration.BOLD));
        p.sendMessage(Component.text(s.description(), NamedTextColor.WHITE));
        p.sendMessage(Component.text("Шошго: " + label(s.history()) + " (" + s.history() + ")", NamedTextColor.GRAY));
        var pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null || services.isSoul.test(p.getUniqueId())) return;
        Area area = WorldContent.AREAS.stream().filter(a -> a.id().equals(s.areaId())).findFirst().orElse(null);
        int areaLevel = area == null ? pr.progression().level() : (area.minLevel() + area.maxLevel()) / 2;
        long exp = Math.max(FIRST_VISIT_EXP, mn.suld.api.balance.Rewards.landmarkExp(services.curve(), areaLevel, pr.progression().level()));
        services.progression().grantExp(pr, exp, ExpSource.DISCOVERY);
        services.profiles().save(pr);
        p.sendMessage(Messages.success("Түүхэн газрыг анх үзлээ: +" + exp + " EXP"));
    }

    private void save() {
        YamlConfiguration st = new YamlConfiguration();
        built.forEach((k, v) -> {
            st.set(k + ".x", v[0]);
            st.set(k + ".y", v[1]);
            st.set(k + ".z", v[2]);
            st.set(k + ".v", v[3]);
        });
        String text = st.saveToString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.nio.file.Path tmp = stateFile.toPath().resolveSibling("sites.yml.tmp");
                java.nio.file.Files.writeString(tmp, text);
                java.nio.file.Files.move(tmp, stateFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                plugin.getLogger().warning("sites.yml: " + e.getMessage());
            }
        });
    }
}
