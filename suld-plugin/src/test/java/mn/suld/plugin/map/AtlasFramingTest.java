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
