package mn.suld.api.worldbuild.kharkhorum;

import mn.suld.api.json.Json;
import mn.suld.api.worldbuild.Facing;
import mn.suld.api.worldbuild.Layer;
import mn.suld.api.worldbuild.Module;
import mn.suld.api.worldbuild.ModuleCanvas;
import mn.suld.api.worldbuild.ModuleContext;
import mn.suld.api.worldbuild.Pass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/** Streets, canal, bridges, grand stair and retaining walls. */
final class Infra {

    private Infra() {
    }

    /**
     * road.paved — a street along a polyline, following the planned terrain. Params: points
     * [[x,z],...], width, class (main | cross | secondary | lane), lanterns (spacing, 0 = none).
     * Main/cross streets: mud-brick kerbs, weathered stone, a polished centre strip on the main
     * avenue. Lanes: packed earth paths. Rises of one block get a step.
     */
    static final Module ROAD = Kit.module("road.paved", Layer.INFRA, (c, ctx) -> {
        c.pass(Pass.WALLS_GATES_ROADS);
        List<int[]> pts = new ArrayList<>();
        for (Object o : ctx.list("points")) {
            List<Object> p = Json.array(o);
            pts.add(new int[]{Json.integer(p.get(0)), Json.integer(p.get(1))});
        }
        int width = ctx.integer("width", 7);
        String cls = ctx.string("class", "cross");
        int lanterns = ctx.integer("lanterns", 0);
        double half = (width - 1) / 2.0;
        SplittableRandom r = ctx.random();
        Map<Long, Double> cells = new HashMap<>(); // xz -> distance from the centre line
        for (int i = 0; i + 1 < pts.size(); i++) {
            int[] a = pts.get(i), b = pts.get(i + 1);
            int minX = Math.min(a[0], b[0]) - width, maxX = Math.max(a[0], b[0]) + width;
            int minZ = Math.min(a[1], b[1]) - width, maxZ = Math.max(a[1], b[1]) + width;
            double vx = b[0] - a[0], vz = b[1] - a[1], len2 = vx * vx + vz * vz;
            boolean axis = a[0] == b[0] || a[1] == b[1];
            for (int x = minX; x <= maxX; x++)
                for (int z = minZ; z <= maxZ; z++) {
                    double t = len2 == 0 ? 0 : ((x - a[0]) * vx + (z - a[1]) * vz) / len2;
                    if (axis && (t < 0 || t > 1)) continue; // square ends on straight runs
                    t = Math.max(0, Math.min(1, t));
                    double dx = x - (a[0] + t * vx), dz = z - (a[1] + t * vz);
                    double d = axis ? Math.max(Math.abs(dx), Math.abs(dz)) : Math.sqrt(dx * dx + dz * dz);
                    if (d <= half + 0.01) cells.merge(key(x, z), d, Math::min);
                }
        }
        Set<Long> road = cells.keySet();
        for (Map.Entry<Long, Double> e : cells.entrySet()) {
            int x = kx(e.getKey()), z = kz(e.getKey());
            double d = e.getValue();
            int g = ctx.ground(x, z);
            String block;
            boolean edge = d > half - 0.99;
            switch (cls) {
                case "main" -> block = edge ? "@road_edge" : d <= 1.01 ? (r.nextInt(9) == 0 ? "@paving" : "@paving_accent") : Kit.paving(r);
                case "lane" -> {
                    int v = r.nextInt(10);
                    block = v < 6 ? "minecraft:dirt_path" : v < 8 ? "minecraft:coarse_dirt" : v < 9 ? "minecraft:gravel" : "@packed";
                }
                case "secondary" -> block = edge ? "@road_edge" : (r.nextInt(5) == 0 ? "@packed" : Kit.paving(r));
                default -> block = edge ? "@road_edge" : Kit.paving(r);
            }
            c.set(x, g, z, block);
            // step where the street rises by one block
            for (Facing f : Facing.values()) {
                long n = key(x + f.dx, z + f.dz);
                if (road.contains(n) && ctx.ground(x + f.dx, z + f.dz) == g + 1) {
                    c.set(x, g + 1, z, Kit.stairs(cls.equals("lane") ? "@base_stairs" : "@stone_stairs", f, false));
                    break;
                }
            }
        }
        if (lanterns > 0) {
            for (int i = 0; i + 1 < pts.size(); i++) {
                int[] a = pts.get(i), b = pts.get(i + 1);
                int len = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
                if (len == 0) continue;
                double ux = (b[0] - a[0]) / (double) len, uz = (b[1] - a[1]) / (double) len;
                double nx = -uz, nz = ux; // left normal
                int off = (int) Math.ceil(half) + 1;
                for (int s = lanterns / 2; s <= len; s += lanterns) {
                    for (int side = -1; side <= 1; side += 2) {
                        int x = (int) Math.round(a[0] + ux * s + nx * off * side);
                        int z = (int) Math.round(a[1] + uz * s + nz * off * side);
                        if (road.contains(key(x, z))) continue;
                        Facing toward = Facing.toward(x, z, a[0] + ux * s, a[1] + uz * s);
                        Kit.armLantern(c, x, ctx.ground(x, z), z, toward);
                    }
                }
            }
        }
    });

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int kx(long k) { return (int) (k >> 32); }
    private static int kz(long k) { return (int) k; }

