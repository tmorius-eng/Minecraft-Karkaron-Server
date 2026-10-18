package mn.suld.plugin.map;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The atlas fills the screen (docs/WORLD_MAP.md): seen from 6 blocks it was a small frame in a sea of navy with its
 * title under the boss bars. Checked at the client's default 70° field of view on 16:9 and 4:3 screens.
 */
class AtlasFramingTest {

    /** The frame's top edge (its title plaque) and the cartouche's text, in panel blocks. */
    static final double FRAME_TOP = AtlasSky.FRONT_V + AtlasSky.FRONT_H / 2;
    static final double CART_BOTTOM = AtlasSky.CART_V - AtlasSky.LINE * AtlasSky.CART_SCALE / 2;
    static final double TITLE_TOP = AtlasSky.TITLE_V + AtlasSky.LINE * 1.2 / 2;

    @Test
    void theTitlePlaqueIsWhollyOnTheScreen() {
        assertTrue(AtlasSky.screenY(FRAME_TOP) <= 0.98, "frame top at " + AtlasSky.screenY(FRAME_TOP));
        assertTrue(AtlasSky.screenY(TITLE_TOP) < AtlasSky.screenY(FRAME_TOP), "«СҮЛДИЙН АТЛАС» sits inside the plaque");
    }

    @Test
    void theCoordinateLineClearsTheHotbarAndTheHearts() {
        double hudTop = -1 + 2 * AtlasSky.HUD_BOTTOM; // the hotbar, the XP bar and the hearts at GUI scale 3
        assertTrue(AtlasSky.screenY(CART_BOTTOM) >= hudTop, "cartouche at " + AtlasSky.screenY(CART_BOTTOM) + ", HUD up to " + hudTop);
    }

    @Test
    void theTabsOnBothSidesStayOnTheScreen() {
        double outer = AtlasSky.TAB_U + AtlasSky.TAB_W / 2;
        for (double aspect : new double[]{16.0 / 9, 4.0 / 3}) {
            assertTrue(AtlasSky.screenX(outer, aspect) < 0.95, "tabs at " + AtlasSky.screenX(outer, aspect) + " of a " + aspect + " screen");
        }
    }

    @Test
    void theBoardIsClearlyLargerThanFromTheOldSeat() {
        // the frame's height on the screen, against the 6-block camera's (where the margins were reported)
        double oldHeight = AtlasSky.FRONT_H / (6.0 * Math.tan(Math.toRadians(AtlasSky.FOV_DEG / 2)));
        double newHeight = AtlasSky.screenY(FRAME_TOP) - AtlasSky.screenY(FRAME_TOP - AtlasSky.FRONT_H);
        assertTrue(newHeight / oldHeight >= 1.12, "the board grew by " + newHeight / oldHeight);
        assertTrue(AtlasSky.DIST > 5.0, "a click on an entity reaches 3–5 blocks: the wall stays out of reach");
    }

    @Test
    void eachPlayersFitShowsTheFrameOnTheirScreenAsOnA70DegreeOne() {
        assertEquals(AtlasSky.DIST, AtlasSky.distFor(AtlasSky.FOV_DEFAULT), 1e-9, "the default is the framing checked above");
        for (int fov = AtlasSky.FOV_MIN; fov <= AtlasSky.FOV_CLIENT_MAX; fov += AtlasSky.FOV_STEP) {
            double d = AtlasSky.distFor(fov), t = Math.tan(Math.toRadians(fov / 2.0));
            // a panel height v shows at (v - EYE_UP) / (d tan(fov / 2)) on that screen: where screenY puts it at 70°
            for (double v : new double[]{FRAME_TOP, CART_BOTTOM, TITLE_TOP, 0}) {
                assertEquals(AtlasSky.screenY(v), (v - AtlasSky.EYE_UP) / (d * t), 1e-9, "fov " + fov + ", panel height " + v);
            }
        }
    }

    @Test
    void aWiderFieldOfViewBringsTheCameraCloser() {
        for (int fov = AtlasSky.FOV_MIN + AtlasSky.FOV_STEP; fov <= AtlasSky.FOV_MAX; fov += AtlasSky.FOV_STEP) {
            assertTrue(AtlasSky.distFor(fov) < AtlasSky.distFor(fov - AtlasSky.FOV_STEP), "fov " + fov);
        }
        // the owner's screen (Quake Pro, 110°) had the atlas at 70°'s distance: a small picture; its fit is about half as far
        assertEquals(2.57, AtlasSky.distFor(110), 0.02);
        // the closest fit still stands well in front of the wall (and nothing is in reach while the atlas is open)
        assertTrue(AtlasSky.distFor(AtlasSky.FOV_MAX) > 1.5, "closest camera at " + AtlasSky.distFor(AtlasSky.FOV_MAX));
        assertEquals(AtlasSky.FOV_MIN, AtlasSky.clampFov(1));
        assertEquals(AtlasSky.FOV_MAX, AtlasSky.clampFov(500));
    }

