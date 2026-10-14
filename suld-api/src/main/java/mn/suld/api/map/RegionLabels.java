package mn.suld.api.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Where the atlas writes the regions' names on its widest views (docs/WORLD_MAP.md), as an atlas names its countries.
 * The plugin's atlas and the preview tool ({@code tools/map/atlas_art.py}) follow the same rule:
 * <ul>
 *   <li>A name stands at the middle of its region's ring sector: half way out the ring (as far as the world goes) and
 *   half way round its arc.</li>
 *   <li>A name of several words that would be wider than {@link #ONE_LINE} letters is written in two lines, broken at
 *   the space nearest its middle.</li>
 *   <li>It stays wholly on the wall: a name by the wall's edge is moved in.</li>
 *   <li>When its middle is taken (another region's name, a place's marker or label), it tries other spots of its
 *   sector, nearest the middle first, and keeps the first free one. With none free it takes the spot that covers
 *   the least of the places and none of another region's name, if there is one; else the least covered spot.</li>
 * </ul>
 * Panel units are {@link AtlasView}'s: one frame is one unit, {@code u} to the right, {@code v} up.
 */
public final class RegionLabels {

    /** Longer than this many letters, a name of several words is written in two lines. */
    public static final int ONE_LINE = 12;
    /** The room kept between a name and the wall's edge, and between two names (panel units). */
    public static final double MARGIN = 0.04;

    /** Where on its ring (0 inner, 1 outer) and its arc (0 first, 1 last) a name may stand, best first. */
    private static final double[] RING = {0.5, 0.38, 0.62, 0.26, 0.74, 0.16, 0.84};
    private static final double[] ARC = {0.5, 0.38, 0.62, 0.26, 0.74, 0.16, 0.84};

    private RegionLabels() {
    }

    /**
     * A region to name: its name as written (capitals) and its ring sector around the spawn: radii in blocks, degrees
     * clockwise from north ({@code toDeg} may be smaller than {@code fromDeg}: the sector then crosses north).
     */
    public record Region(String name, double minRadius, double maxRadius, double fromDeg, double toDeg) {
    }

    /** A box on the wall, in panel units. */
    public record Box(double u0, double v0, double u1, double v1) {

        public Box {
            if (u0 > u1 || v0 > v1) throw new IllegalArgumentException("box " + u0 + "," + v0 + " " + u1 + "," + v1);
        }

        /** A box of half sizes {@code hw}, {@code hh} around ({@code u}, {@code v}). */
        public static Box around(double u, double v, double hw, double hh) {
            return new Box(u - hw, v - hh, u + hw, v + hh);
        }

        public boolean hits(Box o) {
            return u0 < o.u1 && o.u0 < u1 && v0 < o.v1 && o.v0 < v1;
        }

        /** The area this box shares with {@code o}. */
        public double overlap(Box o) {
            double w = Math.min(u1, o.u1) - Math.max(u0, o.u0), h = Math.min(v1, o.v1) - Math.max(v0, o.v0);
            return w > 0 && h > 0 ? w * h : 0;
        }

        Box grown(double d) {
            return new Box(u0 - d, v0 - d, u1 + d, v1 + d);
        }
    }

    /**
     * The size of the names' letters on the wall: a capital's mean width and a line's height, in panel units (the
     * plugin's: {@code 6.5} and {@code 10} font pixels at the text's scale, 1/40 block a pixel).
     */
    public record Metrics(double charWidth, double lineHeight) {
    }

    /** A placed name: its lines (one or two), the middle of its text on the wall, and the box it covers. */
    public record Label(String name, List<String> lines, double u, double v, Box box) {

        /** The text as the atlas writes it: the lines joined by a line break. */
        public String text() {
            return String.join("\n", lines);
        }
    }

    /** The lines a name is written in: one, or two broken at the space nearest its middle when it is long. */
    public static List<String> lines(String name) {
        String n = name.trim();
        if (n.length() <= ONE_LINE || n.indexOf(' ') < 0) return List.of(n);
        int best = -1;
        for (int i = n.indexOf(' '); i >= 0; i = n.indexOf(' ', i + 1)) {
            if (best < 0 || Math.abs(i - n.length() / 2.0) < Math.abs(best - n.length() / 2.0)) best = i;
        }
        return List.of(n.substring(0, best).trim(), n.substring(best + 1).trim());
    }

    /**
     * The names of {@code regions} on {@code view}, the spawn at ({@code spawnX}, {@code spawnZ}) and the world's edge
     * {@code worldEdge} blocks from it. {@code taken} holds the places' markers and labels already on the wall. A region
     * whose whole sector lies off the wall is not named. In the order of {@code regions}.
     */
    public static List<Label> place(List<Region> regions, AtlasView view, double spawnX, double spawnZ, double worldEdge,
                                    Metrics m, List<Box> taken) {
        List<Label> out = new ArrayList<>();
        double halfW = view.cols() / 2.0 - MARGIN, halfH = view.rows() / 2.0 - MARGIN;
        for (Region r : regions) {
            List<String> lines = lines(r.name().toUpperCase(Locale.ROOT));
            int longest = lines.stream().mapToInt(String::length).max().orElse(1);
            double hw = longest * m.charWidth() / 2, hh = lines.size() * m.lineHeight() / 2;
            double from = norm(r.fromDeg()), to = norm(r.toDeg());
            double span = from <= to ? to - from : to + 360 - from;
            if (span == 0) span = 360;
            double inner = r.minRadius(), outer = Math.min(r.maxRadius(), worldEdge);
            if (outer <= inner) continue;
            List<double[]> spots = new ArrayList<>(); // {u, v, rank}
            for (int i = 0; i < RING.length; i++) {
                for (int j = 0; j < ARC.length; j++) {
                    double rr = inner + RING[i] * (outer - inner);
                    double a = Math.toRadians(from + ARC[j] * span);
                    double u = view.u(spawnX + Math.sin(a) * rr), v = view.v(spawnZ - Math.cos(a) * rr);
                    if (!view.onWall(u, v)) continue;
                    // moved in from the wall's edge so the whole name is on it
                    u = Math.max(-halfW + hw, Math.min(halfW - hw, u));
                    v = Math.max(-halfH + hh, Math.min(halfH - hh, v));
                    spots.add(new double[]{u, v, Math.hypot(RING[i] - 0.5, ARC[j] - 0.5) + 1e-3 * (i + j)});
                }
            }
            if (spots.isEmpty()) continue;
            spots.sort(Comparator.comparingDouble(s -> s[2]));
            double[] best = null;
            double bestCover = Double.MAX_VALUE;
            boolean bestClear = false;
            for (double[] s : spots) {
                Box b = Box.around(s[0], s[1], hw, hh);
                boolean clearOfNames = out.stream().noneMatch(l -> l.box().grown(MARGIN).hits(b));
                double cover = 0;
                for (Box t : taken) cover += t.overlap(b);
                if (clearOfNames && cover == 0) {
                    best = s;
                    break;
                }
                // with no free spot: the least covered one clear of the other names, else the least covered one
                if (best == null || (clearOfNames && !bestClear) || (clearOfNames == bestClear && cover < bestCover)) {
                    best = s;
                    bestCover = cover;
                    bestClear = clearOfNames;
                }
            }
            out.add(new Label(r.name(), lines, best[0], best[1], Box.around(best[0], best[1], hw, hh)));
        }
        return out;
    }

    private static double norm(double deg) {
        return (deg % 360 + 360) % 360;
    }
}
