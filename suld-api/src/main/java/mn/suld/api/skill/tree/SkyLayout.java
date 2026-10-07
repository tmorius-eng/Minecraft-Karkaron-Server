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
    static final double RING0 = 0.9;
    static final double RING = 1.05;

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
            if (n.root()) {
                out.add(new Pos(0, 0));
            } else if (n.tags().contains("universal")) {
                out.add(universal(n.x() - rootX));
            } else {
                out.add(branch(n.x() - rootX, n.y()));
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
