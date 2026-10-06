package mn.suld.api.worldbuild.kharkhorum;

import mn.suld.api.worldbuild.Facing;
import mn.suld.api.worldbuild.Layer;
import mn.suld.api.worldbuild.Module;
import mn.suld.api.worldbuild.ModuleCanvas;
import mn.suld.api.worldbuild.ModuleContext;
import mn.suld.api.worldbuild.Pass;

import java.util.List;
import java.util.SplittableRandom;
import java.util.function.BiConsumer;

/**
 * The Kharkhorum architectural kit: the recurring pieces of the city's visual language, kept in
 * one place so every module speaks it the same way (see docs/world/MODULE_LIBRARY.md).
 * <ul>
 *   <li>low, broad hip roofs of dark tiles with up-turned eave corners and an optional gold ridge;</li>
 *   <li>red cloth hangings with the gold SÜLD trident ("сүлд") woven in wool pixels;</li>
 *   <li>tug: horse-hair standards on tall poles (the Mongol spirit banner);</li>
 *   <li>lantern posts, braziers, weathered paving, trees and a voxel sculpting helper.</li>
 * </ul>
 */
final class Kit {

    private Kit() {
    }

    record Simple(String id, Layer layer, BiConsumer<ModuleCanvas, ModuleContext> body) implements Module {
        @Override
        public void build(ModuleCanvas canvas, ModuleContext ctx) {
            body.accept(canvas, ctx);
        }
    }

    static Module module(String id, Layer layer, BiConsumer<ModuleCanvas, ModuleContext> body) {
        return new Simple(id, layer, body);
    }

    // ---- block-state helpers ----------------------------------------------------------------

    static String stairs(String role, Facing f, boolean top) {
        return role + "[facing=" + f.id() + ",half=" + (top ? "top" : "bottom") + ",shape=straight]";
    }

    static String stairs(String role, Facing f, boolean top, String shape) {
        return role + "[facing=" + f.id() + ",half=" + (top ? "top" : "bottom") + ",shape=" + shape + "]";
    }

    static String slab(String role, boolean top) {
        return role + "[type=" + (top ? "top" : "bottom") + "]";
    }

    static Facing opposite(Facing f) {
        return Facing.values()[(f.ordinal() + 2) % 4];
    }

    // ---- roofs -------------------------------------------------------------------------------

