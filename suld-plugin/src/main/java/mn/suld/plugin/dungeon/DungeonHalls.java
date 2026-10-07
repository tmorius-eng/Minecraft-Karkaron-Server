package mn.suld.plugin.dungeon;

import mn.suld.api.dungeon.hall.DungeonSite;
import mn.suld.api.dungeon.hall.EntranceBlueprint;
import mn.suld.api.dungeon.hall.HallBlueprint;
import mn.suld.api.dungeon.hall.HallTheme;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Dungeon halls and gates (docs/world/DUNGEON_HALLS.md).
 * <p>
 * <b>Halls.</b> Every run takes place in a themed arena in the void world {@code suld_halls}: one row of instance slots
 * per theme ({@code x = theme × 1024}, {@code z = slot × 160}), each slot a full {@link HallBlueprint}. A slot is built
 * the first time it is needed (chunks prepared asynchronously, then {@code build-blocks-per-tick} blocks a tick) and
 * reused afterwards; {@code halls.yml} remembers which slots exist at which blueprint version. While a party is inside,
 * the slot's chunks hold a plugin ticket; afterwards they unload. Nobody can break or place blocks there, nothing
 * spawns naturally, and a player who dies, logs in or respawns there without a run goes back to the gate.
 * <p>
 * <b>Gates.</b> Each dungeon has a gate at a fixed place in its region ({@link DungeonSite}): built once on the
 * surface (chunk loaded asynchronously, foundation down into the ground), with a floating title and a clickable
 * doorway. {@code /dungeon enter} works only at the gate (16 blocks).
 */
public final class DungeonHalls implements Listener {

    public static final String WORLD = "suld_halls";
    public static final String TAG = "suld_gate";
    private static final int SLOT_SPACING = 160, THEME_SPACING = 1024, MAX_SLOTS = 12;
    public static final double GATE_RANGE = 16;

    /** A hall a run occupies. */
    public record Hall(HallTheme theme, int slot, Location origin) {
        public Location at(HallBlueprint.Anchor a) {
            Location l = origin.clone().add(a.x(), a.y(), a.z());
            l.setYaw(a.yaw());
            return l;
        }
    }

    private final Plugin plugin;
    private final List<DungeonSite> sites;
    private final File stateFile;
    private final int perTick;
    private World world;
    private final Map<HallTheme, Set<Integer>> built = new HashMap<>();
    private final Map<HallTheme, Set<Integer>> busy = new HashMap<>();
    private final Map<HallTheme, Set<Integer>> building = new HashMap<>();
    private final Map<String, Location> gates = new HashMap<>();
    private final Map<String, UUID[]> gateEntities = new HashMap<>();
    private final ArrayDeque<Runnable> buildQueue = new ArrayDeque<>();

    public DungeonHalls(Plugin plugin, List<DungeonSite> sites) {
        this.plugin = plugin;
        this.sites = List.copyOf(sites);
        this.stateFile = new File(plugin.getDataFolder(), "halls.yml");
        this.perTick = Math.max(500, plugin.getConfig().getInt("world.build-blocks-per-tick", 4000));
    }

    // ------------------------------------------------------------------ lifecycle

