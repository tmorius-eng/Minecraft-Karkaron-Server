package mn.suld.plugin.map;

import mn.suld.api.map.BakedAtlas;
import mn.suld.api.map.Cartography;
import mn.suld.api.map.MapColors;
import mn.suld.api.map.MapGeometry;
import mn.suld.api.region.Area;
import mn.suld.api.region.RegionDefinition;
import mn.suld.plugin.content.Content;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The baked cartography of the bundled content (docs/WORLD_MAP.md, «Baked cartography»): the layout read from world.json,
 * dungeons.json and sites.json, every area the gazetteer names really in world.json, and the atlas's labels and markers
 * readable on every colour the bake paints.
 */
class CartographyContentTest {

    private static Cartography.Layout layout;
    private static BakedAtlas baked;
    private static final Set<Integer> COLOURS = new TreeSet<>();

    @BeforeAll
    static void bake() {
        layout = CartoContent.layout(Content.pack(), 0.5, 0.5);
        baked = Cartography.bake(layout, MapGeometry.ofBorder(0.5, 0.5, Content.pack().worldEdge() * 2 + 1));
        for (int zoom = BakedAtlas.FINEST; zoom <= BakedAtlas.WIDEST; zoom++) for (byte b : baked.raster(zoom)) COLOURS.add(b & 0xFF);
    }

    @Test
    void theLayoutIsReadFromTheContent() {
        long sectors = Content.pack().regions().stream().filter(r -> r.shape() instanceof mn.suld.api.region.RegionShape.Sector).count();
        assertEquals(sectors, layout.regions().size());
        assertEquals(51, layout.cityHalf(), "Хархорум's walls (region.kharkhorum, square half 51)");
        assertEquals(Content.pack().areas().size(), layout.areas().size());
        assertEquals(Content.pack().sites().size() + Content.pack().historicSites().size(), layout.spots().size());
        assertEquals(Content.pack().worldEdge(), layout.edge());
    }

    @Test
    void everyAreaAndRegionTheGazetteerNamesIsInWorldJson() {
        Set<String> areas = new HashSet<>(), regions = new HashSet<>();
        for (Area a : Content.pack().areas()) areas.add(a.id());
        for (RegionDefinition r : Content.pack().regions()) regions.add(r.id());
        for (String a : Cartography.namedAreas()) assertTrue(areas.contains(a), a + " is named by the gazetteer but not in world.json");
        for (String r : Cartography.namedRegions()) assertTrue(regions.contains(r), r);
        // and every wild area has its land
        for (Area a : Content.pack().areas()) assertTrue(Cartography.namedAreas().contains(a.id()), a.id() + " has no land in the gazetteer");
    }

    @Test
    void theRealWorldBakesWithinBudget() {
        assertTrue(baked.bytes() <= 9L * 1024 * 1024, baked.bytes() + " bytes");
        assertTrue(COLOURS.size() > 40, COLOURS.size() + " colours");
        assertTrue(baked.capital(0, 0) && !baked.capital(200, 0));
    }

    /** {@code argb}'s colour laid over {@code under} (0xRRGGBB) at its alpha. */
    static int over(int argb, int under) {
        double a = ((argb >>> 24) & 0xFF) / 255.0;
        int out = 0;
        for (int sh = 16; sh >= 0; sh -= 8) out |= (int) Math.round(((argb >> sh) & 0xFF) * a + ((under >> sh) & 0xFF) * (1 - a)) << sh;
        return out;
    }

    @Test
    void theAtlasLabelsReadOnEveryBakedColour() {
        int ink = AtlasSky.REGION_INK.value(), cream = AtlasSky.CREAM.value();
        for (int b : COLOURS) {
            int bg = MapColors.argb((byte) b) & 0xFFFFFF;
            double region = Cartography.contrast(ink, over(AtlasSky.REGION_PLATE, bg));
            assertTrue(region >= 3, String.format("region name on %06x: %.2f", bg, region));
            double label = Cartography.contrast(cream, over(AtlasSky.LABEL_PLATE, bg));
            assertTrue(label >= 4.5, String.format("place label on %06x: %.2f", bg, label));
        }
        // the old plateless ink at 75% did not read on the Gobi's sand or the snow
        int snow = MapColors.argb(MapColors.pixel(MapColors.SNOW, MapColors.SHADE_HIGH)) & 0xFFFFFF;
        assertTrue(Cartography.contrast(over(0xBE000000 | ink, snow), snow) < 3);
    }

    private static Path atlasTextures() {
        for (String p : new String[]{"../resourcepack/assets/suld/textures/entity/atlas", "resourcepack/assets/suld/textures/entity/atlas"}) {
            if (Files.isDirectory(Path.of(p))) return Path.of(p);
        }
        throw new IllegalStateException("resourcepack/assets/suld/textures/entity/atlas not found from " + Path.of("").toAbsolutePath());
    }

    @Test
    void everyMarkerStandsOutOnEveryBakedColour() throws Exception {
        File[] markers = atlasTextures().toFile().listFiles((d, n) -> n.startsWith("marker_") && n.endsWith(".png"));
        assertTrue(markers != null && markers.length >= 10);
        for (File f : markers) {
            BufferedImage img = ImageIO.read(f);
            Set<Integer> opaque = new HashSet<>();
            for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
                int c = img.getRGB(x, y);
                if ((c >>> 24) >= 200) opaque.add(c & 0xFFFFFF);
            }
            for (int b : COLOURS) {
                int bg = MapColors.argb((byte) b) & 0xFFFFFF;
                double best = 0;
                for (int c : opaque) best = Math.max(best, Cartography.contrast(c, bg));
                assertTrue(best >= 3, String.format("%s on %06x: %.2f", f.getName(), bg, best));
            }
        }
    }
}
