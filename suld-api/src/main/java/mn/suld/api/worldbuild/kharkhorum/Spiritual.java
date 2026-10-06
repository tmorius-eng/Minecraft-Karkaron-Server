package mn.suld.api.worldbuild.kharkhorum;

import mn.suld.api.worldbuild.Facing;
import mn.suld.api.worldbuild.Layer;
import mn.suld.api.worldbuild.Module;
import mn.suld.api.worldbuild.ModuleCanvas;
import mn.suld.api.worldbuild.Pass;

import java.util.SplittableRandom;

/** The spiritual hill: the Tenger Sky Shrine. */
final class Spiritual {

    private Spiritual() {
    }

    private static final String[] FLAGS = {"minecraft:blue_wool", "minecraft:white_wool", "minecraft:red_wool",
            "minecraft:green_wool", "minecraft:yellow_wool"};

    /** A face-connected run of blocks from a to b (so strings never "float"), colours cycling. */
    static void flagLine(ModuleCanvas c, int ax, int ay, int az, int bx, int by, int bz) {
        int x = ax, y = ay, z = az, i = 0;
        double tx = Math.abs(bx - ax), ty = Math.abs(by - ay), tz = Math.abs(bz - az);
        while (x != bx || y != by || z != bz) {
            double rx = tx == 0 ? 0 : Math.abs(bx - x) / tx, ry = ty == 0 ? 0 : Math.abs(by - y) / ty, rz = tz == 0 ? 0 : Math.abs(bz - z) / tz;
            if (rx >= ry && rx >= rz) x += Integer.signum(bx - x);
            else if (rz >= ry) z += Integer.signum(bz - z);
            else y += Integer.signum(by - y);
            if (x == bx && y == by && z == bz) break;
            c.set(x, y, z, FLAGS[(i++ / 2) % FLAGS.length]);
        }
    }

    /**
     * shrine.sky — Тэнгэрийн Тахилга: SÜLD's own sky shrine (invented spirituality, see the research
     * brief). A round stone terrace with a raised sanctum, a great ovoo cairn hung with sky-blue
     * khadag, the sky pole with a gold trident, wind-horse prayer-flag strings to eight poles,
     * four braziers and an offering stone on the south approach (offering point at (0, 1, 10)).
     */
    static final Module SHRINE = Kit.module("shrine.sky", Layer.LANDMARK, (c, ctx) -> {
        SplittableRandom r = ctx.random();
        c.pass(Pass.LANDMARKS);
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 9.3) continue;
                double ang = Math.toDegrees(Math.atan2(z, x));
                boolean ray = Math.abs(((ang % 45) + 45) % 45 - 22.5) > 19.5;
                c.set(x, 0, z, d > 8.5 ? "@trim_dark" : ray ? "@paving_accent" : Kit.paving(r));
                if (d <= 5.4) c.set(x, 1, z, d > 4.6 ? Kit.stairs("@stone_stairs", Facing.toward(x, z, 0, 0), false) : "@stone");
            }
        // ovoo: a cone of mixed stones, hung with khadag
        String[] stones = {"minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:andesite", "minecraft:stone",
                "minecraft:tuff", "minecraft:cobbled_deepslate"};
        for (int y = 2; y <= 7; y++) {
            double rad = 3.4 - (y - 2) * 0.55;
            for (int x = -4; x <= 4; x++)
                for (int z = -4; z <= 4; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d > rad) continue;
                    boolean outer = d > rad - 1;
                    c.set(x, y, z, outer && r.nextInt(7) == 0 ? "minecraft:light_blue_wool" : stones[r.nextInt(stones.length)]);
                }
        }
        // the sky pole: birch, then a tall shaft, gold trident at the top
        for (int y = 8; y <= 10; y++) c.set(0, y, 0, "minecraft:stripped_birch_log");
        for (int y = 11; y <= 15; y++) c.set(0, y, 0, "minecraft:dark_oak_fence");
        c.set(0, 16, 0, "minecraft:gold_block");
        c.set(0, 17, 0, "minecraft:end_rod[facing=up]");
        c.set(-1, 17, 0, "minecraft:gold_block");
        c.set(1, 17, 0, "minecraft:gold_block");
        c.set(-1, 18, 0, "minecraft:end_rod[facing=up]");
        c.set(1, 18, 0, "minecraft:end_rod[facing=up]");
        // khadag streamers from the pole
        for (Facing f : Facing.values()) {
            c.set(f.dx, 14, f.dz, "minecraft:light_blue_wool");
            c.set(f.dx, 13, f.dz, "minecraft:light_blue_wool");
            c.set(f.dx * 2, 13, f.dz * 2, "minecraft:light_blue_wool");
            c.set(f.dx * 2, 12, f.dz * 2, "minecraft:light_blue_wool");
        }
        // prayer-flag strings to eight poles on the rim
        c.pass(Pass.DECORATION);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            int px = (int) Math.round(Math.cos(a) * 8), pz = (int) Math.round(Math.sin(a) * 8);
            for (int y = 1; y <= 6; y++) c.set(px, y, pz, "minecraft:dark_oak_fence");
            c.set(px, 7, pz, "minecraft:light_blue_wool");
            flagLine(c, 0, 12, 0, px, 7, pz);
        }
        // braziers at the four quarters, offering stone at the south approach
        c.pass(Pass.LIGHTING);
        for (Facing f : Facing.values()) Kit.brazier(c, f.dx * 7, 0, f.dz * 7, 3);
        c.pass(Pass.GAMEPLAY);
        c.set(-1, 1, 8, "@trim");
        c.set(1, 1, 8, "@trim");
        c.set(0, 1, 8, "minecraft:chiseled_stone_bricks");
        c.set(0, 2, 8, "minecraft:candle[candles=3,lit=true]");
        c.set(-1, 2, 8, "minecraft:white_carpet");
        c.set(1, 2, 8, "minecraft:light_blue_carpet");
        // the sacred larch beside the terrace
        Kit.tree(c, 7, 0, -7, "larch", r);
    });

    /** prop.cairn — a small ovoo by the road, with a khadag. */
    static final Module CAIRN = Kit.module("prop.cairn", Layer.PROP, (c, ctx) -> {
        c.pass(Pass.DECORATION);
        c.set(0, 1, 0, "minecraft:cobblestone");
        c.set(1, 1, 0, "minecraft:mossy_cobblestone");
        c.set(0, 1, 1, "minecraft:andesite");
        c.set(0, 2, 0, "minecraft:cobblestone_wall");
        c.set(0, 3, 0, "minecraft:light_blue_wool");
    });
}
