package mn.suld.plugin.gui;

import mn.suld.api.clazz.ClassChangeRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The class pick's confirm page tells the truth about changing class later (audit 2026-10-13 BUG-ONB-01). */
class ClassConfirmNoteTest {

    @Test
    void theConfirmPageDoesNotCallThePickPermanent() {
        String note = String.join(" ", ClassSelectionGui.CHANGE_NOTE);
        assertFalse(note.contains("солигдохгүй") || note.contains("байнгын"), note);
        assertTrue(note.contains("Академи"), note);
        // the free-change window it names is the one ClassChangeRules applies
        assertTrue(note.contains(ClassChangeRules.GRACE_MAX_LEVEL + "-р түвшин"), note);
        assertTrue(note.contains(ClassChangeRules.GRACE_MAX_MINUTES / 60 + " цаг"), note);
    }
}
