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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Наадам · Морин уралдаан (docs/world/NAADAM.md): the horse race of the Three Manly Games (VERIFIED tradition; real
 * races run tens of kilometres across the steppe, here a game-scale loop). Every {@code naadam.race-every-minutes}
 * (offset by an hour from the archery) a race opens: 8 checkpoints in a ring 140 blocks out from the spawn, around
 * Kharkhorum, drawn as columns of light. Riders on their SÜLD steppe horse ({@code /horse}) pass them in order within
 * 6 blocks; the first three to close the loop win coins and the honour «Түрүү морь» (the winning horse of a real
 * naadam is praised as түрүү). Players join just by riding through checkpoint 1 while the race is open (5 minutes).
 * Checks run every 5 ticks, only for mounted players.
 */
public final class HorseRaceService implements TabExecutor {

    private static final int CHECKPOINTS = 8, RADIUS = 140, OPEN_S = 300;
    private static final double PASS = 6;
    private static final long[] PRIZES = {300, 200, 100};

    private final Plugin plugin;
    private final SuldServices services;
    private final NamespacedKey horseKey;
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
    private final List<Location> points = new ArrayList<>();
    private final Map<UUID, Integer> next = new HashMap<>();
    private final Map<UUID, Long> started = new HashMap<>();
    private final Map<UUID, Long> finished = new LinkedHashMap<>();
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

    private void open(CommandSender by) {
        World w = Bukkit.getWorlds().get(0);
        Location sp = w.getSpawnLocation();
        points.clear();
        for (int i = 0; i < CHECKPOINTS; i++) {
            double a = Math.toRadians(180 + i * 360.0 / CHECKPOINTS); // from the south gate side, clockwise
            int x = sp.getBlockX() + (int) Math.round(Math.sin(a) * RADIUS), z = sp.getBlockZ() + (int) Math.round(-Math.cos(a) * RADIUS);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                if (by != null) by.sendMessage(Messages.error("Уралдааны замын chunk ачаалагдаагүй — хотын ойр тоглогч хэрэгтэй."));
                points.clear();
                return;
            }
            points.add(new Location(w, x + 0.5, w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, z + 0.5));
        }
        next.clear();
        started.clear();
        finished.clear();
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
            if (!mounted(p) || finished.containsKey(p.getUniqueId())) continue;
            int n = next.getOrDefault(p.getUniqueId(), 0);
            Location c = points.get(n % CHECKPOINTS);
            if (!c.getWorld().equals(p.getWorld()) || c.distanceSquared(p.getLocation()) > PASS * PASS) continue;
            if (n == 0) {
                started.put(p.getUniqueId(), now);
                names.put(p.getUniqueId(), p.getName());
                p.sendActionBar(Component.text("Эхэллээ! Дараагийн цэг рүү давхи", NamedTextColor.GREEN));
            } else if (n == CHECKPOINTS) {
                long ms = now - started.get(p.getUniqueId());
                finished.put(p.getUniqueId(), ms);
                int place = finished.size();
                Bukkit.broadcast(Messages.success(place + "-р байр: " + p.getName() + " — " + fmt(ms) + (place == 1 ? " · Түрүү морь!" : "")));
                if (place <= PRIZES.length) {
                    long prize = PRIZES[place - 1];
                    services.profiles().cached(p.getUniqueId()).ifPresent(pr -> {
                        pr.addCurrency(prize);
                        services.profiles().save(pr);
                    });
                    p.sendMessage(Messages.success("+" + prize + " ₮"));
                }
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                continue;
            } else {
                p.sendActionBar(Component.text("Цэг " + n + "/" + CHECKPOINTS + " · " + fmt(now - started.get(p.getUniqueId())), NamedTextColor.GOLD));
            }
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f + n * 0.05f);
            next.put(p.getUniqueId(), n + 1);
        }
        long left = Math.max(0, (endsAt - now) / 1000);
        bar.name(Component.text("Морин уралдаан — " + left / 60 + ":" + String.format(Locale.ROOT, "%02d", left % 60), NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("  · " + started.size() + " морьтон, " + finished.size() + " барианд", NamedTextColor.WHITE)));
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
        Bukkit.broadcast(Messages.info(finished.isEmpty() ? "Морин уралдаан өндөрлөлөө — энэ удаа барианд орсон морьтон алга."
                : "Морин уралдаан өндөрлөлөө: " + finished.size() + " морьтон барианд орлоо."));
        points.clear();
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
        sender.sendMessage(Messages.accent("Морин уралдаан — " + finished.size() + " барианд"));
        int i = 1;
        for (Map.Entry<UUID, Long> e : finished.entrySet()) sender.sendMessage(Messages.info((i++) + ". " + names.get(e.getKey()) + " — " + fmt(e.getValue())));
        if (!points.isEmpty()) sender.sendMessage(Messages.info("1-р цэг: " + points.get(0).getBlockX() + ", " + points.get(0).getBlockZ()));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 && sender.hasPermission("suld.admin.event") ? List.of("start") : List.of();
    }
}