    /**
     * Hip roof over the rectangle (inclusive), eave course at height y, rising one block per
     * course toward the middle (so it stays low and broad). Eave corners are turned up by one block.
     *
     * @param ridge token for the ridge line (null = roof tiles)
     * @return height of the ridge
     */
    static int hipRoof(ModuleCanvas c, int x1, int z1, int x2, int z2, int y, String ridge, boolean upturn) {
        Pass prev = c.pass();
        c.pass(Pass.ROOFS_DETAIL);
        int k = 0;
        while (true) {
            int ax = x1 + k, bx = x2 - k, az = z1 + k, bz = z2 - k;
            int w = bx - ax + 1, d = bz - az + 1;
            int yy = y + k;
            if (w <= 0 || d <= 0) break;
            if (w <= 2 || d <= 2) {
                c.fill(ax, yy, az, bx, yy, bz, "@roof");
                String r = ridge == null ? "@roof_slab[type=bottom]" : ridge;
                if (w >= d) for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) c.set(x, yy + 1, z, r);
                else for (int z = az; z <= bz; z++) for (int x = ax; x <= bx; x++) c.set(x, yy + 1, z, r);
                // ridge-end finials
                if (ridge != null) {
                    if (w >= d) {
                        c.set(ax, yy + 2, az, "@ridge");
                        c.set(bx, yy + 2, bz, "@ridge");
                    } else {
                        c.set(ax, yy + 2, az, "@ridge");
                        c.set(bx, yy + 2, bz, "@ridge");
                    }
                }
                c.pass(prev);
                return yy + 1;
            }
            for (int x = ax; x <= bx; x++) {
                c.set(x, yy, az, stairs("@roof_stairs", Facing.SOUTH, false));
                c.set(x, yy, bz, stairs("@roof_stairs", Facing.NORTH, false));
            }
            for (int z = az + 1; z < bz; z++) {
                c.set(ax, yy, z, stairs("@roof_stairs", Facing.EAST, false));
                c.set(bx, yy, z, stairs("@roof_stairs", Facing.WEST, false));
            }
            c.set(ax, yy, az, stairs("@roof_stairs", Facing.SOUTH, false, "outer_left"));
            c.set(bx, yy, az, stairs("@roof_stairs", Facing.SOUTH, false, "outer_right"));
            c.set(ax, yy, bz, stairs("@roof_stairs", Facing.NORTH, false, "outer_right"));
            c.set(bx, yy, bz, stairs("@roof_stairs", Facing.NORTH, false, "outer_left"));
            if (k == 0 && upturn) {
                int[][] corners = {{ax, az, 0}, {bx, az, 1}, {ax, bz, 2}, {bx, bz, 3}};
                for (int[] cr : corners) {
                    String s = c.get(cr[0], yy, cr[1]);
                    c.set(cr[0], yy, cr[1], "@roof_slab[type=top]");
                    c.set(cr[0], yy + 1, cr[1], s);
                }
            }
            // under-course: full tiles carrying the next ring
            int nax = ax + 1, nbx = bx - 1, naz = az + 1, nbz = bz - 1;
            if (nbx >= nax && nbz >= naz) {
                for (int x = nax; x <= nbx; x++) {
                    c.set(x, yy, naz, "@roof");
                    c.set(x, yy, nbz, "@roof");
                }
                for (int z = naz; z <= nbz; z++) {
                    c.set(nax, yy, z, "@roof");
                    c.set(nbx, yy, z, "@roof");
                }
            }
            k++;
        }
        c.pass(prev);
        return y + k;
    }

    /**
     * Skirt roof: a ring of eave courses around a tower or hall (outer rectangle inclusive), rising
     * inward for {@code courses} courses with up-turned corners; the inside is left open for the
     * storey above.
     */
    static void skirt(ModuleCanvas c, int x1, int z1, int x2, int z2, int y, int courses) {
        Pass prev = c.pass();
        c.pass(Pass.ROOFS_DETAIL);
        for (int k = 0; k < courses; k++) {
            int ax = x1 + k, bx = x2 - k, az = z1 + k, bz = z2 - k, yy = y + k;
            for (int x = ax; x <= bx; x++) {
                c.set(x, yy, az, stairs("@roof_stairs", Facing.SOUTH, false));
                c.set(x, yy, bz, stairs("@roof_stairs", Facing.NORTH, false));
            }
            for (int z = az + 1; z < bz; z++) {
                c.set(ax, yy, z, stairs("@roof_stairs", Facing.EAST, false));
                c.set(bx, yy, z, stairs("@roof_stairs", Facing.WEST, false));
            }
            c.set(ax, yy, az, stairs("@roof_stairs", Facing.SOUTH, false, "outer_left"));
            c.set(bx, yy, az, stairs("@roof_stairs", Facing.SOUTH, false, "outer_right"));
            c.set(ax, yy, bz, stairs("@roof_stairs", Facing.NORTH, false, "outer_right"));
            c.set(bx, yy, bz, stairs("@roof_stairs", Facing.NORTH, false, "outer_left"));
            // under-course
            for (int x = ax + 1; x <= bx - 1; x++) {
                c.set(x, yy, az + 1, "@roof");
                c.set(x, yy, bz - 1, "@roof");
            }
            for (int z = az + 1; z <= bz - 1; z++) {
                c.set(ax + 1, yy, z, "@roof");
                c.set(bx - 1, yy, z, "@roof");
            }
            if (k == 0) {
                int[][] corners = {{ax, az}, {bx, az}, {ax, bz}, {bx, bz}};
                for (int[] cr : corners) {
                    String s = c.get(cr[0], yy, cr[1]);
                    c.set(cr[0], yy, cr[1], "@roof_slab[type=top]");
                    c.set(cr[0], yy + 1, cr[1], s);
                }
            }
        }
        c.pass(prev);
    }

    /** Flat roof with a parapet (the merchant-quarter roofs). */
    static void flatRoof(ModuleCanvas c, int x1, int z1, int x2, int z2, int y, String deck, String parapet) {
        Pass prev = c.pass();
        c.pass(Pass.ROOFS_DETAIL);
        c.fill(x1, y, z1, x2, y, z2, deck);
        for (int x = x1; x <= x2; x++) {
            c.set(x, y + 1, z1, parapet);
            c.set(x, y + 1, z2, parapet);
        }
        for (int z = z1; z <= z2; z++) {
            c.set(x1, y + 1, z, parapet);
            c.set(x2, y + 1, z, parapet);
        }
        c.pass(prev);
    }

    // ---- cloth, standards, lights --------------------------------------------------------------

    /** 7 × 9 hanging with the SÜLD trident and ring, swallow-tailed. */
    static final List<String> SULDE_7 = List.of(
            "RRRRRRR",
            "RYRYRYR",
            "RYRYRYR",
            "RYYYYYR",
            "RRRYRRR",
            "RRYRYRR",
            "RRRYRRR",
            "RRRRRRR",
            "RRR.RRR");

    /** 5 × 8 hanging. */
    static final List<String> SULDE_5 = List.of(
            "RRRRR",
            "YRYRY",
            "YRYRY",
            "YYYYY",
            "RRYRR",
            "RYRYR",
            "RRYRR",
            "RR.RR");

    /** 3 × 5 pennant. */
    static final List<String> SULDE_3 = List.of(
            "RRR",
            "YRY",
            "YYY",
            "RYR",
            "R.R");

    /**
     * Hangs a wool pixel panel flat against a wall. (x, top, z) is the top-left pixel as seen by a
     * viewer looking at the face; {@code faces} is the direction the panel faces (toward the viewer).
     * A dark rod runs along the top, one block wider on both sides.
     */
    static void hanging(ModuleCanvas c, int x, int top, int z, Facing faces, List<String> rows, String red, String gold) {
        Pass prev = c.pass();
        c.pass(Pass.DECORATION);
        // the viewer looks at the panel (opposite to `faces`); "right" is the viewer's right hand
        Facing right = switch (faces) {
            case SOUTH -> Facing.EAST;
            case NORTH -> Facing.WEST;
            case EAST -> Facing.NORTH;
            case WEST -> Facing.SOUTH;
        };
        int w = rows.get(0).length();
        for (int i = -1; i <= w; i++) {
            c.set(x + right.dx * i, top + 1, z + right.dz * i, "@beam_dark[axis=" + (right.dx != 0 ? "x" : "z") + "]");
        }
        for (int r = 0; r < rows.size(); r++) {
            String row = rows.get(r);
            for (int i = 0; i < row.length(); i++) {
                char ch = row.charAt(i);
                if (ch == '.') continue;
                c.set(x + right.dx * i, top - r, z + right.dz * i, ch == 'Y' ? gold : red);
            }
        }
        c.pass(prev);
    }

    static void hanging(ModuleCanvas c, int x, int top, int z, Facing faces, List<String> rows) {
        hanging(c, x, top, z, faces, rows, "@cloth", "@cloth_emblem");
    }

    /**
     * Tug: a horse-hair standard. Pole from y+1 to y+h, a ring, a hair tassel under it and a silver
     * spear tip. {@code hair} is the wool (white = the Nine White Banners, black = war).
     */
    static void tug(ModuleCanvas c, int x, int y, int z, int h, String hair) {
        Pass prev = c.pass();
        c.pass(Pass.DECORATION);
        c.set(x, y + 1, z, "@trim");
        for (int yy = y + 2; yy <= y + h; yy++) c.set(x, yy, z, "minecraft:dark_oak_fence");
        int t = y + h;
        c.set(x, t + 1, z, "minecraft:gold_block");
        c.set(x, t + 2, z, "minecraft:end_rod[facing=up]");
        // tassel hangs around the top of the pole
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            c.set(x + dx, t, z + dz, hair);
            c.set(x + dx, t - 1, z + dz, hair);
            if (dx == 0 || dz == 0) c.set(x + dx, t - 2, z + dz, hair);
        }
        c.set(x, t - 3, z, hair);
        c.pass(prev);
    }

    /** Lantern post: mud-brick foot, dark timber post, lantern on top (4 tall). */
    static void lanternPost(ModuleCanvas c, int x, int y, int z) {
        Pass prev = c.pass();
        c.pass(Pass.LIGHTING);
        c.set(x, y + 1, z, "@base_wall");
        c.set(x, y + 2, z, "minecraft:dark_oak_fence");
        c.set(x, y + 3, z, "minecraft:dark_oak_fence");
        c.set(x, y + 4, z, "@light");
        c.pass(prev);
    }

    /** Post with an arm over the road and a hanging lantern ({@code arm} = direction of the arm). */
    static void armLantern(ModuleCanvas c, int x, int y, int z, Facing arm) {
        Pass prev = c.pass();
        c.pass(Pass.LIGHTING);
        c.set(x, y + 1, z, "@base_wall");
        for (int yy = y + 2; yy <= y + 4; yy++) c.set(x, yy, z, "minecraft:dark_oak_fence");
        c.set(x, y + 5, z, "@trim_dark");
        c.set(x + arm.dx, y + 5, z + arm.dz, "minecraft:dark_oak_fence");
        c.set(x + arm.dx, y + 4, z + arm.dz, "@light[hanging=true]");
        c.pass(prev);
    }

    /** Brazier: stone foot and a lit campfire on top (h = total height). */
    static void brazier(ModuleCanvas c, int x, int y, int z, int h) {
        Pass prev = c.pass();
        c.pass(Pass.LIGHTING);
        for (int yy = y + 1; yy < y + h; yy++) c.set(x, yy, z, yy == y + h - 1 ? "@statue" : "@stone_wall");
        c.set(x, y + h, z, "@fire[lit=true]");
        c.pass(prev);
    }

    // ---- materials ----------------------------------------------------------------------------

    /** Weathered paving: mostly the paving role with alternate and cracked stones mixed in. */
    static String paving(SplittableRandom r) {
        int v = r.nextInt(100);
        if (v < 62) return "@paving";
        if (v < 84) return "@paving_alt";
        if (v < 94) return "@stone_cracked";
        return "minecraft:tuff_bricks";
    }

    /** Wall stone: stone bricks with tuff and cracked bricks mixed in. */
    static String masonry(SplittableRandom r) {
        int v = r.nextInt(100);
        if (v < 70) return "@stone";
        if (v < 88) return "@stone_alt";
        return "@stone_cracked";
    }

    /** Earth base course: mud bricks with packed mud. */
    static String earth(SplittableRandom r) {
        return r.nextInt(100) < 78 ? "@base" : "@packed";
    }

    // ---- sculpting ----------------------------------------------------------------------------

    /** Voxelises a capsule (segment a→b, radius r). */
    static void capsule(ModuleCanvas c, double ax, double ay, double az, double bx, double by, double bz, double r, String token) {
        int minX = (int) Math.floor(Math.min(ax, bx) - r), maxX = (int) Math.ceil(Math.max(ax, bx) + r);
        int minY = (int) Math.floor(Math.min(ay, by) - r), maxY = (int) Math.ceil(Math.max(ay, by) + r);
        int minZ = (int) Math.floor(Math.min(az, bz) - r), maxZ = (int) Math.ceil(Math.max(az, bz) + r);
        double vx = bx - ax, vy = by - ay, vz = bz - az, len2 = vx * vx + vy * vy + vz * vz;
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    double px = x + 0.5 - ax, py = y + 0.5 - ay, pz = z + 0.5 - az;
                    double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, (px * vx + py * vy + pz * vz) / len2));
                    double dx = px - t * vx, dy = py - t * vy, dz = pz - t * vz;
                    if (dx * dx + dy * dy + dz * dz <= r * r) c.set(x, y, z, token);
                }
    }

    /** Voxelises an axis-aligned ellipsoid. */
    static void ellipsoid(ModuleCanvas c, double cx, double cy, double cz, double rx, double ry, double rz, String token) {
        for (int x = (int) Math.floor(cx - rx); x <= (int) Math.ceil(cx + rx); x++)
            for (int y = (int) Math.floor(cy - ry); y <= (int) Math.ceil(cy + ry); y++)
                for (int z = (int) Math.floor(cz - rz); z <= (int) Math.ceil(cz + rz); z++) {
                    double dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry, dz = (z + 0.5 - cz) / rz;
                    if (dx * dx + dy * dy + dz * dz <= 1) c.set(x, y, z, token);
                }
    }

    // ---- trees --------------------------------------------------------------------------------

    /** Trees: cherry (blossom), larch (spruce), birch, elm (oak). Leaves are persistent. */
    static void tree(ModuleCanvas c, int x, int y, int z, String species, SplittableRandom r) {
        Pass prev = c.pass();
        c.pass(Pass.LANDSCAPING);
        Layer prevLayer = c.layer();
        switch (species) {
            case "cherry" -> {
                int h = 4 + r.nextInt(2);
                int bx = r.nextInt(3) - 1, bz = r.nextInt(3) - 1;
                for (int i = 1; i <= h; i++) c.set(x + (i > h - 2 ? bx : 0), y + i, z + (i > h - 2 ? bz : 0), "minecraft:cherry_log");
                int tx = x + bx, tz = z + bz, ty = y + h;
                blob(c, tx, ty + 1, tz, 3.2, 1.9, "minecraft:cherry_leaves[persistent=true]", r);
                for (int i = 0; i < 6; i++) {
                    int px = x + r.nextInt(7) - 3, pz = z + r.nextInt(7) - 3;
                    if (!c.has(px, y + 1, pz)) c.set(px, y + 1, pz, "minecraft:pink_petals[facing=north,flower_amount=" + (1 + r.nextInt(4)) + "]");
                }
            }
            case "larch" -> {
                int h = 8 + r.nextInt(3);
                for (int i = 1; i <= h; i++) c.set(x, y + i, z, "minecraft:spruce_log");
                for (int i = 3; i <= h + 1; i++) {
                    double rad = Math.max(0.6, (h + 2 - i) * 0.42);
                    if ((i & 1) == 0) rad *= 0.75;
                    for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
                        if (dx * dx + dz * dz <= rad * rad + 0.3 && !(dx == 0 && dz == 0 && i <= h))
                            c.set(x + dx, y + i, z + dz, "minecraft:spruce_leaves[persistent=true]");
                }
                c.set(x, y + h + 1, z, "minecraft:spruce_leaves[persistent=true]");
                c.set(x, y + h + 2, z, "minecraft:spruce_leaves[persistent=true]");
            }
            case "birch" -> {
                int h = 6 + r.nextInt(2);
                for (int i = 1; i <= h; i++) c.set(x, y + i, z, "minecraft:birch_log");
                blob(c, x, y + h - 1, z, 2.2, 2.6, "minecraft:birch_leaves[persistent=true]", r);
                c.set(x, y + h, z, "minecraft:birch_log");
            }
            default -> { // elm
                int h = 4 + r.nextInt(2);
                for (int i = 1; i <= h; i++) c.set(x, y + i, z, "minecraft:oak_log");
                c.set(x + 1, y + h, z, "minecraft:oak_log[axis=x]");
                c.set(x - 1, y + h - 1, z, "minecraft:oak_log[axis=x]");
                blob(c, x, y + h + 1, z, 3.0, 2.2, "minecraft:oak_leaves[persistent=true]", r);
            }
        }
        c.layer(prevLayer);
        c.pass(prev);
    }

    /** Leaf blob: ellipsoid with a ragged outer shell; never overwrites logs. */
    private static void blob(ModuleCanvas c, int cx, int cy, int cz, double rxz, double ry, String leaves, SplittableRandom r) {
        int R = (int) Math.ceil(rxz), RY = (int) Math.ceil(ry);
        for (int dx = -R; dx <= R; dx++)
            for (int dy = -RY; dy <= RY; dy++)
                for (int dz = -R; dz <= R; dz++) {
                    double d = (dx * dx + dz * dz) / (rxz * rxz) + (dy * dy) / (ry * ry);
                    if (d > 1.0) continue;
                    if (d > 0.62 && r.nextInt(100) < 28) continue; // ragged edge
                    String cur = c.get(cx + dx, cy + dy, cz + dz);
                    if (cur != null && cur.contains("_log")) continue;
                    c.set(cx + dx, cy + dy, cz + dz, leaves);
                }
    }
}
