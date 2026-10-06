package mn.suld.api.worldbuild.kharkhorum;

import mn.suld.api.worldbuild.Connector;
import mn.suld.api.worldbuild.Facing;
import mn.suld.api.worldbuild.Layer;
import mn.suld.api.worldbuild.Module;
import mn.suld.api.worldbuild.ModuleCanvas;
import mn.suld.api.worldbuild.ModuleContext;
import mn.suld.api.worldbuild.Pass;

import java.util.List;
import java.util.SplittableRandom;

/** The palace entrance on the terrace. */
final class Palace {

    private Palace() {
    }

    /**
     * palace.gate — Ордны Хаалга: the Great Palace's south gate hall on a stone podium. Red columns
     * on andesite plinths, plaster guard bays with lattice windows, a painted beam frieze, a 5 × 7
     * passage and one broad hip roof with a gold ridge; palace wall stubs either side. y 0 = terrace.
     */
    static final Module GATE = new Module() {
        public String id() { return "palace.gate"; }
        public Layer layer() { return Layer.LANDMARK; }

        public List<Connector> connectors(ModuleContext ctx) {
            return List.of(new Connector("front", 0, 1, 7, Facing.SOUTH, true),
                    new Connector("court", 0, 1, -8, Facing.NORTH, true));
        }

        public void build(ModuleCanvas c, ModuleContext ctx) {
            SplittableRandom r = ctx.random();
            int wall = ctx.integer("wall_length", 24);
            c.pass(Pass.SHELLS);
            // podium (2 high) with front and rear steps
            for (int x = -12; x <= 12; x++)
                for (int z = -4; z <= 4; z++) {
                    boolean edge = Math.abs(x) == 12 || Math.abs(z) == 4;
                    c.set(x, 1, z, edge ? "@trim" : "@stone");
                    c.set(x, 2, z, edge ? "@trim" : (Math.abs(x) <= 2 ? "@paving_accent" : Kit.paving(r)));
                }
            for (int x = -3; x <= 3; x++) {
                c.set(x, 1, 6, Kit.stairs("@stone_stairs", Facing.NORTH, false));
                c.set(x, 1, 5, "@stone");
                c.set(x, 2, 5, Kit.stairs("@stone_stairs", Facing.NORTH, false));
                c.set(x, 1, -6, Kit.stairs("@stone_stairs", Facing.SOUTH, false));
                c.set(x, 1, -5, "@stone");
                c.set(x, 2, -5, Kit.stairs("@stone_stairs", Facing.SOUTH, false));
            }
            // columns (front and back rows) and walls
            int[] cols = {-11, -7, -3, 3, 7, 11};
            for (int x = -11; x <= 11; x++)
                for (int z = -3; z <= 3; z++)
                    for (int y = 3; y <= 9; y++) {
                        boolean col = (z == -3 || z == 3) && contains(cols, x);
                        boolean passage = Math.abs(x) <= 2;
                        boolean bayWall = !passage && (Math.abs(x) == 3 || Math.abs(x) == 11 || z == -2 || z == 2)
                                && Math.abs(x) >= 3;
                        if (col) c.set(x, y, z, "@pillar");
                        else if (passage) c.air(x, y, z);
                        else if (bayWall && (z == -2 || z == 2 || Math.abs(x) == 3 || Math.abs(x) == 11)) {
                            boolean window = (z == -2 || z == 2) && (Math.abs(x) == 5 || Math.abs(x) == 9) && y >= 5 && y <= 7;
                            c.set(x, y, z, window ? "minecraft:spruce_trapdoor[facing=" + (z < 0 ? "north" : "south")
                                    + ",half=bottom,open=true]" : y == 3 ? "@base" : "@plaster");
                        } else c.air(x, y, z);
                    }
            // column plinths
            for (int x : cols) for (int z : new int[]{-3, 3}) c.set(x, 2, z, "@trim");
            // beam frieze, painted
            for (int x = -12; x <= 12; x++)
                for (int z = -4; z <= 4; z++) {
                    boolean ring = Math.abs(x) == 12 || Math.abs(z) == 4 || Math.abs(z) == 3 && Math.abs(x) <= 11;
                    if (!ring) {
                        c.set(x, 10, z, "@plank");
                        continue;
                    }
                    c.set(x, 10, z, ((x + z) & 3) == 0 ? "@paint" : "@beam_dark[axis=" + (Math.abs(z) >= 3 ? "x" : "z") + "]");
                }
            // passage doors (open, against the bay walls) and gold studs
            c.pass(Pass.DECORATION);
            for (int y = 3; y <= 8; y++) {
                c.set(-2, y, 1, "@cloth");
                c.set(2, y, 1, "@cloth");
            }
            for (int y = 4; y <= 8; y += 2) {
                c.set(-2, y, 1, "@cloth_emblem");
                c.set(2, y, 1, "@cloth_emblem");
            }
            c.set(0, 9, 3, "@light[hanging=true]");
            c.set(0, 9, -3, "@light[hanging=true]");
            // roof
            Kit.hipRoof(c, -14, -6, 14, 6, 11, "@ridge", true);
            // name board over the passage
            for (int x = -2; x <= 2; x++) c.set(x, 9, 4, x == 0 ? "@ridge" : "@statue_gold");
            // palace wall stubs
            c.pass(Pass.WALLS_GATES_ROADS);
            for (int side = -1; side <= 1; side += 2)
                for (int i = 13; i < 13 + wall; i++) {
                    int x = side * i;
                    for (int z = -1; z <= 1; z++)
                        for (int y = 1; y <= 6; y++) c.set(x, y, z, y <= 2 ? Kit.earth(r) : y == 6 ? "@trim_dark" : "@plaster");
                    c.set(x, 7, -1, Kit.stairs("@roof_stairs", Facing.SOUTH, false));
                    c.set(x, 7, 1, Kit.stairs("@roof_stairs", Facing.NORTH, false));
                    c.set(x, 7, 0, "@roof");
                    c.set(x, 8, 0, Kit.slab("@roof_slab", false));
                }
            // guardians: braziers and white tug standards
            Kit.brazier(c, -6, 0, 7, 3);
            Kit.brazier(c, 6, 0, 7, 3);
            Kit.tug(c, -10, 0, 7, 9, "@felt");
            Kit.tug(c, 10, 0, 7, 9, "@felt");
        }
    };

