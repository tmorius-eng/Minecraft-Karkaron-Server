package mn.suld.api.worldbuild.kharkhorum;

import mn.suld.api.json.Json;
import mn.suld.api.worldbuild.Connector;
import mn.suld.api.worldbuild.Facing;
import mn.suld.api.worldbuild.Layer;
import mn.suld.api.worldbuild.Module;
import mn.suld.api.worldbuild.ModuleCanvas;
import mn.suld.api.worldbuild.ModuleContext;
import mn.suld.api.worldbuild.Pass;

import java.util.List;
import java.util.SplittableRandom;

/** The yurt (ger) quarter: gers of three sizes, fenced yards and camp props. */
final class Residential {

    private Residential() {
    }

    private static double radius(String size) {
        return switch (size) {
            case "small" -> 3.5;
            case "large" -> 6.5;
            default -> 4.5;
        };
    }

    /**
     * yurt — a Mongol ger. Params: size (small Ø7 | medium Ø9 | large Ø13). White felt wall with a
     * sky-blue top band, a shallow felt roof softened with carpet, a toono crown with the stove pipe,
     * an orange door facing +z. Inside: stove, painted chests, rugs, beds; the large ger has the two
     * orange bagana pillars. y 0 = ground; door connector outside the door.
     */
    static final Module YURT = new Module() {
        public String id() { return "yurt"; }
        public Layer layer() { return Layer.STRUCTURE; }

        public List<Connector> connectors(ModuleContext ctx) {
            int d = (int) Math.round(radius(ctx.string("size", "medium")));
            return List.of(new Connector("door", 0, 1, d + 1, Facing.SOUTH, true));
        }

        public void build(ModuleCanvas c, ModuleContext ctx) {
            String size = ctx.string("size", "medium");
            double R = radius(size);
            int wh = size.equals("large") ? 4 : 3;
            int door = (int) Math.round(R - 0.5);
            SplittableRandom r = ctx.random();
            int span = (int) Math.ceil(R) + 1;
            c.pass(Pass.SHELLS);
            for (int x = -span; x <= span; x++)
                for (int z = -span; z <= span; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d > R + 0.01) continue;
                    c.set(x, 0, z, "@plank");
                    boolean wall = d > R - 1.0;
                    for (int y = 1; y <= wh; y++) {
                        if (wall) c.set(x, y, z, y == wh ? "@felt_trim" : "@felt");
                        else c.air(x, y, z);
                    }
                }
            // roof: two (three for the large ger) felt courses rising inward, carpet-softened
            c.pass(Pass.ROOFS_DETAIL);
            double[] rings = size.equals("large") ? new double[]{R + 0.6, R - 1.6, R - 3.4, 1.4} : new double[]{R + 0.6, R - 1.7, 1.4};
            for (int k = 0; k + 1 < rings.length; k++) {
                int y = wh + 1 + k;
                for (int x = -span - 1; x <= span + 1; x++)
                    for (int z = -span - 1; z <= span + 1; z++) {
                        double d = Math.sqrt(x * x + z * z);
                        // each course overlaps the one above by a block, so the roof is one connected shell
                        if (d <= rings[k] && d > rings[k + 1] - 1.0) {
                            c.set(x, y, z, "@felt");
                            if (d > (rings[k] + rings[k + 1]) / 2 + 0.3 && !c.has(x, y + 1, z)) c.set(x, y + 1, z, "minecraft:white_carpet");
                        }
                    }
            }
            int crownY = wh + rings.length - 1;
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                c.remove(x, crownY, z);
                c.set(x, crownY, z, x == 0 && z == 0 ? "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false]" : "@plank_slab[type=bottom]");
            }
            // stove and pipe through the crown
            c.pass(Pass.INTERIORS);
            c.set(0, 1, 0, "minecraft:smoker[facing=south,lit=false]");
            for (int y = 2; y <= crownY + 1; y++) c.set(0, y, 0, "minecraft:chain[axis=y]");
            c.set(0, crownY + 2, 0, "minecraft:iron_bars");
            // door
            c.pass(Pass.SHELLS);
            c.air(0, 1, door);
            c.air(0, 2, door);
            c.set(0, 3, door, "@felt_accent");
            c.set(-1, 1, door, "@felt_accent");
            c.set(1, 1, door, "@felt_accent");
            c.pass(Pass.INTERIORS);
            c.set(0, 1, door, "minecraft:acacia_door[facing=north,half=lower,hinge=left,open=false]");
            c.set(0, 2, door, "minecraft:acacia_door[facing=north,half=upper,hinge=left,open=false]");
            // furniture: chests at the back (khoimor), beds left and right, rugs
            int back = -(int) Math.floor(R - 1.5);
            c.set(-1, 1, back, "minecraft:chest[facing=south]");
            c.set(1, 1, back, "minecraft:barrel[facing=up]");
            c.set(0, 1, back, "minecraft:orange_terracotta");
            c.set(0, 2, back, "minecraft:flower_pot");
            int side = (int) Math.floor(R - 1.5);
            c.set(-side, 1, 0, "minecraft:orange_bed[facing=south,part=head]");
            c.set(-side, 1, 1, "minecraft:orange_bed[facing=south,part=foot]");
            if (!size.equals("small")) {
                c.set(side, 1, 0, "minecraft:red_bed[facing=south,part=head]");
                c.set(side, 1, 1, "minecraft:red_bed[facing=south,part=foot]");
            }
            String[] rugs = {"minecraft:red_carpet", "minecraft:orange_carpet", "minecraft:yellow_carpet"};
            for (int x = -span; x <= span; x++)
                for (int z = -span; z <= span; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d < 1.2 || d > R - 1.2 || c.has(x, 1, z)) continue;
                    if (r.nextInt(3) > 0) c.set(x, 1, z, rugs[(Math.abs(x) + Math.abs(z)) % 3]);
                }
            if (size.equals("large")) {
                for (int y = 1; y <= wh + 1; y++) {
                    c.set(-1, y, -1, "minecraft:orange_terracotta");
                    c.set(1, y, -1, "minecraft:orange_terracotta");
                }
            }
            c.pass(Pass.LIGHTING);
            c.set(1, 2, back, "@light");
        }
    };

    /**
     * yard.fence — timber fence around a yard. Params: x1, z1, x2, z2 (local), gates [[x, z], ...]
     * on the fence line. y 0 = ground.
     */
    static final Module YARD = Kit.module("yard.fence", Layer.PROP, (c, ctx) -> {
        c.pass(Pass.DECORATION);
        int x1 = ctx.integer("x1", -5), z1 = ctx.integer("z1", -5), x2 = ctx.integer("x2", 5), z2 = ctx.integer("z2", 5);
        for (int x = x1; x <= x2; x++) {
            c.set(x, 1, z1, "@fence");
            c.set(x, 1, z2, "@fence");
        }
        for (int z = z1; z <= z2; z++) {
            c.set(x1, 1, z, "@fence");
            c.set(x2, 1, z, "@fence");
        }
        for (Object o : ctx.list("gates")) {
            List<Object> g = Json.array(o);
            int gx = Json.integer(g.get(0)), gz = Json.integer(g.get(1));
            String facing = gz == z1 || gz == z2 ? "south" : "east";
            c.set(gx, 1, gz, "minecraft:spruce_fence_gate[facing=" + facing + ",open=true]");
        }
    });

    /**
     * prop.camp — yard life. Params: kind (firepit | cart | hay | woodpile | hitching | drying).
     * Faces +z where it matters.
     */
    static final Module CAMP = Kit.module("prop.camp", Layer.PROP, (c, ctx) -> {
        c.pass(Pass.DECORATION);
        switch (ctx.string("kind", "firepit")) {
            case "firepit" -> {
                c.set(0, 1, 0, "@fire[lit=true]");
                for (Facing f : Facing.values()) c.set(f.dx * 2, 1, f.dz * 2, Kit.stairs("@plank_stairs", Kit.opposite(f), false));
                for (int[] d : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) c.set(d[0], 1, d[1], "minecraft:cobblestone_slab[type=bottom]");
            }
            case "cart" -> {
                for (int x = -1; x <= 1; x++) for (int z = -2; z <= 1; z++) c.set(x, 2, z, "@plank_slab[type=bottom]");
                for (int z = -2; z <= 1; z++) {
                    c.set(-2, 2, z, "@fence");
                    c.set(2, 2, z, "@fence");
                }
                for (int sx : new int[]{-2, 2}) for (int sz : new int[]{-1, 0}) c.set(sx, 1, sz, "minecraft:dark_oak_trapdoor[facing=" + (sx < 0 ? "west" : "east") + ",half=bottom,open=true]");
                c.set(0, 1, -1, "@fence");
                c.set(0, 1, 0, "@fence");
                c.set(0, 2, 2, "@fence");
                c.set(0, 2, 3, "@fence");
                c.set(0, 3, -1, "minecraft:hay_block");
                c.set(-1, 3, 0, "minecraft:barrel[facing=up]");
            }
            case "hay" -> {
                c.set(0, 1, 0, "minecraft:hay_block");
                c.set(1, 1, 0, "minecraft:hay_block");
                c.set(0, 1, 1, "minecraft:hay_block");
                c.set(0, 2, 0, "minecraft:hay_block");
            }
            case "woodpile" -> {
                for (int x = -1; x <= 1; x++) {
                    c.set(x, 1, 0, "minecraft:spruce_log[axis=z]");
                    if (x != 1) c.set(x, 2, 0, "minecraft:birch_log[axis=z]");
                }
                c.set(0, 3, 0, "minecraft:spruce_log[axis=z]");
            }
            case "hitching" -> {
                for (int x = -2; x <= 2; x++) c.set(x, 1, 0, "@fence");
                c.set(-2, 2, 0, "@fence");
                c.set(2, 2, 0, "@fence");
                c.set(3, 1, 0, "minecraft:water_cauldron[level=3]");
            }
            case "drying" -> {
                c.set(-2, 1, 0, "@fence");
                c.set(-2, 2, 0, "@fence");
                c.set(2, 1, 0, "@fence");
                c.set(2, 2, 0, "@fence");
                for (int x = -2; x <= 2; x++) c.set(x, 3, 0, "@fence");
                c.set(-1, 2, 0, "minecraft:brown_wool");
                c.set(0, 2, 0, "minecraft:white_wool");
                c.set(1, 2, 0, "minecraft:light_gray_wool");
            }
            default -> throw new IllegalArgumentException("unknown camp prop " + ctx.string("kind", ""));
        }
    });
}
