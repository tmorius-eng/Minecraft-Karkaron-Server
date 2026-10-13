package mn.suld.api.persistence;

import mn.suld.api.dungeon.heroic.HeroicLadder;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Heroic keys (V24, docs/HEROIC_DUNGEONS.md): per player and dungeon, the highest key it may open and the best key it
 * timed. {@link #raise} never lowers anything (GREATEST in SQL), so results written in any order end the same.
 */
public interface HeroicRepository {

    /** A player's keys in one dungeon; a dungeon never run is {@code new Key(id, 1, 0, 0)}. */
    record Key(String dungeonId, int open, int best, int bestSeconds) {
        public static Key fresh(String dungeonId) {
            return new Key(dungeonId, HeroicLadder.MIN_KEY, 0, 0);
        }

        /**
         * The higher of two records of this dungeon's keys (a read that comes back after a run already raised the
         * cache): each key at its maximum, the best time going with the best key (the faster one at the same key).
         * Commutative and idempotent, like the writes.
         */
        public Key max(Key other) {
            int b = Math.max(best, other.best);
            int s = b == 0 ? 0 : best == other.best ? positiveMin(bestSeconds, other.bestSeconds)
                    : best > other.best ? bestSeconds : other.bestSeconds;
            return new Key(dungeonId, Math.max(open, other.open), b, s);
        }

        private static int positiveMin(int x, int y) {
            return x <= 0 ? y : y <= 0 ? x : Math.min(x, y);
        }
    }

    /** A finished heroic run, logged once. {@code members} are the party's names, comma-separated. */
    record Run(UUID runId, String dungeonId, int key, HeroicLadder.Outcome outcome, int clockSeconds, int deaths,
               String affixes, String members, String weekId, long finishedAt) {
    }

    /** Every dungeon the player has keys in (absent dungeons are {@link Key#fresh}). */
    CompletableFuture<Map<String, Key>> keys(UUID player);

    /** Raises the player's keys in a dungeon: open and best to at least these, the best time when the key is the best's. */
    CompletableFuture<Void> raise(UUID player, String dungeonId, int open, int best, int bestSeconds, long now);

    CompletableFuture<Void> logRun(Run run);

    /** The best timed runs of a dungeon: highest key first, then the fastest clock. */
    CompletableFuture<List<Run>> board(String dungeonId, int limit);
}
