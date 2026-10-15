package mn.suld.api.map;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The waypoint cards on the game screen: which show, how they read, where they go (docs/WORLD_MAP.md). */
class WaypointHudTest {

    private static final double EPS = 1e-9;

    private static WaypointHud.Mark pin(String key, String name, double x, double y, double z) {
        return new WaypointHud.Mark(key, WaypointHud.Kind.PIN, WaypointHud.initial(name), name, WaypointHud.colourOf("green"), x, y, z);
    }

    private static WaypointHud.Mark waypoint(double x, double y, double z) {
        return new WaypointHud.Mark("waypoint", WaypointHud.Kind.WAYPOINT, "◆", "Хархорум", 0xECBE4C, x, y, z);
    }

    // ------------------------------------------------------------------------------------------------ text

    @Test
    void distancesReadLikeTheReferenceCard() {
        assertEquals("28.1 м", WaypointHud.meters(28.14));
        assertEquals("8.0 м", WaypointHud.meters(8));
        assertEquals("0.0 м", WaypointHud.meters(0.04));
        assertEquals("99.9 м", WaypointHud.meters(99.96), "never «100.0 м» under 100");
        assertEquals("100 м", WaypointHud.meters(100));
        assertEquals("412 м", WaypointHud.meters(412.4));
        assertEquals("9999 м", WaypointHud.meters(9999.4));
        assertEquals("12.4 км", WaypointHud.meters(12_400));
        assertEquals("? м", WaypointHud.meters(Double.NaN));
        assertEquals("? м", WaypointHud.meters(-1));
        assertEquals("? м", WaypointHud.meters(Double.POSITIVE_INFINITY));
    }

    @Test
    void aPinsLetterIsItsNamesFirstLetter() {
        assertEquals("А", WaypointHud.initial("агуй"), "Cyrillic, upper-cased");
        assertEquals("C", WaypointHud.initial("  cave"));
        assertEquals("Т", WaypointHud.initial("Тэмдэг 3"));
        assertEquals("1", WaypointHud.initial("1-р худаг"));
        assertEquals("Ө", WaypointHud.initial("«өвөлжөө»"), "quotes and signs skipped");
        assertEquals("◆", WaypointHud.initial("!!!"));
        assertEquals("◆", WaypointHud.initial(null));
    }

    @Test
    void everyPinColourHasItsOwnCardColour() {
        Set<Integer> seen = new java.util.HashSet<>();
        for (String c : MapPin.COLOURS) seen.add(WaypointHud.colourOf(c));
        assertEquals(MapPin.COLOURS.size(), seen.size());
        assertEquals(0xECBE4C, WaypointHud.colourOf("gold"));
        assertEquals(0xECBE4C, WaypointHud.colourOf(null), "an unknown colour reads gold");
    }

    @Test
    void theArrowPointsTheWayOfTheAngle() {
        assertEquals("⬆", WaypointHud.arrow(0));
        assertEquals("⬈", WaypointHud.arrow(Math.PI / 4));
        assertEquals("➡", WaypointHud.arrow(Math.PI / 2));
        assertEquals("⬇", WaypointHud.arrow(Math.PI));
        assertEquals("⬇", WaypointHud.arrow(-Math.PI));
        assertEquals("⬅", WaypointHud.arrow(-Math.PI / 2));
        assertEquals("⬉", WaypointHud.arrow(-Math.PI / 4));
        assertEquals("⬆", WaypointHud.arrow(2 * Math.PI + 0.1));
    }

    // ------------------------------------------------------------------------------------------------ which marks

    @Test
    void theWaypointComesFirstThenTheNearestPins() {
        List<WaypointHud.Mark> all = new ArrayList<>();
        for (int i = 0; i < 10; i++) all.add(pin("p" + i, "Тэмдэг " + i, 100 - i * 10, 64, 0)); // p9 nearest
        all.add(waypoint(1400, 64, 0));
        List<WaypointHud.Mark> shown = WaypointHud.choose(all, 0, 64, 0, WaypointHud.MAX_MARKS, null);
        assertEquals(1 + WaypointHud.MAX_MARKS, shown.size());
        assertEquals("waypoint", shown.get(0).key(), "the waypoint first, though farthest");
        assertEquals("p9", shown.get(1).key());
        assertEquals("p8", shown.get(2).key());
        for (int i = 2; i < shown.size(); i++) assertTrue(shown.get(i - 1).x() <= shown.get(i).x(), "nearest first");
    }

    @Test
    void aFarPinIsLeftOffButAFarWaypointNever() {
        List<WaypointHud.Mark> all = List.of(pin("far", "Алс", WaypointHud.RANGE + 10, 64, 0), waypoint(9000, 64, 0));
        List<WaypointHud.Mark> shown = WaypointHud.choose(all, 0, 64, 0, WaypointHud.MAX_MARKS, null);
        assertEquals(1, shown.size());
        assertEquals("waypoint", shown.get(0).key());
    }

