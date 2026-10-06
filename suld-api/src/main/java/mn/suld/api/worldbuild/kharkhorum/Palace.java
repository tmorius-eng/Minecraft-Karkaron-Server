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

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }
}
