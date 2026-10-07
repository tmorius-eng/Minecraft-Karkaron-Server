package mn.suld.plugin.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The damage chip: holds the previous health after any drop, then snaps down; healing moves it up at once. */
class HudChipTest {

    @Test
    void holdsAfterADropThenSnaps() {
        HudPanel.Chip c = new HudPanel.Chip();
        assertEquals(10, c.update(10, 0));
        assertEquals(10, c.update(6, 1000), "a drop (any cause) shows the old value");
        assertEquals(10, c.update(6, 1500));
        assertEquals(6, c.update(6, 1000 + HudPanel.Chip.HOLD_MS + 1), "then snaps to the real value");
        assertEquals(9, c.update(9, 3000), "healing is shown at once");
        assertEquals(9, c.update(4, 3100));
        assertEquals(9, c.update(2, 3500), "a second drop inside the hold keeps the first height and restarts the hold");
        assertEquals(9, c.update(2, 3500 + HudPanel.Chip.HOLD_MS - 1));
        assertEquals(2, c.update(2, 3500 + HudPanel.Chip.HOLD_MS + 1));
    }

    @Test
    void aHitStartsTheHoldFromTheHealthBeforeIt() {
        HudPanel.Chip c = new HudPanel.Chip();
        c.update(20, 0);
        c.hit(20, 5000);
        assertEquals(20, c.update(14, 5100));
        assertEquals(20, c.update(14, 5100 + HudPanel.Chip.HOLD_MS - 1), "held from the drop it saw");
        assertEquals(14, c.update(14, 5100 + HudPanel.Chip.HOLD_MS + 1));
    }
}
