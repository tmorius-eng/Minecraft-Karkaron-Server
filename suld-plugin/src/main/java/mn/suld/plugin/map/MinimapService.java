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
 * The three frames hang {@value #DIST} blocks out, a little above the look and to its sides ({@link #SLOTS}), so one of
 * them is always in view (the client skips entities out of view), none is within reach of a click (an entity farther
 * than 5 blocks is never targeted) and none is on the player's line of fire. Shown only to a player with the SÜLD pack loaded (without the
 * shader the map would hang in the world) and the «Мини газрын зураг» setting on, in the overworld, outside the sky
 * windows. Redrawn when the player moves to another block or turns, at most {@value #REDRAW_TICKS} ticks apart.
 */
public final class MinimapService implements Listener {

    static final String TAG = "suld_minimap";
    static final double DIST = 7.5, NEAR = 72;
    /**
     * Where the frames hang, as (yaw, pitch) turns from the look in degrees (negative pitch = up): all above the line of
     * fire (an arrow leaves just under the eye along the look and only drops), one 14° up in the middle of the view
     * (a 16:9 screen sees ±51° across, ±35° up and down) and two 50° to the sides, which come into view while the
     * player turns faster than the server moves the frames (a two-frame layout at ±28° blinked in fast turns on the
     * real client; the sides keep it drawn through turns of ~90° a tick).
     */
    static final double[][] SLOTS = {{0, -14}, {-50, -10}, {50, -10}};
    static final int REDRAW_TICKS = 4, STALE_TICKS = 200, PARK_TICKS = 20;

    private final class State {
        final List<GlowItemFrame> frames = new ArrayList<>();
        /** A frame something flew into steps out of the way until this tick (see onProjectileHit). */
        final int[] parkedUntil = new int[SLOTS.length];
        byte[] image;
        int version, painted = -1;
        int lastX = Integer.MIN_VALUE, lastZ, lastTurn = -1, lastTick, lastPins = -1;
        WaypointGuide.Target lastTarget;
        boolean drawing;
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
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
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
            Location l = p.getLocation();
            int x = l.getBlockX(), z = l.getBlockZ(), turn = Math.floorMod(Math.round(l.getYaw() / 22.5f), 16);
            boolean moved = x != s.lastX || z != s.lastZ || turn != s.lastTurn;
            // a new waypoint or pin shows at once
            WaypointGuide.Target target = guide.target(id);
            int pinCount = pins.pins(id).size();
            boolean marks = target != s.lastTarget || pinCount != s.lastPins;
            if (!s.drawing && (marks || moved && now - s.lastTick >= REDRAW_TICKS || now - s.lastTick >= STALE_TICKS)) {
                s.lastX = x;
                s.lastZ = z;
                s.lastTurn = turn;
                s.lastTick = now;
                s.lastTarget = target;
                s.lastPins = pinCount;
                draw(p, s, x, z, l.getYaw());
            }
        }
        states.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    /** The frames out along the player's look, spawned when missing; moved only when their block changes. */
    private void place(Player p, State s) {
        Location eye = p.getEyeLocation();
        World w = eye.getWorld();
        if (s.frames.stream().anyMatch(f -> !f.isValid() || f.getWorld() != w)) {
            for (Entity f : s.frames) if (f.isValid()) f.remove();
            s.frames.clear();
        }
        while (s.frames.size() < SLOTS.length) s.frames.add(spawn(p, eye));
        int now = Bukkit.getCurrentTick();
        for (int i = 0; i < SLOTS.length; i++) {
            if (now < s.parkedUntil[i]) continue;
            Location look = eye.clone();
            look.setYaw(eye.getYaw() + (float) SLOTS[i][0]);
            look.setPitch(Math.max(-90f, Math.min(90f, eye.getPitch() + (float) SLOTS[i][1])));
            Location at = eye.clone().add(look.getDirection().multiply(DIST));
            GlowItemFrame f = s.frames.get(i);
            Location cur = f.getLocation();
            if (cur.getBlockX() != at.getBlockX() || cur.getBlockY() != at.getBlockY() || cur.getBlockZ() != at.getBlockZ()) f.teleport(at);
        }
    }

    /**
     * A frame is a real entity on the server, so an arrow, a snowball or a mob's shot can fly into it (only its owner
     * sees it, but Paper lets a projectile hit what its shooter can see, and a mob sees everything). The hit is
     * cancelled and the frame steps 4 blocks up for a second, so the projectile goes on as if nothing were there
     * (a cancelled hit alone would stop a plain arrow on the frame's face) and a frame never shields its owner.
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
            hit.teleport(hit.getLocation().add(0, 4, 0));
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
        GlowItemFrame f = at.getWorld().spawn(at.clone().add(0, 3, 0), GlowItemFrame.class, e -> {
            e.setFacingDirection(BlockFace.UP, true);
            e.setPersistent(false);
            e.setVisibleByDefault(false);
            e.addScoreboardTag(TAG);
            e.setVisible(false);
            e.setFixed(true);
            e.setInvulnerable(true);
            e.setSilent(true);
            e.setItem(item, false);
            e.setRotation(Rotation.NONE);
        });
        p.showEntity(plugin, f);
        return f;
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

    private List<Minimap.Marker> markers(Player p, int x, int z) {
        List<Minimap.Marker> out = new ArrayList<>();
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
            if (Math.abs(pin.x() - x) <= NEAR && Math.abs(pin.z() - z) <= NEAR) out.add(new Minimap.Marker(pin.x(), pin.z(), Minimap.Kind.PIN));
        }
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

    /** A frame left behind by a crash: never kept. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) if (en.getScoreboardTags().contains(TAG)) en.remove();
    }
}
