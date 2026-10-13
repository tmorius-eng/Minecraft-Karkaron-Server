package mn.suld.api.persistence;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The rule a late read of the keys is merged into the cache by (docs/HEROIC_DUNGEONS.md): it never lowers anything. */
class HeroicKeyTest {

    static final String D = "dungeon.khasar_den";

    @Test
    void eachKeyAtItsMaximumAndTheBestTimeGoesWithTheBestKey() {
        HeroicRepository.Key a = new HeroicRepository.Key(D, 5, 4, 400);
        HeroicRepository.Key b = new HeroicRepository.Key(D, 3, 6, 520);
        assertEquals(new HeroicRepository.Key(D, 5, 6, 520), a.max(b), "the better key's own time, even if slower");
        HeroicRepository.Key c = new HeroicRepository.Key(D, 4, 4, 380);
        assertEquals(new HeroicRepository.Key(D, 5, 4, 380), a.max(c), "the same best key: the faster time");
        assertEquals(new HeroicRepository.Key(D, 5, 4, 400), a.max(HeroicRepository.Key.fresh(D)), "a fresh record changes nothing");
        assertEquals(new HeroicRepository.Key(D, 2, 0, 0),
                new HeroicRepository.Key(D, 2, 0, 0).max(HeroicRepository.Key.fresh(D)), "no best: no time");
    }

    @Test
    void commutativeAndIdempotent() {
        Random r = new Random(7);
        for (int i = 0; i < 500; i++) {
            HeroicRepository.Key a = random(r), b = random(r);
            assertEquals(a.max(b), b.max(a), a + " / " + b);
            assertEquals(a.max(b), a.max(b).max(b), "idempotent: " + a + " / " + b);
            assertEquals(a, a.max(a));
        }
    }

    @Test
    void mergingAgreesWithTheRepositoryWhateverTheOrder() {
        // the writes of a player's runs, applied in any order to the in-memory rule, end where merging them ends
        List<int[]> writes = new ArrayList<>();
        for (int k = 1; k <= 9; k++) writes.add(new int[]{k + 1, k, 600 - 20 * k});
        writes.add(new int[]{10, 9, 300});
        writes.add(new int[]{10, 0, 0}); // an untimed result only moves the open key
        Collections.shuffle(writes, new Random(3));
        InMemoryHeroicRepository repo = new InMemoryHeroicRepository();
        UUID p = UUID.randomUUID();
        HeroicRepository.Key merged = HeroicRepository.Key.fresh(D);
        for (int[] w : writes) {
            repo.raise(p, D, w[0], w[1], w[2], 1).join();
            merged = merged.max(new HeroicRepository.Key(D, w[0], w[1], w[1] == 0 ? 0 : w[2]));
        }
        assertEquals(repo.keys(p).join().get(D), merged);
        assertEquals(new HeroicRepository.Key(D, 10, 9, 300), merged);
    }

    private static HeroicRepository.Key random(Random r) {
        int best = r.nextInt(5);
        return new HeroicRepository.Key(D, 1 + r.nextInt(6), best, best == 0 ? 0 : 1 + r.nextInt(4) * 100);
    }
}
