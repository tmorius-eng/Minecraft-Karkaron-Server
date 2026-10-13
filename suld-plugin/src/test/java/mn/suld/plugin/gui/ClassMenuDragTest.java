package mn.suld.plugin.gui;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nothing can be dragged into the class window, whose slots are cleared on a pick (external audit 2026-10-13 F02). */
class ClassMenuDragTest {

    @Test
    void aDragTouchingTheClassWindowIsRefused() {
        ClassSelectionHolder holder = new ClassSelectionHolder();
        assertTrue(ClassSelectionGui.cancelsDrag(holder, Set.of(3), 27));
        assertTrue(ClassSelectionGui.cancelsDrag(holder, Set.of(40, 26), 27), "one top slot is enough");
        assertFalse(ClassSelectionGui.cancelsDrag(holder, Set.of(27, 40), 27), "a drag only inside the player's own bag");
        assertFalse(ClassSelectionGui.cancelsDrag(new Object(), Set.of(3), 27), "another window");
    }
}