    @Test
    void aPinTheWaypointLeadsToShowsOnce() {
        List<WaypointHud.Mark> all = List.of(pin("p1", "Агуй", 10, 64, 0), pin("p2", "Худаг", 20, 64, 0), waypoint(10.5, 64, 0.5));
        List<WaypointHud.Mark> shown = WaypointHud.choose(all, 0, 64, 0, WaypointHud.MAX_MARKS, "p1");
        assertEquals(List.of("waypoint", "p2"), shown.stream().map(WaypointHud.Mark::key).toList());
    }

    // ------------------------------------------------------------------------------------------------ edge cards

    @Test
    void anEdgeCardKeepsOffTheMinimap() {
        double l = WaypointHud.EDGE.left();
        double[] onLeft = WaypointHud.offMinimap(l, 0.6);
        assertEquals(l, onLeft[0], EPS);
        assertEquals(WaypointHud.MINIMAP_BOTTOM, onLeft[1], EPS, "slides down the left edge");
        double[] onTop = WaypointHud.offMinimap(-0.7, WaypointHud.EDGE.top());
        assertEquals(WaypointHud.MINIMAP_RIGHT, onTop[0], EPS, "slides right along the top");
        assertEquals(WaypointHud.EDGE.top(), onTop[1], EPS);
        double[] elsewhere = WaypointHud.offMinimap(WaypointHud.EDGE.right(), 0.6);
        assertEquals(WaypointHud.EDGE.right(), elsewhere[0], EPS);
        assertEquals(0.6, elsewhere[1], EPS);
    }

    @Test
    void edgeCardsNeverOverlap() {
        double r = WaypointHud.EDGE.right();
        List<double[]> cards = new ArrayList<>(List.of(new double[]{r, 0.1}, new double[]{r, 0.1}, new double[]{r, 0.12},
                new double[]{WaypointHud.EDGE.left(), 0.1}, new double[]{0.2, WaypointHud.EDGE.bottom()}, new double[]{0.22, WaypointHud.EDGE.bottom()}));
        WaypointHud.declutter(cards);
        assertEquals(0.1, cards.get(0)[1], EPS, "the first (the waypoint) keeps its place");
        for (int i = 0; i < 3; i++) {
            assertEquals(r, cards.get(i)[0], EPS, "still on the right edge");
            for (int j = i + 1; j < 3; j++) assertTrue(Math.abs(cards.get(i)[1] - cards.get(j)[1]) >= WaypointHud.EDGE_GAP - EPS, i + " and " + j);
        }
        assertEquals(0.1, cards.get(3)[1], EPS, "a card on another edge is not pushed");
        assertTrue(Math.abs(cards.get(4)[0] - cards.get(5)[0]) >= WaypointHud.EDGE_GAP_X - EPS, "side by side along the bottom");
        for (double[] c : cards) assertTrue(WaypointHud.EDGE.contains(c[0], c[1]), "inside the edge area");
    }

    @Test
    void aCrowdedEdgeStaysInsideItsRoom() {
        double r = WaypointHud.EDGE.right();
        List<double[]> cards = new ArrayList<>();
        for (int i = 0; i < 7; i++) cards.add(new double[]{r, WaypointHud.EDGE.top()});
        WaypointHud.declutter(cards);
        for (double[] c : cards) assertTrue(c[1] <= WaypointHud.EDGE.top() + EPS && c[1] >= WaypointHud.EDGE.bottom() - EPS);
        for (int i = 0; i < cards.size(); i++) {
            for (int j = i + 1; j < cards.size(); j++) assertTrue(Math.abs(cards.get(i)[1] - cards.get(j)[1]) >= WaypointHud.EDGE_GAP - EPS);
        }
    }

    @Test
    void cardsOnTwoEdgesNeverMeetInACorner() {
        double r = WaypointHud.EDGE.right(), t = WaypointHud.EDGE.top();
        List<double[]> cards = new ArrayList<>(List.of(new double[]{r, t}, new double[]{r - 0.01, t}, new double[]{r, t - 0.01}));
        WaypointHud.declutter(cards);
        for (int i = 0; i < cards.size(); i++) {
            for (int j = i + 1; j < cards.size(); j++) {
                double[] a = cards.get(i), b = cards.get(j);
                assertTrue(Math.abs(a[0] - b[0]) >= WaypointHud.EDGE_GAP_X - EPS || Math.abs(a[1] - b[1]) >= WaypointHud.EDGE_GAP - EPS, i + " and " + j);
            }
        }
    }

