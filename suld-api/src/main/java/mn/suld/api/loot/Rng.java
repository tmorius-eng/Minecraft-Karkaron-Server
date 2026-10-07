package mn.suld.api.loot;

import java.util.Random;
import java.util.SplittableRandom;

/** Random numbers for loot and item generation. Inject a seeded one in tests to make every roll reproducible. */
public interface Rng {

    /** Uniform in [0, 1). */
    double nextDouble();

    /** Uniform in [0, bound). */
    int nextInt(int bound);

    /** Uniform in [min, max] inclusive. */
    default int between(int min, int max) {
        return max <= min ? min : min + nextInt(max - min + 1);
    }

    /** True with probability {@code p}. */
    default boolean chance(double p) {
        return p > 0 && nextDouble() < p;
    }

    static Rng seeded(long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        return new Rng() {
            @Override
            public double nextDouble() {
                return r.nextDouble();
            }

            @Override
            public int nextInt(int bound) {
                return r.nextInt(bound);
            }
        };
    }

    static Rng of(Random random) {
        return new Rng() {
            @Override
            public double nextDouble() {
                return random.nextDouble();
            }

            @Override
            public int nextInt(int bound) {
                return random.nextInt(bound);
            }
        };
    }

    /** Thread-safe default for the server. */
    static Rng threadLocal() {
        return new Rng() {
            @Override
            public double nextDouble() {
                return java.util.concurrent.ThreadLocalRandom.current().nextDouble();
            }

            @Override
            public int nextInt(int bound) {
                return java.util.concurrent.ThreadLocalRandom.current().nextInt(bound);
            }
        };
    }
}
