package mn.suld.plugin.worldevent;

import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Presentation;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Наадам · Морин уралдаан (docs/world/NAADAM.md): the horse race of the Three Manly Games (VERIFIED tradition; real
 * races run tens of kilometres across the steppe, here a game-scale loop). Every {@code naadam.race-every-minutes}
 * (offset by an hour from the archery) a race opens: 8 checkpoints in a ring at least 140 blocks out from the spawn, around
 * Kharkhorum, drawn as columns of light. Riders on their SÜLD steppe horse ({@code /horse}) pass them in order within
 * 6 blocks; the first three to close the loop win coins and the honour «Түрүү морь» (the winning horse of a real
 * naadam is praised as түрүү). Players join just by riding through checkpoint 1 while the race is open (5 minutes).
 * Checks run every 5 ticks, only for mounted players.
 */
public final class HorseRaceService implements TabExecutor, org.bukkit.event.Listener {

    private static final int CHECKPOINTS = 8, RADIUS = 140, OPEN_S = 300;
    private static final double PASS = 6;
    private static final double[] SLIDE = {0, 6, -6, 12, -12, 18, -18};

    private final Plugin plugin;
    private final SuldServices services;
    private final NamespacedKey horseKey;
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
    private final List<Location> points = new ArrayList<>();
    private mn.suld.api.worldevent.HorseRace race = new mn.suld.api.worldevent.HorseRace(CHECKPOINTS); // rules: suld-api, tested
    private final Map<UUID, String> names = new HashMap<>();
    private long endsAt;
    private int ticker = -1, ticks;

