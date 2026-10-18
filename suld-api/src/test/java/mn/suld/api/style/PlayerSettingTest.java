package mn.suld.api.style;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSettingTest {

    @Test
    void bitsAreUniqueAndEverySettingStartsAtItsDefault() {
        Set<Integer> bits = new HashSet<>();
        for (PlayerSetting s : PlayerSetting.values()) {
            assertTrue(bits.add(s.bit()), s + " shares a bit");
            assertTrue(s.bit() < 63);
            assertNotEquals(8, s.bit(), "bit 8 was the minimap while it started on: it is never given out again");
            assertEquals(s.onByDefault(), s.enabled(0), s + " starts at its default");
            assertEquals(s != PlayerSetting.MINIMAP, s.onByDefault(), s + ": only the minimap starts off");
        }
    }

    @Test
    void theMinimapStartsOffAndATurnSwitchesItOn() {
        PlayerStyle s = new PlayerStyle(UUID.randomUUID());
        assertFalse(s.setting(PlayerSetting.MINIMAP), "off for everyone who never asked for it");
        assertTrue(s.toggleSetting(PlayerSetting.MINIMAP));
        assertTrue(PlayerStyle.restore(s.snapshotAndClean()).setting(PlayerSetting.MINIMAP), "the choice is kept");
        // a player who turned the old minimap off stored bit 8: it stays off, and nothing else moves
        long oldOff = 1L << 8;
        assertFalse(PlayerSetting.MINIMAP.enabled(oldOff));
        for (PlayerSetting other : PlayerSetting.values()) {
            if (other != PlayerSetting.MINIMAP) assertTrue(other.enabled(oldOff), other + " is untouched by the retired bit");
        }
    }

    @Test
    void togglingFlipsOnlyThatSettingAndSurvivesTheSnapshot() {
        PlayerStyle s = new PlayerStyle(UUID.randomUUID());
        assertTrue(s.setting(PlayerSetting.SIDEBAR));
        assertFalse(s.toggleSetting(PlayerSetting.SIDEBAR));
        assertTrue(s.isDirty());
        assertFalse(s.setting(PlayerSetting.SIDEBAR));
        assertTrue(s.setting(PlayerSetting.TIPS));
        PlayerStyle back = PlayerStyle.restore(s.snapshotAndClean());
        assertFalse(back.setting(PlayerSetting.SIDEBAR));
        assertTrue(back.setting(PlayerSetting.TIPS));
        assertTrue(back.toggleSetting(PlayerSetting.SIDEBAR));
        assertEquals(0, back.settingsOff());
    }
}
