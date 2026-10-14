package mn.suld.api.persistence;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Deeds, achievements and the season's pass (V28, docs/ACHIEVEMENTS_AND_PASS.md). Every write commutes: counts and
 * points add, best values keep the greater, an unlock, a claim and an opened heroic track are inserted once. So
 * batches written in any order, on any number of threads, end at the same state.
 */
public interface AchievementRepository {

    /** A player's deeds ({@code DEED|subject} → value) and unlocks (achievement id → when). */
    record Stored(Map<String, Long> deeds, Map<String, Long> unlocked) {
        public Stored {
            deeds = Map.copyOf(deeds);
            unlocked = Map.copyOf(unlocked);
        }
    }

    /** A player's pass in one season: the points, whether the heroic track is open, the rewards claimed ({@code FREE/5}). */
    record Pass(long points, boolean heroic, Set<String> claimed) {
        public Pass {
            claimed = Set.copyOf(claimed);
        }

        public static final Pass NONE = new Pass(0, false, Set.of());
    }

    CompletableFuture<Stored> load(UUID player);

    /**
     * One batch in one transaction: {@code counts} added to their keys, {@code bests} kept if greater, {@code unlocks}
     * (id → when) inserted where absent.
     */
    CompletableFuture<Void> write(UUID player, Map<String, Long> counts, Map<String, Long> bests, Map<String, Long> unlocks, long now);

    CompletableFuture<Pass> pass(UUID player, String season);

    /** Adds {@code points} (> 0) to the player's season. */
    CompletableFuture<Void> addPoints(UUID player, String season, long points, long now);

    /** Claims a reward: true when this call made the claim, false when it was claimed already. */
    CompletableFuture<Boolean> claim(UUID player, String season, String rewardKey, long now);

    /** Opens the heroic track: true when this call opened it. */
    CompletableFuture<Boolean> openHeroic(UUID player, String season, String source, long now);

    /** Staff/QA: forgets a player's deeds, unlocks and every season's pass. */
    CompletableFuture<Void> reset(UUID player);
}