    @Test
    void theOldSizeStepsBecomeTheFovTheyMatched() {
        assertEquals(java.util.List.of(70, 80, 90, 100, 105),
                java.util.stream.IntStream.range(0, AtlasSky.OLD_SIZES.length).map(AtlasSky::fovForSize).boxed().toList());
        assertEquals("Атласын хэмжээ: FOV 110-д тааруулсан", AtlasSky.fitLabel(110));
        assertTrue(AtlasSky.fitLabel(AtlasSky.FOV_MAX).contains("дэлгэцээс том"), "past the client's 110 it says so");
    }

    @Test
    void theGuideFitsOnTheMapAndIsMongolian() {
        double[] box = AtlasSky.box(AtlasSky.helpLines(), AtlasSky.HELP_SCALE);
        double w = box[0] + AtlasSky.HELP_BORDER, h = box[1] + AtlasSky.HELP_BORDER; // AtlasSky.showHelp's frame
        assertTrue(w <= AtlasSky.COLS - 0.2, "the guide is " + w + " blocks wide on a " + AtlasSky.COLS + "-block map");
        assertTrue(h <= AtlasSky.ROWS - 0.2, "the guide is " + h + " blocks high");
        for (String[] l : AtlasSky.HELP) assertTrue(l[1].chars().anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC), l[1]);
        // its text reads at least as large as the places' labels
        assertTrue(AtlasSky.HELP_SCALE > AtlasSky.LABEL_SCALE && AtlasSky.TIP_SCALE > AtlasSky.LABEL_SCALE);
    }

    @Test
    void theSuldTooltipFramesNinePiecesAreInThePack() {
        java.nio.file.Path root = java.nio.file.Files.exists(java.nio.file.Path.of("../resourcepack")) ? java.nio.file.Path.of("../resourcepack") : java.nio.file.Path.of("resourcepack");
        for (String piece : new String[]{"tl", "t", "tr", "l", "m", "r", "bl", "b", "br"}) {
            for (String f : new String[]{"textures/entity/atlas/tip_" + piece + ".png", "models/entity/atlas/tip_" + piece + ".json",
                    "items/entity/atlas/tip_" + piece + ".json"}) {
                assertTrue(java.nio.file.Files.exists(root.resolve("assets/suld/" + f)), f + " (python3 tools/map/atlas_art.py tooltip .)");
            }
        }
    }

    @Test
    void aTooltipFrameIsWiderThanItsText() {
        double[] box = AtlasSky.box(java.util.List.of("Бүх ертөнц", "Бүх нутгийг нэг дор"), AtlasSky.TIP_SCALE);
        double text = mn.suld.plugin.gui.HubLayout.textWidth("Бүх нутгийг нэг дор") * AtlasSky.FONT_PX * AtlasSky.TIP_SCALE;
        assertTrue(box[0] > text + 2 * AtlasSky.TIP_BORDER * 0.6, "room for the gold border on both sides");
        assertTrue(box[1] > 2 * AtlasSky.LINE * AtlasSky.TIP_SCALE, "two lines and the border");
    }

    @Test
    void labelsAndMarkersReadLargerThanBefore() {
        assertTrue(AtlasSky.LABEL_SCALE > 0.42f && AtlasSky.CART_SCALE > 0.7f && AtlasSky.TIP_SCALE > 0.6f);
        assertTrue(AtlasSky.MARKER > 24 * AtlasSky.PX && AtlasSky.MARKER_BIG > 32 * AtlasSky.PX);
        assertEquals(0.045 * AtlasSky.LABEL_SCALE / 0.42, AtlasSky.LABEL_CHAR, 1e-12, "the label's box grows with its letters");
    }

    @Test
    void aFrameAlreadyShowingThePictureIsNotSentAgain() {
        AtlasFrames.Pictures p = new AtlasFrames.Pictures(2);
        byte[] land = new byte[128 * 128];
        Arrays.fill(land, (byte) 7);
        assertFalse(AtlasFrames.unchanged(p, 0, land), "nothing shown yet: sent");
        p.data[0] = land.clone();
        p.version[0] = 1;
        assertFalse(AtlasFrames.unchanged(p, 0, land), "not painted yet: sent");
        p.painted[0] = 1;
        assertTrue(AtlasFrames.unchanged(p, 0, land.clone()), "the same picture again (a tile elsewhere loaded): not sent");
        byte[] other = land.clone();
        other[5000] = 9;
        assertFalse(AtlasFrames.unchanged(p, 0, other), "one pixel changed: sent");
        assertFalse(AtlasFrames.unchanged(p, 1, land), "another frame is its own");
    }
}
