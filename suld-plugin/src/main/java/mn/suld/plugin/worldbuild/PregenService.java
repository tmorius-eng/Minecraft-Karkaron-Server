package mn.suld.plugin.worldbuild;

import mn.suld.api.region.Area;
import mn.suld.api.worldbuild.PregenPlan;
import mn.suld.api.worldbuild.PregenPlan.Priority;
import mn.suld.plugin.content.WorldContent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * SÜLD's own throttled pre-generator (docs/world/PREGENERATION.md). The 10 000-block border is the playable limit, not a
 * generation order: only the places players are sent to are generated ahead (P0 spawn and Kharkhorum, P1 the routes out
 * of the city, P2 shrines and dungeon grounds, P3 the heart of every named area); the open wilderness (P5) is left to
 * normal exploration unless an admin explicitly queues it.
 * <p>
 * Work is spread over ticks: at most {@code max-per-tick} new requests per tick and {@code max-in-flight} outstanding,
 * all through Paper's asynchronous chunk system ({@code getChunkAtAsync}), never a synchronous load. The in-flight
 * allowance adapts to the measured tick time (halved above {@code target-mspt}, zero above {@code pause-mspt}, grown
 * by one while the server is idle), so players never pay for it. Progress is a cursor in {@code pregen.yml}, written
 * asynchronously; a restart resumes where it stopped and a changed plan (moved spawn) starts over, re-reading the
 * chunks that already exist instead of generating them again.
 */
public final class PregenService {

    public enum Status { IDLE, RUNNING, PAUSED, DONE, CANCELLED }

    /** An extra place to generate ahead (dungeon grounds and the like), registered by its owner. */
    public record Point(String id, Priority priority, String world, int x, int z, int radiusBlocks) {
    }

    private final Plugin plugin;
    private final WorldBuildService city;
    private final File stateFile;
    private final List<Point> points = new ArrayList<>();

    private BukkitTask waiting;
    private BukkitTask ticker;
    private PregenPlan plan;
    private PregenPlan.Cursor cursor = PregenPlan.Cursor.START;
    private Status status = Status.IDLE;
    private boolean full;
    private long generated;
    private long unique = -1;
    private int inFlight;
    private int allowance = 2;
    private long startedAt;
    private long lastSave;
    private double rate; // chunks per second, smoothed
    private long rateWindowStart;
    private long rateWindowCount;
    private int lowSince;
    private String lastFingerprint;

    public PregenService(Plugin plugin, WorldBuildService city) {
        this.plugin = plugin;
        this.city = city;
        this.stateFile = new File(plugin.getDataFolder(), "pregen.yml");
    }

    public void addPoint(Point p) {
        points.removeIf(q -> q.id().equals(p.id()));
        points.add(p);
    }

    private int cfgInt(String key, int def) {
        return plugin.getConfig().getInt("world.pregenerate." + key, def);
    }

