package mn.suld.api.worldbuild.kharkhorum;

import mn.suld.api.worldbuild.Facing;
import mn.suld.api.worldbuild.Layer;
import mn.suld.api.worldbuild.Module;
import mn.suld.api.worldbuild.ModuleCanvas;
import mn.suld.api.worldbuild.ModuleContext;
import mn.suld.api.worldbuild.Pass;
import mn.suld.api.worldbuild.Transform;

import java.util.Map;
import java.util.SplittableRandom;

/** The Great Market: stalls, merchant houses and the market street that lines them up. */
final class Market {

    private Market() {
    }

    private static final String[][] AWNINGS = {
            {"minecraft:red_wool", "minecraft:white_wool"},
            {"minecraft:blue_wool", "minecraft:white_wool"},
            {"minecraft:green_wool", "minecraft:white_wool"},
            {"minecraft:orange_wool", "minecraft:white_wool"},
            {"minecraft:yellow_wool", "minecraft:red_wool"},
            {"minecraft:cyan_wool", "minecraft:white_wool"}};

    private static final String[] GOODS = {
            "minecraft:barrel[facing=up]", "minecraft:hay_block", "minecraft:melon", "minecraft:pumpkin",
            "minecraft:decorated_pot", "minecraft:chest[facing=south]", "minecraft:composter",
            "minecraft:bee_nest[facing=south]", "minecraft:loom[facing=south]"};

    /**
     * market.stall — 5 × 4 stall, front (+z) open to the street, merchant stands at (0, 1, -2).
     * Params: variant (0 flat striped awning, 1 tent ridge, 2 timber lean-to), colour (index).
     */
    static final Module STALL = Kit.module("market.stall", Layer.STRUCTURE, (c, ctx) -> {
        SplittableRandom r = ctx.random();
        int variant = ctx.integer("variant", r.nextInt(3));
        String[] aw = AWNINGS[Math.floorMod(ctx.integer("colour", r.nextInt(AWNINGS.length)), AWNINGS.length)];
        c.pass(Pass.SHELLS);
        for (int x = -2; x <= 2; x++) for (int z = -3; z <= 0; z++) c.set(x, 0, z, "@plank");
        for (int sx : new int[]{-2, 2}) for (int sz : new int[]{-3, 0}) for (int y = 1; y <= 3; y++) c.set(sx, y, sz, "@beam[axis=y]");
        // counter
        c.set(-1, 1, 0, "minecraft:barrel[facing=up]");
        c.set(0, 1, 0, "minecraft:spruce_trapdoor[facing=south,half=top,open=false]");
        c.set(1, 1, 0, "minecraft:spruce_trapdoor[facing=south,half=top,open=false]");
        // back shelf of goods
        c.pass(Pass.INTERIORS);
        for (int x = -1; x <= 1; x++) c.set(x, 1, -3, GOODS[r.nextInt(GOODS.length)]);
        c.set(r.nextBoolean() ? -1 : 1, 2, -3, GOODS[r.nextInt(4)]);
        c.set(0, 2, 0, "minecraft:flower_pot");
        c.set(1, 2, 0, aw[0].replace("_wool", "_carpet"));
        c.pass(Pass.ROOFS_DETAIL);
        switch (variant) {
            case 1 -> { // tent ridge running along x
                for (int x = -3; x <= 3; x++) {
                    String a = (x & 1) == 0 ? aw[0] : aw[1];
                    c.set(x, 4, -2, a);
                    c.set(x, 4, -1, a);
                    c.set(x, 3, 0, a);
                    c.set(x, 3, -3, a);
                }
            }
            case 2 -> { // timber lean-to with a cloth valance
                for (int x = -3; x <= 3; x++) {
                    c.set(x, 4, -4, Kit.stairs("@plank_stairs", Facing.NORTH, false));
                    c.set(x, 4, -3, "@plank_slab[type=top]");
                    c.set(x, 4, -2, "@plank_slab[type=bottom]");
                    c.set(x, 4, -1, "@plank_slab[type=bottom]");
                    c.set(x, 4, 0, "@plank_slab[type=bottom]");
                    c.set(x, 4, 1, Kit.stairs("@plank_stairs", Facing.NORTH, true));
                    c.set(x, 3, 1, (x & 1) == 0 ? aw[0] : aw[1]);
                }
            }
            default -> { // flat striped awning with a scalloped valance
                for (int x = -3; x <= 3; x++) {
                    String a = (x & 1) == 0 ? aw[0] : aw[1];
                    for (int z = -4; z <= 1; z++) c.set(x, 4, z, a);
                    if ((x & 1) == 0) c.set(x, 3, 1, a);
                }
            }
        }
        c.pass(Pass.LIGHTING);
        if (variant == 1) c.set(0, 3, -2, "@light[hanging=true]");
        else c.set(0, 3, -1, "@light[hanging=true]");
    });

