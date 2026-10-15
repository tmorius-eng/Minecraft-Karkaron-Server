package mn.suld.api.map;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The baked cartography: deterministic, an array read a pixel, every region its own land, its waters and roads drawn. */
class CartographyTest {

    private static final BakedAtlas B = CartographyFixture.baked();

    @Test
    void theSameLayoutBakesToTheSameBytesEveryTime() {
        BakedAtlas again = Cartography.bake(CartographyFixture.layout(), CartographyFixture.WORLD);
        for (int zoom = BakedAtlas.FINEST; zoom <= BakedAtlas.WIDEST; zoom++) {
            assertArrayEquals(B.raster(zoom), again.raster(zoom), "zoom " + zoom);
            assertEquals(B.originX(zoom), again.originX(zoom));
            assertEquals(B.width(zoom), again.width(zoom));
        }
    }

    @Test
    void everyPixelOfTheWorldIsPaintedAndALookupIsOneArrayRead() {
        MapGeometry g = CartographyFixture.WORLD;
        Random rnd = new Random(7);
        for (int zoom = BakedAtlas.FINEST; zoom <= BakedAtlas.WIDEST; zoom++) {
            byte[] r = B.raster(zoom);
            int s = MapGeometry.scale(zoom), w = B.width(zoom);
            // the raster covers the square, aligned to the world tiles
            assertEquals(0, Math.floorMod(B.originX(zoom), s));
            assertTrue(B.originX(zoom) <= g.minX() && B.originX(zoom) + w * s >= g.maxX());
            for (byte b : r) assertNotEquals(0, b, "no hole at zoom " + zoom);
            for (int i = 0; i < 20_000; i++) {
                int x = g.minX() + rnd.nextInt(g.width()), z = g.minZ() + rnd.nextInt(g.depth());
                int ix = Math.floorDiv(x - B.originX(zoom), s), iz = Math.floorDiv(z - B.originZ(zoom), s);
                assertEquals(r[iz * w + ix], B.pixel(zoom, x, z));
            }
            assertEquals(0, B.pixel(zoom, g.maxX() + 5000, 0), "off the raster");
        }
        // zooms 0 and 1 read the finest level, nudged at most two blocks
        for (int i = 0; i < 5000; i++) {
            int x = rnd.nextInt(8000) - 4000, z = rnd.nextInt(8000) - 4000;
            byte b = B.pixel(0, x, z);
            boolean near = false;
            for (int dz = -2; dz <= 1 && !near; dz++) for (int dx = -2; dx <= 1 && !near; dx++) near = B.pixel(2, x + dx, z + dz) == b;
            assertTrue(near, "zoom 0 at " + x + "," + z);
        }
        // the cost: a lookup is an index and a read, ~10 ns (the bound only catches a lookup that computes something)
        long t0 = System.nanoTime(), sum = 0;
        for (int i = 0; i < 2_000_000; i++) sum += B.pixel(i % 3 + 2, (i * 37) % 9000 - 4500, (i * 91) % 9000 - 4500);
        double ns = (System.nanoTime() - t0) / 2_000_000.0;
        System.out.printf("[carto] lookup %.1f ns (%d)%n", ns, sum & 1);
        assertTrue(ns < 1000, ns + " ns a lookup");
    }

    @Test
    void theBakeStaysWithinItsMemoryAndTimeBudget() {
        System.out.println("[carto] baked in " + B.bakeMillis() + " ms, " + B.bytes() + " bytes");
        assertTrue(B.bytes() <= 9L * 1024 * 1024, B.bytes() + " bytes"); // 2 501² + 1 251² + 626² for the 10 001-block world
        assertTrue(B.bakeMillis() < 30_000, B.bakeMillis() + " ms");
    }

    /** The mean colour (0xRRGGBB) of zoom 3's pixels within {@code r} blocks of polar point (bearing, radius). */
    private static int mean(double bearing, double radius, int r) {
        int[] c = CartographyFixture.polar(bearing, radius);
        long sr = 0, sg = 0, sb = 0, n = 0;
        for (int dz = -r; dz <= r; dz += 8) {
            for (int dx = -r; dx <= r; dx += 8) {
                int argb = MapColors.argb(B.pixel(3, c[0] + dx, c[1] + dz));
                sr += (argb >> 16) & 0xFF;
                sg += (argb >> 8) & 0xFF;
                sb += argb & 0xFF;
                n++;
            }
        }
        return (int) (sr / n) << 16 | (int) (sg / n) << 8 | (int) (sb / n);
    }