    /**
     * palace.hall — Түмэн Амгалан, the Great Palace throne hall. After Ögedei's palace (1235) "on 64
     * wooden columns": an 8 × 8 grid of red columns on andesite bases inside a 35 × 43 plastered hall
     * on a three-step stone podium; a 5 × 8 main door, lattice windows, a painted frieze, a double-eaved
     * roof (skirt + upper hip roof with a gold ridge) and the throne on a raised dais at the back.
     * y 0 = terrace surface; front +z.
     */
    static final Module HALL = new Module() {
        public String id() { return "palace.hall"; }
        public Layer layer() { return Layer.LANDMARK; }

        public List<Connector> connectors(ModuleContext ctx) {
            return List.of(new Connector("front", 0, 1, 29, Facing.SOUTH, true), new Connector("throne", 0, 6, -12, null, true));
        }

        public void build(ModuleCanvas c, ModuleContext ctx) {
            SplittableRandom r = ctx.random();
            c.pass(Pass.LANDMARKS);
            // podium: three steps
            for (int k = 0; k < 3; k++) {
                int hx = 22 - k, hz = 26 - k;
                for (int x = -hx; x <= hx; x++)
                    for (int z = -hz; z <= hz; z++) {
                        boolean edge = Math.abs(x) == hx || Math.abs(z) == hz;
                        c.set(x, k + 1, z, edge ? Kit.stairs("@stone_stairs", Facing.toward(x * 1.2, z, 0, 0), false) : (k == 2 ? Kit.paving(r) : "@stone"));
                    }
            }
            for (int x = -4; x <= 4; x++) for (int k = 0; k < 3; k++) c.set(x, k + 1, 26 - k, Kit.stairs("@stone_stairs", Facing.NORTH, false));
            int x1 = -17, x2 = 17, z1 = -21, z2 = 21, base = 3, top = 17;
            // walls, facade columns, windows, door
            for (int x = x1; x <= x2; x++)
                for (int z = z1; z <= z2; z++) {
                    boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                    for (int y = base + 1; y <= top; y++) {
                        if (!edge) { c.air(x, y, z); continue; }
                        boolean col = ((x - x1) % 4 == 0 && (z == z1 || z == z2)) || ((z - z1) % 4 == 0 && (x == x1 || x == x2));
                        boolean window = !col && y >= base + 5 && y <= base + 8 && ((x + z) & 1) == 0;
                        String b = col ? "@pillar" : y == base + 1 ? "@base" : y >= top - 1 ? "@beam_dark[axis=" + (z == z1 || z == z2 ? "x" : "z") + "]"
                                : window ? "minecraft:spruce_trapdoor[facing=" + facing(x, z, x1, x2, z1) + ",half=bottom,open=true]" : "@plaster";
                        c.set(x, y, z, b);
                    }
                }
            for (int x = -2; x <= 2; x++) for (int y = base + 1; y <= base + 8; y++) c.air(x, y, z2);
            for (int x = -3; x <= 3; x++) c.set(x, base + 9, z2, x == 0 ? "@ridge" : "@statue_gold");
            // painted frieze under the eaves
            for (int x = x1; x <= x2; x++) {
                c.set(x, top - 2, z1, ((x & 3) == 0) ? "@paint" : "@paint_alt");
                c.set(x, top - 2, z2, ((x & 3) == 0) ? "@paint" : "@paint_alt");
            }
            for (int z = z1; z <= z2; z++) {
                c.set(x1, top - 2, z, ((z & 3) == 0) ? "@paint" : "@paint_alt");
                c.set(x2, top - 2, z, ((z & 3) == 0) ? "@paint" : "@paint_alt");
            }
            // the 64 columns, floor, ceiling
            for (int x = x1 + 1; x < x2; x++) for (int z = z1 + 1; z < z2; z++) c.set(x, base, z, ((x + z) & 1) == 0 ? "@paving_accent" : "@trim");
            for (int i = 0; i < 8; i++)
                for (int j = 0; j < 8; j++) {
                    int cx = -14 + i * 4, cz = -16 + j * 4;
                    if (cz > 16) continue;
                    c.set(cx, base + 1, cz, "@trim");
                    for (int y = base + 2; y <= top; y++) c.set(cx, y, cz, "@pillar");
                }
            for (int x = x1 + 1; x < x2; x++) for (int z = z1 + 1; z < z2; z++) c.set(x, top + 1, z, "@plank");
            // red runner and throne dais at the back
            c.pass(Pass.INTERIORS);
            for (int z = -9; z < z2; z++) for (int x = -1; x <= 1; x++) c.set(x, base + 1, z, "minecraft:red_carpet");
            for (int x = -5; x <= 5; x++)
                for (int z = -19; z <= -11; z++) {
                    int d = Math.max(Math.abs(x), Math.abs(z + 15));
                    c.set(x, base + 1, z, "@statue");
                    if (d <= 3) c.set(x, base + 2, z, d == 3 ? Kit.stairs("@stone_stairs", Facing.toward(x, z + 15, 0, 0), false) : "@statue_gold");
                }
            c.set(0, base + 3, -16, Kit.stairs("minecraft:dark_oak_stairs", Facing.NORTH, false));
            c.set(-1, base + 3, -16, "minecraft:gold_block");
            c.set(1, base + 3, -16, "minecraft:gold_block");
            c.set(0, base + 3, -17, "minecraft:gold_block");
            c.set(0, base + 4, -17, "minecraft:gold_block");
            c.set(0, base + 5, -17, "@ridge");
            Kit.tug(c, -4, base + 1, -18, 7, "@felt");
            Kit.tug(c, 4, base + 1, -18, 7, "@felt");
            c.pass(Pass.LIGHTING);
            for (int i = 0; i < 8; i += 2)
                for (int j = 0; j < 8; j += 2) c.set(-12 + i * 4, top, -14 + j * 4, "@light[hanging=true]");
            for (int sx : new int[]{-8, 8}) {
                c.set(sx, base + 1, 24, "@statue");
                c.set(sx, base + 2, 24, "@fire[lit=true]");
            }
            // double-eaved roof: a soffit deck ties the overhanging skirt to the walls, then the skirt,
            // the clerestory and the upper hip roof
            c.pass(Pass.ROOFS_DETAIL);
            for (int x = x1 - 3; x <= x2 + 3; x++)
                for (int z = z1 - 3; z <= z2 + 3; z++)
                    if (x <= x1 || x >= x2 || z <= z1 || z >= z2) c.set(x, top + 1, z, "@roof");
            Kit.skirt(c, x1 - 3, z1 - 3, x2 + 3, z2 + 3, top + 1, 3);
            c.pass(Pass.SHELLS);
            int cx1 = -11, cx2 = 11, cz1 = -15, cz2 = 15;
            for (int x = cx1; x <= cx2; x++)
                for (int z = cz1; z <= cz2; z++) {
                    boolean edge = x == cx1 || x == cx2 || z == cz1 || z == cz2;
                    for (int y = top + 2; y <= top + 5; y++) {
                        if (!edge) continue;
                        boolean col = (x - cx1) % 4 == 0 || (z - cz1) % 5 == 0;
                        c.set(x, y, z, col ? "@pillar" : y == top + 5 ? "@paint" : y == top + 3 ? "minecraft:spruce_trapdoor[facing=north,half=bottom,open=true]" : "@plaster");
                    }
                }
            for (int x = x1 + 3; x <= x2 - 3; x++)
                for (int z = z1 + 3; z <= z2 - 3; z++)
                    if (!c.has(x, top + 1 + 2, z)) c.setIfEmpty(x, top + 1, z, "@roof");
            Kit.hipRoof(c, cx1 - 3, cz1 - 3, cx2 + 3, cz2 + 3, top + 6, "@ridge", true);
        }

        private String facing(int x, int z, int x1, int x2, int z1) {
            return z == z1 ? "north" : x == x1 ? "west" : x == x2 ? "east" : "south";
        }
    };

