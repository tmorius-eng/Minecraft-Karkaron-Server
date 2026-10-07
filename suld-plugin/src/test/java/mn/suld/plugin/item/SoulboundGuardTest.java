package mn.suld.plugin.item;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoulboundGuardTest {

    @Test
    void onlyThePlayersOwnInventoryAndSuldMenusAreOwnViews() {
        assertTrue(SoulboundGuard.ownView("CRAFTING", false)); // the inventory screen (2x2 grid)
        assertTrue(SoulboundGuard.ownView("PLAYER", false));
        assertTrue(SoulboundGuard.ownView("CHEST", true)); // a SÜLD menu: it cancels its own clicks
    }

    @Test
    void everyContainerABoundItemCouldEscapeIntoIsRefused() {
        for (String t : List.of("CHEST", "ENDER_CHEST", "SHULKER_BOX", "BARREL", "HOPPER", "DROPPER", "DISPENSER", "ANVIL",
                "GRINDSTONE", "SMITHING", "WORKBENCH", "CRAFTER", "MERCHANT", "ENCHANTING", "FURNACE", "BEACON", "LECTERN")) {
            assertFalse(SoulboundGuard.ownView(t, false), t);
        }
    }

    @Test
    void allBundleColoursAreBundles() {
        long bundles = java.util.Arrays.stream(Material.values()).filter(m -> !m.isLegacy()).filter(SoulboundGuard::isBundle).count();
        assertEquals(17, bundles);
        assertTrue(SoulboundGuard.isBundle(Material.RED_BUNDLE));
        assertFalse(SoulboundGuard.isBundle(Material.SHULKER_BOX));
    }
}
