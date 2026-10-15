package mn.suld.api.map;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The waypoint markers' camera: world → screen and back, the edge of the screen, the distance (docs/WORLD_MAP.md). */
class ScreenProjectionTest {

    private static final double EPS = 1e-9;

    private static ScreenProjection.Camera cam(float yaw, float pitch) {
        return ScreenProjection.Camera.standard(0, 64, 0, yaw, pitch);
    }

    @Test
    void aPointStraightAheadIsOnTheCrosshair() {
        // yaw 0 looks south (+z)
        ScreenProjection.Projection p = ScreenProjection.project(cam(0, 0), 0, 64, 20);
        assertEquals(0, p.sx(), EPS);
        assertEquals(0, p.sy(), EPS);
        assertEquals(20, p.depth(), EPS);
        assertTrue(p.onScreen());
        assertFalse(p.behind());
        // yaw 90 looks west (−x), 180 north (−z), 270 east (+x)
        assertEquals(0, ScreenProjection.project(cam(90, 0), -30, 64, 0).sx(), EPS);
        assertEquals(30, ScreenProjection.project(cam(90, 0), -30, 64, 0).depth(), EPS);
        assertEquals(30, ScreenProjection.project(cam(180, 0), 0, 64, -30).depth(), EPS);
        assertEquals(30, ScreenProjection.project(cam(270, 0), 30, 64, 0).depth(), EPS);
    }

    @Test
    void leftRightAndUpDownFollowMinecraftsAxes() {
        // facing south, east (+x) is on the left and west on the right
        assertTrue(ScreenProjection.project(cam(0, 0), 5, 64, 20).sx() < 0, "east is left when facing south");
        assertTrue(ScreenProjection.project(cam(0, 0), -5, 64, 20).sx() > 0, "west is right when facing south");
        // facing west, north (−z) is on the right
        assertTrue(ScreenProjection.project(cam(90, 0), -20, 64, -5).sx() > 0, "north is right when facing west");
        // higher is up; looking down (positive pitch) lifts a level point up the screen
        assertTrue(ScreenProjection.project(cam(0, 0), 0, 70, 20).sy() > 0);
        assertTrue(ScreenProjection.project(cam(0, 30), 0, 64, 20).sy() > 0, "looking down: the horizon is above the crosshair");
        assertTrue(ScreenProjection.project(cam(0, -30), 0, 64, 20).sy() < 0, "looking up: the horizon is below");
    }

    @Test
    void theFieldOfViewAndTheWindowShapeSetTheEdges() {
        double half = Math.toRadians(ScreenProjection.DEFAULT_FOV / 2);
        // a point exactly half the vertical field of view up sits on the top edge
        ScreenProjection.Projection top = ScreenProjection.project(cam(0, 0), 0, 64 + Math.tan(half) * 10, 10);
        assertEquals(1, top.sy(), 1e-9);
        // the horizontal edge is aspect × wider (in tangent)
        double tanH = Math.tan(half) * ScreenProjection.DEFAULT_ASPECT;
        ScreenProjection.Projection right = ScreenProjection.project(cam(0, 0), -(tanH * 10 - 1e-9), 64, 10);
        assertEquals(1, right.sx(), 1e-8);
        assertTrue(right.onScreen());
        assertFalse(ScreenProjection.project(cam(0, 0), -tanH * 10 - 0.1, 64, 10).onScreen(), "just past the right edge");
        // a wider window (or field of view) pulls the same point toward the middle
        ScreenProjection.Camera wide = new ScreenProjection.Camera(0, 64, 0, 0, 0, 70, 21.0 / 9.0);
        ScreenProjection.Camera fov90 = new ScreenProjection.Camera(0, 64, 0, 0, 0, 90, 16.0 / 9.0);
        double sx = ScreenProjection.project(cam(0, 0), -8, 64, 10).sx();
        assertTrue(ScreenProjection.project(wide, -8, 64, 10).sx() < sx);
        assertTrue(ScreenProjection.project(fov90, -8, 64, 10).sx() < sx);
    }

    @Test
    void aPointBehindTheCameraIsNeverOnScreen() {
        ScreenProjection.Projection p = ScreenProjection.project(cam(0, 0), 0, 64, -20);
        assertTrue(p.behind());
        assertFalse(p.onScreen());
        assertFalse(p.inside(0));
        assertTrue(p.depth() < 0);
        // a point level with the eye, dead beside it, is not in front either
        assertTrue(ScreenProjection.project(cam(0, 0), 10, 64, 0).behind());
    }

