package mn.suld.api.worldbuild.kharkhorum;

import mn.suld.api.json.Json;
import mn.suld.api.worldbuild.Facing;
import mn.suld.api.worldbuild.Layer;
import mn.suld.api.worldbuild.Module;
import mn.suld.api.worldbuild.ModuleCanvas;
import mn.suld.api.worldbuild.Pass;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** The Central Plaza, the Khan's equestrian monument and the plaza's gameplay props. */
final class Plaza {

    private Plaza() {
    }

    private static boolean nearAny(List<double[]> pts, double x, double z, double d) {
        for (double[] p : pts) if ((p[0] - x) * (p[0] - x) + (p[1] - z) * (p[1] - z) < d * d) return true;
        return false;
    }

    /** True when (x, z) lies on one of the four street mouths (avenue 9 wide N/S, cross street 7 wide E/W). */
    private static boolean mouth(double x, double z) {
        return Math.abs(x) <= 6 || Math.abs(z) <= 5;
    }

    /**
     * plaza.ceremonial — Ø 57 raised plaza (origin = centre, y 0 = the plaza surface, one block
     * above the surrounding ground). A stepped rim of stairs, concentric rings and eight spokes in
     * polished stone, a dark border, nine white tug standards (after the Nine White Banners), a ring
     * of lantern posts, benches and planters. Params: radius (28), avoid [[x,z],...] (gameplay points
     * kept clear).
     */
    static final Module PLAZA = Kit.module("plaza.ceremonial", Layer.INFRA, (c, ctx) -> {
        int R = ctx.integer("radius", 28);
        List<double[]> avoid = new ArrayList<>();
        for (Object o : ctx.list("avoid")) {
            List<Object> p = Json.array(o);
            avoid.add(new double[]{((Number) p.get(0)).doubleValue(), ((Number) p.get(1)).doubleValue()});
        }
        SplittableRandom r = ctx.random();
        c.pass(Pass.DISTRICT_FOOTPRINTS);
        for (int x = -R - 1; x <= R + 1; x++)
            for (int z = -R - 1; z <= R + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > R + 1.2) continue;
                if (d > R + 0.3) { // rim step down to the street level
                    Facing in = Facing.toward(x, z, 0, 0);
                    c.set(x, 0, z, Kit.stairs("@stone_stairs", in, false));
                    continue;
                }
                double ang = Math.toDegrees(Math.atan2(z, x));
                double spoke = Math.abs(((ang % 45) + 45) % 45 - 22.5); // 22.5 on a spoke
                String b;
                if (d > R - 0.7) b = "@trim_dark";
                else if (d > R - 1.7) b = "@paving_accent";
                else if (Math.abs(d - 19) < 0.5 || Math.abs(d - 10.5) < 0.5) b = "@paving_accent";
                else if (Math.abs(d - 22.5) < 0.55) b = ((int) Math.floor((ang + 180) / 7.5) & 1) == 0 ? "minecraft:polished_tuff" : "@trim_dark";
                else if (d > 10.5 && 22.5 - spoke < 0.5 + 0.6 * 10 / d) b = "@paving_accent";
                else if (d <= 10.5) b = ((x + z) & 1) == 0 ? "@paving" : "@paving_alt";
                else b = Kit.paving(r);
                c.set(x, 0, z, b);
                c.set(x, -1, z, "@packed");
            }
        // nine white tug standards on a ring
        c.pass(Pass.DECORATION);
        for (int i = 0; i < 9; i++) {
            double a = Math.toRadians(-90 + 20 + i * 40);
            double tx = Math.cos(a) * 24, tz = Math.sin(a) * 24;
            int x = (int) Math.round(tx), z = (int) Math.round(tz);
            if (mouth(x, z) || nearAny(avoid, x, z, 3.5)) continue;
            Kit.tug(c, x, 0, z, 9, "@felt");
        }
        // lantern ring
        for (int i = 0; i < 24; i++) {
            double a = Math.toRadians(i * 15 + 7.5);
            int x = (int) Math.round(Math.cos(a) * (R - 2.5)), z = (int) Math.round(Math.sin(a) * (R - 2.5));
            if (mouth(x, z) || nearAny(avoid, x, z, 2)) continue;
            Kit.lanternPost(c, x, 0, z);
        }
        // benches (stairs facing the centre) and planters on the inner ring
        c.pass(Pass.DECORATION);
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(i * 22.5 + 11.25);
            double cx = Math.cos(a) * 15.5, cz = Math.sin(a) * 15.5;
            int x = (int) Math.round(cx), z = (int) Math.round(cz);
            if (mouth(x, z) || nearAny(avoid, x, z, 3)) continue;
            if ((i & 1) == 0) {
                // planter: mud-brick box with a flowering shrub
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    boolean rim = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                    c.set(x + dx, 1, z + dz, rim ? "@base" : "minecraft:rooted_dirt");
                }
                c.set(x, 2, z, r.nextBoolean() ? "minecraft:flowering_azalea" : "minecraft:azalea");
                c.set(x + 1, 2, z, "minecraft:poppy");
                c.set(x - 1, 2, z, "minecraft:cornflower");
            } else {
                Facing in = Facing.toward(cx, cz, 0, 0);
                Facing along = Facing.values()[(in.ordinal() + 1) % 4];
                for (int k = -1; k <= 1; k++) {
                    int bx = x + along.dx * k, bz = z + along.dz * k;
                    c.set(bx, 1, bz, Kit.stairs("@plank_stairs", Kit.opposite(in), false));
                }
            }
        }
    });

    /**
     * monument.equestrian — Хааны Морьт Хөшөө: the Khan on a rearing horse, raising the SÜLD spear,
     * on a three-tier stepped pedestal. Original SÜLD design (dark bronze with gold details),
     * facing +z (south, toward the Imperial Gate). y 0 = plaza surface.
     */
    static final Module MONUMENT = Kit.module("monument.equestrian", Layer.LANDMARK, (c, ctx) -> {
        SplittableRandom r = ctx.random();
        c.pass(Pass.LANDMARKS);
        // pedestal: three stepped tiers and a plinth
        for (int x = -6; x <= 6; x++)
            for (int z = -6; z <= 6; z++) {
                int m = Math.max(Math.abs(x), Math.abs(z));
                c.set(x, 1, z, m == 6 ? Kit.stairs("@stone_stairs", Facing.toward(x, z, 0, 0), false) : "@trim");
                if (m <= 5) c.set(x, 2, z, m == 5 ? Kit.stairs("@stone_stairs", Facing.toward(x, z, 0, 0), false) : "@stone");
                if (m <= 4) for (int y = 3; y <= 5; y++) c.set(x, y, z, m == 4 ? (y == 4 ? "@statue_gold" : "@statue") : "@statue_dark");
                if (m <= 3) c.set(x, 6, z, m == 3 ? "minecraft:polished_blackstone_slab[type=bottom]" : "@statue");
            }
        // plaques on the four faces
        for (int k = -1; k <= 1; k++) {
            c.set(k, 4, 5, "@ridge");
            c.set(k, 4, -5, "@ridge");
        }
        // the horse (scale ~2), facing +z; hooves on the plinth top (y 7)
        String bronze = "@statue";
        String dark = "@statue_dark";
        Kit.ellipsoid(c, 0.5, 12.0, 0.5, 1.9, 2.1, 4.4, bronze);                 // barrel
        Kit.capsule(c, -0.6, 7.0, -2.4, -0.6, 11.0, -2.4, 0.65, bronze);        // hind legs
        Kit.capsule(c, 1.6, 7.0, -2.6, 1.6, 11.0, -2.4, 0.65, bronze);
        Kit.capsule(c, 1.6, 7.0, 3.0, 1.6, 11.0, 3.0, 0.65, bronze);            // planted foreleg
        Kit.capsule(c, -0.6, 11.0, 3.2, -0.6, 10.0, 5.0, 0.6, bronze);          // raised foreleg: forearm
        Kit.capsule(c, -0.6, 10.0, 5.0, -0.6, 8.6, 4.6, 0.55, bronze);          //                 cannon
        Kit.capsule(c, 0.5, 13.2, 3.6, 0.5, 16.4, 5.4, 1.15, bronze);           // neck
        Kit.capsule(c, 0.5, 16.6, 5.6, 0.5, 15.0, 7.8, 0.8, bronze);            // head
        c.set(0, 17, 5, dark);
        c.set(1, 17, 5, dark);                                                   // ears
        Kit.capsule(c, 0.5, 13.0, 3.6, 0.5, 17.0, 4.6, 0.5, dark);               // mane
        Kit.capsule(c, 0.5, 12.6, -4.3, 0.5, 9.0, -5.9, 0.55, dark);             // tail
        // saddle cloth
        for (int x = -1; x <= 2; x++) for (int z = -1; z <= 1; z++) c.set(x, 14, z, z == -1 || z == 1 ? "@statue_gold" : bronze);
        // the rider
        Kit.capsule(c, 0.5, 14.5, -0.2, 0.5, 17.6, 0.4, 1.05, bronze);          // torso in a deel
        Kit.capsule(c, -1.0, 14.6, 0.0, -1.3, 12.4, 1.1, 0.55, bronze);         // legs astride
        Kit.capsule(c, 2.0, 14.6, 0.0, 2.3, 12.4, 1.1, 0.55, bronze);
        Kit.ellipsoid(c, 0.5, 18.9, 0.4, 0.85, 0.95, 0.85, bronze);             // head
        c.set(0, 20, 0, "@statue_gold");                                         // helmet crest
        Kit.capsule(c, 0.5, 16.5, -0.8, 0.5, 13.8, -2.6, 0.85, dark);            // cloak behind
        Kit.capsule(c, -0.6, 17.2, 0.2, -1.4, 15.4, 2.0, 0.45, bronze);         // left arm → reins
        Kit.capsule(c, 1.8, 17.4, 0.2, 2.9, 19.6, 0.9, 0.45, bronze);           // right arm raised
        c.set(1, 15, 0, "@statue_gold");                                         // belt
        // the SÜLD spear: shaft, gold ring, white horse-hair tassel, silver tip
        int sx = 3, sz = 1;
        for (int y = 13; y <= 23; y++) c.set(sx, y, sz, "minecraft:dark_oak_fence");
        c.set(sx, 24, sz, "minecraft:gold_block");
        c.set(sx, 25, sz, "minecraft:end_rod[facing=up]");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            c.set(sx + dx, 23, sz + dz, "@felt");
            if (dx == 0 || dz == 0) c.set(sx + dx, 22, sz + dz, "@felt");
        }
        // lights at the pedestal corners
        c.pass(Pass.LIGHTING);
        for (int sxx : new int[]{-5, 5}) for (int szz : new int[]{-5, 5}) {
            c.set(sxx, 3, szz, "@statue");
            c.set(sxx, 4, szz, "@fire[lit=true]");
        }
    });

    /** prop.class_stones — four standing stones around the class-selection point (origin left clear). */
    static final Module CLASS_STONES = Kit.module("prop.class_stones", Layer.PROP, (c, ctx) -> {
        c.pass(Pass.GAMEPLAY);
        String[] tops = {"minecraft:red_wool", "minecraft:light_blue_wool", "minecraft:lime_wool", "minecraft:yellow_wool"};
        int[][] at = {{-3, 0}, {3, 0}, {0, -3}, {0, 3}};
        for (int i = 0; i < 4; i++) {
            int x = at[i][0], z = at[i][1];
            c.set(x, 1, z, "@statue_dark");
            c.set(x, 2, z, "@statue");
            c.set(x, 3, z, "minecraft:chiseled_polished_blackstone");
            c.set(x, 4, z, tops[i]);
        }
    });

    /** prop.relay_post — Өртөө relay post: hitching rail, a white tug and a lantern (origin left clear). */
    static final Module RELAY_POST = Kit.module("prop.relay_post", Layer.PROP, (c, ctx) -> {
        c.pass(Pass.GAMEPLAY);
        for (int z = -1; z <= 1; z++) c.set(-2, 1, z, "@fence");
        c.set(-2, 2, -1, "@fence");
        c.set(-2, 2, 1, "@fence");
        Kit.tug(c, -2, 0, -2, 6, "@felt");
        Kit.lanternPost(c, -2, 0, 2);
        c.set(2, 1, 0, "minecraft:hay_block");
        c.set(2, 1, 1, "minecraft:barrel[facing=up]");
    });
}
