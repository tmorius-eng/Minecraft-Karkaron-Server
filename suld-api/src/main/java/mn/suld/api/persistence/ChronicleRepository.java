package mn.suld.api.persistence;

import mn.suld.api.chronicle.ChronicleEntry;
import mn.suld.api.chronicle.ChronicleKind;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Storage of the Баатрын Хроник (V19): an append-only log plus one record row per (kind, subject). A first is an
 * INSERT that either succeeds or hits the primary key; a record is replaced only when better. Both commit with the
 * log row in one transaction.
 */
public interface ChronicleRepository {

    /** Claim a FIRST: the entry is written and returned (with its id) only if nobody holds the subject yet. */
    CompletableFuture<Optional<ChronicleEntry>> claimFirst(ChronicleEntry entry);

    /** Write a RECORD if it beats the current one; returns the written entry, empty when the record stands. */
    CompletableFuture<Optional<ChronicleEntry>> recordIfBetter(ChronicleEntry entry);

    /** Append a LOG entry; returns it with its id. */
    CompletableFuture<ChronicleEntry> append(ChronicleEntry entry);

    /** Newest visible entries. */
    CompletableFuture<List<ChronicleEntry>> latest(int limit);

    CompletableFuture<List<ChronicleEntry>> byKind(ChronicleKind kind, int limit);

    /** Entries where the player is the hero or in the party. */
    CompletableFuture<List<ChronicleEntry>> byPlayer(UUID player, int limit);

    /** The current record or first of a subject, if any. */
    CompletableFuture<Optional<ChronicleEntry>> holder(ChronicleKind kind, String subjectKey);

    /** Hide an entry from every listing (it is never deleted); false when there is no such id. */
    CompletableFuture<Boolean> hide(long id);
}