    /** An empty world: no terrain, no structures, no caves; one void biome. */
    static final class VoidGenerator extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }

        @Override
        public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
            return new BiomeProvider() {
                @Override public Biome getBiome(WorldInfo w, int x, int y, int z) { return Biome.THE_VOID; }
                @Override public List<Biome> getBiomes(WorldInfo w) { return List.of(Biome.THE_VOID); }
            };
        }
    }

    @SuppressWarnings("removal")
    public void start() {
        world = Bukkit.getWorld(WORLD);
        if (world == null) {
            world = new WorldCreator(WORLD).generator(new VoidGenerator()).generateStructures(false).createWorld();
        }
        if (world == null) {
            plugin.getLogger().severe("Dungeon halls: cannot create world " + WORLD + "; dungeons run in the open world.");
            return;
        }
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_INSOMNIA, false);
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setTime(18000);
        world.setStorm(false);
        world.setSpawnLocation(0, 80, 0);
        YamlConfiguration st = YamlConfiguration.loadConfiguration(stateFile);
        for (HallTheme t : HallTheme.values()) {
            Set<Integer> s = new HashSet<>();
            if (st.getInt("version." + t.key(), -1) == HallBlueprint.VERSION) s.addAll(st.getIntegerList("built." + t.key()));
            built.put(t, s);
            busy.put(t, new HashSet<>());
            building.put(t, new HashSet<>());
        }
        org.bukkit.configuration.ConfigurationSection gs = st.getConfigurationSection("gates");
        if (gs != null) for (String k : gs.getKeys(false)) {
            gateRecords.put(k, new int[]{gs.getInt(k + ".x"), gs.getInt(k + ".y"), gs.getInt(k + ".z"), gs.getInt(k + ".version", -1)});
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::drainBuild, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::keepGates, 40L, 100L);
        Bukkit.getScheduler().runTaskLater(plugin, this::placeGates, 100L);
        if (plugin.getConfig().getBoolean("dungeons.prewarm-halls", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> prewarm(new ArrayDeque<>(List.of(HallTheme.values()))), 20L * 45);
        }
        plugin.getLogger().info("Dungeon halls: world " + WORLD + " ready; built slots " + countBuilt() + ".");
    }

    /**
     * One hall per theme is built ahead (one theme at a time, 10 s apart, only while nobody is in it), so the first
     * party at any gate walks straight in instead of waiting for the build.
     */
    private void prewarm(ArrayDeque<HallTheme> todo) {
        HallTheme t = todo.pollFirst();
        if (t == null || !plugin.isEnabled()) return;
        if (!built.get(t).isEmpty() || building.get(t).contains(0) || busy.get(t).contains(0)) {
            prewarm(todo);
            return;
        }
        building.get(t).add(0);
        build(originOf(t, 0), HallBlueprint.build(t), false, () -> {
            building.get(t).remove(0);
            built.get(t).add(0);
            saveState();
            plugin.getLogger().info("Dungeon halls: pre-built " + t.key() + " slot 0.");
            Bukkit.getScheduler().runTaskLater(plugin, () -> prewarm(todo), 200L);
        });
    }

    public void shutdown() {
        writer.shutdown();
        for (UUID[] ids : gateEntities.values()) for (UUID id : ids) {
            Entity e = id == null ? null : Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        gateEntities.clear();
    }

    public boolean ready() {
        return world != null;
    }

    public World world() {
        return world;
    }

    private int countBuilt() {
        int n = 0;
        for (Set<Integer> s : built.values()) n += s.size();
        return n;
    }

    /** Gate records (short id → x, y, z, version), kept in memory and written with the slots. */
    private final Map<String, int[]> gateRecords = new HashMap<>();
    private final java.util.concurrent.ExecutorService writer = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "suld-halls-state");
        t.setDaemon(true);
        return t;
    });

    /** Snapshot on the main thread, written by one thread with an atomic replace (a reader never sees half a file). */
    private void saveState() {
        YamlConfiguration st = new YamlConfiguration();
        for (HallTheme t : HallTheme.values()) {
            st.set("version." + t.key(), HallBlueprint.VERSION);
            st.set("built." + t.key(), new ArrayList<>(built.get(t)));
        }
        for (Map.Entry<String, int[]> g : gateRecords.entrySet()) {
            String key = "gates." + g.getKey();
            st.set(key + ".x", g.getValue()[0]);
            st.set(key + ".y", g.getValue()[1]);
            st.set(key + ".z", g.getValue()[2]);
            st.set(key + ".version", g.getValue()[3]);
        }
        String text = st.saveToString();
        Runnable write = () -> {
            try {
                java.nio.file.Path tmp = stateFile.toPath().resolveSibling("halls.yml.tmp");
                java.nio.file.Files.createDirectories(tmp.getParent());
                java.nio.file.Files.writeString(tmp, text);
                java.nio.file.Files.move(tmp, stateFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                plugin.getLogger().warning("Dungeon halls: cannot save halls.yml: " + e.getMessage());
            }
        };
        if (plugin.isEnabled()) writer.execute(write); else write.run();
    }

    // ------------------------------------------------------------------ halls

    private Location originOf(HallTheme t, int slot) {
        return new Location(world, t.ordinal() * THEME_SPACING, 64, slot * SLOT_SPACING);
    }

    /**
     * A free hall of {@code theme}: immediately when a built slot is free, otherwise after one is built. Completes on
     * the main thread; empty when all {@link #MAX_SLOTS} are busy.
     */
    public CompletableFuture<Optional<Hall>> acquire(HallTheme t) {
        CompletableFuture<Optional<Hall>> f = new CompletableFuture<>();
        if (world == null) {
            f.complete(Optional.empty());
            return f;
        }
        for (int s : built.get(t)) {
            if (!busy.get(t).contains(s)) {
                busy.get(t).add(s);
                Hall h = new Hall(t, s, originOf(t, s));
                // the slot's chunks may be unloaded: prepare them off the main thread, then hand the hall over
                holdAsync(h).whenComplete((v, err) -> onMain(() -> f.complete(Optional.of(h))));
                return f;
            }
        }
        int slot = -1;
        for (int s = 0; s < MAX_SLOTS; s++) {
            if (!built.get(t).contains(s) && !building.get(t).contains(s)) {
                slot = s;
                break;
            }
        }
        if (slot < 0) {
            f.complete(Optional.empty());
            return f;
        }
        int s = slot;
        building.get(t).add(s);
        busy.get(t).add(s);
        Hall h = new Hall(t, s, originOf(t, s));
        build(h.origin(), HallBlueprint.build(t), true, () -> { // build() loads the chunks async and leaves its tickets for the run
            building.get(t).remove(s);
            built.get(t).add(s);
            saveState();
            plugin.getLogger().info("Dungeon halls: built " + t.key() + " slot " + s + ".");
            f.complete(Optional.of(h));
        });
        return f;
    }

    public void release(Hall h) {
        busy.get(h.theme()).remove(h.slot());
        // leftovers (drops, arrows, a summon that escaped cleanup) go with the party; anyone still inside goes out
        int r = HallBlueprint.extent() + 2;
        for (Entity e : world.getNearbyEntities(h.origin(), r, 20, r)) {
            if (e instanceof Player p) {
                mn.suld.plugin.perf.SafeTeleport.to(plugin, p, exitFor(p.getUniqueId()));
            } else {
                e.remove();
            }
        }
        hold(h, false);
    }

    /**
     * Loads the hall's chunks asynchronously and tickets them for the run. A plugin ticket on an unloaded chunk would
     * load (or generate) it synchronously on the main thread (World#addPluginChunkTicket), so tickets are only ever
     * added to chunks that are already loaded.
     */
    private CompletableFuture<Void> holdAsync(Hall h) {
        int r = HallBlueprint.extent();
        int cx0 = (h.origin().getBlockX() - r) >> 4, cx1 = (h.origin().getBlockX() + r) >> 4;
        int cz0 = (h.origin().getBlockZ() - r) >> 4, cz1 = (h.origin().getBlockZ() + r) >> 4;
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                loads.add(world.getChunkAtAsync(cx, cz, true).thenAccept(c -> onMain(() -> c.addPluginChunkTicket(plugin))));
            }
        }
        return CompletableFuture.allOf(loads.toArray(new CompletableFuture[0]));
    }

    private void onMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run(); else Bukkit.getScheduler().runTask(plugin, r);
    }

    /** Removes the run's chunk tickets (removal never loads anything). */
    private void hold(Hall h, boolean on) {
        int r = HallBlueprint.extent();
        int cx0 = (h.origin().getBlockX() - r) >> 4, cx1 = (h.origin().getBlockX() + r) >> 4;
        int cz0 = (h.origin().getBlockZ() - r) >> 4, cz1 = (h.origin().getBlockZ() + r) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (on) world.addPluginChunkTicket(cx, cz, plugin); else world.removePluginChunkTicket(cx, cz, plugin);
            }
        }
    }

    public boolean inHalls(Location l) {
        return world != null && l != null && world.equals(l.getWorld());
    }

    // ------------------------------------------------------------------ building

    /** Prepares the chunks asynchronously, then queues the placements; {@code done} runs on the main thread. */
    private void build(Location origin, List<HallBlueprint.Placement> ps, boolean keepTickets, Runnable done) {
        World w = origin.getWorld();
        Set<Long> chunks = new HashSet<>();
        for (HallBlueprint.Placement p : ps) {
            chunks.add((((long) ((origin.getBlockX() + p.x()) >> 4)) << 32) ^ (((origin.getBlockZ() + p.z()) >> 4) & 0xffffffffL));
        }
        List<CompletableFuture<Chunk>> loads = new ArrayList<>();
        for (long k : chunks) loads.add(w.getChunkAtAsync((int) (k >> 32), (int) k, true));
        CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            for (long k : chunks) w.addPluginChunkTicket((int) (k >> 32), (int) k, plugin);
            List<Object[]> resolved = new ArrayList<>(ps.size());
            Map<String, BlockData> cache = new HashMap<>();
            for (HallBlueprint.Placement p : ps) {
                BlockData bd = cache.computeIfAbsent(p.block(), s -> {
                    try {
                        return Bukkit.createBlockData(s);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Dungeon halls: unknown block " + s + " (skipped)");
                        return null;
                    }
                });
                if (bd != null) resolved.add(new Object[]{p, bd});
            }
            int[] i = {0};
            Runnable step = new Runnable() {
                @Override
                public void run() {
                    int n = 0;
                    while (i[0] < resolved.size() && n++ < perTick) {
                        Object[] o = resolved.get(i[0]++);
                        HallBlueprint.Placement p = (HallBlueprint.Placement) o[0];
                        w.getBlockAt(origin.getBlockX() + p.x(), origin.getBlockY() + p.y(), origin.getBlockZ() + p.z())
                                .setBlockData((BlockData) o[1], false);
                    }
                    if (i[0] < resolved.size()) {
                        buildQueue.addFirst(this);
                    } else {
                        if (!keepTickets) for (long k : chunks) w.removePluginChunkTicket((int) (k >> 32), (int) k, plugin);
                        done.run();
                    }
                }
            };
            buildQueue.addLast(step);
        }));
    }

    private void drainBuild() {
        Runnable r = buildQueue.pollFirst();
        if (r != null) r.run();
    }

    // ------------------------------------------------------------------ gates

    public List<DungeonSite> sites() {
        return sites;
    }

    public Optional<DungeonSite> site(String dungeonId) {
        return sites.stream().filter(s -> s.dungeonId().equals(dungeonId)).findFirst();
    }

    /** The gate's standing point, once the gate exists. */
    public Optional<Location> gate(String dungeonId) {
        Location g = gates.get(dungeonId);
        if (g == null) return Optional.empty();
        Location l = g.clone().add(EntranceBlueprint.STAND.x(), EntranceBlueprint.STAND.y(), EntranceBlueprint.STAND.z());
        l.setYaw(EntranceBlueprint.STAND.yaw());
        return Optional.of(l);
    }

    /** Where a gate will stand (block x/z), even before it is built. */
    public int[] gateXZ(DungeonSite s) {
        Location sp = Bukkit.getWorlds().get(0).getSpawnLocation();
        int[] o = s.offset();
        return new int[]{sp.getBlockX() + o[0], sp.getBlockZ() + o[1]};
    }

    public boolean nearGate(Player p, String dungeonId) {
        Location g = gate(dungeonId).orElse(null);
        return g != null && g.getWorld().equals(p.getWorld()) && g.distanceSquared(p.getLocation()) <= GATE_RANGE * GATE_RANGE;
    }

    /** Builds every gate that is missing at its current spawn-relative place (also after the spawn moved). */
    public void placeGates() {
        World ow = Bukkit.getWorlds().get(0);
        for (DungeonSite s : sites) {
            int[] xz = gateXZ(s);
            int[] rec = gateRecords.get(s.shortId());
            if (rec != null && rec[3] == EntranceBlueprint.VERSION && rec[0] == xz[0] && rec[2] == xz[1]) {
                gates.put(s.dungeonId(), new Location(ow, rec[0], rec[1], rec[2]));
                continue;
            }
            gates.remove(s.dungeonId());
            ow.getChunkAtAsync(xz[0] >> 4, xz[1] >> 4, true).thenAccept(c -> Bukkit.getScheduler().runTask(plugin, () -> {
                int y = ow.getHighestBlockYAt(xz[0], xz[1], HeightMap.MOTION_BLOCKING_NO_LEAVES);
                y = Math.max(ow.getSeaLevel(), y); // on water: a platform at the surface
                Location at = new Location(ow, xz[0], y, xz[1]);
                build(at, EntranceBlueprint.build(s.theme()), false, () -> {
                    gates.put(s.dungeonId(), at);
                    gateRecords.put(s.shortId(), new int[]{at.getBlockX(), at.getBlockY(), at.getBlockZ(), EntranceBlueprint.VERSION});
                    saveState();
                    plugin.getLogger().info("Dungeon gate " + s.shortId() + " built at " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ());
                });
            }));
        }
    }

    /** Title and clickable doorway of every gate whose chunk is loaded (not persistent; respawned as needed). */
    private void keepGates() {
        for (DungeonSite s : sites) {
            Location g = gates.get(s.dungeonId());
            if (g == null) continue;
            UUID[] ids = gateEntities.get(s.dungeonId());
            boolean loaded = g.getWorld().isChunkLoaded(g.getBlockX() >> 4, g.getBlockZ() >> 4);
            if (!loaded) {
                gateEntities.remove(s.dungeonId());
                continue;
            }
            if (ids != null && Bukkit.getEntity(ids[0]) != null && Bukkit.getEntity(ids[1]) != null) continue;
            if (ids != null) for (UUID id : ids) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) e.remove();
            }
            Location title = g.clone().add(0.5, 10.2, -0.5);
            TextDisplay td = g.getWorld().spawn(title, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.addScoreboardTag(TAG);
                d.setBillboard(Display.Billboard.CENTER);
                d.setShadowed(true);
                d.setBackgroundColor(org.bukkit.Color.fromARGB(150, 10, 6, 20));
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.6f, 1.6f, 1.6f), new Quaternionf()));
                d.text(gateTitle(s));
            });
            Interaction door = g.getWorld().spawn(g.clone().add(0.5, 1, -0.5), Interaction.class, d -> {
                d.setPersistent(false);
                d.addScoreboardTag(TAG);
                d.setInteractionWidth(3.2f);
                d.setInteractionHeight(6f);
                d.setResponsive(true);
            });
            gateEntities.put(s.dungeonId(), new UUID[]{td.getUniqueId(), door.getUniqueId()});
        }
    }

    private Component gateTitle(DungeonSite s) {
        var def = mn.suld.plugin.content.DungeonContent.ALL.stream().filter(d -> d.id().equals(s.dungeonId())).findFirst().orElse(null);
        String name = def == null ? s.shortId() : def.displayName();
        int lvl = def == null ? 1 : def.minLevel();
        return Component.text("⚔ " + name, NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("Түвшин " + lvl + "+ · Хаалгыг дарж оръё", NamedTextColor.GRAY));
    }

    @EventHandler
    public void onDoor(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Interaction) || !e.getRightClicked().getScoreboardTags().contains(TAG)) return;
        for (Map.Entry<String, UUID[]> en : gateEntities.entrySet()) {
            if (en.getValue()[1].equals(e.getRightClicked().getUniqueId())) {
                e.setCancelled(true);
                site(en.getKey()).ifPresent(s -> e.getPlayer().performCommand("dungeon enter " + s.shortId()));
                return;
            }
        }
    }

    // ------------------------------------------------------------------ protection and stray players

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (inHalls(e.getBlock().getLocation()) && !e.getPlayer().hasPermission("suld.admin.world")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (inHalls(e.getBlock().getLocation()) && !e.getPlayer().hasPermission("suld.admin.world")) e.setCancelled(true);
    }

    /** The way out for anyone in the halls without a run (DungeonService decides who has one). */
    private java.util.function.Predicate<UUID> inRun = id -> false;
    private java.util.function.Function<UUID, Optional<String>> lastDungeon = id -> Optional.empty();

    public void hooks(java.util.function.Predicate<UUID> inRun, java.util.function.Function<UUID, Optional<String>> lastDungeon) {
        this.inRun = inRun;
        this.lastDungeon = lastDungeon;
    }

    public Location exitFor(UUID player) {
        return lastDungeon.apply(player).flatMap(this::gate).orElse(Bukkit.getWorlds().get(0).getSpawnLocation().add(0.5, 0, 0.5));
    }

    /** Whatever a player loses by dying in a hall is left at the gate (the hall is closed to them afterwards). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!inHalls(p.getLocation()) || e.getKeepInventory() || e.getDrops().isEmpty()) return;
        List<org.bukkit.inventory.ItemStack> drops = new ArrayList<>(e.getDrops());
        e.getDrops().clear();
        Location to = exitFor(p.getUniqueId());
        to.getWorld().getChunkAtAsync(to).thenAccept(c -> Bukkit.getScheduler().runTask(plugin, () -> {
            for (org.bukkit.inventory.ItemStack it : drops) to.getWorld().dropItem(to.clone().add(0, 0.5, 0), it);
        }));
        p.sendMessage(Messages.info("Танхимд унасан эд зүйлс тань хаалган дээр үлдлээ."));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        if (inHalls(e.getRespawnLocation()) || inHalls(e.getPlayer().getLocation())) e.setRespawnLocation(exitFor(e.getPlayer().getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!inHalls(p.getLocation()) || inRun.test(p.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && inHalls(p.getLocation()) && !inRun.test(p.getUniqueId())) {
                mn.suld.plugin.perf.SafeTeleport.to(plugin, p, exitFor(p.getUniqueId()), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN,
                        ok -> p.sendMessage(Messages.info("Агуйн аян дууссан байсан тул хаалган дээр гарлаа.")));
            }
        }, 10L);
    }

    /** Sends a player who is in the halls back to the gate of {@code dungeonId} (or the spawn). */
    public void sendOut(Player p, String dungeonId) {
        if (!inHalls(p.getLocation())) return;
        Location to = gate(dungeonId).orElse(Bukkit.getWorlds().get(0).getSpawnLocation().add(0.5, 0, 0.5));
        mn.suld.plugin.perf.SafeTeleport.to(plugin, p, to, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN, null);
    }

    public void describe(Consumer<String> say) {
        for (HallTheme t : HallTheme.values()) {
            say.accept(t.key() + ": built " + built.get(t) + " busy " + busy.get(t) + (building.get(t).isEmpty() ? "" : " building " + building.get(t)));
        }
        for (DungeonSite s : sites) {
            Location g = gates.get(s.dungeonId());
            say.accept("gate " + s.shortId() + ": " + (g == null ? "not built" : g.getBlockX() + " " + g.getBlockY() + " " + g.getBlockZ()));
        }
    }
}