    /**
     * canal.segment — straight channel along local x from 0 to length-1: 9 wide (7 water), water
     * surface one block below the banks, stone banks with a low balustrade, optional end caps.
     */
    static final Module CANAL = Kit.module("canal.segment", Layer.INFRA, (c, ctx) -> {
        c.pass(Pass.WALLS_GATES_ROADS);
        int len = ctx.integer("length", 32);
        boolean capStart = ctx.flag("cap_start", false), capEnd = ctx.flag("cap_end", false);
        List<Object> gaps = ctx.list("rail_gaps"); // [[x1,x2],...] where the balustrade is left open (bridges)
        SplittableRandom r = ctx.random();
        int x0 = capStart ? -1 : 0, x1 = capEnd ? len : len - 1;
        for (int x = x0; x <= x1; x++) {
            boolean cap = x < 0 || x >= len;
            for (int z = -4; z <= 4; z++) {
                boolean bank = Math.abs(z) == 4 || cap;
                if (bank) {
                    for (int y = -3; y <= 0; y++) c.set(x, y, z, y == 0 ? "@trim" : Kit.masonry(r));
                    continue;
                }
                c.set(x, -3, z, r.nextInt(4) == 0 ? "minecraft:gravel" : "@packed");
                c.set(x, -2, z, "minecraft:water");
                c.set(x, -1, z, "minecraft:water");
                c.set(x, 0, z, r.nextInt(23) == 0 ? "minecraft:lily_pad" : "minecraft:air");
            }
            for (int side = -1; side <= 1; side += 2) {
                int zz = side * 4;
                if (cap || inGap(gaps, x)) continue;
                c.pass(Pass.DECORATION);
                c.set(x, 1, zz, x % 6 == 0 ? "@stone" : "@stone_wall");
                if (x % 12 == 0) c.set(x, 2, zz, "@light");
                c.pass(Pass.WALLS_GATES_ROADS);
            }
        }
    });

    private static boolean inGap(List<Object> gaps, int x) {
        for (Object o : gaps) {
            List<Object> g = Json.array(o);
            if (x >= Json.integer(g.get(0)) && x <= Json.integer(g.get(1))) return true;
        }
        return false;
    }

    /** bridge.arched — 9-wide arched stone bridge over a 7-wide canal, span 15, running along local z. */
    static final Module BRIDGE = Kit.module("bridge.arched", Layer.STRUCTURE, (c, ctx) -> {
        c.pass(Pass.WALLS_GATES_ROADS);
        int halfW = ctx.integer("half_width", 4);
        SplittableRandom r = ctx.random();
        // deck top height (in half blocks) by |z|
        int[] h2 = {6, 6, 6, 5, 4, 3, 2, 1}; // |z| = 0..7
        for (int z = -7; z <= 7; z++) {
            int az = Math.abs(z);
            int hh = h2[az];
            int full = hh / 2;             // top full block y
            boolean slab = hh % 2 == 1;    // a bottom slab on top
            for (int x = -halfW - 1; x <= halfW + 1; x++) {
                boolean parapet = Math.abs(x) == halfW + 1;
                int bottom = az <= 3 ? (az <= 1 ? 2 : az == 2 ? 1 : 0) : (az == 4 ? 0 : 1);
                int top = parapet ? full + (slab ? 1 : 0) : full;
                for (int y = bottom; y <= top; y++) {
                    String b;
                    if (y == top && !parapet) b = Kit.paving(r);
                    else b = Kit.masonry(r);
                    c.set(x, y, z, b);
                }
                // arch curve under the opening
                if (az == 2) c.set(x, 1, z, Kit.stairs("@stone_stairs", z < 0 ? Facing.NORTH : Facing.SOUTH, true));
                if (az == 3) c.set(x, 0, z, Kit.stairs("@stone_stairs", z < 0 ? Facing.NORTH : Facing.SOUTH, true));
                if (!parapet && slab) c.set(x, full + 1, z, Kit.slab("@paving_alt", false));
                if (parapet) {
                    c.pass(Pass.DECORATION);
                    c.set(x, top + 1, z, az == 0 || az == 7 ? "@stone" : "@stone_wall");
                    if (az == 0 || az == 7) {
                        c.pass(Pass.LIGHTING);
                        c.set(x, top + 2, z, "@light");
                    }
                    c.pass(Pass.WALLS_GATES_ROADS);
                }
            }
        }
    });

