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
     * on a three-tier stepped pedestal. Original SÜLD design in patinated bronze with gold details,
     * facing +z (south, toward the Imperial Gate). y 0 = plaza surface. The figure is modelled in
     * horse units and voxelised at {@code scale} (default 2.4).
     */
    static final Module MONUMENT = Kit.module("monument.equestrian", Layer.LANDMARK, (c, ctx) -> {
        c.pass(Pass.LANDMARKS);
        // pedestal: three stepped tiers (longer front-to-back, like the horse) and a dark plinth
        for (int x = -7; x <= 7; x++)
            for (int z = -10; z <= 10; z++) {
                int ax = Math.abs(x), az = Math.abs(z);
                boolean t1 = ax <= 7 && az <= 10, t2 = ax <= 6 && az <= 9, t3 = ax <= 5 && az <= 8, t4 = ax <= 4 && az <= 7;
                if (t1) c.set(x, 1, z, t2 ? "@trim" : Kit.stairs("@stone_stairs", Facing.toward(x * 1.4, z, 0, 0), false));
                if (t2) c.set(x, 2, z, t3 ? "@stone" : Kit.stairs("@stone_stairs", Facing.toward(x * 1.4, z, 0, 0), false));
                if (t3) for (int y = 3; y <= 5; y++) c.set(x, y, z, !t4 ? (y == 4 ? "@statue_gold" : "@statue") : "@statue_dark");
                if (t4) c.set(x, 6, z, ax == 4 || az == 7 ? "minecraft:polished_blackstone_slab[type=bottom]" : "@statue");
            }
        for (int k = -1; k <= 1; k++) {
            c.set(k, 4, 9, "@ridge");
            c.set(k, 4, -9, "@ridge");
        }
        double s = ctx.number("scale", 2.4);
        double oz = -1.0, base = 6.6;
        Sculpt f = new Sculpt(c, s, 0.5, base, oz);
        String hide = "@bronze", mane = "@bronze_dark", gold = "minecraft:gold_block";
        // horse
        f.ell(0, 3.40, 0.0, 0.85, 0.95, 2.05, hide);      // barrel
        f.ell(0, 3.55, 1.55, 0.86, 1.02, 0.95, hide);     // chest
        f.ell(0, 3.65, -1.65, 0.92, 1.0, 0.95, hide);     // rump
        for (int side = -1; side <= 1; side += 2) {
            f.cap(0.48 * side, 3.4, -1.8, 0.55 * side, 1.9, -2.25, 0.45, hide);   // hind thigh
            f.cap(0.55 * side, 1.9, -2.25, 0.55 * side, 0.1, -2.0, 0.27, hide);   // hind cannon
        }
        f.cap(0.5, 3.0, 1.7, 0.5, 1.5, 1.9, 0.38, hide);           // planted foreleg
        f.cap(0.5, 1.5, 1.9, 0.5, 0.1, 1.9, 0.27, hide);
        f.cap(-0.5, 3.0, 1.8, -0.5, 2.35, 2.65, 0.36, hide);       // raised foreleg
        f.cap(-0.5, 2.35, 2.65, -0.5, 1.45, 2.35, 0.26, hide);
        f.cap(0, 3.9, 1.9, 0, 5.45, 2.45, 0.6, hide);              // neck, arched upright
        f.cap(0, 5.6, 2.55, 0, 4.95, 3.45, 0.38, hide);            // head, nose down
        f.cap(0, 4.2, 1.65, 0, 5.85, 2.25, 0.24, mane);            // mane crest
        f.cap(0, 3.95, -2.5, 0, 2.35, -3.1, 0.25, mane);           // tail
        f.cap(0, 4.25, -0.35, 0, 4.25, 0.55, 0.62, "@cloth");       // saddle cloth
        // rider
        f.cap(0, 4.55, -0.1, 0, 6.15, 0.1, 0.5, hide);             // torso in a deel
        f.cap(0, 4.7, 0.0, 0, 4.7, 0.0, 0.55, gold);                // belt
        for (int side = -1; side <= 1; side += 2) f.cap(0.72 * side, 4.5, 0.0, 0.85 * side, 3.45, 0.6, 0.27, hide); // legs astride
        f.ell(0, 6.75, 0.15, 0.36, 0.38, 0.36, hide);              // head
        f.cap(0, 7.05, 0.15, 0, 7.45, 0.15, 0.12, gold);           // helmet crest
        f.cap(0, 6.0, -0.4, 0, 4.6, -1.25, 0.42, mane);            // cloak
        f.cap(0.55, 5.9, 0.0, 1.0, 7.0, 0.3, 0.2, hide);           // right arm raised
        f.cap(-0.55, 5.8, 0.1, -0.75, 4.9, 0.95, 0.2, hide);       // left arm, reins
        // the SÜLD spear held aloft: shaft, gold ring, white horse-hair tassel, silver tip
        int sx = (int) Math.floor(0.5 + 1.1 * s), sz = (int) Math.floor(oz + 0.35 * s);
        int shaft0 = (int) Math.floor(base + 3.6 * s), top = (int) Math.floor(base + 8.6 * s);
        for (int y = shaft0; y <= top; y++) c.set(sx, y, sz, "minecraft:dark_oak_fence");
        c.set(sx, top + 1, sz, gold);
        c.set(sx, top + 2, sz, "minecraft:end_rod[facing=up]");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            c.set(sx + dx, top, sz + dz, "@felt");
            if (dx == 0 || dz == 0) c.set(sx + dx, top - 1, sz + dz, "@felt");
        }
        c.pass(Pass.LIGHTING);
        for (int sxx : new int[]{-6, 6}) for (int szz : new int[]{-9, 9}) {
            c.set(sxx, 3, szz, "@statue");
            c.set(sxx, 4, szz, "@fire[lit=true]");
        }
    });

    /** Sculpting in model units: x, y, z scaled by s and offset (origin x, base y, origin z). */
    private record Sculpt(ModuleCanvas c, double s, double ox, double oy, double oz) {
        void ell(double x, double y, double z, double rx, double ry, double rz, String t) {
            Kit.ellipsoid(c, ox + x * s, oy + y * s, oz + z * s, rx * s, ry * s, rz * s, t);
        }

        void cap(double ax, double ay, double az, double bx, double by, double bz, double r, String t) {
            Kit.capsule(c, ox + ax * s, oy + ay * s, oz + az * s, ox + bx * s, oy + by * s, oz + bz * s, Math.max(0.72, r * s), t);
        }
    }

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
