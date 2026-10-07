package mn.suld.plugin.worldevent;

import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Presentation;
import mn.suld.plugin.worldbuild.WorldBuildService;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Наадам · Сур харваа (docs/world/NAADAM.md): the archery of the Three Manly Games (эрийн гурван наадам: wrestling,
 * archery, horse racing; VERIFIED tradition). Every {@code naadam.every-minutes} a contest opens on the field outside
 * Kharkhorum's south gate: a row of сур targets lies on the ground at 20, 30 and 40 blocks from the shooting line
 * (Mongolian archers shoot at small targets on the ground, VERIFIED; the distances are game-scale). For five minutes
 * every arrow that hits a сур scores by distance (3/5/8) and +2 for the centre of the face; the best three archers
 * win coins and are proclaimed. The targets are temporary blocks: what stood there is restored when the contest
 * ends (and on shutdown). {@code /naadam} shows the board; {@code /naadam start} (admin) opens one now.
 */
public final class NaadamService implements Listener, TabExecutor {

    private static final int[] RANGES = {20, 30, 40};
    private static final int[] POINTS = {3, 5, 8};
    private static final long[] PRIZES = {300, 200, 100};
    private static final int DURATION_S = 300;

    private final Plugin plugin;
    private final SuldServices services;
    private final WorldBuildService city;
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10);
    private final Map<Block, BlockData> placed = new LinkedHashMap<>();
    private final Map<Block, Integer> targetPoints = new HashMap<>();
    private final Map<UUID, Integer> scores = new HashMap<>();
    /** Arrows shot at this contest per archer: each has a quiver of {@link #ARROWS} (a real archer shoots a set count). */
    private final Map<UUID, Integer> shots = new HashMap<>();
    /** Arrows within the quiver -> where along the field their archer stood when shooting (z). */
    private final Map<UUID, Double> launchZ = new HashMap<>();
    private static final int ARROWS = 20;
    private final Map<UUID, String> names = new HashMap<>();
    private final Set<UUID> viewers = new HashSet<>();
    private Location line;
    private long endsAt;
    private int ticker = -1;

    public NaadamService(Plugin plugin, SuldServices services, WorldBuildService city) {
        this.plugin = plugin;
        this.services = services;
        this.city = city;
    }

    public void start() {
        restoreAfterCrash();
        long every = Math.max(10, plugin.getConfig().getInt("naadam.every-minutes", 120)) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running() && Bukkit.getOnlinePlayers().size() >= plugin.getConfig().getInt("naadam.min-players", 2)) open(null);
        }, every, every);
    }

    public boolean running() {
        return line != null;
    }

    // ------------------------------------------------------------------ the field

    /** The shooting line's column: outside the south gate, past the city's footprint (no chunk is read here). */
    private int[] fieldColumn(World w) {
        Location gate = city.pointLocation("fast_travel.gate_south");
        if (gate == null) gate = w.getSpawnLocation().clone().add(0, 0, 60);
        int x = gate.getBlockX(), z = gate.getBlockZ();
        for (int i = 0; i < 80 && city.near(w.getName(), x, z, 4); i++) z++; // walk south until clear of the walls
        return new int[]{x, z + 6};
    }

    private boolean opening;

    /**
     * Every chunk the field touches (the line with its banners at x±6, the сур out to 40 blocks) is loaded
     * asynchronously first; nothing is loaded or generated on the main thread, and nobody has to stand at the gate.
     */
    private void open(CommandSender by) {
        if (opening) return;
        World w = Bukkit.getWorlds().get(0);
        int[] c = fieldColumn(w);
        List<java.util.concurrent.CompletableFuture<?>> loads = new ArrayList<>();
        for (int cx = (c[0] - 8) >> 4; cx <= (c[0] + 8) >> 4; cx++) {
            for (int cz = (c[1] - 2) >> 4; cz <= (c[1] + RANGES[RANGES.length - 1] + 2) >> 4; cz++) loads.add(w.getChunkAtAsync(cx, cz));
        }
        opening = true;
        java.util.concurrent.CompletableFuture.allOf(loads.toArray(new java.util.concurrent.CompletableFuture<?>[0])).whenComplete((ok, err) -> {
            if (!plugin.isEnabled()) {
                opening = false;
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                opening = false;
                if (err != null || running()) {
                    if (err != null && by != null) by.sendMessage(Messages.error("Наадмын талбайг ачаалж чадсангүй: " + err.getMessage()));
                    return;
                }
                build(w, c[0], c[1]);
            });
        });
    }

    private void build(World w, int fx, int fz) {
        Location at = new Location(w, fx + 0.5, w.getHighestBlockYAt(fx, fz, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, fz + 0.5, 0, 0); // facing south
        line = at;
        // the сур: five targets abreast at each range, a white lane line, a banner on each side of the line
        for (int r = 0; r < RANGES.length; r++) {
            for (int dx = -2; dx <= 2; dx++) {
                int x = at.getBlockX() + dx * 2, z = at.getBlockZ() + RANGES[r];
                int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                Block b = w.getBlockAt(x, y, z);
                if (put(b, Material.TARGET, true)) targetPoints.put(b, POINTS[r]);
            }
        }
        for (int dx = -5; dx <= 5; dx++) {
            Block b = w.getBlockAt(at.getBlockX() + dx, at.getBlockY() - 1, at.getBlockZ());
            if (!b.getType().isAir() && b.getType().isSolid()) put(b, Material.WHITE_CONCRETE, false);
        }
        put(w.getBlockAt(at.getBlockX() - 6, at.getBlockY(), at.getBlockZ()), Material.LIGHT_BLUE_BANNER, true);
        put(w.getBlockAt(at.getBlockX() + 6, at.getBlockY(), at.getBlockZ()), Material.LIGHT_BLUE_BANNER, true);
        saveRestore();
        scores.clear();
        shots.clear();
        launchZ.clear();
        names.clear();
        endsAt = System.currentTimeMillis() + DURATION_S * 1000L;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Presentation.banner(p, "НААДАМ · СУР ХАРВАА", "Хархорумын өмнөд хаалганы гадна — нумаа бэлдээрэй!", NamedTextColor.AQUA);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.7f, 1.3f);
        }
        Bukkit.broadcast(Messages.accent("Наадам эхэллээ! Өмнөд хаалганы гаднах сурыг харваарай: 20/30/40 блок = 3/5/8 оноо, голд +2. 5 минут."));
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L).getTaskId();
    }

    /**
     * Places a festival block, remembering what was there. Never over a block with contents (a chest, a sign, a
     * banner: its contents would not come back); {@code intoAir} only into air or replaceable plants.
     */
    private boolean put(Block b, Material m, boolean intoAir) {
        if (b.getState() instanceof org.bukkit.block.TileState) return false;
        if (intoAir && !(b.getType().isAir() || b.isReplaceable())) return false;
        if (!placed.containsKey(b)) placed.put(b, b.getBlockData().clone());
        b.setType(m, false);
        return true;
    }

    // ------------------------------------------------------------------ crash safety

    /** What the field replaced, on disk while a contest runs: a crash or a kill mid-contest is undone at the next start. */
    private java.io.File restoreFile() {
        return new java.io.File(plugin.getDataFolder(), "naadam-restore.yml");
    }

    private void saveRestore() {
        org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
        List<String> rows = new ArrayList<>();
        for (Map.Entry<Block, BlockData> e : placed.entrySet()) {
            Block b = e.getKey();
            rows.add(b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ() + ";" + e.getValue().getAsString());
        }
        y.set("blocks", rows);
        String text = y.saveToString();
        java.nio.file.Path file = restoreFile().toPath();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Path tmp = file.resolveSibling("naadam-restore.yml.tmp");
                java.nio.file.Files.writeString(tmp, text);
                java.nio.file.Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.io.IOException ex) {
                plugin.getLogger().warning("naadam-restore.yml: " + ex.getMessage());
            }
        });
    }

    /** At start: a field left behind by a crash is put back (its chunks loaded asynchronously first). */
    private void restoreAfterCrash() {
        java.io.File f = restoreFile();
        if (!f.exists()) return;
        List<String> rows = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f).getStringList("blocks");
        List<Object[]> todo = new ArrayList<>();
        List<java.util.concurrent.CompletableFuture<?>> loads = new ArrayList<>();
        for (String row : rows) {
            String[] c = row.split(";", 5);
            World w = c.length == 5 ? Bukkit.getWorld(c[0]) : null;
            if (w == null) continue;
            int x = Integer.parseInt(c[1]), y = Integer.parseInt(c[2]), z = Integer.parseInt(c[3]);
            todo.add(new Object[]{w, x, y, z, c[4]});
            loads.add(w.getChunkAtAsync(x >> 4, z >> 4));
        }
        java.util.concurrent.CompletableFuture.allOf(loads.toArray(new java.util.concurrent.CompletableFuture<?>[0])).whenComplete((ok, err) -> {
            if (!plugin.isEnabled() || err != null) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Object[] t : todo) {
                    try {
                        ((World) t[0]).getBlockAt((int) t[1], (int) t[2], (int) t[3]).setBlockData(Bukkit.createBlockData((String) t[4]), false);
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("naadam restore: " + ex.getMessage());
                    }
                }
                plugin.getLogger().info("Naadam: restored " + todo.size() + " field blocks left by an unclean stop.");
                if (!f.delete()) plugin.getLogger().warning("naadam-restore.yml could not be deleted");
            });
        });
    }

    private void tick() {
        long left = Math.max(0, (endsAt - System.currentTimeMillis()) / 1000);
        Map.Entry<UUID, Integer> best = scores.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
        bar.name(Component.text("Наадам · Сур харваа — " + left / 60 + ":" + String.format(Locale.ROOT, "%02d", left % 60), NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.text(best == null ? "  · хэн ч оноо аваагүй" : "  · Тэргүүн: " + names.get(best.getKey()) + " " + best.getValue(), NamedTextColor.WHITE)));
        bar.progress(Math.max(0f, Math.min(1f, left / (float) DURATION_S)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean near = line != null && p.getWorld().equals(line.getWorld()) && p.getLocation().distanceSquared(line) < 90 * 90;
            if (near && viewers.add(p.getUniqueId())) p.showBossBar(bar);
            if (!near && viewers.remove(p.getUniqueId())) p.hideBossBar(bar);
        }
        if (left == 0) close();
    }

    private void close() {
        if (ticker != -1) Bukkit.getScheduler().cancelTask(ticker);
        ticker = -1;
        List<Map.Entry<UUID, Integer>> top = new ArrayList<>(scores.entrySet());
        // titles and prizes are given at the closing ceremony: to archers who are still here
        top.removeIf(en -> Bukkit.getPlayer(en.getKey()) == null || services.profiles().cached(en.getKey()).isEmpty());
        top.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());
        if (top.isEmpty()) {
            Bukkit.broadcast(Messages.info("Наадам өндөрлөлөө — энэ удаа харвасан мэргэн алга."));
        } else {
            Bukkit.broadcast(Messages.accent("Наадам өндөрлөлөө! Мэргэдийн цол:"));
            String[] title = {"Улсын Мэргэн", "Аймгийн Мэргэн", "Сумын Мэргэн"};
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                UUID id = top.get(i).getKey();
                long prize = PRIZES[i];
                Bukkit.broadcast(Messages.success((i + 1) + ". " + names.get(id) + " — " + top.get(i).getValue() + " оноо · " + title[i] + " · +" + prize + " ₮"));
                services.profiles().cached(id).ifPresent(pr -> {
                    pr.addCurrency(prize);
                    services.profiles().save(pr);
                });
            }
        }
        restore();
    }

    private void restore() {
        for (Map.Entry<Block, BlockData> e : placed.entrySet()) e.getKey().setBlockData(e.getValue(), false);
        boolean had = !placed.isEmpty();
        placed.clear();
        if (had && restoreFile().exists() && !restoreFile().delete()) plugin.getLogger().warning("naadam-restore.yml could not be deleted");
        targetPoints.clear();
        for (UUID id : viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(bar);
        }
        viewers.clear();
        line = null;
    }

    public void shutdown() {
        if (ticker != -1) Bukkit.getScheduler().cancelTask(ticker);
        restore();
    }

    // ------------------------------------------------------------------ scoring

    /**
     * Every arrow an archer near the field looses counts against the quiver (a multishot crossbow looses three);
     * arrows past the 20th, and spell arrows (the Mergen's volley), do not score.
     */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(org.bukkit.event.entity.ProjectileLaunchEvent e) {
        if (!running() || !(e.getEntity() instanceof Arrow a) || !(a.getShooter() instanceof Player p)) return;
        if (a.getScoreboardTags().contains("suld_volley")) return;
        if (!p.getWorld().equals(line.getWorld()) || p.getLocation().distanceSquared(line) > 90 * 90) return;
        int n = shots.merge(p.getUniqueId(), 1, Integer::sum);
        if (n <= ARROWS) {
            launchZ.put(a.getUniqueId(), p.getLocation().getZ());
            if (n == ARROWS) p.sendActionBar(Component.text("Сүүлчийн сум!", NamedTextColor.GOLD));
        } else if (n == ARROWS + 1) {
            p.sendMessage(Messages.info("Таны " + ARROWS + " сум дууслаа — энэ наадамд цаашдын харвалт тооцогдохгүй."));
        }
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (!running() || !(e.getEntity() instanceof Arrow a) || !(a.getShooter() instanceof Player p)) return;
        Double shotFrom = launchZ.remove(a.getUniqueId()); // forgotten on any hit, so misses do not pile up
        if (e.getHitBlock() == null) return;
        Integer pts = targetPoints.get(e.getHitBlock());
        if (pts == null || shotFrom == null) return; // not a сур, or past the quiver, a spell arrow, shot from afar
        // the shot must have been loosed from behind the shooting line (not lobbed from the сур, then run back)
        if (shotFrom > line.getZ() + 1.5) {
            p.sendActionBar(Messages.error("Харвах шугамын цаанаас харвана!"));
            return;
        }
        int bonus = 0;
        if (e.getHitBlockFace() != null) {
            Vector c = e.getHitBlock().getLocation().toCenterLocation().toVector().add(e.getHitBlockFace().getDirection().multiply(0.5));
            if (a.getLocation().toVector().distance(c) < 0.28) bonus = 2;
        }
        int total = scores.merge(p.getUniqueId(), pts + bonus, Integer::sum);
        names.put(p.getUniqueId(), p.getName());
        a.remove();
        e.getHitBlock().getWorld().spawnParticle(Particle.CRIT, e.getHitBlock().getLocation().toCenterLocation(), 12, 0.3, 0.3, 0.3, 0.1);
        p.playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, bonus > 0 ? 1.6f : 1.1f);
        p.sendActionBar(Component.text("Сур! +" + (pts + bonus) + (bonus > 0 ? " (голд!)" : "") + " · нийт " + total, NamedTextColor.GOLD));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (placed.containsKey(e.getBlock())) e.setCancelled(true); // the field belongs to the festival
    }

    // ------------------------------------------------------------------ /naadam

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("start")) {
            if (!sender.hasPermission("suld.admin.event")) {
                sender.sendMessage(Messages.error("Эрх алга."));
            } else if (running()) {
                sender.sendMessage(Messages.error("Наадам аль хэдийн явж байна."));
            } else {
                open(sender);
            }
            return true;
        }
        if (!running()) {
            sender.sendMessage(Messages.info("Наадам одоогоор алга. Дараагийнх нь ойролцоогоор " + plugin.getConfig().getInt("naadam.every-minutes", 120) + " минут тутамд."));
            return true;
        }
        List<Map.Entry<UUID, Integer>> top = new ArrayList<>(scores.entrySet());
        top.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());
        sender.sendMessage(Messages.accent("Наадам · Сур харваа — " + Math.max(0, (endsAt - System.currentTimeMillis()) / 1000) + " с үлдсэн"));
        for (int i = 0; i < Math.min(5, top.size()); i++) {
            sender.sendMessage(Messages.info((i + 1) + ". " + names.get(top.get(i).getKey()) + " — " + top.get(i).getValue()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 && sender.hasPermission("suld.admin.event") ? List.of("start") : List.of();
    }
}
