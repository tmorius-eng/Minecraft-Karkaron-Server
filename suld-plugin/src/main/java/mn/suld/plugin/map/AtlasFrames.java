package mn.suld.plugin.map;

import mn.suld.api.map.TileComposer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
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
import java.util.UUID;

/**
 * The atlas's map views (docs/WORLD_MAP.md): one {@link MapView} per frame of the wall, created once and kept for good
 * (their ids in {@code plugins/SULD/map/views.yml}, so a restart never allocates new map ids), shared by every open atlas.
 * Each view has one per-player renderer: it paints the frame the player's own atlas shows, so two players looking at
 * different places see their own pictures in the same map ids. Our views are marked by a centre no real map has.
 * Main thread.
 */
final class AtlasFrames {

    /** The centre our views carry: far beyond any world border, never a real map's. */
    static final int MARK_X = 29_999_984, MARK_Z = -29_999_984;

    /** What one player's atlas shows in each frame. */
    static final class Pictures {
        final byte[][] data;
        final int[] version, painted;

        Pictures(int frames) {
            data = new byte[frames][];
            version = new int[frames];
            painted = new int[frames];
            java.util.Arrays.fill(painted, -1);
        }
    }

    private final Plugin plugin;
    private final MapView[] views;
    private final Map<UUID, Pictures> pictures = new HashMap<>();

    AtlasFrames(Plugin plugin, int frames) {
        this.plugin = plugin;
        this.views = new MapView[frames];
    }

    MapView view(int frame) {
        return views[frame];
    }

    int size() {
        return views.length;
    }

    /** Finds or creates the views. Main thread, at start. */
    void start() {
        World w = Bukkit.getWorlds().get(0);
        File f = new File(plugin.getDataFolder(), "map/views.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        List<Integer> ids = new ArrayList<>(y.getIntegerList("ids"));
        boolean changed = false;
        for (int i = 0; i < views.length; i++) {
            MapView v = i < ids.size() ? ours(ids.get(i)) : null;
            if (v == null) {
                v = Bukkit.createMap(w);
                if (i < ids.size()) ids.set(i, v.getId());
                else ids.add(v.getId());
                changed = true;
            }
            prepare(v, i);
            views[i] = v;
        }
        if (changed) {
            y.set("ids", ids);
            try {
                y.save(f);
            } catch (IOException e) {
                plugin.getLogger().warning("[map] views.yml not saved (" + e.getMessage() + "): new map ids next start");
            }
        }
    }

    /** The view with {@code id} when it is one of ours, else null. */
    @SuppressWarnings("deprecation")
    private static MapView ours(int id) {
        MapView v = Bukkit.getMap(id);
        if (v == null) return null;
        return v.getCenterX() == MARK_X && v.getCenterZ() == MARK_Z ? v : null;
    }

    private void prepare(MapView v, int frame) {
        for (MapRenderer r : new ArrayList<>(v.getRenderers())) v.removeRenderer(r);
        v.setCenterX(MARK_X);
        v.setCenterZ(MARK_Z);
        v.setScale(MapView.Scale.CLOSEST);
        v.setTrackingPosition(false);
        v.setUnlimitedTracking(false);
        v.setLocked(true);
        v.addRenderer(new Painter(frame));
    }

    /** Starts (or replaces) what {@code player} sees: the frames are blank until {@link #show}. */
    Pictures open(UUID player) {
        Pictures p = new Pictures(views.length);
        pictures.put(player, p);
        return p;
    }

    void close(UUID player) {
        pictures.remove(player);
    }

    /** Shows {@code bytes} (128 × 128) in frame {@code frame} for {@code player} and sends it at once. */
    void show(Player player, int frame, byte[] bytes) {
        Pictures p = pictures.get(player.getUniqueId());
        if (p == null) return;
        p.data[frame] = bytes;
        p.version[frame]++;
        player.sendMap(views[frame]);
    }

    private final class Painter extends MapRenderer {
        private final int frame;

        Painter(int frame) {
            super(true);
            this.frame = frame;
        }

        @Override
        @SuppressWarnings({"deprecation", "removal"})
        public void render(MapView map, MapCanvas canvas, Player player) {
            Pictures p = pictures.get(player.getUniqueId());
            if (p == null || p.data[frame] == null || p.painted[frame] == p.version[frame]) return;
            byte[] px = p.data[frame];
            for (int i = 0; i < TileComposer.PIXELS; i++) canvas.setPixel(i & 127, i >> 7, px[i]);
            p.painted[frame] = p.version[frame];
        }
    }
}
