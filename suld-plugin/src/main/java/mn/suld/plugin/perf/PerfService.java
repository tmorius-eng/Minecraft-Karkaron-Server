package mn.suld.plugin.perf;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.loot.LootContext;
import mn.suld.api.loot.LootTable;
import mn.suld.api.loot.LootTier;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Measurement sessions for the performance baseline ({@code /suldperf}). A session records every tick's duration
 * (Paper's tick-end event), samples TPS, process/system CPU, heap, entities, chunks and players once a second, and
 * at the end writes everything plus the {@link PerfProbe} code-path costs to {@code plugins/SULD/perf/<label>.json}.
 * {@code /suldperf bench} times the item engine from the outside (generation, loot rolls, validation, stack building,
 * equipment recompute). Nothing here changes gameplay; outside a session only the probes' counters run.
 */
public final class PerfService implements Listener {

    private final Plugin plugin;
    private final SuldServices services;

    private String label;
    private long startedNs;
    private Instant startedAt;
    private BukkitTask sampler;
    private final List<double[]> samples = new ArrayList<>(); // t, tps, procCpu, sysCpu, heapUsedMb, heapCommittedMb, entities, chunks, players

    public PerfService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickEnd(ServerTickEndEvent e) {
        if (label != null) PerfProbe.timer("server.tick").record((long) (e.getTickDuration() * 1_000_000L));
    }

    public boolean running() {
        return label != null;
    }

    public void start(String name) {
        if (label != null) stop();
        PerfProbe.reset();
        samples.clear();
        label = name.replaceAll("[^A-Za-z0-9_.-]", "_");
        startedNs = System.nanoTime();
        startedAt = Instant.now();
        sampler = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, 20L, 20L);
    }

    private void sample() {
        var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        Runtime rt = Runtime.getRuntime();
        int entities = 0, chunks = 0;
        for (World w : Bukkit.getWorlds()) {
            entities += w.getEntityCount();
            chunks += w.getChunkCount();
        }
        samples.add(new double[]{(System.nanoTime() - startedNs) / 1e9, Bukkit.getTPS()[0], os.getProcessCpuLoad() * 100, os.getCpuLoad() * 100,
                (rt.totalMemory() - rt.freeMemory()) / 1048576.0, rt.totalMemory() / 1048576.0, entities, chunks, Bukkit.getOnlinePlayers().size()});
    }

    /** End the session and write its report; returns the file. */
    public Path stop() {
        if (label == null) return null;
        sampler.cancel();
        sample();
        Path out = plugin.getDataFolder().toPath().resolve("perf").resolve(label + ".json");
        String json = report();
        label = null;
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("[perf] cannot write " + out + ": " + e.getMessage());
        }
        return out;
    }

    private static String n(double v) {
        return Double.isFinite(v) ? String.format(Locale.ROOT, "%.3f", v) : "null";
    }

    private static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String report() {
        StringBuilder sb = new StringBuilder("{\n");
        sb.append("  \"label\": ").append(q(label)).append(",\n");
        if (!benchExtra.isEmpty()) sb.append("  ").append(benchExtra).append("\n");
        sb.append("  \"startedAt\": ").append(q(startedAt.toString())).append(",\n");
        sb.append("  \"seconds\": ").append(n((System.nanoTime() - startedNs) / 1e9)).append(",\n");
        sb.append("  \"environment\": {\"java\": ").append(q(System.getProperty("java.version"))).append(", \"cpus\": ")
                .append(Runtime.getRuntime().availableProcessors()).append(", \"maxHeapMb\": ").append(Runtime.getRuntime().maxMemory() / 1048576)
                .append(", \"server\": ").append(q(Bukkit.getVersion())).append(", \"jvmArgs\": ")
                .append(q(String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments()))).append("},\n");
        String[] names = {"t", "tps", "processCpuPct", "systemCpuPct", "heapUsedMb", "heapCommittedMb", "entities", "chunks", "players"};
        sb.append("  \"summary\": {");
        for (int c = 1; c < names.length; c++) {
            double min = Double.MAX_VALUE, max = -Double.MAX_VALUE, sum = 0;
            int cnt = 0;
            for (double[] s : samples) {
                if (!Double.isFinite(s[c]) || s[c] < 0) continue;
                min = Math.min(min, s[c]);
                max = Math.max(max, s[c]);
                sum += s[c];
                cnt++;
            }
            sb.append(c > 1 ? ", " : "").append(q(names[c])).append(": {\"min\": ").append(n(cnt == 0 ? Double.NaN : min))
                    .append(", \"avg\": ").append(n(cnt == 0 ? Double.NaN : sum / cnt)).append(", \"max\": ").append(n(cnt == 0 ? Double.NaN : max)).append("}");
        }
        sb.append("},\n  \"probesUs\": {\n");
        int i = 0;
        Map<String, PerfProbe.Stats> snap = PerfProbe.snapshot();
        for (Map.Entry<String, PerfProbe.Stats> e : snap.entrySet()) {
            PerfProbe.Stats s = e.getValue();
            sb.append("    ").append(q(e.getKey())).append(": {\"count\": ").append(s.count()).append(", \"mean\": ").append(n(s.meanUs()))
                    .append(", \"p50\": ").append(n(s.p50Us())).append(", \"p95\": ").append(n(s.p95Us())).append(", \"p99\": ").append(n(s.p99Us()))
                    .append(", \"max\": ").append(n(s.maxUs())).append("}").append(++i < snap.size() ? ",\n" : "\n");
        }
        PerfProbe.Stats tick = snap.get("server.tick");
        if (tick != null) {
            long[] ring = PerfProbe.timer("server.tick").ring.clone();
            int filled = (int) Math.min(tick.count(), PerfProbe.WINDOW);
            int over50 = 0, over100 = 0, over300 = 0;
            for (int k = 0; k < filled; k++) {
                if (ring[k] > 50_000_000L) over50++;
                if (ring[k] > 100_000_000L) over100++;
                if (ring[k] > 300_000_000L) over300++;
            }
            sb.append("  },\n  \"ticksOver\": {\"50ms\": ").append(over50).append(", \"100ms\": ").append(over100).append(", \"300ms\": ").append(over300)
                    .append(", \"window\": ").append(filled).append("},\n");
        } else {
            sb.append("  },\n");
        }
        sb.append("  \"samples\": [\n");
        for (int k = 0; k < samples.size(); k++) {
            double[] s = samples.get(k);
            sb.append("    [");
            for (int c = 0; c < s.length; c++) sb.append(c > 0 ? ", " : "").append(n(s[c]));
            sb.append(k + 1 < samples.size() ? "],\n" : "]\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------ item engine micro-benchmarks

    /** Times the item engine from the outside on the main thread; results go to the {@code bench.*} probes. */
    public void bench(CommandSender out, int n) {
        var items = services.itemService();
        List<ItemDefinition> defs = new ArrayList<>();
        for (ItemDefinition d : items.catalog().items()) if (d.rarity() != ItemRarity.UNIQUE) defs.add(d);
        List<LootTable> tables = new ArrayList<>(items.catalog().lootTables());
        List<ItemInstance> made = new ArrayList<>();
        for (int i = 0; i < 200; i++) made.add(items.generate(defs.get(i % defs.size()), null, 10, null, "bench")); // warm-up
        for (int i = 0; i < n; i++) {
            ItemDefinition d = defs.get(i % defs.size());
            ItemRarity r = d.rarity();
            long t = PerfProbe.start();
            ItemInstance it = items.generate(d, r, 1 + i % 60, null, "bench");
            PerfProbe.stop("bench.item.generate", t);
            if (i < 2000) made.add(it);
        }
        for (int i = 0; i < n; i++) {
            LootTable tb = tables.get(i % tables.size());
            long t = PerfProbe.start();
            items.roll(tb, LootContext.of(1 + i % 60, tb.tier() == null ? LootTier.NORMAL : tb.tier()));
            PerfProbe.stop("bench.loot.roll", t);
        }
        Player viewer = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < Math.min(n, 2000); i++) {
            ItemInstance it = made.get(i % made.size());
            long t = PerfProbe.start();
            stacks.add(items.stack(it, viewer, 1));
            PerfProbe.stop("bench.item.stack_build", t);
        }
        for (int i = 0; i < Math.min(n, 2000); i++) { // first check of each new document: decode + validate
            ItemStack s = items.stack(made.get(i % made.size()).boundTo(UUID.randomUUID()), viewer, 1);
            long t = PerfProbe.start();
            items.check(s);
            PerfProbe.stop("bench.item.check_uncached", t);
        }
        for (int i = 0; i < n; i++) {
            long t = PerfProbe.start();
            items.check(stacks.get(i % stacks.size()));
            PerfProbe.stop("bench.item.check_cached", t);
        }
        if (viewer != null && services.equipment() != null) {
            for (int i = 0; i < Math.min(n, 2000); i++) {
                long t = PerfProbe.start();
                services.equipment().compute(viewer);
                PerfProbe.stop("bench.equipment.compute", t);
            }
        }
        for (Map.Entry<String, PerfProbe.Stats> e : PerfProbe.snapshot().entrySet()) {
            if (!e.getKey().startsWith("bench.")) continue;
            PerfProbe.Stats s = e.getValue();
            out.sendMessage(Messages.info(String.format(Locale.ROOT, "%s n=%d mean=%.1fµs p95=%.1fµs p99=%.1fµs max=%.1fµs",
                    e.getKey(), s.count(), s.meanUs(), s.p95Us(), s.p99Us(), s.maxUs())));
        }
    }

    // ------------------------------------------------------------------ /suldperf

    /**
     * Model-renderer benchmark (docs/perf/MODEL_RENDERER_BENCH.md): n rig hosts (wandering Ravagers dressed with the
     * rig) around a player for {@code seconds}, recorded as a perf session ({@code model_<rig>_<n>.json}) plus the
     * renderer's own figures — displays, entities, transforms sent per second, heap delta, spawn/despawn cost.
     */
    private void modelBench(CommandSender s, int n, int seconds, Player at, String rig) {
        mn.suld.plugin.model.ModelService models = services.models;
        if (models == null || !models.has(rig) || at == null) {
            s.sendMessage(Messages.error("[perf] model <n> <сек> <тоглогч> [rig] — rig loaded: " + (models == null ? "none" : models.modelIds())));
            return;
        }
        start("model_" + rig + "_" + n);
        Runtime rt = Runtime.getRuntime();
        long heap0 = rt.totalMemory() - rt.freeMemory();
        int entities0 = at.getWorld().getEntityCount();
        long sent0 = models.transformsSent();
        boolean invul = at.isInvulnerable();
        at.setInvulnerable(true);
        List<org.bukkit.entity.LivingEntity> hosts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double ang = 2 * Math.PI * i / n, r = 6 + (i % 3) * 2;
            org.bukkit.Location l = at.getLocation().add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            org.bukkit.entity.Ravager host = at.getWorld().spawn(l, org.bukkit.entity.Ravager.class, h -> {
                h.setPersistent(false);
                h.setRemoveWhenFarAway(false);
            });
            models.attach(host, rig);
            hosts.add(host);
        }
        int entities1 = at.getWorld().getEntityCount(), displays = models.displays();
        s.sendMessage(Messages.info("[perf] " + n + " × " + rig + ": " + displays + " displays, " + (entities1 - entities0) + " new entities; " + seconds + " s…"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            long heap1 = rt.totalMemory() - rt.freeMemory();
            double perSecond = (models.transformsSent() - sent0) / (double) seconds;
            for (org.bukkit.entity.LivingEntity h : hosts) h.remove();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                at.setInvulnerable(invul);
                benchExtra = String.format(Locale.ROOT, "\"model\":{\"rig\":\"%s\",\"instances\":%d,\"displays\":%d,\"newEntities\":%d,"
                        + "\"transformsPerSecond\":%.1f,\"heapDeltaMb\":%.2f,\"leftAfterDespawn\":%d},", rig, n, displays, entities1 - entities0,
                        perSecond, (heap1 - heap0) / 1048576.0, models.displays());
                Path p = stop();
                benchExtra = "";
                s.sendMessage(Messages.success(String.format(Locale.ROOT, "[perf] %d × %s: %.0f transforms/s, heap Δ %.1f MB → %s", n, rig,
                        perSecond, (heap1 - heap0) / 1048576.0, p)));
            }, 3L);
        }, seconds * 20L);
    }

    /** Extra JSON members of the next report (model benchmark figures). */
    private String benchExtra = "";

    public TabExecutor command() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
                switch (sub) {
                    case "start" -> {
                        start(a.length > 1 ? a[1] : "session-" + System.currentTimeMillis());
                        s.sendMessage(Messages.success("[perf] хэмжилт эхэллээ: " + label));
                    }
                    case "stop" -> {
                        Path p = stop();
                        s.sendMessage(p == null ? Messages.error("[perf] идэвхтэй хэмжилт алга.") : Messages.success("[perf] тайлан: " + p));
                    }
                    case "bench" -> {
                        int n;
                        try {
                            n = a.length > 1 ? Math.max(100, Math.min(100_000, Integer.parseInt(a[1]))) : 10_000;
                        } catch (NumberFormatException e) {
                            n = 10_000;
                        }
                        bench(s, n);
                    }
                    case "model" -> {
                        int n = 1, secs = 30;
                        try {
                            if (a.length > 1) n = Math.max(1, Math.min(50, Integer.parseInt(a[1])));
                            if (a.length > 2) secs = Math.max(5, Math.min(600, Integer.parseInt(a[2])));
                        } catch (NumberFormatException ignored) {
                            // defaults
                        }
                        Player at = a.length > 3 ? Bukkit.getPlayerExact(a[3]) : s instanceof Player p ? p : null;
                        modelBench(s, n, secs, at, a.length > 4 ? a[4] : "khasar");
                    }
                    case "hud" -> {
                        boolean on = a.length < 2 || !a[1].equalsIgnoreCase("off");
                        services.hud().panelEnabled(on);
                        s.sendMessage(Messages.info("[perf] HUD panel " + (on ? "on" : "off (measurement)")));
                    }
                    case "report" -> {
                        for (Map.Entry<String, PerfProbe.Stats> e : PerfProbe.snapshot().entrySet()) {
                            PerfProbe.Stats st = e.getValue();
                            s.sendMessage(Messages.info(String.format(Locale.ROOT, "%s n=%d mean=%.1fµs p95=%.1fµs p99=%.1fµs max=%.1fµs",
                                    e.getKey(), st.count(), st.meanUs(), st.p95Us(), st.p99Us(), st.maxUs())));
                        }
                    }
                    default -> s.sendMessage(Messages.info("/suldperf start <нэр> | stop | bench [n] | report | hud on|off | model <n> <сек> [тоглогч] [rig]"));
                }
                return true;
            }

            @Override
            public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                return a.length == 1 ? List.of("start", "stop", "bench", "report", "hud", "model").stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList() : List.of();
            }
        };
    }
}
