package mn.suld.plugin.region;

import mn.suld.api.region.Area;
import mn.suld.api.region.OvooCircle;
import mn.suld.api.progression.ExpSource;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.WorldContent;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ovoo at the heart of every named area (docs/world/OVOO.md). An ovoo is a sacred cairn of the steppe; the custom is
 * to walk around it three times clockwise and add a stone (VERIFIED tradition). Walking the three turns grants
 * «Тэнгэрийн ивээл»: +5 % EXP for 30 minutes and a minute of regeneration, once per ovoo per day; the first visit
 * of each ovoo also pays a little EXP. The cairns are built when their chunk is first loaded by a player (never a
 * forced load) and remembered in ovoo.yml. Checks run every 10 ticks, only for players within 10 blocks of an ovoo.
 */
public final class OvooService implements Listener {

    private static final long BLESS_MS = 30 * 60_000L;
    private static final double BLESS_EXP = 0.05;
    private static final long FIRST_VISIT_EXP = 40;

    record Ovoo(Area area, int x, int z) {
        String id() {
            return area.id().substring("area.".length());
        }
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final File stateFile;
    private final List<Ovoo> ovoos = new ArrayList<>();
    private final Map<String, int[]> built = new HashMap<>(); // id → x, y, z
    private final Map<UUID, Map<String, OvooCircle>> walking = new HashMap<>();
    private final Map<UUID, String> hinted = new HashMap<>();
    private final NamespacedKey blessKey;

    public OvooService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        this.stateFile = new File(plugin.getDataFolder(), "ovoo.yml");
        this.blessKey = new NamespacedKey(plugin, "ovoo_bless_until");
    }

    public void start() {
        World w = Bukkit.getWorlds().get(0);
        Location sp = w.getSpawnLocation();
        double border = w.getWorldBorder().getSize() / 2;
        for (Area a : WorldContent.AREAS) {
            double span = ((a.toDeg() - a.fromDeg()) % 360 + 360) % 360;
            double mid = Math.toRadians(a.fromDeg() + (span == 0 ? 360 : span) / 2);
            double r = (a.minRadius() + Math.min(a.maxRadius(), border - 64)) / 2;
            ovoos.add(new Ovoo(a, sp.getBlockX() + (int) Math.round(Math.sin(mid) * r), sp.getBlockZ() + (int) Math.round(-Math.cos(mid) * r)));
        }
        YamlConfiguration st = YamlConfiguration.loadConfiguration(stateFile);
        for (String k : st.getKeys(false)) built.put(k, new int[]{st.getInt(k + ".x"), st.getInt(k + ".y"), st.getInt(k + ".z")});
        Bukkit.getScheduler().runTaskTimer(plugin, this::buildLoaded, 200L, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::track, 40L, 10L);
        for (Player p : Bukkit.getOnlinePlayers()) loadBlessing(p);
    }

    /** Where each area's ovoo is (for maps and the tutorial). */
    public List<int[]> positions() {
        List<int[]> out = new ArrayList<>();
        for (Ovoo o : ovoos) out.add(new int[]{o.x(), o.z()});
        return out;
    }

    // ------------------------------------------------------------------ building

