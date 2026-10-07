package mn.suld.api.skill.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * Where each node of a tree sits on the full-screen skill tree (docs/SKILL_SKY.md): a radial "sky" layout in the style
 * of a constellation map. The root is the centre; the class's grid columns fan out over the upper half like the
 * branches of a tree (column 2 straight up, the outer columns towards the horizon) and each grid row is one ring
 * further out; the universal nodes hang below the root in two short arcs. Units are node spacings; the plugin
 * multiplies by its spacing in blocks.
 */
public final class SkyLayout {

    /** Grid column offset to angle: 40 degrees per column, so 5 columns span 10..170 degrees. */
    static final double COLUMN_DEG = 40;
    /** Ring radius: the first ring sits clear of the root, each next row one spacing further. */
    static final double RING0 = 1.2;
    static final double RING = 2.2;
    /** Satellites sit this far from their hub, fanned over the outward side. */
    static final double SAT = 0.9;
    static final double SAT_FAN_DEG = 50;

    public record Pos(double u, double v) {
        public double distance(Pos o) {
            return Math.hypot(u - o.u, v - o.v);
        }
    }

    private final List<Pos> pos;

    private SkyLayout(List<Pos> pos) {
        this.pos = pos;
    }

    public Pos of(SkillNode n) {
        return pos.get(n.index());
    }

    public List<Pos> all() {
        return pos;
    }

    /** Extent of the layout (max |u|, max |v|) in spacing units. */
    public double[] extent() {
        double mu = 0, mv = 0;
        for (Pos p : pos) {
            mu = Math.max(mu, Math.abs(p.u()));
            mv = Math.max(mv, Math.abs(p.v()));
        }
        return new double[]{mu, mv};
    }

    public static SkyLayout of(SkillTree tree) {
        List<Pos> out = new ArrayList<>();
        int rootX = tree.root().x();
        for (SkillNode n : tree.nodes()) {
            if (n.root() || n.satellite()) {
                out.add(new Pos(0, 0)); // satellites are placed below, once their hub is known
            } else if (n.tags().contains("universal")) {
                out.add(universal(n.x() - rootX));
            } else {
                out.add(branch(n.x() - rootX, n.y()));
            }
        }
        // satellites: fanned around the outward direction of their hub (k of m: -fan .. +fan)
        java.util.Map<String, List<SkillNode>> orbits = new java.util.LinkedHashMap<>();
        for (SkillNode n : tree.nodes()) if (n.satellite()) orbits.computeIfAbsent(n.orbit(), k -> new ArrayList<>()).add(n);
        for (java.util.Map.Entry<String, List<SkillNode>> e : orbits.entrySet()) {
            Pos hub = out.get(tree.node(e.getKey()).index());
            double base = hub.u() == 0 && hub.v() == 0 ? Math.PI / 2 : Math.atan2(hub.v(), hub.u());
            List<SkillNode> sats = e.getValue();
            for (int k = 0; k < sats.size(); k++) {
                double off = sats.size() == 1 ? 0 : -SAT_FAN_DEG + 2 * SAT_FAN_DEG * k / (sats.size() - 1);
                double a = base + Math.toRadians(off);
                out.set(sats.get(k).index(), new Pos(hub.u() + Math.cos(a) * SAT, hub.v() + Math.sin(a) * SAT));
            }
        }
        return new SkyLayout(List.copyOf(out));
    }

    /** A class node: column offset c (-2..2) is the branch direction, row y (1..) the ring. */
    static Pos branch(int c, int y) {
        // outer rings bend slightly towards the centre line so a branch curves like a bough instead of a straight spoke
        double deg = 90 - c * COLUMN_DEG * (1 - 0.025 * Math.max(0, y - 1));
        double r = RING0 + y * RING;
        double a = Math.toRadians(deg);
        return new Pos(Math.cos(a) * r, Math.sin(a) * r);
    }

    /** A universal node d steps left (d<0) or right (d>0) of the root: two arcs hanging below it. */
    static Pos universal(int d) {
        int k = Math.abs(d);
        double deg = d < 0 ? 235 + (k - 1) * 14 : 305 - (k - 1) * 14;
        double r = RING0 + k * RING;
        double a = Math.toRadians(deg);
        return new Pos(Math.cos(a) * r, Math.sin(a) * r);
    }
}
