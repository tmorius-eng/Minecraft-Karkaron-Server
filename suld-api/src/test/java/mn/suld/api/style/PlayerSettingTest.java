package mn.suld.api.style;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSettingTest {

    @Test
    void bitsAreUniqueAndEverySettingStartsOn() {
        Set<Integer> bits = new HashSet<>();
        for (PlayerSetting s : PlayerSetting.values()) {
            assertTrue(bits.add(s.bit()), s + " shares a bit");
            assertTrue(s.bit() < 63);
            assertTrue(s.enabled(0), s + " must be on by default");
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