    private void buildLoaded() {
        World w = Bukkit.getWorlds().get(0);
        for (Ovoo o : ovoos) {
            int[] b = built.get(o.id());
            if (b != null && b[0] == o.x() && b[2] == o.z()) continue;
            if (!w.isChunkLoaded(o.x() >> 4, o.z() >> 4)) continue; // never force a load: built when someone is near
            int y = w.getHighestBlockYAt(o.x(), o.z(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
            build(w, o.x(), y + 1, o.z());
            built.put(o.id(), new int[]{o.x(), y + 1, o.z()});
            save();
        }
    }

    /** A cone of stones (5 wide at the base), a spruce pole with blue silk khadag on top, three stones to add. */
    private static void build(World w, int x, int y, int z) {
        Material[] stones = {Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.STONE, Material.ANDESITE, Material.TUFF};
        int k = 0;
        for (int layer = 0; layer < 4; layer++) {
            double r = 2.4 - layer * 0.65;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.hypot(dx, dz) > r) continue;
                    w.getBlockAt(x + dx, y + layer, z + dz).setType(stones[Math.floorMod(k++ * 7 + dx * 3 + dz, stones.length)], false);
                }
            }
            // a solid base under the bottom layer so the cairn never floats on uneven ground
            if (layer == 0) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                for (int d = 1; d <= 3; d++) {
                    if (w.getBlockAt(x + dx, y - d, z + dz).isPassable()) w.getBlockAt(x + dx, y - d, z + dz).setType(Material.COBBLESTONE, false);
                }
            }
        }
        for (int h = 4; h <= 7; h++) w.getBlockAt(x, y + h, z).setType(Material.SPRUCE_FENCE, false);
        w.getBlockAt(x + 1, y + 6, z).setType(Material.LIGHT_BLUE_WOOL, false);
        w.getBlockAt(x - 1, y + 5, z).setType(Material.BLUE_WOOL, false);
        w.getBlockAt(x, y + 6, z + 1).setType(Material.WHITE_WOOL, false);
        w.getBlockAt(x, y + 8, z).setType(Material.LIGHT_BLUE_BANNER, false);
    }

    private void save() {
        YamlConfiguration st = new YamlConfiguration();
        built.forEach((k, v) -> {
            st.set(k + ".x", v[0]);
            st.set(k + ".y", v[1]);
            st.set(k + ".z", v[2]);
        });
        String text = st.saveToString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.nio.file.Path tmp = stateFile.toPath().resolveSibling("ovoo.yml.tmp");
                java.nio.file.Files.writeString(tmp, text);
                java.nio.file.Files.move(tmp, stateFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                plugin.getLogger().warning("ovoo.yml: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------ walking around

    private void track() {
        World w = Bukkit.getWorlds().get(0);
        for (Ovoo o : ovoos) {
            int[] b = built.get(o.id());
            if (b == null || !w.isChunkLoaded(o.x() >> 4, o.z() >> 4)) continue;
            for (Player p : w.getPlayers()) {
                double dx = p.getLocation().getX() - (b[0] + 0.5), dz = p.getLocation().getZ() - (b[2] + 0.5);
                if (dx * dx + dz * dz > 14 * 14 || Math.abs(p.getLocation().getY() - b[1]) > 6) continue;
                OvooCircle c = walking.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).computeIfAbsent(o.id(), k -> new OvooCircle());
                if (c.step(dx, dz)) {
                    bless(p, o, b);
                } else if (!o.id().equals(hinted.get(p.getUniqueId())) && dx * dx + dz * dz < 100) {
                    hinted.put(p.getUniqueId(), o.id());
                    if (services.hud() != null) {
                        services.hud().toast(p, Component.text("Овоо — нар зөв (цагийн зүүний дагуу) гурвантаа тойрвол Тэнгэр ивээнэ", NamedTextColor.AQUA), 5000);
                    }
                }
            }
        }
    }

    private void bless(Player p, Ovoo o, int[] b) {
        String key = "ovoo_" + o.id();
        NamespacedKey dayKey = new NamespacedKey(plugin, key);
        long today = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        Long last = p.getPersistentDataContainer().get(dayKey, PersistentDataType.LONG);
        Location top = new Location(p.getWorld(), b[0] + 0.5, b[1] + 6, b[2] + 0.5);
        p.getWorld().spawnParticle(Particle.END_ROD, top, 30, 1.2, 1.5, 1.2, 0.02);
        p.playSound(top, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        if (last != null && last == today) {
            p.sendMessage(Messages.info("Энэ овоог өнөөдөр аль хэдийн тойрсон байна. Маргааш дахин ир."));
            return;
        }
        p.getPersistentDataContainer().set(dayKey, PersistentDataType.LONG, today);
        long until = System.currentTimeMillis() + BLESS_MS;
        services.boosts().bless(p.getUniqueId(), until, BLESS_EXP);
        p.getPersistentDataContainer().set(blessKey, PersistentDataType.LONG, until);
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 60, 0, true, true));
        if (last == null) {
            services.profiles().cached(p.getUniqueId()).ifPresent(pr -> {
                services.progression().grantExp(pr, FIRST_VISIT_EXP, ExpSource.DISCOVERY);
                services.profiles().save(pr);
            });
        }
        p.sendMessage(Messages.success("«" + o.area().name() + "»-ийн овоог нар зөв гурвантаа тойрлоо. Тэнгэр ивээг! "
                + "(+5% EXP 30 мин" + (last == null ? ", анхны айлчлал +" + FIRST_VISIT_EXP + " EXP" : "") + ")"));
    }

    private void loadBlessing(Player p) {
        Long until = p.getPersistentDataContainer().get(blessKey, PersistentDataType.LONG);
        if (until != null && until > System.currentTimeMillis()) services.boosts().bless(p.getUniqueId(), until, BLESS_EXP);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        loadBlessing(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        walking.remove(e.getPlayer().getUniqueId());
        hinted.remove(e.getPlayer().getUniqueId());
        services.boosts().forget(e.getPlayer().getUniqueId());
    }
}
