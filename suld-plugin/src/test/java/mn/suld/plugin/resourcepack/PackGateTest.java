package mn.suld.plugin.resourcepack;

import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ONB-001 (audit 2026-10-11): the first-join class menu must not open over the pack prompt. It waits for the
 * client's answer: accepted+loaded, declined, failed, or a bounded silence.
 */
class PackGateTest {

    private static final long PROMPT = 30_000, DOWNLOAD = 90_000;

    @Test
    void nothingRunsWhileThePromptIsUnanswered() {
        PackGate g = new PackGate(PROMPT, DOWNLOAD);
        UUID p = UUID.randomUUID();
        AtomicInteger ran = new AtomicInteger();
        g.await(p, 0, ran::incrementAndGet);
        assertTrue(g.due(PROMPT - 1).isEmpty());
        assertTrue(g.pending(p));
        assertEquals(0, ran.get());
    }

    @Test
    void acceptedThenLoadedRunsOnLoad() {
        PackGate g = new PackGate(PROMPT, DOWNLOAD);
        UUID p = UUID.randomUUID();
        AtomicInteger ran = new AtomicInteger();
        g.await(p, 0, ran::incrementAndGet);
        assertNull(g.answer(p, PackGate.Answer.IN_PROGRESS, 1_000));   // ACCEPTED
        assertNull(g.answer(p, PackGate.Answer.IN_PROGRESS, 5_000));   // DOWNLOADED
        assertTrue(g.due(PROMPT + 1).isEmpty(), "accepted: the download gets its own budget");
        Runnable r = g.answer(p, PackGate.Answer.TERMINAL, 8_000);      // SUCCESSFULLY_LOADED
        assertNotNull(r);
        r.run();
        assertEquals(1, ran.get());
        assertFalse(g.pending(p));
        assertNull(g.answer(p, PackGate.Answer.TERMINAL, 9_000), "runs once");
    }

    @Test
    void declinedOrFailedRunsAtOnce() {
        for (Status s : new Status[]{Status.DECLINED, Status.FAILED_DOWNLOAD, Status.INVALID_URL, Status.FAILED_RELOAD, Status.DISCARDED}) {
            PackGate g = new PackGate(PROMPT, DOWNLOAD);
            UUID p = UUID.randomUUID();
            g.await(p, 0, () -> { });
            assertNotNull(g.answer(p, ResourcePackService.answer(s), 100), s.name());
        }
    }

    @Test
    void silenceAndStalledDownloadsAreBounded() {
        PackGate g = new PackGate(PROMPT, DOWNLOAD);
        UUID silent = UUID.randomUUID(), stalled = UUID.randomUUID();
        AtomicInteger ran = new AtomicInteger();
        g.await(silent, 0, ran::incrementAndGet);
        g.await(stalled, 0, ran::incrementAndGet);
        g.answer(stalled, PackGate.Answer.IN_PROGRESS, 10_000);
        g.due(PROMPT).forEach(Runnable::run);
        assertEquals(1, ran.get(), "no answer in 30 s: open anyway");
        assertTrue(g.due(10_000 + DOWNLOAD - 1).isEmpty());
        g.due(10_000 + DOWNLOAD).forEach(Runnable::run);
        assertEquals(2, ran.get(), "accepted but never loaded: open after the download budget");
    }

    @Test
    void quitDropsThePendingAction() {
        PackGate g = new PackGate(PROMPT, DOWNLOAD);
        UUID p = UUID.randomUUID();
        g.await(p, 0, () -> { throw new AssertionError("must not run"); });
        g.forget(p);
        assertTrue(g.due(Long.MAX_VALUE).isEmpty());
        assertNull(g.answer(p, PackGate.Answer.TERMINAL, 1));
    }

    @Test
    void everyClientStatusIsClassified() {
        for (Status s : Status.values()) assertNotNull(ResourcePackService.answer(s), s.name());
        assertEquals(PackGate.Answer.IN_PROGRESS, ResourcePackService.answer(Status.ACCEPTED));
        assertEquals(PackGate.Answer.IN_PROGRESS, ResourcePackService.answer(Status.DOWNLOADED));
        assertEquals(PackGate.Answer.TERMINAL, ResourcePackService.answer(Status.SUCCESSFULLY_LOADED));
    }
}