    /**
     * shop.house — 9 × 11 two-storey merchant house, front (+z) to the street: earth ground floor
     * with a door and shuttered windows, timber floor band, plastered upper floor with a balcony.
     * Params: roof (hip | flat), shutters colour.
     */
    static final Module SHOP = Kit.module("shop.house", Layer.STRUCTURE, (c, ctx) -> {
        SplittableRandom r = ctx.random();
        String roof = ctx.string("roof", r.nextBoolean() ? "hip" : "flat");
        c.pass(Pass.SHELLS);
        int x1 = -4, x2 = 4, z1 = -10, z2 = 0;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                c.set(x, 0, z, "@stone");
                for (int y = 1; y <= 9; y++) {
                    if (!edge) {
                        c.set(x, y, z, y == 5 ? "@plank" : "minecraft:air");
                        continue;
                    }
                    String b;
                    if (corner) b = y <= 4 ? "@base" : "@beam[axis=y]";
                    else if (y == 5) b = "@beam[axis=" + (z == z1 || z == z2 ? "x" : "z") + "]";
                    else if (y <= 4) b = y == 1 ? "@packed" : Kit.earth(r);
                    else b = (x == 0 && (z == z1 || z == z2)) || (z == -5 && (x == x1 || x == x2)) ? "@beam[axis=y]" : "@plaster";
                    c.set(x, y, z, b);
                }
            }
        // door, windows, sign
        c.air(0, 1, z2);
        c.air(0, 2, z2);
        c.pass(Pass.INTERIORS);
        c.set(0, 1, z2, "minecraft:spruce_door[facing=south,half=lower,hinge=left,open=false]");
        c.set(0, 2, z2, "minecraft:spruce_door[facing=south,half=upper,hinge=left,open=false]");
        c.pass(Pass.SHELLS);
        for (int x : new int[]{-2, 2}) {
            c.set(x, 2, z2, "@glass");
            c.set(x, 3, z2, "@glass");
            c.set(x, 7, z2, "@glass");
            c.set(x, 2, z1, "@glass");
            c.set(x, 7, z1, "@glass");
        }
        for (int z : new int[]{-3, -7}) {
            c.set(x1, 2, z, "@glass");
            c.set(x2, 2, z, "@glass");
            c.set(x1, 7, z, "@glass");
            c.set(x2, 7, z, "@glass");
        }
        c.pass(Pass.DECORATION);
        String shutter = ctx.string("shutters", r.nextBoolean() ? "minecraft:dark_oak_trapdoor" : "minecraft:spruce_trapdoor");
        for (int x : new int[]{-3, -1, 1, 3})
            c.set(x, 7, z2 + 1, shutter + "[facing=south,half=bottom,open=true]");
        // balcony over the door
        for (int x = -2; x <= 2; x++) {
            c.set(x, 5, z2 + 1, "@plank_slab[type=top]");
            c.set(x, 6, z2 + 2, "@fence");
            c.set(x, 5, z2 + 2, "@plank_slab[type=top]");
        }
        c.set(-2, 6, z2 + 1, "@fence");
        c.set(2, 6, z2 + 1, "@fence");
        c.set(0, 6, z2, "minecraft:spruce_door[facing=south,half=lower,hinge=right,open=false]");
        c.set(0, 7, z2, "minecraft:spruce_door[facing=south,half=upper,hinge=right,open=false]");
        // interior: counter, goods, stairs up
        c.pass(Pass.INTERIORS);
        for (int x = -3; x <= 1; x++) c.set(x, 1, -4, "minecraft:spruce_trapdoor[facing=south,half=top,open=false]");
        c.set(-3, 1, -8, "minecraft:barrel[facing=up]");
        c.set(-2, 1, -9, "minecraft:barrel[facing=up]");
        c.set(-3, 1, -9, "minecraft:chest[facing=east]");
        for (int i = 0; i < 4; i++) c.set(3, 1 + i, -9 + i, Kit.stairs("@plank_stairs", Facing.SOUTH, false));
        for (int i = 0; i < 4; i++) c.air(3, 5, -9 + i);
        c.set(3, 5, -9, "minecraft:air");
        c.pass(Pass.LIGHTING);
        c.set(0, 4, -6, "@light[hanging=true]");
        c.set(-1, 9, -5, "@light[hanging=true]");
        c.set(-2, 3, z2 + 1, "minecraft:wall_torch[facing=south]");
        c.set(2, 3, z2 + 1, "minecraft:wall_torch[facing=south]");
        // roof
        if (roof.equals("flat")) {
            Kit.flatRoof(c, x1, z1, x2, z2, 10, "@packed", "@base");
            // roof terrace with a striped canopy on four posts
            c.pass(Pass.DECORATION);
            for (int sx : new int[]{-2, 2}) for (int sz : new int[]{-8, -5}) c.set(sx, 11, sz, "@fence");
            for (int x = -2; x <= 2; x++) for (int z = -8; z <= -5; z++) c.set(x, 12, z, (x & 1) == 0 ? "minecraft:red_wool" : "minecraft:white_wool");
        } else {
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) c.set(x, 10, z, "@plank");
            Kit.hipRoof(c, x1 - 1, z1 - 1, x2 + 1, z2 + 1, 10, null, true);
        }
    });

    /**
     * market.street — one side-by-side market street along local x (0..length-1), the paved road
     * itself (z -3..3) is a separate road module. Sidewalks at z ±4, stalls every 8 (centres at
     * 8, 16, ...; merchants stand at z ∓7), merchant houses every 12 behind them, lanterns between.
     */
    static final Module STREET = Kit.module("market.street", Layer.STRUCTURE, (c, ctx) -> {
        SplittableRandom r = ctx.random();
        int len = ctx.integer("length", 72);
        c.pass(Pass.DISTRICT_FOOTPRINTS);
        for (int x = 2; x < len; x++) {
            c.set(x, 0, 4, Kit.paving(r));
            c.set(x, 0, -4, Kit.paving(r));
        }
        int k = 0;
        for (int cx = 8; cx + 2 < len; cx += 8, k++) {
            // north side stall faces south (+z): origin (cx, 0, -5)
            c.place(STALL, cx, 0, -5, Transform.IDENTITY, Map.of("variant", (long) (k % 3), "colour", (long) (k * 2)), ctx);
            // south side stall faces north: rotate 180, origin (cx, 0, 5)
            c.place(STALL, cx, 0, 5, Transform.of(180, false), Map.of("variant", (long) ((k + 1) % 3), "colour", (long) (k * 2 + 1)), ctx);
        }
        for (int cx = 4; cx + 4 < len; cx += 4) {
            if (cx % 8 == 0) continue;
            Kit.lanternPost(c, cx, 0, -4);
            Kit.lanternPost(c, cx, 0, 4);
        }
        k = 0;
        for (int cx = 6; cx + 5 < len; cx += 12, k++) {
            c.place(SHOP, cx, 0, -11, Transform.IDENTITY, Map.of("roof", k % 2 == 0 ? "flat" : "hip"), ctx);
            c.place(SHOP, cx, 0, 11, Transform.of(180, false), Map.of("roof", k % 2 == 0 ? "hip" : "flat"), ctx);
            // alley paving between the stalls and the houses
            for (int x = cx - 5; x <= cx + 5; x++) {
                c.setIfEmpty(x, 0, -9, Kit.paving(r));
                c.setIfEmpty(x, 0, -10, Kit.paving(r));
                c.setIfEmpty(x, 0, 9, Kit.paving(r));
                c.setIfEmpty(x, 0, 10, Kit.paving(r));
            }
        }
    });
}
