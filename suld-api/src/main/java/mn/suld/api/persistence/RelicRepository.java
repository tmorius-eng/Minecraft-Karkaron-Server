package mn.suld.api.persistence;

import mn.suld.api.relic.CasResult;
import mn.suld.api.relic.RelicHistoryEntry;
import mn.suld.api.relic.RelicRecord;
import mn.suld.api.relic.RelicTransition;
import mn.suld.api.relic.ShrineLocation;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Storage for world-unique relics. The database row is the single source of truth.
 * Implementations MUST make {@link #apply} an atomic compare-and-set on the version and write
 * the history row in the same transaction.
 */
public interface RelicRepository {

    /**
     * Create the relic row if it does not exist yet (first boot), else leave it untouched.
     * {@code freshItemUuid} is only used on creation; the stored one wins forever after.
     */
    CompletableFuture<RelicRecord> ensure(String key, UUID freshItemUuid);

    CompletableFuture<List<RelicRecord>> loadAll();

    CompletableFuture<CasResult> apply(RelicTransition transition);

    /** Set or move the shrine. Does not change ownership or generation. */
    CompletableFuture<RelicRecord> setShrine(String key, ShrineLocation shrine, String actor);

    /** Newest first. */
    CompletableFuture<List<RelicHistoryEntry>> history(String key, int limit);
}
