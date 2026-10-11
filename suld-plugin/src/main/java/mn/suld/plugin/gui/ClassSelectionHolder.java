package mn.suld.plugin.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Marker {@link InventoryHolder} that identifies the SÜLD class-selection GUI,
 * so click/close handlers can recognise it without fragile title comparisons.
 */
public final class ClassSelectionHolder implements InventoryHolder {

    private Inventory inventory;
    /** The class shown on the confirm page (ONB-006), or null while the five classes are shown. */
    private mn.suld.api.clazz.PlayerClass pending;

    mn.suld.api.clazz.PlayerClass pending() {
        return pending;
    }

    void pending(mn.suld.api.clazz.PlayerClass clazz) {
        this.pending = clazz;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
