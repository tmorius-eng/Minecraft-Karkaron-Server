package mn.suld.api.persistence;

import mn.suld.api.style.PlayerStyle;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Storage of {@link PlayerStyle} (rank, cosmetics, claimed level rewards). */
public interface StyleRepository {

    CompletableFuture<Optional<PlayerStyle.Snapshot>> load(UUID player);

    /** Save rank, cosmetics and claimed rewards. Never writes credits (see {@link #addCredits}). */
    CompletableFuture<Void> save(PlayerStyle.Snapshot style);

    /**
     * Atomically add {@code delta} credits (negative = spend). Spending more than the balance changes nothing.
     *
     * @return the new balance, or {@code -1} if the spend was refused for lack of credits
     */
    CompletableFuture<Long> addCredits(UUID player, long delta);
}