    /** Waits for the city (the plan is centred on its plaza), then resumes or starts the automatic plan. */
    public void start() {
        if (!plugin.getConfig().getBoolean("world.pregenerate.enabled", true)) return;
        waiting = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!city.isBuilt()) return;
            waiting.cancel();
            waiting = null;
            YamlConfiguration st = loadState();
            full = st.getBoolean("full", false);
            String saved = st.getString("status", "IDLE");
            if (saved.equals("CANCELLED") || saved.equals("PAUSED")) {
                status = Status.valueOf(saved);
                build();
                restoreCursor(st);
                plugin.getLogger().info("Pre-generation: " + saved.toLowerCase(Locale.ROOT) + " by an admin; /suldworld pregen resume");
                return;
            }
            build();
            if (restoreCursor(st) && saved.equals("DONE")) {
                status = Status.DONE;
                return;
            }
            run(null);
        }, 20L * 10, 20L * 10);
    }

    public void stop() {
        if (waiting != null) waiting.cancel();
        if (ticker != null) ticker.cancel();
        ticker = null;
        if (plan != null) saveState(true);
    }

    // ------------------------------------------------------------------ plan

    private void build() {
        World w = WorldBorderService.overworld();
        Location spawn = w.getSpawnLocation();
        int sx = spawn.getBlockX() >> 4, sz = spawn.getBlockZ() >> 4;
        Set<Priority> levels = levels();
        List<PregenPlan.Job> jobs = new ArrayList<>();
        if (levels.contains(Priority.P0)) {
            jobs.add(PregenPlan.Job.square("spawn", Priority.P0, sx, sz, Math.max(4, cfgInt("spawn-radius", 384) >> 4)));
        }
        int half = Math.max(1, cfgInt("corridor-half-width", 2));
        int areaR = Math.max(2, cfgInt("area-radius", 128) >> 4);
        for (Area a : WorldContent.AREAS) {
            double span = ((a.toDeg() - a.fromDeg()) % 360 + 360) % 360;
            double mid = Math.toRadians(a.fromDeg() + (span == 0 ? 360 : span) / 2);
            double r = (a.minRadius() + Math.min(a.maxRadius(), borderRadius() - 64)) / 2;
            int ax = sx + (int) Math.round(Math.sin(mid) * r / 16), az = sz + (int) Math.round(-Math.cos(mid) * r / 16);
            boolean near = a.maxRadius() <= 1500;
            Priority route = near ? Priority.P1 : Priority.P4;
            if (levels.contains(route)) jobs.add(PregenPlan.Job.corridor("route." + a.id(), route, sx, sz, ax, az, half));
            if (levels.contains(Priority.P3)) jobs.add(PregenPlan.Job.square(a.id(), Priority.P3, ax, az, areaR));
        }
        for (Point p : points) {
            if (!levels.contains(p.priority()) || !p.world().equals(w.getName())) continue;
            jobs.add(PregenPlan.Job.square(p.id(), p.priority(), p.x() >> 4, p.z() >> 4, Math.max(1, p.radiusBlocks() >> 4)));
        }
        if (full) {
            jobs.add(PregenPlan.Job.square("wilderness", Priority.P5, sx, sz, (int) Math.ceil(borderRadius() / 16)));
        }
        WorldBorder b = w.getWorldBorder();
        double bx = b.getCenter().getX(), bz = b.getCenter().getZ(), br = b.getSize() / 2;
        plan = new PregenPlan(jobs, (cx, cz) -> {
            // wanted when any part of the chunk is inside the border
            double minX = cx * 16.0, minZ = cz * 16.0;
            return minX + 16 > bx - br && minX < bx + br && minZ + 16 > bz - br && minZ < bz + br;
        });
        unique = -1;
        PregenPlan p = plan;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long n = p.countUnique();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plan == p) unique = n;
            });
        });
    }

    private double borderRadius() {
        return WorldBorderService.overworld().getWorldBorder().getSize() / 2;
    }

    private Set<Priority> levels() {
        Set<Priority> out = EnumSet.noneOf(Priority.class);
        List<String> raw = plugin.getConfig().getStringList("world.pregenerate.levels");
        if (raw.isEmpty()) raw = List.of("P0", "P1", "P2", "P3");
        for (String s : raw) {
            try {
                out.add(Priority.valueOf(s.strip().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("world.pregenerate.levels: unknown level " + s + " (P0..P5)");
            }
        }
        return out;
    }

    private boolean restoreCursor(YamlConfiguration st) {
        lastFingerprint = st.getString("plan");
        if (plan.fingerprint().equals(lastFingerprint)) {
            cursor = new PregenPlan.Cursor(st.getInt("job"), st.getInt("center"), st.getLong("step"));
            generated = st.getLong("generated");
            return true;
        }
        cursor = PregenPlan.Cursor.START;
        generated = 0;
        return false;
    }

    // ------------------------------------------------------------------ control

    public void run(Consumer<String> say) {
        if (plan == null) build();
        if (status == Status.DONE && say != null) {
            say.accept("Урьдчилсан үүсгэлт аль хэдийн дууссан. Шинээр: /suldworld pregen restart");
            return;
        }
        status = Status.RUNNING;
        startedAt = System.currentTimeMillis();
        rateWindowStart = startedAt;
        rateWindowCount = 0;
        if (ticker == null) ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        plugin.getLogger().info("Pre-generation: running " + plan.jobs().size() + " job(s), up to " + plan.upperBound()
                + " chunks" + (generated > 0 ? ", resuming after " + generated : "") + ". /suldworld pregen status");
        saveState(false);
        if (say != null) say.accept("Урьдчилсан үүсгэлт эхэллээ.");
    }

    public void pause(Consumer<String> say) {
        if (status != Status.RUNNING) {
            say.accept("Ажиллаагүй байна (" + status + ").");
            return;
        }
        status = Status.PAUSED;
        saveState(true);
        say.accept("Түр зогслоо. Үргэлжлүүлэх: /suldworld pregen resume");
    }

    public void cancel(Consumer<String> say) {
        status = Status.CANCELLED;
        full = false;
        saveState(true);
        say.accept("Цуцаллаа. Дахин эхлүүлэх: /suldworld pregen restart");
    }

    public void restart(Consumer<String> say) {
        cursor = PregenPlan.Cursor.START;
        generated = 0;
        status = Status.IDLE;
        build();
        run(say);
    }

    /** Queues the whole playable square (P5) after the priority jobs. */
    public void queueFull(Consumer<String> say) {
        full = true;
        PregenPlan.Cursor keep = cursor;
        long keepGen = generated;
        String before = plan == null ? null : plan.fingerprint();
        build();
        if (before != null) { // the P5 job is appended last, so the cursor into the earlier jobs stays valid
            cursor = keep;
            generated = keepGen;
        }
        if (status == Status.DONE) status = Status.IDLE;
        long side = (long) Math.ceil(borderRadius() / 16) * 2 + 1;
        say.accept("Бүх нутгийг дараалалд нэмлээ: ~" + side * side + " chunk (хилийн доторх). Явц: /suldworld pregen status");
        run(null);
    }

    // ------------------------------------------------------------------ tick

    private double recentMspt() {
        long[] times = Bukkit.getServer().getTickTimes();
        if (times == null || times.length == 0) return Bukkit.getServer().getAverageTickTime();
        int now = Bukkit.getCurrentTick();
        long sum = 0;
        int n = Math.min(20, times.length);
        for (int k = 1; k <= n; k++) sum += times[Math.floorMod(now - k, times.length)];
        return sum / (double) n / 1_000_000.0;
    }

    private void tick() {
        if (status != Status.RUNNING) {
            if (inFlight == 0) {
                ticker.cancel();
                ticker = null;
            }
            return;
        }
        double mspt = recentMspt();
        int target = cfgInt("target-mspt", 30), pauseAt = cfgInt("pause-mspt", 45), max = Math.max(1, cfgInt("max-in-flight", 12));
        if (mspt > pauseAt) {
            allowance = 0;
            lowSince = 0;
        } else if (mspt > target) {
            allowance = Math.max(1, allowance / 2);
            lowSince = 0;
        } else if (mspt < target - 10 && ++lowSince >= 10) { // ten calm ticks in a row: one more in flight
            allowance = Math.min(max, allowance + 1);
            lowSince = 0;
        }
        int perTick = Math.max(1, cfgInt("max-per-tick", 6));
        World w = WorldBorderService.overworld();
        for (int sent = 0; sent < perTick && inFlight < allowance; ) {
            PregenPlan.Step s = plan.next(cursor, 2048);
            if (s.done()) {
                if (inFlight == 0) finish();
                return;
            }
            cursor = s.after();
            if (s.chunk() == null) return; // skip budget spent; continue next tick
            int x = s.chunk()[0], z = s.chunk()[1];
            inFlight++;
            sent++;
            w.getChunkAtAsync(x, z, true).whenComplete((chunk, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
                inFlight--;
                if (err == null) {
                    generated++;
                    rateWindowCount++;
                    // let it go again unless a player (or anything else) holds it
                    w.unloadChunkRequest(x, z);
                } else {
                    plugin.getLogger().warning("Pre-generation: chunk " + x + "," + z + " failed: " + err.getMessage());
                }
            }));
        }
        long now = System.currentTimeMillis();
        if (now - rateWindowStart >= 5000) {
            double r = rateWindowCount * 1000.0 / (now - rateWindowStart);
            rate = rate == 0 ? r : rate * 0.6 + r * 0.4;
            rateWindowStart = now;
            rateWindowCount = 0;
        }
        if (now - lastSave > 15_000) saveState(false);
    }

    private void finish() {
        status = Status.DONE;
        saveState(true);
        plugin.getLogger().info("Pre-generation: done, " + generated + " chunks in "
                + (System.currentTimeMillis() - startedAt) / 1000 + " s.");
    }

    // ------------------------------------------------------------------ state

    private YamlConfiguration loadState() {
        YamlConfiguration st = new YamlConfiguration();
        if (!stateFile.isFile()) return st;
        try {
            st.load(stateFile);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Pre-generation: unreadable pregen.yml (" + e.getMessage() + "), starting over");
        }
        return st;
    }

    /** Snapshot on the main thread, write off it (atomic replace); {@code now} writes synchronously (shutdown). */
    private void saveState(boolean now) {
        if (plan == null) return;
        lastSave = System.currentTimeMillis();
        YamlConfiguration st = new YamlConfiguration();
        st.set("version", 2);
        st.set("plan", plan.fingerprint());
        st.set("status", status.name());
        st.set("full", full);
        st.set("job", cursor.job());
        st.set("center", cursor.center());
        st.set("step", cursor.step());
        st.set("generated", generated);
        st.set("savedAt", lastSave);
        String text = st.saveToString();
        Runnable write = () -> {
            try {
                Path tmp = stateFile.toPath().resolveSibling("pregen.yml.tmp");
                Files.createDirectories(tmp.getParent());
                Files.writeString(tmp, text, StandardCharsets.UTF_8);
                Files.move(tmp, stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                plugin.getLogger().warning("Pre-generation: cannot save pregen.yml: " + e.getMessage());
            }
        };
        if (now || !plugin.isEnabled()) write.run(); else Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
    }

    // ------------------------------------------------------------------ reports

    public List<String> status() {
        List<String> out = new ArrayList<>();
        if (plan == null) {
            out.add("Урьдчилсан үүсгэлт: " + (city.isBuilt() ? "бэлтгэгдээгүй" : "хот баригдахыг хүлээж байна"));
            return out;
        }
        out.add("Төлөв: " + status + " · үүсгэсэн " + generated + (unique > 0 ? " / " + unique : "") + " chunk"
                + (unique > 0 ? String.format(Locale.ROOT, " (%.1f%%)", 100.0 * generated / unique) : ""));
        out.add(String.format(Locale.ROOT, "Хурд %.1f chunk/s · зэрэг %d/%d · tick %.1f ms", rate, inFlight, allowance, recentMspt()));
        if (status == Status.RUNNING && rate > 0.1 && unique > 0) {
            long left = Math.max(0, unique - generated);
            out.add("Үлдсэн ~" + left + " chunk, ~" + Math.round(left / rate / 60) + " мин");
        }
        if (cursor.job() < plan.jobs().size()) {
            PregenPlan.Job j = plan.jobs().get(cursor.job());
            out.add("Одоо: " + j.priority() + " " + j.id() + " (" + (cursor.job() + 1) + "/" + plan.jobs().size() + ")");
        }
        return out;
    }

    /** World size, chunk counts and disk use; the region-file scan runs off the main thread. */
    public void report(Consumer<String> say) {
        World w = WorldBorderService.overworld();
        WorldBorder b = w.getWorldBorder();
        int loaded = w.getChunkCount(), entities = w.getEntityCount(), tiles = w.getTileEntityCount(), players = w.getPlayerCount();
        File region = new File(w.getWorldFolder(), "region");
        long side = (long) Math.ceil(b.getSize() / 16);
        List<String> head = new ArrayList<>(status());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long files = 0, bytes = 0, chunks = 0;
            File[] mca = region.listFiles((d, n) -> n.endsWith(".mca"));
            if (mca != null) {
                byte[] header = new byte[4096];
                for (File f : mca) {
                    files++;
                    bytes += f.length();
                    try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                        if (raf.length() < 4096) continue;
                        raf.readFully(header);
                        for (int i = 0; i < 4096; i += 4) {
                            if ((header[i] | header[i + 1] | header[i + 2] | header[i + 3]) != 0) chunks++;
                        }
                    } catch (IOException ignored) {
                        // a file being written right now; counted next time
                    }
                }
            }
            long fChunks = chunks, fFiles = files, fBytes = bytes;
            Bukkit.getScheduler().runTask(plugin, () -> {
                say.accept("Ертөнц " + w.getName() + ": хил " + (int) b.getSize() + " x " + (int) b.getSize() + " блок, төв "
                        + (int) b.getCenter().getX() + ", " + (int) b.getCenter().getZ());
                say.accept("Үүссэн chunk: " + fChunks + " / " + side * side + String.format(Locale.ROOT, " (%.2f%%)", 100.0 * fChunks / (side * side))
                        + " · " + fFiles + " region файл, " + fBytes / (1024 * 1024) + " MB");
                if (fChunks > 0) {
                    say.accept("Бүгдийг үүсгэвэл ~" + (fBytes / fChunks) * side * side / (1024L * 1024 * 1024) + " GB (дундаж "
                            + fBytes / fChunks / 1024 + " KB/chunk)");
                }
                say.accept("Ачаалагдсан chunk " + loaded + " · entity " + entities + " · tile " + tiles + " · тоглогч " + players);
                head.forEach(say);
            });
        });
    }
}
