package mn.suld.plugin.map;

import mn.suld.api.map.MapGeometry;
import mn.suld.api.map.MapPin;
import mn.suld.api.map.Minimap;
import mn.suld.api.region.Area;
import mn.suld.api.style.PlayerSetting;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.WorldContent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * The corner minimap (docs/WORLD_MAP.md): a 128 × 128 map of the real land around the player, drawn by
 * {@link Minimap} and shown in the top-left corner of the screen while {@code /map} is closed. The picture is a filled
 * map in invisible glow item frames that only its owner sees; the resource pack's text shader recognises it by the
 * colours in its first row and draws it in the corner instead of where the frames hang.
 * <p>
 * The frames hang {@value #DIST} blocks out around the look ({@link #SLOTS}), so one of them is always in view (the
 * client skips entities out of view), none is within reach of a click (an entity farther than 5 blocks is never
 * targeted) and none is on the player's line of fire. Shown only to a player with the SÜLD pack loaded (without the
 * shader the map would hang in the world) and the «Мини газрын зураг» setting on, in the overworld, outside the sky
 * windows, never to a Bedrock player (Geyser answers the pack request but cannot run a Java core shader). Redrawn when the player has moved {@value #MOVE_BLOCKS} blocks or turned, at most {@value #REDRAW_TICKS}
 * ticks apart (a single step is caught up after {@value #SETTLE_TICKS} ticks), at once when the waypoint or a pin
 * changes (the guide's target and {@link PinService#version}), when the hostile mobs close by have moved at most every
 * {@value #MOB_REDRAW_TICKS} ticks, and only sent when the picture changed.
 */
public final class MinimapService implements Listener {

    static final String TAG = "suld_minimap";
    static final double DIST = 7.5, NEAR = 72;
    /**
     * Where the frames hang, as (yaw, pitch) turns from the look in degrees (negative pitch = up): one 14° up in the
     * middle of the view (a 16:9 screen sees ±51° across, ±35° up and down), two 50° to the sides and one 24° under
     * the look, none on the line of fire (an arrow leaves just under the eye along the look and drops a few
     * hundredths of a block in 7.5). The server moves the frames a round trip after the client turned: the sides keep
     * the map drawn through fast turns (a two-frame layout at ±28° blinked on the real client) and the low frame
     * through a quick look down, which took every frame of the old three-frame layout (all 10–14° up) out of a view
     * only 35° tall, the blink players saw in fights: 21° of look-down within the lag was enough.
     */
    static final double[][] SLOTS = {{0, -14}, {-50, -10}, {50, -10}, {0, 24}};
    /**
     * A frame follows the look only once its spot is this far past the edge of the block it hangs in (~2° at
     * {@link #DIST}): hanging on the block grid, it hopped back and forth between two blocks on every small turn of a
     * look resting near an edge, a teleport packet a frame a tick. Small enough that the middle frame stays well over
     * the line of fire.
     */
    static final double MOVE_SLACK = 0.3;
    static final int REDRAW_TICKS = 5, SETTLE_TICKS = 20, STALE_TICKS = 200, MOVE_BLOCKS = 2;
    /** A frame something flew into steps {@value #PARK_UP} blocks up (~15°: still in view) for this long, enough for the shot to pass. */
    static final int PARK_TICKS = 5, PARK_UP = 2;
    /**
     * Hostile mobs within {@value #MOB_RANGE} blocks (the window reaches 60 across and 49 up and down) show as red dots,
     * the nearest {@value #MAX_MOBS}; they are looked up every {@value #MOB_SCAN_TICKS} ticks, and their moving redraws
     * the picture at most every {@value #MOB_REDRAW_TICKS} ticks (a fight would otherwise send a map every 5 ticks).
     */
    static final int MOB_RANGE = 48, MAX_MOBS = 24, MOB_SCAN_TICKS = 10, MOB_REDRAW_TICKS = 10;

    private final class State {
        final List<GlowItemFrame> frames = new ArrayList<>();
        /** A frame something flew into steps out of the way until this tick (see onProjectileHit). */
        final int[] parkedUntil = new int[SLOTS.length];
        byte[] image;
        int version, painted = -1;
        int lastX = Integer.MIN_VALUE, lastZ, lastTurn = -1, lastTick, lastPins = -1, lastMates, lastMobs, mobsKey, mobScan = Integer.MIN_VALUE / 2;
        List<int[]> mobs = List.of();
        WaypointGuide.Target lastTarget;
        boolean drawing;
        /** The eye the frames were last placed for: standing still and not turning, nothing is computed or sent. */
        double eyeX = Double.NaN, eyeY, eyeZ;
        float eyeYaw, eyePitch;
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final MapService map;
    private final PinService pins;
    private final WaypointGuide guide;
    private final Map<UUID, State> states = new HashMap<>();
    private MapView view;
    /** Set by SuldPlugin: true while the player is in a sky window (atlas, menu, skill tree). */
    public volatile Predicate<Player> busy = p -> false;

    public MinimapService(Plugin plugin, SuldServices services, MapService map, PinService pins, WaypointGuide guide) {
        this.plugin = plugin;
        this.services = services;
        this.map = map;
        this.pins = pins;
        this.guide = guide;
    }

    public void start() {
        view = minimapView();
        sweep();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
    }

    /** Frames no minimap owns (a reload, a crash in a chunk that stayed loaded): removed, never kept. */
    private void sweep() {
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            for (GlowItemFrame f : w.getEntitiesByClass(GlowItemFrame.class)) {
                if (orphan(f.getScoreboardTags(), owned(f))) {
                    f.remove();
                    n++;
                }
            }
        }
        if (n > 0) plugin.getLogger().info("[map] removed " + n + " orphaned minimap frame(s)");
    }

    private boolean owned(Entity e) {
        for (State s : states.values()) if (s.frames.contains(e)) return true;
        return false;
    }

    /** A minimap frame (by its tag) that no player's minimap holds. */
    static boolean orphan(java.util.Set<String> tags, boolean owned) {
        return tags.contains(TAG) && !owned;
    }

    /**
     * A Bedrock player through Geyser/Floodgate. Floodgate's own answer when it is installed: a Bedrock account linked to
     * a Java one plays under the Java UUID. Without it, the UUID's shape: Floodgate's are {@code new UUID(0, xuid)}, every
     * Java UUID (online v4, offline v3) has its version bits in the high half.
     */
    static boolean bedrock(UUID id) {
        Boolean floodgate = Floodgate.isBedrock(id);
        return floodgate != null ? floodgate : id.getMostSignificantBits() == 0;
    }

    /**
     * Floodgate's {@code FloodgateApi.isFloodgatePlayer(UUID)}, reached by reflection so SÜLD needs no Floodgate jar to
     * build or run (it is a soft dependency, installed only on servers that let Bedrock players in).
     */
    static final class Floodgate {
        private static volatile java.lang.invoke.MethodHandle check;
        private static volatile boolean looked;

        private Floodgate() {
        }

        /** True / false from Floodgate, or null when Floodgate is not there (or did not answer). */
        static Boolean isBedrock(UUID id) {
            java.lang.invoke.MethodHandle h = handle();
            if (h == null) return null;
            try {
                return (boolean) h.invoke(id);
            } catch (Throwable t) {
                return null;
            }
        }

        private static java.lang.invoke.MethodHandle handle() {
            if (looked) return check;
            synchronized (Floodgate.class) {
                if (!looked) {
                    check = find();
                    looked = true;
                }
            }
            return check;
        }

        private static java.lang.invoke.MethodHandle find() {
            if (Bukkit.getServer() == null || Bukkit.getPluginManager().getPlugin("floodgate") == null) return null; // no server: a unit test
            try {
                Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                return java.lang.invoke.MethodHandles.publicLookup().findVirtual(api, "isFloodgatePlayer",
                        java.lang.invoke.MethodType.methodType(boolean.class, UUID.class)).bindTo(instance);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                return null;
            }
        }
    }

    /** The one map view every minimap shows (per-player pictures), kept in map/views.yml like the atlas's. */
    @SuppressWarnings("deprecation")
    private MapView minimapView() {
        File f = new File(plugin.getDataFolder(), "map/views.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        MapView v = null;
        if (y.isInt("minimap")) {
            MapView old = Bukkit.getMap(y.getInt("minimap"));
            if (old != null && old.getCenterX() == AtlasFrames.MARK_X && old.getCenterZ() == AtlasFrames.MARK_Z) v = old;
        }
        if (v == null) {
            v = Bukkit.createMap(Bukkit.getWorlds().get(0));
            y.set("minimap", v.getId());
            try {
                y.save(f);
            } catch (IOException e) {
                plugin.getLogger().warning("[map] views.yml not saved (" + e.getMessage() + ")");
            }
        }
        for (MapRenderer r : new ArrayList<>(v.getRenderers())) v.removeRenderer(r);
        v.setCenterX(AtlasFrames.MARK_X);
        v.setCenterZ(AtlasFrames.MARK_Z);
        v.setScale(MapView.Scale.CLOSEST);
        v.setTrackingPosition(false);
        v.setUnlimitedTracking(false);
        v.setLocked(true);
        v.addRenderer(new MapRenderer(true) {
            @Override
            @SuppressWarnings({"deprecation", "removal"})
            public void render(MapView m, MapCanvas canvas, Player player) {
                State s = states.get(player.getUniqueId());
                if (s == null || s.image == null || s.painted == s.version) return;
                byte[] px = s.image;
                for (int i = 0; i < px.length; i++) canvas.setPixel(i & 127, i >> 7, px[i]);
                s.painted = s.version;
            }
        });
        return v;
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(states.keySet())) hide(id);
        states.clear();
    }

    /** True when the player sees a minimap now. */
    public boolean showing(Player p) {
        State s = states.get(p.getUniqueId());
        return s != null && !s.frames.isEmpty();
    }

    private boolean wanted(Player p) {
        if (!p.isOnline() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) return false;
        if (bedrock(p.getUniqueId())) return false;
        if (p.getWorld() != Bukkit.getWorlds().get(0)) return false;
        if (busy.test(p)) return false;
        if (services.resourcePacks().statusOf(p.getUniqueId()) != org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) return false;
        return services.styles().setting(p.getUniqueId(), PlayerSetting.MINIMAP);
    }

    private void tick() {
        int now = Bukkit.getCurrentTick();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (!wanted(p)) {
                if (states.containsKey(id)) hide(id);
                continue;
            }
            State s = states.computeIfAbsent(id, k -> new State());
            place(p, s);
            if (s.drawing) continue;
            Location l = p.getLocation();
            int x = l.getBlockX(), z = l.getBlockZ(), turn = Math.floorMod(Math.round(l.getYaw() / 22.5f), 16);
            int mates = matesKey(p); // a party mate who moves or turns redraws it too, at the same pace
            int moved = s.lastX == Integer.MIN_VALUE ? Integer.MAX_VALUE : Math.max(Math.abs(x - s.lastX), Math.abs(z - s.lastZ));
            boolean turned = turn != s.lastTurn || mates != s.lastMates;
            // a new, moved or ended waypoint or pin shows at once (a pin removed and another added count, not the total)
            WaypointGuide.Target target = guide.target(id);
            int pinVersion = pins.version(id);
            boolean marks = target != s.lastTarget || pinVersion != s.lastPins;
            if (now - s.mobScan >= MOB_SCAN_TICKS) {
                s.mobScan = now;
                s.mobs = hostiles(p);
                s.mobsKey = mobsKey(s.mobs);
            }
            if (redrawDue(moved, turned, marks, s.mobsKey != s.lastMobs, now - s.lastTick)) {
                s.lastX = x;
                s.lastZ = z;
                s.lastTurn = turn;
                s.lastMates = mates;
                s.lastMobs = s.mobsKey;
                s.lastTick = now;
                s.lastTarget = target;
                s.lastPins = pinVersion;
                draw(p, s, x, z, l.getYaw());
            }
        }
        // a player gone without a quit event: the frames go with the state (they used to stay, unowned)
        for (UUID id : new ArrayList<>(states.keySet())) if (Bukkit.getPlayer(id) == null) hide(id);
    }

    /**
     * Whether the picture is drawn again now: at once for a new waypoint or pin; after {@value #REDRAW_TICKS} ticks for
     * a move of {@value #MOVE_BLOCKS} blocks, a turn or a mate's move; after {@value #SETTLE_TICKS} for a single step
     * (so the player ends up in the middle); after {@value #STALE_TICKS} anyway. {@code moved} is the move in blocks
     * since the last picture (the larger of x and z).
     */
    static boolean redrawDue(int moved, boolean turned, boolean marks, int ticksSince) {
        return redrawDue(moved, turned, marks, false, ticksSince);
    }

    /** {@link #redrawDue(int, boolean, boolean, int)}, and after {@value #MOB_REDRAW_TICKS} ticks when the mobs close by moved. */
    static boolean redrawDue(int moved, boolean turned, boolean marks, boolean mobsMoved, int ticksSince) {
        if (marks || ticksSince >= STALE_TICKS) return true;
        if ((moved >= MOVE_BLOCKS || turned) && ticksSince >= REDRAW_TICKS) return true;
        if (mobsMoved && ticksSince >= MOB_REDRAW_TICKS) return true;
        return moved > 0 && ticksSince >= SETTLE_TICKS;
    }

    /** The hostile mobs (SÜLD's own and every vanilla enemy) within {@value #MOB_RANGE} blocks, nearest first, as {x, z}. */
    private List<int[]> hostiles(Player p) {
        Location l = p.getLocation();
        List<org.bukkit.entity.LivingEntity> found = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(MOB_RANGE, 24, MOB_RANGE)) {
            if (!(e instanceof org.bukkit.entity.LivingEntity le) || le.isDead() || e instanceof Player) continue;
            if (e instanceof org.bukkit.entity.Enemy || services.mobs().isSuldMob(e)) found.add(le);
        }
        found.sort(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(l)));
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_MOBS, found.size()); i++) {
            Location m = found.get(i).getLocation();
            out.add(new int[]{m.getBlockX(), m.getBlockZ()});
        }
        return out;
    }

    /** Where the mobs stand, as one number: it changes when one moves a block (order aside). */
    static int mobsKey(List<int[]> mobs) {
        int h = mobs.size();
        for (int[] m : mobs) h += 31 * m[0] ^ m[1] * 0x9E3779B1; // a sum: the nearest-first order may swap without a move
        return h;
    }

    /**
     * Whether a frame hanging in block (bx, by, bz) goes to its spot {@code at}: only once the spot is more than
     * {@link #MOVE_SLACK} outside that block on some axis.
     */
    static boolean frameMoves(int bx, int by, int bz, double atX, double atY, double atZ) {
        return outside(bx, atX) || outside(by, atY) || outside(bz, atZ);
    }

    private static boolean outside(int block, double at) {
        return at < block - MOVE_SLACK || at >= block + 1 + MOVE_SLACK;
    }

    /** The frames out along the player's look, spawned when missing; each moved only when its spot has left its block (see {@link #frameMoves}). */
    private void place(Player p, State s) {
        Location eye = p.getEyeLocation();
        World w = eye.getWorld();
        if (s.frames.stream().anyMatch(f -> !f.isValid() || f.getWorld() != w)) {
            for (Entity f : s.frames) if (f.isValid()) f.remove();
            s.frames.clear();
            s.eyeX = Double.NaN;
        }
        while (s.frames.size() < SLOTS.length) s.frames.add(spawn(p, eye));
        int now = Bukkit.getCurrentTick();
        boolean parked = false;
        for (int until : s.parkedUntil) parked |= now <= until;
        if (!parked && eye.getX() == s.eyeX && eye.getY() == s.eyeY && eye.getZ() == s.eyeZ && eye.getYaw() == s.eyeYaw && eye.getPitch() == s.eyePitch) {
            return; // standing still, not turning: every frame is where it was put
        }
        s.eyeX = eye.getX();
        s.eyeY = eye.getY();
        s.eyeZ = eye.getZ();
        s.eyeYaw = eye.getYaw();
        s.eyePitch = eye.getPitch();
        for (int i = 0; i < SLOTS.length; i++) {
            if (now < s.parkedUntil[i]) continue;
            Location look = eye.clone();
            look.setYaw(eye.getYaw() + (float) SLOTS[i][0]);
            look.setPitch(Math.max(-90f, Math.min(90f, eye.getPitch() + (float) SLOTS[i][1])));
            Location at = eye.clone().add(look.getDirection().multiply(DIST));
            GlowItemFrame f = s.frames.get(i);
            Location cur = f.getLocation();
            if (frameMoves(cur.getBlockX(), cur.getBlockY(), cur.getBlockZ(), at.getX(), at.getY(), at.getZ())) f.teleport(at);
        }
    }

    /**
     * A frame is a real entity on the server, so an arrow, a snowball or a mob's shot can fly into it (only its owner
     * sees it, but Paper lets a projectile hit what its shooter can see, and a mob sees everything). The hit is
     * cancelled and the frame steps {@value #PARK_UP} blocks up for {@value #PARK_TICKS} ticks, so the projectile goes on
     * as if nothing were there (a cancelled hit alone would stop a plain arrow on the frame's face) and a frame never
     * shields its owner. (It stepped 4 blocks up for a second: out of view, so every arrow or fireball that clipped the
     * middle frame blinked the minimap for a second in fights.)
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent e) {
        Entity hit = e.getHitEntity();
        if (hit == null || !hit.getScoreboardTags().contains(TAG)) return;
        e.setCancelled(true);
        int now = Bukkit.getCurrentTick();
        for (State s : states.values()) {
            int i = s.frames.indexOf(hit);
            if (i < 0) continue;
            s.parkedUntil[i] = now + PARK_TICKS;
            hit.teleport(hit.getLocation().add(0, PARK_UP, 0));
            return;
        }
        hit.remove(); // a frame no minimap owns any more
    }

    private GlowItemFrame spawn(Player p, Location at) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta mm = (MapMeta) item.getItemMeta();
        mm.setMapView(view);
        item.setItemMeta(mm);
        // first above the player's head, out of every line of fire; place() puts it on its slot the same tick
        GlowItemFrame f = at.getWorld().spawn(at.clone().add(0, 3, 0), GlowItemFrame.class, e -> dress(e, item));
        p.showEntity(plugin, f);
        return f;
    }

    /**
     * How a frame is made, before it is ever sent: hidden from everyone (only {@code showEntity} shows it to its owner),
     * an invisible frame (only the map is drawn, and the shader moves that to the corner), never saved with the chunk,
     * fixed (no drop, no break, no rotate), invulnerable and silent.
     */
    static void dress(GlowItemFrame e, ItemStack item) {
        e.setFacingDirection(BlockFace.UP, true);
        e.setPersistent(false);
        e.setVisibleByDefault(false);
        e.addScoreboardTag(TAG);
        e.setVisible(false);
        e.setFixed(true);
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setItemDropChance(0f);
        e.setItem(item, false);
        e.setRotation(Rotation.NONE);
    }

    private void hide(UUID id) {
        State s = states.remove(id);
        if (s == null) return;
        for (Entity f : s.frames) if (f.isValid()) f.remove();
        s.frames.clear();
    }

    /** Composes the picture on the map's workers from the zoom-0 tiles around (x, z), then sends it. */
    private void draw(Player p, State s, int x, int z, float yaw) {
        s.drawing = true;
        int x0 = x - Minimap.VIEW_W / 2, z0 = z - Minimap.VIEW_H / 2, x1 = x0 + Minimap.VIEW_W - 1, z1 = z0 + Minimap.VIEW_H - 1;
        int tx0 = MapGeometry.tileOf(x0, 0), tx1 = MapGeometry.tileOf(x1, 0), tz0 = MapGeometry.tileOf(z0, 0), tz1 = MapGeometry.tileOf(z1, 0);
        Map<Long, CompletableFuture<byte[]>> wanted = new HashMap<>();
        for (int tz = tz0; tz <= tz1; tz++) for (int tx = tx0; tx <= tx1; tx++) wanted.put(MapTiles.key(0, tx, tz), map.tiles().tile(0, tx, tz));
        List<Minimap.Marker> markers = markers(p, x, z);
        WaypointGuide.Target t = guide.target(p.getUniqueId());
        int[] target = t != null && t.world().equals(p.getWorld().getName()) ? new int[]{(int) Math.floor(t.x()), (int) Math.floor(t.z())} : null;
        String line1 = "X " + x + "  Z " + z, line2 = placeName(x, z);
        CompletableFuture.allOf(wanted.values().toArray(CompletableFuture[]::new)).handleAsync((v, err) -> {
            Minimap.Terrain terrain = (bx, bz) -> {
                CompletableFuture<byte[]> f = wanted.get(MapTiles.key(0, MapGeometry.tileOf(bx, 0), MapGeometry.tileOf(bz, 0)));
                byte[] tile = f == null || f.isCompletedExceptionally() ? null : f.getNow(null);
                if (tile == null) return 0;
                return tile[Math.floorMod(bz, 128) * 128 + Math.floorMod(bx, 128)];
            };
            return Minimap.draw(terrain, x, z, yaw, markers, target, line1, line2);
        }, map.workers()).whenComplete((img, err) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                s.drawing = false;
                if (img == null || states.get(p.getUniqueId()) != s || !p.isOnline()) return;
                // the same picture (standing still, the 10 s refresh) is not sent again: ~2.6 KB a send compressed
                if (s.image != null && java.util.Arrays.equals(s.image, img) && s.painted == s.version) return;
                s.image = img;
                s.version++;
                p.sendMap(view);
            });
        });
    }

    /**
     * The party's other members the map shows (docs/WORLD_MAP.md): online, alive, in the same world, not standing in
     * for themselves in the training yard. Only the player's own party ever shows.
     */
    static List<Player> mates(SuldServices services, Player p) {
        var party = services.parties().partyOf(p.getUniqueId()).orElse(null);
        if (party == null) return List.of();
        List<Player> out = new ArrayList<>();
        for (UUID id : party.members()) {
            if (id.equals(p.getUniqueId())) continue;
            Player m = Bukkit.getPlayer(id);
            if (m == null || m.isDead() || m.getWorld() != p.getWorld() || m.getGameMode() == GameMode.SPECTATOR) continue;
            if (services.training != null && services.training.training(id)) continue;
            out.add(m);
        }
        return out;
    }

    private int matesKey(Player p) {
        return matesKey(services, p);
    }

    /** Where the mates stand and look, as one number: it changes when one moves a block or turns. */
    static int matesKey(SuldServices services, Player p) {
        int h = 1;
        for (Player m : mates(services, p)) {
            Location l = m.getLocation();
            h = 31 * (31 * (31 * h + l.getBlockX()) + l.getBlockZ()) + Math.floorMod(Math.round(l.getYaw() / 22.5f), 16);
        }
        return h;
    }

    private List<Minimap.Marker> markers(Player p, int x, int z) {
        List<Minimap.Marker> out = new ArrayList<>();
        // the party first, wherever they are: the ones out of view get an arrow on the rim
        for (Player m : mates(services, p)) {
            Location l = m.getLocation();
            out.add(new Minimap.Marker(l.getBlockX(), l.getBlockZ(), Minimap.Kind.PARTY, l.getYaw()));
        }
        for (MapPois.Poi poi : map.pois.get()) {
            if (Math.abs(poi.x() - x) > NEAR || Math.abs(poi.z() - z) > NEAR) continue;
            Minimap.Kind k = switch (poi.kind()) {
                case CITY -> Minimap.Kind.CITY;
                case DUNGEON -> Minimap.Kind.DUNGEON;
                case BOSS -> Minimap.Kind.BOSS;
                case OVOO -> Minimap.Kind.OVOO;
                case SITE -> Minimap.Kind.SITE;
                case RELAY, TRAINING -> Minimap.Kind.RELAY;
            };
            out.add(new Minimap.Marker(poi.x(), poi.z(), k));
        }
        for (MapPin pin : pins.pins(p.getUniqueId())) {
            if (Math.abs(pin.x() - x) <= NEAR && Math.abs(pin.z() - z) <= NEAR) out.add(Minimap.Marker.pin(pin.x(), pin.z(), pin.colour()));
        }
        State s = states.get(p.getUniqueId());
        if (s != null) for (int[] m : s.mobs) out.add(new Minimap.Marker(m[0], m[1], Minimap.Kind.MOB));
        var beacons = services.soulBeacons;
        if (beacons != null) {
            var b = beacons.of(p.getUniqueId());
            if (b != null && Math.abs(b.x() - x) <= NEAR && Math.abs(b.z() - z) <= NEAR) out.add(new Minimap.Marker(b.x(), b.z(), Minimap.Kind.DEATH));
        }
        return out;
    }

    /** The name the panel shows: the city, the area, or the steppe. */
    private String placeName(int x, int z) {
        World w = Bukkit.getWorlds().get(0);
        if (services.city().contains(w.getName(), x, z)) return "Хархорум";
        Location spawn = w.getSpawnLocation();
        Optional<Area> a = WorldContent.areaAt(x - spawn.getX(), z - spawn.getZ());
        return a.map(Area::name).orElse("Их Монголын тал");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(HangingBreakEvent e) {
        if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hide(e.getPlayer().getUniqueId());
    }

    /** The frames stay in the world the player left: removed at once (the next tick hangs new ones if the minimap is wanted there). */
    @EventHandler
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        hide(e.getPlayer().getUniqueId());
    }

    /** A frame left behind by a crash: never kept. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) if (en.getScoreboardTags().contains(TAG)) en.remove();
    }
}
