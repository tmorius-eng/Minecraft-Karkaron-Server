package mn.suld.api.dungeon.hall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The arena of a dungeon (docs/world/DUNGEON_HALLS.md): a round hall with terrain, pillars, light and a boss dais,
 * reached through a short corridor, dressed in the dungeon's theme. Built once per instance slot in the halls world and
 * reused; players cannot change it.
 * <p>
 * Coordinates are relative to the hall origin: the floor is at y = 0 (players stand at y = 1), the hall's centre is
 * (0, 0), the corridor runs south (+z) and the boss dais is north (−z). Every placement is a block-data string
 * ({@code minecraft:stone}, {@code minecraft:lantern[hanging=false]}). Pure and deterministic: the same theme always
 * gives the same hall, so a rebuilt slot looks exactly like its neighbours.
 */
public final class HallBlueprint {

    /** Arena radius (walkable), wall thickness and height. */
    public static final int R = 20, WALL = 2, HEIGHT = 12;
    /** The corridor: from the wall to its back end, half width, height. */
    public static final int CORRIDOR_END = 34, CORRIDOR_HALF = 2, CORRIDOR_HEIGHT = 5;
    /** Version of the layout: a slot built from another version is rebuilt. */
    public static final int VERSION = 1;

    public record Placement(int x, int y, int z, String block) {
    }

    /** A point in the hall: a block position and the facing yaw (Minecraft degrees, 180 = north). */
    public record Anchor(double x, double y, double z, float yaw) {
    }

    public static final Anchor PLAYER_SPAWN = new Anchor(0.5, 1, 30.5, 180);
    public static final Anchor WAVE_CENTER = new Anchor(0.5, 1, 2.5, 180);
    public static final Anchor BOSS_SPAWN = new Anchor(0.5, 2, -11.5, 0);

    private HallBlueprint() {
    }

    /** Extent of everything placed: x and z within ±{@link #extent()}, y from −2 to {@link #HEIGHT} + 1. */
    public static int extent() {
        return Math.max(R + WALL, CORRIDOR_END + 1);
    }