    /**
     * stair.grand — 11-wide ceremonial stair rising {@code rise} blocks toward local -z (north),
     * a landing after every 4 steps, solid masonry cheeks with braziers.
     */
    static final Module GRAND_STAIR = Kit.module("stair.grand", Layer.STRUCTURE, (c, ctx) -> {
        c.pass(Pass.WALLS_GATES_ROADS);
        int rise = ctx.integer("rise", 8);
        int halfW = ctx.integer("half_width", 5);
        int landing = ctx.integer("landing_depth", 6);
        SplittableRandom r = ctx.random();
        int z = -1, y = 0;
        List<int[]> profile = new ArrayList<>(); // {z, topY, isStep}
        for (int step = 1; step <= rise; step++) {
            y = step;
            profile.add(new int[]{z--, y, 1});
            if (step % 4 == 0 && step < rise) {
                for (int i = 0; i < landing; i++) profile.add(new int[]{z--, y, 0});
            }
        }
        profile.add(new int[]{z--, rise, 0});
        profile.add(new int[]{z, rise, 0});
        for (int[] p : profile) {
            for (int x = -halfW - 1; x <= halfW + 1; x++) {
                boolean cheek = Math.abs(x) == halfW + 1;
                int top = cheek ? p[1] + 1 : p[1];
                for (int yy = 1; yy < top; yy++) c.set(x, yy, p[0], Kit.masonry(r));
                if (cheek) {
                    c.set(x, top, p[0], "@trim");
                    c.pass(Pass.DECORATION);
                    c.set(x, top + 1, p[0], "@stone_wall");
                    c.pass(Pass.WALLS_GATES_ROADS);
                } else if (p[2] == 1) {
                    c.set(x, top, p[0], Kit.stairs("@stone_stairs", Facing.NORTH, false));
                } else {
                    c.set(x, top, p[0], Math.abs(x) <= 1 ? "@paving_accent" : Kit.paving(r));
                }
            }
        }
        // braziers at the foot and on the landings
        Kit.brazier(c, -halfW - 1, 0, 0, 3);
        Kit.brazier(c, halfW + 1, 0, 0, 3);
        for (int[] p : profile) {
            if (p[2] == 0 && p[1] < rise && profile.indexOf(p) > 0 && profile.get(profile.indexOf(p) - 1)[2] == 1) {
                c.remove(-halfW - 1, p[1] + 2, p[0]);
                c.remove(halfW + 1, p[1] + 2, p[0]);
                Kit.brazier(c, -halfW - 1, p[1] + 1, p[0], 2);
                Kit.brazier(c, halfW + 1, p[1] + 1, p[0], 2);
            }
        }
    });

    /**
     * wall.retaining — the face of a raised terrace along local x (0..length-1); the high side is
     * -z, the face looks +z. Earth base course, masonry, cornice, balustrade with lanterns,
     * pilasters every 12 and SÜLD hangings every 24. {@code gaps}: [[x1,x2]] left open (stairs).
     */
    static final Module RETAINING = Kit.module("wall.retaining", Layer.STRUCTURE, (c, ctx) -> {
        c.pass(Pass.WALLS_GATES_ROADS);
        int len = ctx.integer("length", 24);
        int h = ctx.integer("height", 8);
        List<Object> gaps = ctx.list("gaps");
        boolean hangings = ctx.flag("hangings", true);
        SplittableRandom r = ctx.random();
        for (int x = 0; x < len; x++) {
            boolean gap = inGap(gaps, x);
            for (int y = 1; y <= h; y++) {
                if (gap && y == h) continue;
                c.set(x, y, 0, y <= 2 ? Kit.earth(r) : y == h ? "@trim" : Kit.masonry(r));
            }
            if (gap) continue;
            c.pass(Pass.DECORATION);
            boolean post = x % 6 == 0;
            c.set(x, h + 1, 0, post ? "@stone" : "@stone_wall");
            if (post && x % 12 == 0) {
                c.pass(Pass.LIGHTING);
                c.set(x, h + 2, 0, "@light");
            }
            c.pass(Pass.SHELLS);
            if (x % 12 == 6) { // pilaster
                for (int y = 1; y < h; y++) c.set(x, y, 1, y <= 2 ? "@base" : "@stone_alt");
                c.set(x, h - 1, 1, Kit.stairs("@stone_stairs", Facing.NORTH, false));
            }
            c.pass(Pass.WALLS_GATES_ROADS);
        }
        if (hangings && h >= 7) {
            List<String> pattern = h >= 11 ? Kit.SULDE_5 : Kit.SULDE_3;
            int w = pattern.get(0).length();
            for (int x = 12; x + w + 1 < len; x += 24) {
                if (inGap(gaps, x - 1) || inGap(gaps, x + w)) continue;
                Kit.hanging(c, x, h - 1, 1, Facing.SOUTH, pattern);
            }
        }
    });
}
