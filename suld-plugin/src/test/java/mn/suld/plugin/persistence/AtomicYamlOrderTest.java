package mn.suld.plugin.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Many async writes of one file (a party clear saves the dungeon fatigue once per member, audit 2026-10-13): the file
 * ends with the newest snapshot, never an older one or a torn mix, and no writer fails on a shared .tmp.
 */
class AtomicYamlOrderTest {

    @Test
    void concurrentWritesLeaveTheNewestSnapshot(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("fatigue.yml");
        int n = 24;
        List<long[]> taken = new ArrayList<>();
        for (int i = 0; i < n; i++) taken.add(new long[]{i, AtomicYaml.next(file)}); // snapshots in caller order
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) { // the threads run in the worst order: newest first
            long[] t = taken.get(i);
            String text = ("k: " + t[0] + "\n").repeat(2000);
            fs.add(pool.submit(() -> {
                go.await();
                return AtomicYaml.writeIfNewest(file, text, t[1]);
            }));
        }
        go.countDown();
        for (Future<?> f : fs) f.get(10, TimeUnit.SECONDS); // none throws (no NoSuchFileException on a shared .tmp)
        pool.shutdown();
        String body = Files.readString(file);
        assertEquals(("k: " + (n - 1) + "\n").repeat(2000), body);
        assertFalse(Files.exists(dir.resolve("fatigue.yml.tmp")));
    }
}
