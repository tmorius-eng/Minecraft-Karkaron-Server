package mn.suld.api.mastery;

import mn.suld.api.region.Area;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Where the crafts gather (docs/MASTERY_AND_PROFESSIONS.md): every wild area holds {@link #VEINS} ore veins, {@link #HERBS}
 * herb patches and one herder's pen («малчны хашаа», horses and sheep), at spots that follow from the area, the spawn and
 * a fixed seed (the same spots on every start, moving with the spawn as the area rings do). A node's tier is its area's
 * band ({@link #tier}). Nodes keep clear of the places given as keep-away points (ovoos, historic sites, dungeon gates),
 * of the keep-out rectangles (Kharkhorum's walls, which reach well into the inner areas), of each other, of the area's
 * edges and of the world border. Pure: the plugin checks the ground when it builds a node.
 */
public final class GatherPlan {

    /** Ore veins in each area. */
    public static final int VEINS = 4;
    /** Herb patches in each area. */
    public static final int HERBS = 4;
    /** The animals of a pen: horses for the Адуучин, sheep for the Эсгийч. */
    public static final int PEN_HORSES = 3, PEN_SHEEP = 4;
    /** A pen's fence: a square this many blocks from its middle (9 × 9 inside the posts). */
    public static final int PEN_HALF = 5;
    /**
     * Other spots a pen may stand at, in order, when the ground at the ones before holds no yard (a lake, a mountain):
     * {@code pen.<area>~1} … {@code ~PEN_ALTS}. Only one of them is ever built.
     */
    public static final int PEN_ALTS = 5;
    /** Distances in blocks: node to node, node to a keep-away point, pen to a keep-away point, a node to its area's edge. */
    public static final int SPACING = 40, KEEP = 24, PEN_KEEP = 36, EDGE = 24;
    /** The seed of the spots. Changing it moves every node of a running world. */
    public static final long SEED = 0x5D1DCAFEL;

    private static final int TRIES = 400;

    private GatherPlan() {
    }

    /** What a node is. */
    public enum Kind {
        /** An ore vein: the Төмрийн Дархан. */
        VEIN(Profession.TOMRIIN_DARKHAN),
        /** A herb patch: the Эмийн Бариач. */
        HERB(Profession.EMIIN_BARIACH),
        /** A herder's pen: horses (the Адуучин) and sheep (the Эсгийч). */
        PEN(null);

        private final Profession craft;

        Kind(Profession craft) {
            this.craft = craft;
        }

        /** The craft that gathers here; null for a pen (its animals belong to two). */
        public Profession craft() {
            return craft;
        }
    }

    /**
     * A node: {@code id} is stable ({@code vein.tuul.2}, {@code herb.ongi.0}, {@code pen.orkhon}); {@code x, z} are world
     * block coordinates.
     */
    public record Node(String id, Kind kind, String areaId, int tier, int x, int z, int alt) {

        /** A node at its first spot. */
        public Node(String id, Kind kind, String areaId, int tier, int x, int z) {
            this(id, kind, areaId, tier, x, z, 0);
        }

        public double distanceTo(int px, int pz) {
            return Math.hypot(px - x, pz - z);
        }

        /** The pen this spot belongs to ({@code pen.tuul} for {@code pen.tuul~2}); the id itself otherwise. */
        public String base() {
            int t = id.indexOf('~');
            return t < 0 ? id : id.substring(0, t);
        }
    }

    /** An area's node tier: the highest tier whose level ({@link Profession#tierLevel}) is at most the area's first level. */
    public static int tier(Area a) {
        int t = 1;
        for (int k = 2; k <= Profession.MAX_TIER; k++) if (Profession.tierLevel(k) <= a.minLevel()) t = k;
        return t;
    }

    /**
     * Every node of every area, for a spawn at ({@code spawnX}, {@code spawnZ}) and a world border {@code borderHalf}
     * blocks from it. {@code keepAway} holds {x, z} points nothing may stand near. An area too small (or beyond the
     * border) to hold a node simply has fewer.
     */
    public static List<Node> plan(List<Area> areas, int spawnX, int spawnZ, double borderHalf, List<int[]> keepAway) {
        return plan(areas, spawnX, spawnZ, borderHalf, keepAway, List.of());
    }

    /**
     * As {@link #plan(List, int, int, double, List)}, with {@code keepOut} rectangles {x1, z1, x2, z2} (world blocks,
     * the city's bounds) that no node stands in or within its keep-away distance of.
     */
    public static List<Node> plan(List<Area> areas, int spawnX, int spawnZ, double borderHalf, List<int[]> keepAway,
                                  List<int[]> keepOut) {
        List<Area> sorted = new ArrayList<>(areas);
        sorted.sort(Comparator.comparingInt(Area::index));
        List<Node> out = new ArrayList<>();
        for (Area a : sorted) {
            String key = a.id().startsWith("area.") ? a.id().substring("area.".length()) : a.id();
            SplittableRandom rnd = new SplittableRandom(SEED * 31 + a.id().hashCode());
            int tier = tier(a);
            double rMin = a.minRadius() + EDGE;
            double rMax = Math.min(a.maxRadius(), borderHalf - 64) - EDGE;
            if (rMax <= rMin) continue;
            spot(a, rnd, rMin, rMax, spawnX, spawnZ, keepAway, keepOut, out, PEN_KEEP + PEN_HALF, SPACING + PEN_HALF)
                    .ifPresent(p -> out.add(new Node("pen." + key, Kind.PEN, a.id(), tier, p[0], p[1])));
            for (int i = 0; i < VEINS; i++) {
                final int n = i;
                spot(a, rnd, rMin, rMax, spawnX, spawnZ, keepAway, keepOut, out, KEEP, SPACING)
                        .ifPresent(p -> out.add(new Node("vein." + key + "." + n, Kind.VEIN, a.id(), tier, p[0], p[1])));
            }
            for (int i = 0; i < HERBS; i++) {
                final int n = i;
                spot(a, rnd, rMin, rMax, spawnX, spawnZ, keepAway, keepOut, out, KEEP, SPACING)
                        .ifPresent(p -> out.add(new Node("herb." + key + "." + n, Kind.HERB, a.id(), tier, p[0], p[1])));
            }
            for (int i = 1; i <= PEN_ALTS; i++) {
                final int n = i;
                spot(a, rnd, rMin, rMax, spawnX, spawnZ, keepAway, keepOut, out, PEN_KEEP + PEN_HALF, SPACING + PEN_HALF)
                        .ifPresent(p -> out.add(new Node("pen." + key + "~" + n, Kind.PEN, a.id(), tier, p[0], p[1], n)));
            }
        }
        return List.copyOf(out);
    }

    /**
     * A spot inside the area, clear of the keep-away points and the keep-out rectangles by {@code keep} and of the nodes
     * so far by {@code spacing}.
     */
    private static java.util.Optional<int[]> spot(Area a, SplittableRandom rnd, double rMin, double rMax, int sx, int sz,
                                                  List<int[]> keepAway, List<int[]> keepOut, List<Node> placed, int keep, int spacing) {
        double span = ((a.toDeg() - a.fromDeg()) % 360 + 360) % 360;
        if (span == 0) span = 360;
        for (int t = 0; t < TRIES; t++) {
            // uniform over the ring segment's area: the radius by the square root
            double r = Math.sqrt(rMin * rMin + rnd.nextDouble() * (rMax * rMax - rMin * rMin));
            double margin = Math.toDegrees(EDGE / Math.max(1, r));
            if (span < 360 && span <= 2 * margin) return java.util.Optional.empty();
            double deg = span >= 360 ? rnd.nextDouble() * 360 : a.fromDeg() + margin + rnd.nextDouble() * (span - 2 * margin);
            double b = Math.toRadians(deg);
            int x = sx + (int) Math.round(Math.sin(b) * r);
            int z = sz + (int) Math.round(-Math.cos(b) * r);
            if (near(keepAway, x, z, keep) || within(keepOut, x, z, keep)) continue;
            boolean crowded = false;
            for (Node n : placed) {
                double need = n.kind() == Kind.PEN ? spacing + PEN_HALF : spacing;
                if (n.distanceTo(x, z) < need) {
                    crowded = true;
                    break;
                }
            }
            if (crowded) continue;
            return java.util.Optional.of(new int[]{x, z});
        }
        return java.util.Optional.empty();
    }

    private static boolean near(List<int[]> points, int x, int z, int d) {
        for (int[] p : points) if (Math.hypot(p[0] - x, p[1] - z) < d) return true;
        return false;
    }

    /** Whether (x, z) lies in one of the rectangles {x1, z1, x2, z2} or within {@code d} blocks of it. */
    static boolean within(List<int[]> rects, int x, int z, int d) {
        for (int[] r : rects) {
            if (x >= Math.min(r[0], r[2]) - d && x <= Math.max(r[0], r[2]) + d && z >= Math.min(r[1], r[3]) - d && z <= Math.max(r[1], r[3]) + d) return true;
        }
        return false;
    }
}
