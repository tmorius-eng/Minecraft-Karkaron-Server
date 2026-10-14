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
 * forced load) and remembered in ovoo.yml. Checks run every 10 ticks, only for players within 14 blocks of an ovoo.
 * <p>
 * A soul is not blessed: it walks «Овооны цацал» instead (docs/DEATH_AND_RECOVERY.md, 2026-10-13; the game's own
 * rite, INSPIRED by the milk offering): the same three sunwise rounds, then it stays by the cairn until the ritual
 * has lasted {@code death.ritual-seconds}; then it lives again ({@link mn.suld.api.death.OvooRitual}). A soul without
 * a timer rises beside the ovoo nearest to where it fell ({@link #soulRise}).
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
    /** Where every area's ovoo stands, by the spawn (replaced whole when the spawn moves; read from any thread). */
    private volatile List<Ovoo> ovoos = List.of();
    private final Map<String, int[]> built = new HashMap<>(); // id → x, y, z
    private final Map<UUID, Map<String, OvooCircle>> walking = new HashMap<>();
    private final Map<UUID, String> hinted = new HashMap<>();
    private final NamespacedKey blessKey;
    /** A soul's «Овооны цацал» in progress, and the ovoo it is at. */
    private final Map<UUID, mn.suld.api.death.OvooRitual> rituals = new HashMap<>();
    private final Map<UUID, String> ritualAt = new HashMap<>();
    /** The death system (set by SuldPlugin): a finished ritual ends the soul state. */
    public volatile mn.suld.plugin.death.DeathService deaths;

    public OvooService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        this.stateFile = new File(plugin.getDataFolder(), "ovoo.yml");
        this.blessKey = new NamespacedKey(plugin, "ovoo_bless_until");
    }

    /**
     * False while the city is on its way and will move the spawn (SuldPlugin: WorldBuildService.spawnSettled): no cairn
     * is built until it is (the pre-generator loads the hearts of the areas), or it would stand by the first spawn of a
     * fresh world and again by the city's.
     */
    public volatile java.util.function.BooleanSupplier spawnSettled = () -> true;

    private static List<Ovoo> place() {
        World w = Bukkit.getWorlds().get(0);
        Location sp = w.getSpawnLocation();
        double border = w.getWorldBorder().getSize() / 2;
        List<Ovoo> out = new ArrayList<>();
        for (Area a : WorldContent.AREAS) {
            double span = ((a.toDeg() - a.fromDeg()) % 360 + 360) % 360;
            double mid = Math.toRadians(a.fromDeg() + (span == 0 ? 360 : span) / 2);
            double r = (a.minRadius() + Math.min(a.maxRadius(), border - 64)) / 2;
            out.add(new Ovoo(a, sp.getBlockX() + (int) Math.round(Math.sin(mid) * r), sp.getBlockZ() + (int) Math.round(-Math.cos(mid) * r)));
        }
        return List.copyOf(out);
    }

    /**
     * The spawn moved (SuldPlugin, SpawnChangeEvent: the city's first build, a rollback, /setworldspawn): every ovoo
     * stands at the heart of its area by the new spawn, as the area rings do; a cairn built by the old spawn is built
     * again by the new one when someone comes near.
     */
    public void spawnMoved() {
        List<Ovoo> next = place();
        if (next.equals(ovoos)) return;
        ovoos = next;
        Location sp = Bukkit.getWorlds().get(0).getSpawnLocation();
        plugin.getLogger().info("[ovoo] the spawn moved to " + sp.getBlockX() + " " + sp.getBlockZ() + ": " + next.size() + " ovoos stand by it");
    }

    public void start() {
        ovoos = place();
        YamlConfiguration st = YamlConfiguration.loadConfiguration(stateFile);
        for (String k : st.getKeys(false)) {
            built.put(k, new int[]{st.getInt(k + ".x"), st.getInt(k + ".y"), st.getInt(k + ".z")});
            guard(k, built.get(k));
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::buildLoaded, 200L, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::track, 40L, 10L);
        for (Player p : Bukkit.getOnlinePlayers()) loadBlessing(p);
    }

    /** The nearest ovoo's {x, z} to {@code l}, or null when none is placed. */
    public int[] nearest(Location l) {
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (Ovoo o : ovoos) {
            double d = (o.x() - l.getX()) * (o.x() - l.getX()) + (o.z() - l.getZ()) * (o.z() - l.getZ());
            if (d < bestD) {
                bestD = d;
                best = new int[]{o.x(), o.z()};
            }
        }
        return best;
    }

    /**
     * Where a soul rises after a death at {@code fell} (DeathService, 2026-10-13): five blocks south of the ovoo nearest
     * to it, inside the ritual ring, on the ground, facing the cairn; null when no ovoo is placed. The chunk is loaded
     * by the respawn anyway.
     */
    public Location soulRise(Location fell) {
        World w = Bukkit.getWorlds().get(0);
        if (fell.getWorld() == null || !fell.getWorld().equals(w)) return null;
        Ovoo best = null;
        double bestD = Double.MAX_VALUE;
        for (Ovoo o : ovoos) {
            double d = (o.x() - fell.getX()) * (o.x() - fell.getX()) + (o.z() - fell.getZ()) * (o.z() - fell.getZ());
            if (d < bestD) {
                bestD = d;
                best = o;
            }
        }
        if (best == null) return null;
        int x = best.x(), z = best.z() + 5;
        int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        Location at = new Location(w, x + 0.5, y, z + 0.5);
        at.setYaw(180f); // facing north, toward the cairn
        return at;
    }

    /** The name of the area whose ovoo is nearest to {@code l} (for the soul's messages), or null. */
    public String nearestName(Location l) {
        Ovoo best = null;
        double bestD = Double.MAX_VALUE;
        for (Ovoo o : ovoos) {
            double d = (o.x() - l.getX()) * (o.x() - l.getX()) + (o.z() - l.getZ()) * (o.z() - l.getZ());
            if (d < bestD) {
                bestD = d;
                best = o;
            }
        }
        return best == null ? null : best.area().name();
    }

    /** Where each area's ovoo is (for maps and the tutorial). */
    public List<int[]> positions() {
        List<int[]> out = new ArrayList<>();
        for (Ovoo o : ovoos) out.add(new int[]{o.x(), o.z()});
        return out;
    }

    // ------------------------------------------------------------------ building

    private void buildLoaded() {
        if (!spawnSettled.getAsBoolean()) return;
        World w = Bukkit.getWorlds().get(0);
        for (Ovoo o : ovoos) {
            int[] b = built.get(o.id());
            if (b != null && b[0] == o.x() && b[2] == o.z()) continue;
            if (!footprintLoaded(w, o.x(), o.z())) continue; // never force a load: built when someone is near
            int y = w.getHighestBlockYAt(o.x(), o.z(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
            build(w, o.x(), y + 1, o.z());
            built.put(o.id(), new int[]{o.x(), y + 1, o.z()});
            guard(o.id(), built.get(o.id()));
            save();
            plugin.getLogger().info("[ovoo] built " + o.id() + " at " + o.x() + " " + (y + 1) + " " + o.z());
        }
    }

    /** The cairn, its pole and its base cannot be broken or blown up (VillageProtection). */
    private static void guard(String id, int[] b) {
        mn.suld.plugin.worldbuild.VillageProtection.protect("ovoo." + id, b[0] - 3, b[1] - 4, b[2] - 3, b[0] + 3, b[1] + 8, b[2] + 3);
    }

    /** The cairn reaches 3 blocks out (base and stones): every chunk it touches must already be loaded. */
    private static boolean footprintLoaded(World w, int x, int z) {
        for (int dx = -3; dx <= 3; dx += 6) {
            for (int dz = -3; dz <= 3; dz += 6) if (!w.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) return false;
        }
        return true;
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
        java.util.Set<UUID> stepped = new java.util.HashSet<>();
        for (Ovoo o : ovoos) {
            int[] b = built.get(o.id());
            if (b == null || !w.isChunkLoaded(o.x() >> 4, o.z() >> 4)) continue;
            for (Player p : w.getPlayers()) {
                double dx = p.getLocation().getX() - (b[0] + 0.5), dz = p.getLocation().getZ() - (b[2] + 0.5);
                if (dx * dx + dz * dz > 14 * 14 || Math.abs(p.getLocation().getY() - b[1]) > 6) continue;
                if (services.training != null && services.training.training(p.getUniqueId())) continue; // the day's mark would stay, its EXP not
                if (services.isSoul.test(p.getUniqueId())) { // a soul is not blessed: it walks the ritual back to life
                    stepped.add(p.getUniqueId());
                    ritual(p, o, b, dx, dz);
                    continue;
                }
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
        // a ritual whose soul was not by its ovoo this pass is broken, whatever took the soul away (a walk, a fall, a
        // teleport, a revive elsewhere): it is walked in one go
        for (java.util.Iterator<Map.Entry<UUID, mn.suld.api.death.OvooRitual>> it = rituals.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, mn.suld.api.death.OvooRitual> en = it.next();
            if (stepped.contains(en.getKey())) continue;
            it.remove();
            ritualAt.remove(en.getKey());
            Player p = Bukkit.getPlayer(en.getKey());
            if (en.getValue().started() && p != null && services.isSoul.test(en.getKey())) {
                p.sendMessage(Messages.error("Цацал тасарлаа: овооны дэргэдээс холдсон тул дахин эхэлнэ."));
            }
        }
    }

    /** One step of a soul's «Овооны цацал» at {@code o} (every 10 ticks while it is within 14 blocks). */
    private void ritual(Player p, Ovoo o, int[] b, double dx, double dz) {
        UUID id = p.getUniqueId();
        mn.suld.plugin.death.DeathService d = deaths;
        if (d == null || p.isDead()) return;
        if (services.inDeathTrial.test(id)) { // fighting its trial: the ritual starts over afterwards
            rituals.remove(id);
            ritualAt.remove(id);
            return;
        }
        mn.suld.api.death.OvooRitual r = rituals.get(id);
        if (r == null || !o.id().equals(ritualAt.get(id))) {
            r = new mn.suld.api.death.OvooRitual(services.config().death().ritualSeconds());
            rituals.put(id, r);
            ritualAt.put(id, o.id());
        }
        long now = System.currentTimeMillis();
        mn.suld.api.death.OvooRitual.Step step = r.step(dx, dz, now);
        switch (step) {
            case IDLE -> {
                if (!("ritual:" + o.id()).equals(hinted.get(id))) {
                    hinted.put(id, "ritual:" + o.id());
                    p.sendMessage(Messages.info("Овооны цацал: овоог нар зөв (цагийн зүүний дагуу) гурвантаа тойроод, "
                            + r.seconds() + " секунд хүртэл дэргэд нь бай — сүнс тань амилна."));
                }
            }
            case WALKING, OFFERING -> mn.suld.plugin.hud.HudService.notice(p, Component.text("Овооны цацал: "
                    + (int) Math.floor(r.rounds()) + "/" + (int) OvooCircle.TURNS + " тойрог · "
                    + Math.min(r.elapsedSeconds(now), r.seconds()) + "/" + r.seconds() + " с"
                    + (step == mn.suld.api.death.OvooRitual.Step.OFFERING ? " · өргөл: овооны дэргэд бай" : ""), NamedTextColor.AQUA));
            case BROKEN -> p.sendMessage(Messages.error("Цацал тасарлаа: овооны дэргэдээс холдсон тул дахин эхэлнэ."));
            case DONE -> {
                rituals.remove(id);
                ritualAt.remove(id);
                Location top = new Location(p.getWorld(), b[0] + 0.5, b[1] + 6, b[2] + 0.5);
                d.recover(id, mn.suld.api.death.DeathRecord.State.RECOVERED, "ovoo").thenAccept(had -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!had || !p.isOnline()) return;
                    p.sendMessage(Messages.success("«" + o.area().name() + "»-ийн овоонд цацал өргөлөө: Тэнгэр сүнсийг тань бие рүү буцаалаа (шарх үлдэнэ)."));
                    p.getWorld().spawnParticle(Particle.END_ROD, top, 40, 1.2, 1.5, 1.2, 0.02);
                    p.playSound(top, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
                }));
            }
        }
    }

    private void bless(Player p, Ovoo o, int[] b) {
        String key = "ovoo_" + o.id();
        NamespacedKey dayKey = new NamespacedKey(plugin, key);
        // the day turns at midnight in Ulaanbaatar, like the daily rewards and tasks (daily.timezone)
        long today = LocalDate.now(zone()).toEpochDay();
        Long last = p.getPersistentDataContainer().get(dayKey, PersistentDataType.LONG);
        Location top = new Location(p.getWorld(), b[0] + 0.5, b[1] + 6, b[2] + 0.5);
        p.getWorld().spawnParticle(Particle.END_ROD, top, 30, 1.2, 1.5, 1.2, 0.02);
        p.playSound(top, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        if (last != null && last == today) {
            p.sendMessage(Messages.info("Энэ овоог өнөөдөр аль хэдийн тойрсон байна. Маргааш дахин ир."));
            return;
        }
        p.getPersistentDataContainer().set(dayKey, PersistentDataType.LONG, today);
        // the "visited" guard reaches disk before the EXP is committed, so a crash cannot pay the first visit twice (PERS-002)
        mn.suld.plugin.item.PlayerDataSaves.now(p);
        services.marks().record(p.getUniqueId(), mn.suld.api.progress.MarkKind.OVOO_DAY, o.id(), today); // V17 mirror
        services.achievements.record(p, mn.suld.api.achievement.Deed.OVOO, o.id(), 1);
        long until = System.currentTimeMillis() + BLESS_MS;
        services.boosts().bless(p.getUniqueId(), until, BLESS_EXP);
        p.getPersistentDataContainer().set(blessKey, PersistentDataType.LONG, until);
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 60, 0, true, true));
        // an OVOO_RITUAL chapter on this ovoo's land is done by the blessing (audit 2026-10-14)
        services.profiles().cached(p.getUniqueId()).ifPresent(qp -> {
            if (!services.quests().onOvooRitual(p, qp, o.area().id()) && o.area().regionId() != null) {
                services.quests().onOvooRitual(p, qp, o.area().regionId()); // a chapter may name the region instead
            }
        });
        long first = 0;
        if (last == null) {
            var pr = services.profiles().cached(p.getUniqueId()).orElse(null);
            if (pr != null) {
                // a landmark (progression v2, Rewards.landmarkExp): 2 % of a level at the area's level × the gap
                first = Math.max(FIRST_VISIT_EXP, mn.suld.api.balance.Rewards.landmarkExp(services.progression().engine().curve(),
                        (o.area().minLevel() + o.area().maxLevel()) / 2, pr.progression().level()));
                services.progression().grantExp(pr, first, ExpSource.DISCOVERY);
                services.profiles().save(pr);
            }
        }
        p.sendMessage(Messages.success("«" + o.area().name() + "»-ийн овоог нар зөв гурвантаа тойрлоо. Тэнгэр ивээг! "
                + "(+5% EXP 30 мин" + (first > 0 ? ", анхны айлчлал +" + first + " EXP" : "") + ")"));
    }

    private java.time.ZoneId zone() {
        return services.zone();
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
        rituals.remove(e.getPlayer().getUniqueId()); // a ritual is walked in one go
        ritualAt.remove(e.getPlayer().getUniqueId());
        services.boosts().forget(e.getPlayer().getUniqueId());
    }
}