    private static int r(int c) {
        return (c >> 16) & 0xFF;
    }

    private static int g(int c) {
        return (c >> 8) & 0xFF;
    }

    private static int b(int c) {
        return c & 0xFF;
    }

    @Test
    void everyMajorRegionReadsAsItsOwnLand() {
        int taiga = mean(8, 3100, 200);        // Хөвсгөлийн Тайга: Эгийн Гол
        int forest = mean(20, 2050, 200);      // Хангай: Мэргидийн Тайга
        int steppe = mean(112, 1150, 200);     // Хэрлэнгийн Тал: Хөдөө Арал
        int meadow = mean(30, 520, 120);       // Орхоны Хөндий
        int gobi = mean(158, 2050, 250);       // Говь: Галбын Говь
        int zuungar = mean(158, 4400, 250);    // Зүүнгарын Говь: Замын Үүд
        int badlands = mean(200, 2050, 200);   // Тангудын Хил
        // the greens are green, the Gobi warm, the badlands red
        for (int c : new int[]{taiga, forest, steppe, meadow}) assertTrue(g(c) > r(c) && g(c) > b(c), Integer.toHexString(c) + " is green");
        for (int c : new int[]{gobi, zuungar}) assertTrue(r(c) > g(c) && g(c) > b(c) && r(c) - b(c) > 70, Integer.toHexString(c) + " is golden ochre");
        assertTrue(r(badlands) > g(badlands) * 1.3, Integer.toHexString(badlands) + " is red rock");
        // the taiga is the darkest land, the open steppe far lighter
        double lt = Cartography.luminance(taiga), ls = Cartography.luminance(steppe);
        assertTrue(lt < 0.15 && ls > 1.6 * lt, lt + " vs " + ls);
        assertTrue(Cartography.luminance(forest) < ls, "the forest is darker than the steppe");
        // and they are told apart from each other
        int[] lands = {taiga, steppe, gobi, badlands};
        for (int i = 0; i < lands.length; i++) {
            for (int j = i + 1; j < lands.length; j++) assertTrue(distance(lands[i], lands[j]) > 20, Integer.toHexString(lands[i]) + " ~ " + Integer.toHexString(lands[j]));
        }
    }

    @Test
    void theHighRangesCarrySnowAndBareRock() {
        for (double[] range : new double[][]{{250, 1230}, {262, 4400}, {297, 4600}}) { // Отгонтэнгэр, Мөнххайрхан, Таван Богд
            int[] c = CartographyFixture.polar(range[0], range[1]);
            int snow = 0, rock = 0;
            for (int dz = -400; dz <= 400; dz += 8) {
                for (int dx = -400; dx <= 400; dx += 8) {
                    int id = MapColors.id(B.pixel(3, c[0] + dx, c[1] + dz));
                    if (id == MapColors.SNOW || id == MapColors.CLAY) snow++;
                    if (id == MapColors.STONE || id == MapColors.TERRACOTTA_LIGHT_GRAY) rock++;
                }
            }
            assertTrue(snow > 20 && rock > 20, "range at " + range[0] + "°: snow " + snow + ", rock " + rock);
        }
        // and the steppe has none
        int[] c = CartographyFixture.polar(112, 1150);
        for (int dz = -200; dz <= 200; dz += 8) for (int dx = -200; dx <= 200; dx += 8) assertNotEquals(MapColors.SNOW, MapColors.id(B.pixel(3, c[0] + dx, c[1] + dz)));
    }

