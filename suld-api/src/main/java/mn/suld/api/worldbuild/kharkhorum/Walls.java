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

/** The Imperial Gate, the city wall and the watchtower. */
final class Walls {

    private Walls() {
    }

    /** Solid masonry block with an earth base course and tuff band courses. */
    static void mass(ModuleCanvas c, int x1, int y1, int z1, int x2, int y2, int z2, SplittableRandom r, int... bands) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++) {
                    boolean band = false;
                    for (int b : bands) band |= b == y;
                    c.set(x, y, z, y <= 3 ? Kit.earth(r) : band ? "@stone_alt" : Kit.masonry(r));
                }
    }

    /**
     * gate.imperial — Их Хаалга. 35 wide: a 7 × 9 arched passage through a gatehouse with an open
     * gate hall on top, flanked by two 9 × 9 towers with skirt roofs and upper pavilions, red
     * SÜLD hangings, braziers and white tug standards. Local front (+z) faces out of the city.
     */
    static final Module GATE = new Module() {
        public String id() { return "gate.imperial"; }
        public Layer layer() { return Layer.LANDMARK; }

        public List<Connector> connectors(ModuleContext ctx) {
            return List.of(new Connector("inside", 0, 1, -6, Facing.NORTH, true),
                    new Connector("outside", 0, 1, 6, Facing.SOUTH, true));
        }

        public void build(ModuleCanvas c, ModuleContext ctx) {
            SplittableRandom r = ctx.random();
            c.pass(Pass.WALLS_GATES_ROADS);
            // passage floor and approach
            for (int x = -3; x <= 3; x++) for (int z = -5; z <= 5; z++) c.set(x, 0, z, Math.abs(x) <= 1 ? "@paving_accent" : Kit.paving(r));
            // towers
            for (int side = -1; side <= 1; side += 2) {
                int xa = side < 0 ? -17 : 9, xb = side < 0 ? -9 : 17;
                mass(c, xa, 1, -4, xb, 15, 4, r, 8, 12);
                for (int x = xa; x <= xb; x++) for (int z = -4; z <= 4; z++) c.set(x, 16, z, "@trim_dark");
                // arrow slits on the outer, inner and side faces
                for (int y : new int[]{6, 10, 14}) {
                    c.set(side * 13, y, 4, "@metal");
                    c.set(side * 13, y, -4, "@metal");
                    c.set(side < 0 ? xa : xb, y, 0, "@metal");
                }
                // skirt roof, upper pavilion, crowning roof
                Kit.skirt(c, xa - 1, -5, xb + 1, 5, 17, 2);
                c.pass(Pass.SHELLS);
                for (int x = xa + 2; x <= xb - 2; x++)
                    for (int z = -2; z <= 2; z++)
                        for (int y = 17; y <= 19; y++) {
                            boolean edge = x == xa + 2 || x == xb - 2 || z == -2 || z == 2;
                            boolean corner = (x == xa + 2 || x == xb - 2) && (z == -2 || z == 2);
                            if (!edge) c.air(x, y, z);
                            else if (corner) c.set(x, y, z, "@pillar");
                            else if (y == 19) c.set(x, y, z, "@beam_dark[axis=" + (z == -2 || z == 2 ? "x" : "z") + "]");
                            else if (y == 18 && (x == side * 13 || z == 0)) c.set(x, y, z, "@glass");
                            else c.set(x, y, z, "@plank");
                        }
                Kit.hipRoof(c, xa, -4, xb, 4, 20, "@ridge", true);
                // hangings: outer and inner faces
                Kit.hanging(c, side * 13 - 3, 14, 5, Facing.SOUTH, Kit.SULDE_7);
                Kit.hanging(c, side * 13 + 3, 14, -5, Facing.NORTH, Kit.SULDE_7);
            }
            // gatehouse
            c.pass(Pass.WALLS_GATES_ROADS);
            mass(c, -8, 1, -3, 8, 13, 3, r, 8, 12);
            for (int x = -3; x <= 3; x++)
                for (int y = 1; y <= 9; y++)
                    for (int z = -3; z <= 3; z++) c.air(x, y, z);
            for (int z = -3; z <= 3; z++) {
                // rounded arch head
                c.set(-3, 9, z, Kit.masonry(r));
                c.set(3, 9, z, Kit.masonry(r));
                c.set(-3, 8, z, Kit.stairs("@stone_stairs", Facing.WEST, true));
                c.set(3, 8, z, Kit.stairs("@stone_stairs", Facing.EAST, true));
                c.set(-2, 9, z, Kit.stairs("@stone_stairs", Facing.WEST, true));
                c.set(2, 9, z, Kit.stairs("@stone_stairs", Facing.EAST, true));
            }
            // voussoirs framing the arch on both faces
            for (int zf : new int[]{-3, 3}) {
                for (int y = 1; y <= 8; y++) {
                    c.set(-4, y, zf, "@trim");
                    c.set(4, y, zf, "@trim");
                }
                for (int x = -4; x <= 4; x++) c.set(x, 10, zf, Math.abs(x) <= 1 ? "@ridge" : "@trim_dark");
            }
            // raised portcullis
            c.pass(Pass.DECORATION);
            for (int x = -1; x <= 1; x++) c.set(x, 9, 2, "@metal");
            for (int x = -2; x <= 2; x++) c.set(x, 8, 2, "@metal");
            c.set(-2, 8, 2, "@metal");
            // battlements
            for (int x = -8; x <= 8; x++) {
                if ((x & 1) == 0) {
                    c.set(x, 14, 3, Kit.masonry(r));
                    c.set(x, 14, -3, Kit.masonry(r));
                }
            }
            // gate hall on the gatehouse
            c.pass(Pass.SHELLS);
            for (int x = -6; x <= 6; x++)
                for (int z = -2; z <= 2; z++) {
                    boolean post = (x == -6 || x == -2 || x == 2 || x == 6) && (z == -2 || z == 2);
                    if (post) {
                        c.set(x, 14, z, "@trim");
                        for (int y = 15; y <= 17; y++) c.set(x, y, z, "@pillar");
                    } else if (z == -2 || z == 2 || x == -6 || x == 6) {
                        c.set(x, 14, z, "@fence");
                        c.set(x, 17, z, "@beam[axis=" + (z == -2 || z == 2 ? "x" : "z") + "]");
                    }
                }
            for (int x = -6; x <= 6; x++) for (int z = -2; z <= 2; z++) c.set(x, 13, z, "@plank");
            Kit.hipRoof(c, -8, -4, 8, 4, 18, "@ridge", true);
            // name plaque over the arch (outer face)
            c.pass(Pass.DECORATION);
            for (int x = -2; x <= 2; x++) c.set(x, 12, 4, "@statue_gold");
            c.set(0, 12, 4, "@ridge");
            // lights: lanterns in the passage, braziers at the mouth, beacons on the gatehouse corners
            c.pass(Pass.LIGHTING);
            c.set(0, 9, -1, "@light[hanging=true]");
            c.set(0, 9, 1, "@light[hanging=true]");
            Kit.brazier(c, -5, 0, 6, 3);
            Kit.brazier(c, 5, 0, 6, 3);
            Kit.brazier(c, -5, 0, -6, 3);
            Kit.brazier(c, 5, 0, -6, 3);
            for (int sx : new int[]{-8, 8}) for (int sz : new int[]{-3, 3}) {
                c.set(sx, 14, sz, "@statue");
                c.set(sx, 15, sz, "@fire[lit=true]");
            }
            Kit.tug(c, -7, 13, 0, 7, "@felt");
            Kit.tug(c, 7, 13, 0, 7, "@felt");
        }
    };

    /**
     * wall.straight — city wall along local x (0..length-1), 5 thick (z -2..2), 12 high, outer
     * face +z: earth base course with a battered plinth, masonry, a band course, pilasters every
     * 12, crenellated outer parapet, walkway, inner parapet with lanterns. Optional end bastion.
     */
    static final Module WALL = Kit.module("wall.straight", Layer.STRUCTURE, (c, ctx) -> {
        c.pass(Pass.WALLS_GATES_ROADS);
        int len = ctx.integer("length", 24);
        int h = ctx.integer("height", 12);
        boolean bastion = ctx.flag("end_bastion", false);
        SplittableRandom r = ctx.random();
        for (int x = 0; x < len; x++) {
            for (int z = -2; z <= 2; z++)
                for (int y = 1; y <= h; y++)
                    c.set(x, y, z, y <= 3 ? Kit.earth(r) : y == 8 && z == 2 ? "@stone_alt" : y == h ? "@paving" : Kit.masonry(r));
            c.set(x, 1, 3, Kit.stairs("@base_stairs", Facing.NORTH, false));
            if ((x & 1) == 0) c.set(x, h + 1, 2, Kit.masonry(r));
            else c.set(x, h + 1, 2, Kit.slab("@stone_slab", false));
            c.pass(Pass.DECORATION);
            c.set(x, h + 1, -2, "@stone_wall");
            if (x % 12 == 6) {
                c.pass(Pass.LIGHTING);
                c.set(x, h + 1, -2, "@stone");
                c.set(x, h + 2, -2, "@light");
            }
            c.pass(Pass.SHELLS);
            if (x % 12 == 0 && x > 0 && x < len - 1) {
                for (int y = 2; y < h - 1; y++) c.set(x, y, 3, y <= 3 ? "@base" : "@stone_alt");
                c.set(x, h - 1, 3, Kit.stairs("@stone_stairs", Facing.NORTH, false));
            }
            c.pass(Pass.WALLS_GATES_ROADS);
        }
        if (bastion) {
            int bx = len, bh = h + 2;
            mass(c, bx, 1, -3, bx + 6, bh, 3, r, 8);
            for (int x = bx; x <= bx + 6; x++)
                for (int z = -3; z <= 3; z++)
                    if (x == bx + 6 || z == -3 || z == 3) {
                        if (((x + z) & 1) == 0) c.set(x, bh + 1, z, Kit.masonry(r));
                    }
            Kit.brazier(c, bx + 3, bh, 0, 2);
        }
    });

    /**
     * tower.watch — 9 × 9 watchtower, about 22 tall: hollow masonry shaft with timber floors and a
     * ladder, door on the inner (north) side, crenellated deck, open pavilion with a hip roof, a
     * brazier and a SÜLD hanging on the outer face.
     */
    static final Module WATCHTOWER = new Module() {
        public String id() { return "tower.watch"; }
        public Layer layer() { return Layer.STRUCTURE; }

        public List<Connector> connectors(ModuleContext ctx) {
            return List.of(new Connector("door", 0, 1, -5, Facing.NORTH, true),
                    new Connector("deck", 2, 18, 2, null, true));
        }

        public void build(ModuleCanvas c, ModuleContext ctx) {
            SplittableRandom r = ctx.random();
            c.pass(Pass.SHELLS);
            int top = 17;
            for (int x = -4; x <= 4; x++)
                for (int z = -4; z <= 4; z++) {
                    boolean shell = Math.abs(x) == 4 || Math.abs(z) == 4;
                    for (int y = 1; y <= top; y++) {
                        if (shell || y == top) c.set(x, y, z, y <= 3 ? Kit.earth(r) : y == 9 || y == 14 ? "@stone_alt" : Kit.masonry(r));
                        else if (y == 6 || y == 12) c.set(x, y, z, "@plank");
                        else c.air(x, y, z);
                    }
                    c.set(x, 0, z, "@plank");
                }
            // corner quoins of trim stone
            for (int y = 4; y < top; y += 2)
                for (int sx : new int[]{-4, 4}) for (int sz : new int[]{-4, 4}) c.set(sx, y, sz, "@trim");
            // door (north face), ladder against the north wall
            c.air(0, 1, -4);
            c.air(0, 2, -4);
            c.pass(Pass.INTERIORS);
            c.set(0, 1, -4, "minecraft:spruce_door[facing=south,half=lower,hinge=left,open=false]");
            c.set(0, 2, -4, "minecraft:spruce_door[facing=south,half=upper,hinge=left,open=false]");
            for (int y = 1; y <= top; y++) c.set(2, y, -3, "minecraft:ladder[facing=south]");
            // slits
            c.pass(Pass.SHELLS);
            for (int y : new int[]{8, 13}) {
                c.set(0, y, 4, "@metal");
                c.set(-4, y, 0, "@metal");
                c.set(4, y, 0, "@metal");
            }
            c.set(0, 13, -4, "@metal");
            // deck parapet
            for (int x = -4; x <= 4; x++)
                for (int z = -4; z <= 4; z++) {
                    if (Math.abs(x) != 4 && Math.abs(z) != 4) continue;
                    c.set(x, top + 1, z, ((x + z) & 1) == 0 ? Kit.masonry(r) : Kit.slab("@stone_slab", false));
                }
            // pavilion and roof
            for (int sx : new int[]{-3, 3}) for (int sz : new int[]{-3, 3})
                for (int y = top + 1; y <= top + 3; y++) c.set(sx, y, sz, "@pillar");
            for (int x = -3; x <= 3; x++) {
                c.set(x, top + 4, -3, "@beam[axis=x]");
                c.set(x, top + 4, 3, "@beam[axis=x]");
            }
            for (int z = -2; z <= 2; z++) {
                c.set(-3, top + 4, z, "@beam[axis=z]");
                c.set(3, top + 4, z, "@beam[axis=z]");
            }
            Kit.hipRoof(c, -5, -5, 5, 5, top + 5, null, true);
            Kit.brazier(c, 0, top, 0, 2);
            Kit.hanging(c, -2, 15, 5, Facing.SOUTH, Kit.SULDE_5);
        }
    };
}
