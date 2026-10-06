package mn.suld.api.relic;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/** One row of a relic's ownership history. */
public record RelicHistoryEntry(Instant at, RelicEvent event, @Nullable UUID owner, String actor, String detail) {
}