    @Test
    void aFullTopEdgeSendsItsLastCardsDownTheSides() {
        List<double[]> cards = new ArrayList<>();
        for (int i = 0; i < 7; i++) cards.add(new double[]{0.4, WaypointHud.EDGE.top()});
        WaypointHud.declutter(cards);
        int onTop = 0, onSides = 0;
        for (double[] c : cards) {
            if (c[1] >= WaypointHud.EDGE.top() - EPS) onTop++;
            else if (Math.abs(Math.abs(c[0]) - WaypointHud.EDGE.right()) < EPS) onSides++;
        }
        assertEquals(7, onTop + onSides);
        assertTrue(onSides > 0, "the top holds only so many");
        for (int i = 0; i < cards.size(); i++) {
            for (int j = i + 1; j < cards.size(); j++) {
                double[] a = cards.get(i), b = cards.get(j);
                assertTrue(Math.abs(a[0] - b[0]) >= WaypointHud.EDGE_GAP_X - EPS || Math.abs(a[1] - b[1]) >= WaypointHud.EDGE_GAP - EPS, i + " and " + j);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the layout

    @Test
    void aMarkInViewIsDrawnByTheClientsOwnCameraAtThePlace() {
        // feet at y 64, eye 1.62 above, looking south at a pin 18 blocks ahead
        ScreenProjection.Camera cam = ScreenProjection.Camera.standard(0.5, 65.62, 0.5, 0, 0);
        WaypointHud.Mark m = pin("p", "Агуй", 0.5, 64, 18.5);
        WaypointHud.Placement p = WaypointHud.layout(cam, 0.5, 64, 0.5, List.of(m), Set.of()).get(0);
        assertTrue(p.inView());
        assertNull(p.arrow());
        assertEquals(0.5, p.x(), EPS);
        assertEquals(64 + WaypointHud.LIFT, p.y(), EPS, "over the place");
        assertEquals(18.5, p.z(), EPS);
        assertEquals(18, p.distance(), EPS, "measured from the feet");
        assertEquals("18.0 м", WaypointHud.meters(p.distance()));
        // the card's spot projects where the place's own anchor projects: the same pixel on the client
        ScreenProjection.Projection card = ScreenProjection.project(cam, p.x(), p.y(), p.z());
        assertEquals(p.sx(), card.sx(), EPS);
        assertEquals(p.sy(), card.sy(), EPS);
    }

    @Test
    void aFarMarkInViewStandsNearOnTheSameLineAndGrowsToMatch() {
        ScreenProjection.Camera cam = ScreenProjection.Camera.standard(0, 65.62, 0, -20, 5);
        WaypointHud.Mark far = pin("p", "Худаг", -300, 90, 800);
        WaypointHud.Placement p = WaypointHud.layout(cam, 0, 64, 0, List.of(far), Set.of()).get(0);
        assertTrue(p.inView());
        assertEquals(WaypointHud.NEAR, ScreenProjection.distance(cam.x(), cam.y(), cam.z(), p.x(), p.y(), p.z()), 1e-9, "brought in to NEAR blocks");
        ScreenProjection.Projection place = ScreenProjection.project(cam, far.x(), far.y() + WaypointHud.LIFT, far.z());
        ScreenProjection.Projection card = ScreenProjection.project(cam, p.x(), p.y(), p.z());
        assertEquals(place.sx(), card.sx(), 1e-9, "on the line of sight to the place");
        assertEquals(place.sy(), card.sy(), 1e-9);
        assertEquals(WaypointHud.NEAR * WaypointHud.CARD_SCALE, p.scale(), EPS);
        assertEquals(ScreenProjection.distance(0, 64, 0, -300, 90, 800), p.distance(), EPS);
    }

    @Test
    void aCloseCardStopsShrinking() {
        ScreenProjection.Camera cam = ScreenProjection.Camera.standard(0, 65.62, 0, 0, 30);
        WaypointHud.Placement p = WaypointHud.layout(cam, 0, 64, 0, List.of(pin("p", "Ойр", 0, 62, 3)), Set.of()).get(0);
        assertTrue(p.inView());
        assertEquals(WaypointHud.MIN_SCALE, p.scale(), EPS);
    }

    @Test
    void aMarkBehindBecomesAnEdgeCardThatProjectsOntoTheEdge() {
        ScreenProjection.Camera cam = ScreenProjection.Camera.standard(0, 65.62, 0, 0, 0); // facing south
        WaypointHud.Mark behind = pin("p", "Агуй", -15, 64, -40); // behind, to the west: turn right
        WaypointHud.Placement p = WaypointHud.layout(cam, 0, 64, 0, List.of(behind), Set.of()).get(0);
        assertFalse(p.inView());
        assertEquals("➡", p.arrow(), "turn right");
        assertEquals(WaypointHud.EDGE.right(), p.sx(), EPS);
        assertEquals(WaypointHud.EDGE_DIST, ScreenProjection.distance(cam.x(), cam.y(), cam.z(), p.x(), p.y(), p.z()), 1e-9);
        ScreenProjection.Projection back = ScreenProjection.project(cam, p.x(), p.y(), p.z());
        assertEquals(p.sx(), back.sx(), 1e-9, "the server puts it where it means it to be seen");
        assertEquals(p.sy(), back.sy(), 1e-9);
        assertTrue(back.onScreen());
        assertEquals(ScreenProjection.distance(0, 64, 0, -15, 64, -40), p.distance(), EPS, "the real distance, not the card's");
    }

    @Test
    void theInViewEdgeHasHysteresis() {
        ScreenProjection.Camera cam = ScreenProjection.Camera.standard(0, 65.62, 0, 0, 0);
        // a place at sx ≈ 0.93: between VIEW_IN (0.88) and VIEW_OUT (0.97)
        double tanH = Math.tan(Math.toRadians(ScreenProjection.DEFAULT_FOV / 2)) * ScreenProjection.DEFAULT_ASPECT;
        WaypointHud.Mark m = pin("p", "Зах", -0.93 * tanH * 20, 65.62 - WaypointHud.LIFT, 20);
        assertEquals(0.93, ScreenProjection.project(cam, m.x(), m.y() + WaypointHud.LIFT, m.z()).sx(), 1e-9, "facing south, −x is on the right");
        assertFalse(WaypointHud.layout(cam, 0, 64, 0, List.of(m), Set.of()).get(0).inView(), "not in yet: an edge card");
        assertTrue(WaypointHud.layout(cam, 0, 64, 0, List.of(m), Set.of("p")).get(0).inView(), "was in view: stays");
    }

    @Test
    void anUnknownHeightFloatsAtThePlayersHeightAndIsMeasuredAcrossTheGround() {
        ScreenProjection.Camera cam = ScreenProjection.Camera.standard(0, 81.62, 0, 0, 0);
        WaypointHud.Mark place = new WaypointHud.Mark("waypoint", WaypointHud.Kind.WAYPOINT, "◆", "x 0, z 300", 0xECBE4C, 0, Double.NaN, 300);
        WaypointHud.Placement p = WaypointHud.layout(cam, 0, 80, 0, List.of(place), Set.of()).get(0);
        assertTrue(p.inView());
        assertEquals(300, p.distance(), EPS);
        assertTrue(Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()));
    }

    @Test
    void everyLayoutKeepsItsEdgeCardsApartOnScreenAndOffTheMinimap() {
        Random r = new Random(11);
        for (int round = 0; round < 300; round++) {
            ScreenProjection.Camera cam = ScreenProjection.Camera.standard(0, 65.62, 0, (float) (r.nextDouble() * 360), (float) (r.nextDouble() * 120 - 60));
            List<WaypointHud.Mark> marks = new ArrayList<>();
            marks.add(waypoint(r.nextDouble() * 2000 - 1000, 64, r.nextDouble() * 2000 - 1000));
            for (int i = 0; i < WaypointHud.MAX_MARKS; i++) {
                marks.add(pin("p" + i, "П" + i, r.nextDouble() * 400 - 200, 40 + r.nextDouble() * 60, r.nextDouble() * 400 - 200));
            }
            List<WaypointHud.Placement> out = WaypointHud.layout(cam, 0, 64, 0, marks, Set.of());
            assertEquals(marks.size(), out.size(), "every mark gets a card");
            List<WaypointHud.Placement> edge = out.stream().filter(p -> !p.inView()).toList();
            for (WaypointHud.Placement p : out) {
                ScreenProjection.Projection back = ScreenProjection.project(cam, p.x(), p.y(), p.z());
                assertTrue(back.onScreen(), "round " + round + ": every card is on the screen the server expects");
            }
            for (WaypointHud.Placement p : edge) {
                assertTrue(WaypointHud.EDGE.contains(p.sx(), p.sy()), "round " + round + ": inside the edge area");
                assertFalse(p.sx() < WaypointHud.MINIMAP_RIGHT - EPS && p.sy() > WaypointHud.MINIMAP_BOTTOM + EPS, "round " + round + ": off the minimap");
            }
            for (int i = 0; i < edge.size(); i++) {
                for (int j = i + 1; j < edge.size(); j++) {
                    WaypointHud.Placement a = edge.get(i), b = edge.get(j);
                    boolean apart = Math.abs(a.sx() - b.sx()) >= WaypointHud.EDGE_GAP_X - EPS || Math.abs(a.sy() - b.sy()) >= WaypointHud.EDGE_GAP - EPS;
                    assertTrue(apart, "round " + round + ": edge cards " + i + " and " + j + " overlap");
                }
            }
        }
    }
}
