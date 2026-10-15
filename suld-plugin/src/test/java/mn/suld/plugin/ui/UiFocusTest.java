package mn.suld.plugin.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * One HUD policy for full-screen views. In the live test of 2026-10-15 the welcome chest opened under three boss bars,
 * the corner minimap, a waypoint card in the chat and the «✦ SÜLD ✦» sidebar, because only the sky windows hid them.
 */
class UiFocusTest {

    @Test
    void theDeathScreenASkyWindowOrASuldChestIsModal() {
        assertFalse(UiFocus.modal(false, false, false), "walking about: the HUD is up");
        assertTrue(UiFocus.modal(false, false, true), "the welcome chest");
        assertTrue(UiFocus.modal(false, true, false), "/map, /menu, /skills");
        assertTrue(UiFocus.modal(true, false, false), "the death screen");
    }

    @Test
    void onlySuldChestsCountNotTheVanillaInventory() {
        assertFalse(UiFocus.isSuldChest(null), "no top inventory holder (a crafting grid, the E screen)");
        assertFalse(UiFocus.isSuldChest(new Object()), "a chest block or another plugin's holder");
    }

    /** The edge detector: a flip either way fires once; the first sample of a (re)joined player fires nothing. */
    @Test
    void aFlipFiresOnceEachWayAndARejoinStartsFresh() {
        Map<UUID, Boolean> last = new HashMap<>();
        UUID p = UUID.randomUUID();
        assertFalse(UiFocus.flipped(last, p, false), "first sample");
        assertFalse(UiFocus.flipped(last, p, false));
        assertTrue(UiFocus.flipped(last, p, true), "the welcome chest opens: the sidebar hides now, not in 2 s");
        assertFalse(UiFocus.flipped(last, p, true), "still open (menu-to-menu clicks stay modal)");
        assertTrue(UiFocus.flipped(last, p, false), "ESC: the sidebar comes back the same tick");
        last.remove(p);
        assertFalse(UiFocus.flipped(last, p, true), "after a quit the map entry is gone: a rejoin is a first sample");
    }

    static String main(String rel) throws IOException {
        for (String root : new String[]{"src/main/java/", "suld-plugin/src/main/java/"}) {
            Path p = Path.of(root + "mn/suld/plugin/" + rel);
            if (Files.exists(p)) return Files.readString(p, StandardCharsets.UTF_8);
        }
        throw new IllegalStateException(rel);
    }

    /** Every HUD element asks the same question, so no view can disagree with another again. */
    @Test
    void everyHudElementFollowsTheOnePolicy() throws IOException {
        String plugin = main("SuldPlugin.java");
        assertTrue(plugin.contains("BossBars.start(this, mn.suld.plugin.ui.UiFocus::isModal)"), "boss bars");
        assertTrue(plugin.contains("minimap.busy = mn.suld.plugin.ui.UiFocus::isModal"), "corner minimap");
        assertTrue(plugin.contains("screenMarkers.busy = mn.suld.plugin.ui.UiFocus::isModal"), "screen markers");
        assertTrue(plugin.contains("UiFocus.onChange(") && plugin.contains("UiFocus.start(this)"), "the sidebar's refresh on a flip");
        assertTrue(main("hud/HudService.java").contains("UiFocus.isModal(player)"), "the sidebar steps aside");
        assertTrue(main("gui/MenuListener.java").contains("!mn.suld.plugin.ui.UiFocus.isModal(p)) menus.welcome(p)"),
                "the welcome chest never opens over another full-screen view");
    }
}