    @Test
    void unprojectingAScreenPointAndProjectingItBackMeetsExactly() {
        Random r = new Random(7);
        for (int i = 0; i < 500; i++) {
            ScreenProjection.Camera c = new ScreenProjection.Camera(r.nextDouble() * 200 - 100, 40 + r.nextDouble() * 80, r.nextDouble() * 200 - 100,
                    (float) (r.nextDouble() * 720 - 360), (float) (r.nextDouble() * 178 - 89), 50 + r.nextDouble() * 60, 1 + r.nextDouble());
            double sx = r.nextDouble() * 2 - 1, sy = r.nextDouble() * 2 - 1, d = 1 + r.nextDouble() * 40;
            double[] w = ScreenProjection.unproject(c, sx, sy, d);
            ScreenProjection.Projection back = ScreenProjection.project(c, w[0], w[1], w[2]);
            assertEquals(sx, back.sx(), 1e-7, "sx at " + i);
            assertEquals(sy, back.sy(), 1e-7, "sy at " + i);
            assertEquals(d, ScreenProjection.distance(c.x(), c.y(), c.z(), w[0], w[1], w[2]), 1e-7, "distance at " + i);
        }
    }

    @Test
    void anOffScreenPointGoesToTheEdgeOnTheLineFromTheCrosshair() {
        ScreenProjection.Camera c = cam(0, 0);
        ScreenProjection.Bounds b = ScreenProjection.Bounds.inset(0.15);
        // far to the right and a little up
        ScreenProjection.Projection p = ScreenProjection.project(c, -40, 70, 10);
        assertFalse(p.onScreen());
        double[] e = ScreenProjection.edge(c, p, b);
        assertEquals(b.right(), e[0], EPS, "on the right edge");
        assertEquals(p.sy() / p.sx(), e[1] / e[0], 1e-9, "on the line toward the point");
        assertTrue(e[2] > 0 && e[2] < Math.PI / 2, "the arrow points right and up");
        // straight up: the top edge, arrow up
        double[] up = ScreenProjection.edge(c, ScreenProjection.project(c, 0, 200, 10), b);
        assertEquals(b.top(), up[1], EPS);
        assertEquals(0, up[0], EPS);
        assertEquals(0, up[2], EPS);
        // a point already inside stays where it is
        ScreenProjection.Projection in = ScreenProjection.project(c, -1, 65, 20);
        double[] stay = ScreenProjection.edge(c, in, b);
        assertEquals(in.sx(), stay[0], EPS);
        assertEquals(in.sy(), stay[1], EPS);
    }

    @Test
    void aPointBehindGoesToTheSideToTurnTo() {
        ScreenProjection.Camera c = cam(0, 0); // facing south: west (−x) is on the right
        ScreenProjection.Bounds b = ScreenProjection.Bounds.inset(0.15);
        double[] backRight = ScreenProjection.edge(c, ScreenProjection.project(c, -10, 64, -30), b);
        assertEquals(b.right(), backRight[0], EPS, "behind and to the right: turn right");
        assertEquals(Math.PI / 2, backRight[2], 1e-9, "the arrow says turn right");
        double[] backLeft = ScreenProjection.edge(c, ScreenProjection.project(c, 10, 64, -30), b);
        assertEquals(b.left(), backLeft[0], EPS, "behind and to the left: turn left");
        assertEquals(-Math.PI / 2, backLeft[2], 1e-9);
        // dead behind: the right edge, at the crosshair's height
        double[] dead = ScreenProjection.edge(c, ScreenProjection.project(c, 0, 64, -30), b);
        assertEquals(b.right(), dead[0], EPS);
        assertEquals(0, dead[1], EPS);
        // behind and far below: low on the edge, never past it
        double[] low = ScreenProjection.edge(c, ScreenProjection.project(c, -5, 0, -10), b);
        assertEquals(b.bottom(), low[1], EPS);
        assertTrue(low[2] > Math.PI / 2, "the arrow leans down");
    }

    @Test
    void edgeBoundsMustSurroundTheCrosshair() {
        assertThrows(IllegalArgumentException.class, () -> new ScreenProjection.Bounds(0.1, 0.9, -0.5, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new ScreenProjection.Camera(0, 0, 0, 0, 0, 0, 1.7));
        assertThrows(IllegalArgumentException.class, () -> new ScreenProjection.Camera(0, 0, 0, 0, 0, 70, 0));
        assertTrue(ScreenProjection.Bounds.inset(0.1).contains(0.9, -0.9));
        assertFalse(ScreenProjection.Bounds.inset(0.1).contains(0.91, 0));
    }

    @Test
    void theDistanceIsStraightUnlessTheHeightIsUnknown() {
        assertEquals(5, ScreenProjection.distance(0, 0, 0, 3, 0, 4), EPS);
        assertEquals(Math.sqrt(9 + 144 + 16), ScreenProjection.distance(0, 0, 0, 3, 12, 4), EPS);
        assertEquals(5, ScreenProjection.distance(0, 70, 0, 3, Double.NaN, 4), EPS, "a place picked on the map: across the ground");
        // the reference card: «28.1 м»
        assertEquals("28.1 м", WaypointHud.meters(ScreenProjection.distance(0, 64, 0, 20, 70, 18.9)));
    }
}
