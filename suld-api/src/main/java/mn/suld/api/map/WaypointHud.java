package mn.suld.api.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The waypoint markers on the game screen (docs/WORLD_MAP.md), the part that needs no server: which marks show, how a
 * card reads («A · Агуй» over «28.1 м»), where an off-screen mark's edge card goes (off the corner minimap, the boss
 * bars and the hotbar, never on top of another edge card) and which arrow it carries.
 * <p>
 * A mark in view is put in the world on the line from the eye to it, so the client's own camera draws it at the right
 * place every frame; only an edge card depends on the look the server knows ({@link ScreenProjection#edge}).
 */
public final class WaypointHud {

    /** Pins and the latest death shown at once besides the waypoint: the nearest. */
    public static final int MAX_MARKS = 6;
    /** Farther than this (blocks), a pin is not shown on screen (the waypoint always is). */
    public static final double RANGE = 1500;
    /**
     * Where edge cards may sit: 15 % in from the left and right edges; under the boss bars at the top (three bars at GUI
     * scale 3 on 1080p reach y = 0.75); over the hotbar, the hearts and the HUD panel at the bottom.
     */
    public static final ScreenProjection.Bounds EDGE = new ScreenProjection.Bounds(-1 + ScreenProjection.EDGE_INSET,
            1 - ScreenProjection.EDGE_INSET, -0.6, 0.7);
    /**
     * The corner minimap (the pack's shader: 128 map pixels at 1, 2, 3 or 4 screen pixels each, 3 pixels in from the
     * corner) covers x &lt; −0.67 and y &gt; 0.42 on every 16:9 window of 900 px or more; an edge card keeps out of a
     * slightly larger corner.
     */
    public static final double MINIMAP_RIGHT = -0.6, MINIMAP_BOTTOM = 0.36;
    /** Edge cards on the left or right edge keep this far apart up and down (screen units: a card line and a half). */
    public static final double EDGE_GAP = 0.08;
    /** Edge cards on the top or bottom edge keep this far apart side to side (a card with the waypoint's name is about this wide). */
    public static final double EDGE_GAP_X = 0.3;

    /** Arrows clockwise from straight up. */
    private static final String[] ARROWS = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};

    private WaypointHud() {
    }

    /** What a mark is: the waypoint the player follows, one of their pins, or where they last fell. */
    public enum Kind { WAYPOINT, PIN, DEATH }

    /**
     * A mark: {@code key} names it across ticks (a pin's id, «waypoint», «death»), {@code letter} the glyph on its card,
     * {@code colour} its card's colour as 0xRRGGBB; {@code y} NaN when its height is unknown (a place picked on the map).
     */
    public record Mark(String key, Kind kind, String letter, String name, int colour, double x, double y, double z) {
    }

    /** In view and within this many blocks, a mark's card stands at the place itself; farther, this far out toward it. */
    public static final double NEAR = 24;
    /** A card's size a block of distance from the eye, so it reads the same on screen (3× at {@link #NEAR}: a 20 px line at 1080p). */
    public static final double CARD_SCALE = 0.125;
    /** Nearer than 8 blocks the card stops shrinking (it would read smaller than the HUD text). */
    public static final double MIN_SCALE = 1.0;
    /** How high over the place a card floats (over a standing player's head). */
    public static final double LIFT = 2.2;
    /** Edge cards are put this far from the eye along the screen point's ray, at {@link #EDGE_SCALE} a block. */
    public static final double EDGE_DIST = 10, EDGE_SCALE = 0.1;
    /**
     * A mark switches to its place in view once it is this far inside the screen, and back to an edge card only once it
     * is out past {@link #VIEW_OUT}: on the line it flickered between the two every tick.
     */
    public static final ScreenProjection.Bounds VIEW_IN = ScreenProjection.Bounds.inset(0.12), VIEW_OUT = ScreenProjection.Bounds.inset(0.03);

    /**
     * Where a mark's card goes this tick: {@code inView} at its place (or {@link #NEAR} blocks toward it) for the
     * client's camera to draw, or an edge card; (x, y, z) the world spot, {@code scale} the card's size,
     * {@code distance} from the player's feet in blocks, {@code arrow} the edge card's arrow (null in view), (sx, sy)
     * where the server expects it on screen.
     */
    public record Placement(Mark mark, boolean inView, double x, double y, double z, double scale, double distance, String arrow,
                            double sx, double sy) {
    }

    /**
     * Lays out the marks for one tick. {@code cam} is the player's eye and look, (px, py, pz) their feet; {@code marks}
     * in priority order ({@link #choose}); a mark whose height is unknown (NaN: a place in an unloaded chunk) floats at
     * the player's own height and is measured across the ground. {@code wasInView} holds the marks in view last tick, for the {@link #VIEW_IN}/{@link #VIEW_OUT}
     * hysteresis.
     */
    public static List<Placement> layout(ScreenProjection.Camera cam, double px, double py, double pz, List<Mark> marks,
                                         java.util.Set<String> wasInView) {
        List<Placement> out = new ArrayList<>();
        List<double[]> cards = new ArrayList<>();
        List<Integer> cardOf = new ArrayList<>();
        for (Mark m : marks) {
            double distance = ScreenProjection.distance(px, py, pz, m.x(), m.y(), m.z());
            double ground = Double.isNaN(m.y()) ? py : m.y();
            double ax = m.x(), ay = ground + LIFT, az = m.z();
            ScreenProjection.Projection p = ScreenProjection.project(cam, ax, ay, az);
            ScreenProjection.Bounds keep = wasInView.contains(m.key()) ? VIEW_OUT : VIEW_IN;
            if (!p.behind() && keep.contains(p.sx(), p.sy())) {
                double vx = ax - cam.x(), vy = ay - cam.y(), vz = az - cam.z();
                double d = Math.sqrt(vx * vx + vy * vy + vz * vz);
                double at = Math.min(d, NEAR), k = d < 1e-9 ? 0 : at / d;
                out.add(new Placement(m, true, cam.x() + vx * k, cam.y() + vy * k, cam.z() + vz * k, Math.max(MIN_SCALE, at * CARD_SCALE),
                        distance, null, p.sx(), p.sy()));
                continue;
            }
            double[] e = ScreenProjection.edge(cam, p, EDGE);
            double[] off = offMinimap(e[0], e[1]);
            cards.add(new double[]{off[0], off[1], e[2]});
            cardOf.add(out.size());
            out.add(new Placement(m, false, 0, 0, 0, EDGE_DIST * EDGE_SCALE, distance, arrow(e[2]), off[0], off[1]));
        }
        declutter(cards);
        for (int i = 0; i < cards.size(); i++) {
            double[] c = cards.get(i);
            Placement q = out.get(cardOf.get(i));
            double[] w = ScreenProjection.unproject(cam, c[0], c[1], EDGE_DIST);
            out.set(cardOf.get(i), new Placement(q.mark(), false, w[0], w[1], w[2], q.scale(), q.distance(), q.arrow(), c[0], c[1]));
        }
        return out;
    }

    /**
     * The marks to show for a player at (px, py, pz), in drawing order: the waypoint first, whatever its distance, then
     * the other marks within {@link #RANGE}, nearest first, at most {@code max} of them. A pin or a death the waypoint
     * already leads to ({@code sameAs}) is not shown twice.
     */
    public static List<Mark> choose(List<Mark> all, double px, double py, double pz, int max, String sameAs) {
        List<Mark> out = new ArrayList<>();
        List<Mark> rest = new ArrayList<>();
        for (Mark m : all) {
            if (m.kind() == Kind.WAYPOINT) out.add(m);
            else if (sameAs == null || !sameAs.equals(m.key())) rest.add(m);
        }
        if (out.size() > 1) out.subList(1, out.size()).clear();
        rest.removeIf(m -> ScreenProjection.distance(px, py, pz, m.x(), m.y(), m.z()) > RANGE);
        rest.sort(Comparator.comparingDouble(m -> ScreenProjection.distance(px, py, pz, m.x(), m.y(), m.z())));
        for (int i = 0; i < Math.min(max, rest.size()); i++) out.add(rest.get(i));
        return out;
    }

    /** A distance as the card shows it: «8.0 м» and «28.1 м» to the tenth under 100, «412 м» under 10 km, «12.4 км» past. */
    public static String meters(double d) {
        if (!(d >= 0) || Double.isInfinite(d)) return "? м";
        if (d < 100) return String.format(Locale.ROOT, "%.1f м", Math.floor(d * 10 + 1e-6) / 10); // cut, not rounded: never «100.0 м»
        if (d < 10_000) return Math.round(d) + " м";
        return String.format(Locale.ROOT, "%.1f км", d / 1000);
    }

    /** The letter on a pin's card: its name's first letter or digit, upper-case («агуй» → «А»); «◆» when there is none. */
    public static String initial(String name) {
        if (name != null) {
            for (int i = 0; i < name.length(); ) {
                int cp = name.codePointAt(i);
                if (Character.isLetterOrDigit(cp)) return new String(Character.toChars(Character.toUpperCase(cp)));
                i += Character.charCount(cp);
            }
        }
        return "◆";
    }

    /** A pin colour ({@link MapPin#COLOURS}) as 0xRRGGBB for its card. */
    public static int colourOf(String pinColour) {
        return switch (pinColour == null ? "" : pinColour) {
            case "green" -> 0x55D46A;
            case "red" -> 0xE0524A;
            case "blue" -> 0x4F9BEA;
            case "white" -> 0xF2F2F2;
            default -> 0xECBE4C; // gold
        };
    }

    /** The arrow for a turn of {@code angle} radians clockwise from straight up (one of eight). */
    public static String arrow(double angle) {
        double turns = angle / (2 * Math.PI);
        int i = (int) Math.round((turns - Math.floor(turns)) * 8) % 8;
        return ARROWS[i];
    }

    /**
     * An edge card at (sx, sy) moved out of the corner minimap: down along the left edge, or right along the top. {sx, sy}.
     */
    public static double[] offMinimap(double sx, double sy) {
        if (sx >= MINIMAP_RIGHT || sy <= MINIMAP_BOTTOM) return new double[]{sx, sy};
        // closer to the left edge than to the top: slide down; else slide right
        double toLeft = sx - EDGE.left(), toTop = EDGE.top() - sy;
        return toLeft <= toTop ? new double[]{sx, MINIMAP_BOTTOM} : new double[]{MINIMAP_RIGHT, sy};
    }

    /**
     * Edge cards that would overlap pushed apart along their edge: {@code cards} are {sx, sy} positions on the edge of
     * {@link #EDGE} (as {@link ScreenProjection#edge} and {@link #offMinimap} give them), changed in place. Cards on the
     * left or right edge keep {@link #EDGE_GAP} apart up and down, cards on the top or bottom edge {@link #EDGE_GAP_X}
     * apart side to side (a card is much wider than tall). No card comes nearer a corner than its gap, so cards on two
     * edges never meet there either. A top or bottom edge with no room left sends its card to the end of the left or
     * right edge on its side, which hold twice as many. Order in the list is priority: an earlier card (the waypoint)
     * keeps its place where it can, the others take the free spot nearest where they point.
     */
    public static void declutter(List<double[]> cards) {
        List<double[]> left = new ArrayList<>(), right = new ArrayList<>(), top = new ArrayList<>(), bottom = new ArrayList<>();
        for (double[] c : cards) {
            switch (edgeOf(c)) {
                case 0 -> left.add(c);
                case 1 -> right.add(c);
                case 2 -> top.add(c);
                case 3 -> bottom.add(c);
                default -> { } // inside: not an edge card
            }
        }
        double xLo = EDGE.left() + EDGE_GAP_X, xHi = EDGE.right() - EDGE_GAP_X;
        double yLo = EDGE.bottom() + EDGE_GAP, yHi = EDGE.top() - EDGE_GAP;
        for (double[] c : spread(top, 0, EDGE_GAP_X, Math.max(xLo, MINIMAP_RIGHT), xHi)) {
            c[1] = yHi;
            (c[0] < 0 ? left : right).add(c);
            c[0] = c[0] < 0 ? EDGE.left() : EDGE.right();
        }
        for (double[] c : spread(bottom, 0, EDGE_GAP_X, xLo, xHi)) {
            c[1] = yLo;
            (c[0] < 0 ? left : right).add(c);
            c[0] = c[0] < 0 ? EDGE.left() : EDGE.right();
        }
        spread(right, 1, EDGE_GAP, yLo, yHi);
        spread(left, 1, EDGE_GAP, yLo, Math.min(yHi, MINIMAP_BOTTOM));
    }

    /**
     * Places the cards along one axis in [lo, hi], each at the free spot nearest where it wants to be, at least
     * {@code gap} from those placed before it. Returns the cards with no room left (they keep their place in the list).
     */
    private static List<double[]> spread(List<double[]> on, int axis, double gap, double lo, double hi) {
        List<double[]> left = new ArrayList<>();
        List<Double> placed = new ArrayList<>();
        for (double[] c : on) {
            double want = Math.max(lo, Math.min(hi, c[axis]));
            double at = nearestFree(want, placed, gap, lo, hi);
            if (Double.isNaN(at)) {
                left.add(c);
                continue;
            }
            c[axis] = at;
            placed.add(at);
        }
        return left;
    }

    /** The spot nearest {@code want} in [lo, hi] at least {@code gap} from every placed spot; NaN if there is none. */
    private static double nearestFree(double want, List<Double> placed, double gap, double lo, double hi) {
        List<Double> candidates = new ArrayList<>();
        candidates.add(want);
        candidates.add(lo);
        candidates.add(hi);
        for (double p : placed) {
            candidates.add(p + gap);
            candidates.add(p - gap);
        }
        double best = Double.NaN, bestCost = Double.MAX_VALUE;
        for (double c : candidates) {
            if (c < lo - 1e-9 || c > hi + 1e-9) continue;
            boolean free = true;
            for (double p : placed) if (Math.abs(c - p) < gap - 1e-9) free = false;
            if (free && Math.abs(c - want) < bestCost) {
                best = c;
                bestCost = Math.abs(c - want);
            }
        }
        return best;
    }

    /** Which edge of {@link #EDGE} a card is on: 0 left, 1 right, 2 top, 3 bottom; −1 inside. */
    static int edgeOf(double[] c) {
        double e = 1e-6;
        if (c[0] <= EDGE.left() + e) return 0;
        if (c[0] >= EDGE.right() - e) return 1;
        if (c[1] >= EDGE.top() - e) return 2;
        if (c[1] <= EDGE.bottom() + e) return 3;
        return -1;
    }
}