    public static List<Placement> build(HallTheme t) {
        List<Placement> out = new ArrayList<>(40_000);
        int outer = R + WALL;
        boolean roofed = t.roofed();
        for (int x = -outer; x <= outer; x++) {
            for (int z = -outer; z <= outer; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > outer + 0.5) continue;
                // foundation and floor
                out.add(new Placement(x, -2, z, t.base()));
                out.add(new Placement(x, -1, z, t.base()));
                if (r <= R + 0.5) {
                    out.add(new Placement(x, 0, z, pick(t.floor(), x, 0, z)));
                    // the ground is not flat: low mounds of the floor material near the wall, a scatter of cover
                    double edge = R - r;
                    if (edge < 3.5 && noise(x, 7, z) < 0.45) out.add(new Placement(x, 1, z, pick(t.floor(), x, 1, z)));
                    if (r > 6 && edge > 4 && noise(x, 3, z) < t.scatter()) {
                        out.add(new Placement(x, 1, z, pick(t.decor(), x, 2, z)));
                    }
                    if (roofed) out.add(new Placement(x, HEIGHT + 1, z, pick(t.wall(), x, HEIGHT + 1, z)));
                    else out.add(new Placement(x, HEIGHT + 1, z, "minecraft:barrier"));
                } else {
                    // the wall: solid, slightly irregular inside face, a ledge of accent every 4 blocks
                    for (int y = 0; y <= HEIGHT + 1; y++) {
                        String b = y % 4 == 3 ? t.accent() : pick(t.wall(), x, y, z);
                        out.add(new Placement(x, y, z, b));
                    }
                }
            }
        }
        // pillars with light: 8 around the centre, 2x2, full height, a light block on top and one on the floor in front
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            int px = (int) Math.round(Math.cos(a) * 14), pz = (int) Math.round(Math.sin(a) * 14);
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    for (int y = 1; y <= HEIGHT; y++) {
                        out.add(new Placement(px + dx, y, pz + dz, y == HEIGHT ? t.lightBlock() : t.pillar()));
                    }
                }
            }
            int lx = px + (px > 0 ? -1 : 2), lz = pz + (pz > 0 ? -1 : 2);
            out.add(new Placement(lx, 1, lz, t.lamp()));
        }
        // boss dais: a raised disc north of the centre, its rim in the accent block, two steps up from the south
        for (int x = -4; x <= 4; x++) {
            for (int z = -16; z <= -8; z++) {
                double d = Math.hypot(x, z + 12);
                if (d > 4.5) continue;
                out.add(new Placement(x, 1, z, d > 3.5 ? t.accent() : t.dais()));
            }
        }
        out.add(new Placement(-3, 2, -12, t.lamp()));
        out.add(new Placement(3, 2, -12, t.lamp()));
        // the corridor south: floor, walls, roof, lamps; the hall wall is opened where it meets it
        for (int z = R - 1; z <= CORRIDOR_END + 1; z++) {
            for (int x = -CORRIDOR_HALF - 1; x <= CORRIDOR_HALF + 1; x++) {
                boolean side = Math.abs(x) == CORRIDOR_HALF + 1, back = z == CORRIDOR_END + 1;
                out.add(new Placement(x, -1, z, t.base()));
                out.add(new Placement(x, 0, z, side || back ? pick(t.wall(), x, 0, z) : pick(t.floor(), x, 0, z)));
                for (int y = 1; y <= CORRIDOR_HEIGHT; y++) {
                    if (side || back) out.add(new Placement(x, y, z, pick(t.wall(), x, y, z)));
                    else if (z >= R - 1) out.add(new Placement(x, y, z, "minecraft:air")); // carve the doorway
                }
                out.add(new Placement(x, CORRIDOR_HEIGHT + 1, z, pick(t.wall(), x, CORRIDOR_HEIGHT + 1, z)));
            }
            if ((z - R) % 5 == 2) {
                out.add(new Placement(-CORRIDOR_HALF, 1, z, t.lamp()));
                out.add(new Placement(CORRIDOR_HALF, 1, z, t.lamp()));
            }
        }
        // keep the spawn columns clear (a scattered decoration must never block a spawn point)
        clear(out, PLAYER_SPAWN);
        clear(out, WAVE_CENTER);
        clear(out, BOSS_SPAWN);
        return Collections.unmodifiableList(dedupe(out));
    }

    private static void clear(List<Placement> out, Anchor a) {
        int ax = (int) Math.floor(a.x()), ay = (int) Math.floor(a.y()), az = (int) Math.floor(a.z());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) out.add(new Placement(ax + dx, ay + dy, az + dz, "minecraft:air"));
            }
        }
    }

    /** Later placements win (the corridor carves the wall, clearances remove decorations). */
    private static List<Placement> dedupe(List<Placement> in) {
        java.util.LinkedHashMap<Long, Placement> m = new java.util.LinkedHashMap<>();
        for (Placement p : in) {
            long k = ((long) (p.x() + 512) << 40) | ((long) (p.y() + 512) << 20) | (p.z() + 512);
            m.remove(k);
            m.put(k, p);
        }
        // air first is pointless in a fresh void; keep it only where it overrides something solid later in the list
        List<Placement> out = new ArrayList<>(m.size());
        for (Placement p : m.values()) out.add(p);
        return out;
    }

    /** Deterministic noise in [0, 1) from integer coordinates. */
    static double noise(int x, int y, int z) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L;
        h ^= (h >>> 13);
        h *= 0x5bd1e995L;
        h ^= (h >>> 15);
        return ((h & 0xffffff) / (double) 0x1000000);
    }

    static String pick(List<String> palette, int x, int y, int z) {
        int i = (int) (noise(x, y, z) * palette.size());
        return palette.get(Math.min(palette.size() - 1, i));
    }
}
