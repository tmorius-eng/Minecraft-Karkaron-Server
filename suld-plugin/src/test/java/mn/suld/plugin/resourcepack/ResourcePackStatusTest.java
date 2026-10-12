package mn.suld.plugin.resourcepack;

import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for fc0c47e: a status with no / the nil pack id is SÜLD's (f91b914 ignored it and the onboarding gate and
 * the glyph HUD stalled); only a status naming another pack is ignored.
 */
class ResourcePackStatusTest {

    @Test
    void ourIdTheNilIdAndNoIdAreOurs() {
        assertTrue(ResourcePackService.ours(ResourcePackService.packId()));
        assertTrue(ResourcePackService.ours(new UUID(0L, 0L)), "the nil id (Mineflayer, some clients)");
        assertTrue(ResourcePackService.ours(null), "no id at all");
    }

    @Test
    void anotherPacksStatusIsNotOurs() {
        assertFalse(ResourcePackService.ours(UUID.randomUUID()));
    }

    @Test
    void everyFinalStatusEndsTheOnboardingWaitAndOnlyTheTwoStepsInBetweenKeepIt() {
        for (Status s : Status.values()) {
            PackGate.Answer a = ResourcePackService.answer(s);
            if (s == Status.ACCEPTED || s == Status.DOWNLOADED) assertEquals(PackGate.Answer.IN_PROGRESS, a, s.name());
            else assertEquals(PackGate.Answer.TERMINAL, a, s + ": a failed or declined pack must still let the class menu open");
        }
    }
}
