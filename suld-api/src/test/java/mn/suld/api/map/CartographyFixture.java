package mn.suld.api.map;

import java.util.ArrayList;
import java.util.List;

/** The bundled world.json's layout (regions, areas, the ten dungeon gates), the spawn at (0, 0), the 10 001-block square. */
final class CartographyFixture {

    /** Each 45° slice from its first bearing: its five areas from the capital out. */
    private static final String[][] SLICES = {
            {"0", "area.orkhon", "area.selenge", "area.merkit", "area.egiin_gol", "area.selenge_adag"},
            {"45", "area.tuul", "area.burkhan_khaldun", "area.onon", "area.khentii_taiga", "area.ulz"},
            {"90", "area.kherlen", "area.khodoo_aral", "area.buir", "area.dariganga", "area.menen"},
            {"135", "area.ongi", "area.omnii_govi", "area.galba", "area.khongor", "area.zamyn_uud"},
            {"180", "area.taats", "area.gurvan_saikhan", "area.tangut", "area.nemegt", "area.khar_khot_road"},
            {"225", "area.khangai_range", "area.otgontenger", "area.naiman", "area.govi_altai", "area.munkh_khairkhan"},
            {"270", "area.tamir", "area.zavkhan", "area.kharkhiraa", "area.khar_us", "area.tavan_bogd"},
            {"315", "area.khar_balgas", "area.ider", "area.khuvsgul", "area.darkhad", "area.tsagaannuur"}};
    private static final double[] RINGS = {51, 700, 1500, 2600, 3800, 5000};
    /** The dungeon sites of dungeons.json: bearing, radius. */
    static final double[][] GATES = {{75, 380}, {165, 650}, {20, 1000}, {255, 1400}, {335, 2600}, {200, 3000}, {110, 3300},
            {60, 3700}, {285, 4100}, {0, 4500}};

    static final MapGeometry WORLD = MapGeometry.ofBorder(0.5, 0.5, 10_001);

    private CartographyFixture() {
    }

    static List<Cartography.Land> regions() {
        return List.of(
                new Cartography.Land("region.kherlen", 0, 2600, 45, 135), new Cartography.Land("region.gobi", 0, 2600, 135, 225),
                new Cartography.Land("region.khangai", 0, 2600, 315, 45), new Cartography.Land("region.altai", 0, 2600, 225, 315),
                new Cartography.Land("region.khentii", 2600, 5000, 45, 135), new Cartography.Land("region.zuungar", 2600, 5000, 135, 225),
                new Cartography.Land("region.otgon", 2600, 5000, 225, 315), new Cartography.Land("region.khuvsgul", 2600, 5000, 315, 45));
    }

    static List<Cartography.Land> areas() {
        List<Cartography.Land> out = new ArrayList<>();
        for (String[] slice : SLICES) {
            double from = Double.parseDouble(slice[0]);
            for (int ring = 0; ring < 5; ring++) out.add(new Cartography.Land(slice[ring + 1], RINGS[ring], RINGS[ring + 1], from, from + 45));
        }
        return out;
    }

    static List<Cartography.Spot> gates() {
        List<Cartography.Spot> out = new ArrayList<>();
        for (double[] gt : GATES) {
            double a = Math.toRadians(gt[0]);
            out.add(new Cartography.Spot(Math.round(Math.sin(a) * gt[1]), Math.round(-Math.cos(a) * gt[1])));
        }
        return out;
    }

    static Cartography.Layout layout() {
        return new Cartography.Layout(0, 0, 51, regions(), areas(), gates());
    }

    private static BakedAtlas baked;

    /** The bundled world baked once for every test (it is immutable). */
    static synchronized BakedAtlas baked() {
        if (baked == null) baked = Cartography.bake(layout(), WORLD);
        return baked;
    }

    /** The world point at {@code bearing} degrees and {@code radius} blocks from the spawn. */
    static int[] polar(double bearing, double radius) {
        double a = Math.toRadians(bearing);
        return new int[]{(int) Math.round(Math.sin(a) * radius), (int) Math.round(-Math.cos(a) * radius)};
    }
}
