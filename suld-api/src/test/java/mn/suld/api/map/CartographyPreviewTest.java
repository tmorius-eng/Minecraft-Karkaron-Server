package mn.suld.api.map;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the baked world as PNGs for a person to look at (set SULD_CARTO_PREVIEW to a folder): the whole world at
 * zooms 4 and 3, the capital's surroundings at zoom 2, and a zoom-0 survey window. Does nothing otherwise.
 */
class CartographyPreviewTest {

    @Test
    void writesPreviewsWhenAsked() throws Exception {
        String dir = System.getenv("SULD_CARTO_PREVIEW");
        if (dir == null || dir.isBlank()) return;
        Path out = Path.of(dir);
        Files.createDirectories(out);
        BakedAtlas b = CartographyFixture.baked();
        for (int zoom = BakedAtlas.FINEST; zoom <= BakedAtlas.WIDEST; zoom++) {
            write(b.raster(zoom), b.width(zoom), b.height(zoom), out.resolve("world-z" + zoom + ".png").toFile());
        }
        // the survey ink at 1 block a pixel: 512 blocks around a spot north-west of the capital
        int n = 512;
        byte[] close = new byte[n * n];
        for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) close[z * n + x] = b.survey(0, -1400 + x, -1700 + z);
        write(close, n, n, out.resolve("survey-z0.png").toFile());
        System.out.println("[carto] baked in " + b.bakeMillis() + " ms, " + b.bytes() + " bytes; previews in " + out.toAbsolutePath());
    }

    static void write(byte[] px, int w, int h, File f) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int z = 0; z < h; z++) for (int x = 0; x < w; x++) {
            int c = MapColors.argb(px[z * w + x]);
            img.setRGB(x, z, c == 0 ? 0xFFEADAB4 : c);
        }
        ImageIO.write(img, "png", f);
    }
}