    public HorseRaceService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        this.horseKey = new NamespacedKey(plugin, "steppe_horse");
    }

    public void start() {
        long every = Math.max(10, plugin.getConfig().getInt("naadam.race-every-minutes", 120)) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running() && Bukkit.getOnlinePlayers().size() >= plugin.getConfig().getInt("naadam.min-players", 2)) open(null);
        }, every + 60L * 60 * 20, every); // an hour after the archery cycle
    }

    public boolean running() {
        return ticker != -1;
    }

    private boolean opening;

    /**
     * The ring lies past the view distance of players in the city, so its 8 chunks are loaded asynchronously first
     * (the area around the spawn is pre-generated, so nothing is generated on the main thread); the race opens
     * once all of them are in.
     */
    private void open(CommandSender by) {
        if (opening) return;
        World w = Bukkit.getWorlds().get(0);
        Location sp = w.getSpawnLocation();
        // each checkpoint has candidates sliding along the ring (0, ±6°, ±12°, ±18°): one in a lake moves to the
        // nearest dry chunk
        int[][][] xz = new int[CHECKPOINTS][SLIDE.length][];
        List<java.util.concurrent.CompletableFuture<?>> loads = new ArrayList<>();
        for (int i = 0; i < CHECKPOINTS; i++) {
            for (int k = 0; k < SLIDE.length; k++) {
                double a = Math.toRadians(180 + i * 360.0 / CHECKPOINTS + SLIDE[k]); // from the south gate side, clockwise
                xz[i][k] = new int[]{sp.getBlockX() + (int) Math.round(Math.sin(a) * RADIUS), sp.getBlockZ() + (int) Math.round(-Math.cos(a) * RADIUS)};
                loads.add(w.getChunkAtAsync(xz[i][k][0] >> 4, xz[i][k][1] >> 4));
            }
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
                    if (err != null && by != null) by.sendMessage(Messages.error("Уралдааны замыг ачаалж чадсангүй: " + err.getMessage()));
                    return;
                }
                points.clear();
                for (int[][] cand : xz) {
                    Location at = null;
                    for (int[] c : cand) if ((at = dryGround(w, c[0], c[1])) != null) break;
                    points.add(at != null ? at : new Location(w, cand[0][0] + 0.5,
                            w.getHighestBlockYAt(cand[0][0], cand[0][1], HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, cand[0][1] + 0.5));
                }
                begin();
            });
        });
    }

    /**
     * The nearest dry ground to (x, z) inside its chunk, or null if the whole chunk is water (a gate in a lake would
     * make riders swim); only the chunk loaded for it is read.
     */
    private static Location dryGround(World w, int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        Location best = null;
        double bestD = Double.MAX_VALUE;
        for (int bx = cx << 4; bx < (cx << 4) + 16; bx++) {
            for (int bz = cz << 4; bz < (cz << 4) + 16; bz++) {
                double d = (bx - x) * (bx - x) + (bz - z) * (bz - z);
                if (d >= bestD) continue;
                org.bukkit.block.Block top = w.getHighestBlockAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                // a gate stands on natural ground: not in a lake, on a trunk, a roof or a city wall
                if (wet(top) || !natural(top.getType()) || top.getRelative(0, -1, 0).isPassable()) continue;
                best = new Location(w, bx + 0.5, top.getY() + 1, bz + 0.5);
                bestD = d;
            }
        }
        return best;
    }

    /** Steppe, desert, mountain or snow ground (never a built block: SÜLD builds with planks, bricks, tiles, wool). */
    private static boolean natural(org.bukkit.Material m) {
        if (org.bukkit.Tag.DIRT.isTagged(m) || org.bukkit.Tag.SAND.isTagged(m) || org.bukkit.Tag.BASE_STONE_OVERWORLD.isTagged(m)
                || org.bukkit.Tag.TERRACOTTA.isTagged(m)) {
            return true;
        }
        return switch (m) {
            case GRAVEL, CLAY, SNOW, SNOW_BLOCK, SANDSTONE, RED_SANDSTONE, DIRT_PATH, CALCITE, DRIPSTONE_BLOCK -> true;
            default -> false;
        };
    }

    /** Water, lava, a water plant (kelp, seagrass) or a waterlogged block: not a place to stand a horse. */
    private static boolean wet(org.bukkit.block.Block b) {
        if (b.isLiquid() || b.getRelative(org.bukkit.block.BlockFace.UP).isLiquid()) return true;
        switch (b.getType()) {
            case KELP, KELP_PLANT, SEAGRASS, TALL_SEAGRASS, BUBBLE_COLUMN -> {
                return true;
            }
            default -> {
            }
        }
        return b.getBlockData() instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged();
    }

    private void begin() {
        race = new mn.suld.api.worldevent.HorseRace(CHECKPOINTS);
        names.clear();
        endsAt = System.currentTimeMillis() + OPEN_S * 1000L;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Presentation.banner(p, "НААДАМ · МОРИН УРАЛДААН", "Мориндоо мордоод 1-р цэгээс эхэл — /horse", NamedTextColor.GOLD);
            p.playSound(p.getLocation(), Sound.ENTITY_HORSE_GALLOP, 1f, 1f);
            p.showBossBar(bar);
        }
        Location p1 = points.get(0);
        Bukkit.broadcast(Messages.accent("Морин уралдаан эхэллээ! Хархорумыг тойрсон 8 цэг — 1-р цэг: " + p1.getBlockX() + ", " + p1.getBlockZ()
                + ". Өөрийн тал нутгийн морьтой оролцоно. 5 минут."));
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L).getTaskId();
    }

    private boolean mounted(Player p) {
        Entity v = p.getVehicle();
        // only the rider's own steppe horse (HorseService stores the owner's UUID under suld:steppe_horse)
        return v instanceof AbstractHorse h
                && p.getUniqueId().toString().equals(h.getPersistentDataContainer().get(horseKey, PersistentDataType.STRING));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        // the gates: light columns at the next checkpoint of anyone racing, a dim one at every other
        if (ticks++ % 4 == 0) { // once a second (the task runs every 5 ticks)
            for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar); // late joiners too; idempotent
            for (int i = 0; i < points.size(); i++) {
                Location c = points.get(i);
                for (int y = 0; y < 8; y += 2) {
                    c.getWorld().spawnParticle(Particle.DUST, c.clone().add(0, y, 0), 2, 0.2, 0.4, 0.2, 0,
                            new Particle.DustOptions(i == 0 ? Color.fromRGB(0x60E060) : Color.fromRGB(0xF2D27A), 1.6f));
                }
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (!mounted(p) || race.finished(id)) continue;
            int gate = race.nextGate(id);
            Location c = points.get(gate);
            Location at = p.getLocation();
            double hx = at.getX() - c.getX(), hz = at.getZ() - c.getZ();
            // passed within 6 blocks on the map, at about the gate's height (a hill or a dip near it still counts)
            if (!c.getWorld().equals(p.getWorld()) || hx * hx + hz * hz > PASS * PASS || Math.abs(at.getY() - c.getY()) > 8) continue;
            mn.suld.api.worldevent.HorseRace.Pass pass = race.pass(id, gate, now);
            switch (pass.kind()) {
                case START -> {
                    names.put(id, p.getName());
                    p.sendActionBar(Component.text("Эхэллээ! Дараагийн цэг рүү давхи", NamedTextColor.GREEN));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
                }
                case CHECKPOINT -> {
                    p.sendActionBar(Component.text("Цэг " + pass.index() + "/" + CHECKPOINTS + " · " + fmt(pass.millis()), NamedTextColor.GOLD));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f + pass.index() * 0.05f);
                }
                case FINISH -> {
                    Bukkit.broadcast(Messages.success(pass.place() + "-р байр: " + p.getName() + " — " + fmt(pass.millis())
                            + (pass.place() == 1 ? " · Түрүү морь!" : "")));
                    if (pass.prize() > 0) {
                        services.profiles().cached(id).ifPresent(pr -> {
                            pr.addCurrency(pass.prize());
                            services.profiles().save(pr);
                        });
                        p.sendMessage(Messages.success("+" + pass.prize() + " ₮"));
                    }
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                }
                default -> {
                }
            }
        }
        long left = Math.max(0, (endsAt - now) / 1000);
        bar.name(Component.text("Морин уралдаан — " + left / 60 + ":" + String.format(Locale.ROOT, "%02d", left % 60), NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("  · " + race.startedCount() + " морьтон, " + race.results().size() + " барианд", NamedTextColor.WHITE)));
        bar.progress(Math.max(0f, Math.min(1f, left / (float) OPEN_S)));
        if (left == 0) close();
    }

    private static String fmt(long ms) {
        return (ms / 60000) + ":" + String.format(Locale.ROOT, "%02d.%d", (ms / 1000) % 60, (ms / 100) % 10);
    }

    private void close() {
        if (ticker != -1) Bukkit.getScheduler().cancelTask(ticker);
        ticker = -1;
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        int done = race.results().size();
        Bukkit.broadcast(Messages.info(done == 0 ? "Морин уралдаан өндөрлөлөө — энэ удаа барианд орсон морьтон алга."
                : "Морин уралдаан өндөрлөлөө: " + done + " морьтон барианд орлоо."));
        points.clear();
    }

    /**
     * The course must be ridden: a teleport mid-race (/tpa, /home, the relay, an ender pearl…) puts the rider back
     * to the start, so nobody hops between checkpoints. Dismounting is not a teleport here.
     */
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent e) {
        if (!running() || e.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.DISMOUNT) return;
        if (race.reset(e.getPlayer().getUniqueId())) e.getPlayer().sendMessage(Messages.error("Уралдааны замаас зөөгдлөө — 1-р цэгээс дахин эхэл."));
    }

    @org.bukkit.event.EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        race.reset(e.getPlayer().getUniqueId());
    }

    public void shutdown() {
        if (ticker != -1) Bukkit.getScheduler().cancelTask(ticker);
        ticker = -1;
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("start")) {
            if (!sender.hasPermission("suld.admin.event")) sender.sendMessage(Messages.error("Эрх алга."));
            else if (running()) sender.sendMessage(Messages.error("Уралдаан аль хэдийн явж байна."));
            else open(sender);
            return true;
        }
        if (!running()) {
            sender.sendMessage(Messages.info("Морин уралдаан одоогоор алга."));
            return true;
        }
        List<Map.Entry<UUID, Long>> results = race.results();
        sender.sendMessage(Messages.accent("Морин уралдаан — " + results.size() + " барианд"));
        int i = 1;
        for (Map.Entry<UUID, Long> e : results) sender.sendMessage(Messages.info((i++) + ". " + names.get(e.getKey()) + " — " + fmt(e.getValue())));
        if (!points.isEmpty()) sender.sendMessage(Messages.info("1-р цэг: " + points.get(0).getBlockX() + ", " + points.get(0).getBlockZ()));
        if (sender.hasPermission("suld.admin.event")) {
            StringBuilder riding = new StringBuilder("Замд:");
            race.riding().forEach((id, n) -> riding.append(' ').append(names.get(id)).append('→').append(n));
            sender.sendMessage(Messages.info(riding.toString()));
            StringBuilder all = new StringBuilder("Цэгүүд:");
            for (int k = 0; k < points.size(); k++) {
                Location c = points.get(k);
                all.append(' ').append(k + 1).append('=').append(c.getBlockX()).append(',').append(c.getBlockY()).append(',').append(c.getBlockZ());
            }
            sender.sendMessage(Messages.info(all.toString()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 && sender.hasPermission("suld.admin.event") ? List.of("start") : List.of();
    }
}