    /**
     * fountain.silver_tree — Мөнгөн Мод: the Silver Tree fountain made for Möngke Khan by the Parisian
     * goldsmith Guillaume Boucher (described by William of Rubruck): a silver tree with four lions at its
     * roots, branches hung with fruit and an angel with a trumpet at the top, over a stone basin.
     */
    static final Module SILVER_TREE = Kit.module("fountain.silver_tree", Layer.LANDMARK, (c, ctx) -> {
        c.pass(Pass.LANDMARKS);
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 5.3) continue;
                c.set(x, 0, z, "@trim");
                if (d > 4.4) c.set(x, 1, z, "@stone_slab[type=bottom]");
                else c.set(x, 1, z, "minecraft:water");
            }
        c.set(0, 1, 0, "@trim");
        String silver = "minecraft:iron_block", twig = "minecraft:iron_bars";
        for (int y = 2; y <= 6; y++) c.set(0, y, 0, silver);
        for (Facing f : Facing.values()) {
            // lions at the roots
            c.set(f.dx * 2, 2, f.dz * 2, "minecraft:smooth_quartz");
            c.set(f.dx * 2, 3, f.dz * 2, "minecraft:chiseled_quartz_block");
            c.set(f.dx, 2, f.dz, silver);
            // branches (face-connected: out, then up)
            c.set(f.dx, 6, f.dz, silver);
            c.set(f.dx * 2, 6, f.dz * 2, silver);
            c.set(f.dx * 2, 7, f.dz * 2, silver);
            c.set(f.dx * 2, 8, f.dz * 2, twig);
            c.set(f.dx * 3, 7, f.dz * 3, twig);
            c.set(f.dx * 2 + f.dz, 7, f.dz * 2 + f.dx, "minecraft:gold_block");
        }
        c.set(0, 7, 0, silver);
        c.set(0, 8, 0, silver);
        c.set(0, 9, 0, "minecraft:gold_block");
        c.set(0, 10, 0, "minecraft:end_rod[facing=up]");
        c.pass(Pass.LIGHTING);
        for (Facing f : Facing.values()) c.set(f.dx * 5, 2, f.dz * 5, "@light");
    });

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }
}