    @Test
    void theLakesRiversAndCaravanRoadsAreDrawn() {
        // Хөвсгөл Нуур, Хар Ус Нуур, Буйр Нуур: water at their middles
        for (double[] lake : new double[][]{{339.75, 2050}, {290.25, 3140}, {114.75, 2205}}) {
            int[] c = CartographyFixture.polar(lake[0], lake[1]);
            assertEquals(MapColors.LIGHT_BLUE, MapColors.id(B.pixel(3, c[0], c[1])), "lake at " + lake[0] + "°");
        }
        // rivers: plenty of river pixels, and the Selenge leaves the known lands in the north
        int river = 0;
        byte[] z3 = B.raster(3);
        for (byte b : z3) if (b == Cartography.RIVER) river++;
        assertTrue(river > 2000, river + " river pixels");
        // a road reaches every dungeon gate
        for (double[] gt : CartographyFixture.GATES) {
            int[] c = CartographyFixture.polar(gt[0], gt[1]);
            boolean road = false;
            for (int dz = -48; dz <= 48 && !road; dz += 8) { // on a trunk it may stand off its meander by up to 40 blocks
                for (int dx = -48; dx <= 48 && !road; dx += 8) {
                    byte b = B.pixel(3, c[0] + dx, c[1] + dz);
                    road = b == Cartography.ROAD_DARK || b == Cartography.ROAD_LIGHT;
                }
            }
            assertTrue(road, "a road to the gate at " + gt[0] + "° " + gt[1]);
        }
        // the capital is drawn where it stands
        assertTrue(B.capital(0, 0) && B.capital(51, -51) && !B.capital(60, 0));
        Set<Byte> city = new HashSet<>();
        for (int z = -40; z <= 40; z += 4) for (int x = -40; x <= 40; x += 4) city.add(B.pixel(2, x, z));
        assertTrue(city.contains(Cartography.ROOF), "red roofs in Хархорум");
    }

    @Test
    void theSurveyInkIsLighterAndHatchedCloseUpAndTheAtlasFarOut() {
        Random rnd = new Random(3);
        int hatched = 0, n = 0;
        for (int i = 0; i < 20_000; i++) {
            int x = rnd.nextInt(6000) - 3000, z = rnd.nextInt(6000) - 3000;
            assertEquals(B.pixel(4, x, z), B.survey(4, x, z), "far out the survey is the atlas");
            assertEquals(B.pixel(3, x, z), B.survey(3, x, z));
            byte p = B.pixel(0, x, z), s = B.survey(0, x, z);
            if (s == BakedAtlas.Survey.LINE && Math.floorMod(x + z, 4) == 0) {
                hatched++;
            } else {
                boolean tooDark = Cartography.luminance(MapColors.pixel(MapColors.id(p), MapColors.SHADE_HIGH)) < BakedAtlas.Survey.LIGHT_ENOUGH;
                assertTrue(MapColors.id(p) == MapColors.id(s) || tooDark, "the hue is kept unless even its lightest shade is too dark");
                assertTrue(MapColors.argb(s) != 0 && Cartography.luminance(s) >= Cartography.luminance(p) - 1e-9, "lighter");
            }
            n++;
        }
        assertEquals(0.25, hatched / (double) n, 0.03, "one diagonal line in four");
    }

    @Test
    void theGazetteerNamesOnlyTheWorldsAreasAndAMissingOneDropsOnlyItsFeatures() {
        Set<String> areas = new HashSet<>();
        for (Cartography.Land a : CartographyFixture.areas()) areas.add(a.id());
        for (String a : Cartography.namedAreas()) assertTrue(areas.contains(a), a);
        Set<String> regions = new HashSet<>();
        for (Cartography.Land r : CartographyFixture.regions()) regions.add(r.id());
        assertTrue(regions.containsAll(Cartography.namedRegions()));
        // without Хөвсгөл Нуур's area the lake is gone, the rest still bakes (a small square around it, for speed)
        List<Cartography.Land> less = new ArrayList<>(CartographyFixture.areas());
        less.removeIf(a -> a.id().equals("area.khuvsgul"));
        int[] c = CartographyFixture.polar(339.75, 2050);
        MapGeometry around = new MapGeometry(c[0] - 600, c[1] - 600, c[0] + 600, c[1] + 600);
        BakedAtlas without = Cartography.bake(new Cartography.Layout(0, 0, 51, CartographyFixture.regions(), less, CartographyFixture.gates()), around);
        assertNotEquals(MapColors.LIGHT_BLUE, MapColors.id(without.pixel(3, c[0], c[1])));
        assertEquals(MapColors.LIGHT_BLUE, MapColors.id(B.pixel(3, c[0], c[1])));
        assertFalse(without.bytes() == 0);
    }

    /** CIE76 distance of two colours. */
    private static double distance(int a, int b) {
        double[] la = MapColors.lab(a), lb = MapColors.lab(b);
        return Math.sqrt(Math.pow(la[0] - lb[0], 2) + Math.pow(la[1] - lb[1], 2) + Math.pow(la[2] - lb[2], 2));
    }
}
