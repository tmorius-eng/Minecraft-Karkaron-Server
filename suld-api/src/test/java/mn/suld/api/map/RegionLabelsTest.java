package mn.suld.api.map;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionLabelsTest {

    // the eight wild regions of the bundled world.json (radii from the spawn, degrees clockwise from north)
    private static final List<RegionLabels.Region> REGIONS = List.of(
            new RegionLabels.Region("Хэрлэнгийн Тал", 0, 2600, 45, 135),
            new RegionLabels.Region("Говь", 0, 2600, 135, 225),
            new RegionLabels.Region("Хангай", 0, 2600, 315, 45),
            new RegionLabels.Region("Алтай", 0, 2600, 225, 315),
            new RegionLabels.Region("Хэнтийн Нуруу", 2600, 5000, 45, 135),
            new RegionLabels.Region("Зүүнгарын Говь", 2600, 5000, 135, 225),
            new RegionLabels.Region("Отгонтэнгэрийн Оргил", 2600, 5000, 225, 315),
            new RegionLabels.Region("Хөвсгөлийн Тайга", 2600, 5000, 315, 45));
    /** The plugin's letters: 7 and 10 font pixels at a scale of 0.62, 1/40 block a pixel. */
    private static final RegionLabels.Metrics M = new RegionLabels.Metrics(7 * 0.025 * 0.62, 10 * 0.025 * 0.62);
    /** The whole world on the 5 × 5 wall: 16 blocks a pixel, the spawn at the middle (the 10 000-block world). */
    private static final AtlasView WHOLE = new AtlasView(5, 5, 0, 0, 4);

    private static void onTheWallAndApart(List<RegionLabels.Label> labels, AtlasView view) {
        for (RegionLabels.Label l : labels) {
            assertTrue(Math.abs(l.box().u0()) <= view.cols() / 2.0 && Math.abs(l.box().u1()) <= view.cols() / 2.0
                    && Math.abs(l.box().v0()) <= view.rows() / 2.0 && Math.abs(l.box().v1()) <= view.rows() / 2.0, l + " leaves the wall");
            for (RegionLabels.Label o : labels) if (o != l) assertFalse(l.box().hits(o.box()), l.name() + " covers " + o.name());
        }
    }

    @Test
    void atTheWholeWorldEveryRegionIsNamedOnTheWallAndNoNameCoversAnother() {
        List<RegionLabels.Label> labels = RegionLabels.place(REGIONS, WHOLE, 0, 0, 4900, M, List.of());
        assertEquals(8, labels.size());
        onTheWallAndApart(labels, WHOLE);
        // the long west name had run off the wall's edge, and the east names met on one line (map preview, 2026-10-13)
        RegionLabels.Label otgon = labels.get(6);
        assertEquals(List.of("ОТГОНТЭНГЭРИЙН", "ОРГИЛ"), otgon.lines());
        assertTrue(otgon.u() < 0, "west of the spawn");
    }

    @Test
    void aLongNameOfTwoWordsIsWrittenInTwoLinesAShortOneInOne() {
        assertEquals(List.of("ХӨВСГӨЛИЙН", "ТАЙГА"), RegionLabels.lines("ХӨВСГӨЛИЙН ТАЙГА"));
        assertEquals(List.of("ГОВЬ"), RegionLabels.lines("ГОВЬ"));
        assertEquals(List.of("ХЭНТИЙН", "НУРУУ"), RegionLabels.lines("ХЭНТИЙН НУРУУ"), "13 letters: two lines");
        assertEquals(List.of("ИХ ГОВЬ"), RegionLabels.lines("ИХ ГОВЬ"), "a short name of two words: one line");
        assertEquals(List.of("ОТГОНТЭНГЭРИЙНОРГИЛ"), RegionLabels.lines("ОТГОНТЭНГЭРИЙНОРГИЛ"), "one word stays whole");
        assertEquals(List.of("ИХ ХААНЫ", "ЭРТНИЙ ТАЛ"), RegionLabels.lines("ИХ ХААНЫ ЭРТНИЙ ТАЛ"), "broken at the space nearest the middle");
    }

    @Test
    void aNameWhoseMiddleIsTakenMovesToAFreeSpotOfItsOwnSector() {
        // the city's places crowd the middle of every inner region at the whole world: a label over Хангай's middle
        RegionLabels.Label free = RegionLabels.place(List.of(REGIONS.get(2)), WHOLE, 0, 0, 4900, M, List.of()).get(0);
        RegionLabels.Box placeLabel = RegionLabels.Box.around(free.u(), free.v(), 0.3, 0.06);
        RegionLabels.Label moved = RegionLabels.place(List.of(REGIONS.get(2)), WHOLE, 0, 0, 4900, M, List.of(placeLabel)).get(0);
        assertFalse(moved.box().hits(placeLabel), moved + " still covers the place");
        double x = WHOLE.worldX(moved.u()), z = WHOLE.worldZ(moved.v());
        double deg = (Math.toDegrees(Math.atan2(x, -z)) + 360) % 360;
        assertTrue(deg >= 315 || deg <= 45, "still in Хангай's arc: " + deg);
        assertTrue(Math.hypot(x, z) < 2600, "still in its ring: " + Math.hypot(x, z));
    }

    @Test
    void crowdedByPlacesTheNamesStillStayApartAndOnTheWall() {
        // a dense ring of wide place labels round the city: some names cannot find a free spot, none covers another
        List<RegionLabels.Box> taken = new ArrayList<>();
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(k * 30);
            taken.add(RegionLabels.Box.around(WHOLE.u(Math.sin(a) * 1200), WHOLE.v(-Math.cos(a) * 1200), 0.28, 0.06));
        }
        List<RegionLabels.Label> labels = RegionLabels.place(REGIONS, WHOLE, 0, 0, 4900, M, taken);
        assertEquals(8, labels.size(), "every region is still named");
        onTheWallAndApart(labels, WHOLE);
    }

    @Test
    void onTheDevWorldEveryNameFindsAFreeSpotAmongThePlaces() {
        // the places the whole world labels on the dev world (the city, the dungeons, the world bosses), from the spawn:
        // {x, z, letters of the label}; their markers and labels as the atlas places them (the label under the marker)
        int[][] places = {
                {0, 0, 8}, {367, -98, 12}, {168, 628, 11}, {342, -940, 12}, {-1352, 362, 11}, {-1099, -2356, 10},
                {-1026, 2819, 16}, {3101, 1129, 17}, {3204, -1850, 16}, {-3960, -1061, 13}, {0, -4500, 15},
                {-650, -2039, 21}, {-4171, -1354, 16}};
        List<RegionLabels.Box> taken = new ArrayList<>();
        for (int[] p : places) {
            double u = WHOLE.u(p[0]), v = WHOLE.v(p[1]);
            taken.add(RegionLabels.Box.around(u, v, 0.125, 0.125));
            taken.add(RegionLabels.Box.around(u, v - 0.125 - 0.07, Math.max(0.3, p[2] * 0.045) / 2, 0.06));
        }
        List<RegionLabels.Label> labels = RegionLabels.place(REGIONS, WHOLE, 0, 0, 4900, M, taken);
        assertEquals(8, labels.size());
        onTheWallAndApart(labels, WHOLE);
        for (RegionLabels.Label l : labels) assertTrue(taken.stream().noneMatch(t -> t.hits(l.box())), l.name() + " covers a place");
    }

    @Test
    void aRegionWhollyOffTheWallIsNotNamed() {
        // 8 blocks a pixel looking at the far east: the western regions lie beyond the wall
        AtlasView east = new AtlasView(5, 5, 3500, 0, 3);
        List<RegionLabels.Label> labels = RegionLabels.place(REGIONS, east, 0, 0, 4900, M, List.of());
        assertTrue(labels.stream().noneMatch(l -> l.name().equals("Отгонтэнгэрийн Оргил")));
        assertTrue(labels.stream().anyMatch(l -> l.name().equals("Хэнтийн Нуруу")));
        onTheWallAndApart(labels, east);
    }

    @Test
    void theSameViewGivesTheSameNames() {
        assertEquals(RegionLabels.place(REGIONS, WHOLE, 0, 0, 4900, M, List.of()), RegionLabels.place(REGIONS, WHOLE, 0, 0, 4900, M, List.of()));
    }
}
