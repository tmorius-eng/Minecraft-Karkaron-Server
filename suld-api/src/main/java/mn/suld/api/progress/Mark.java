package mn.suld.api.progress;

import java.util.Objects;
import java.util.UUID;

/** One progress mark: {@code (player, kind, key) → value}, with the moment it was written. */
public record Mark(UUID player, MarkKind kind, String key, long value, long at) {

    public Mark {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(kind, "kind");
        if (key == null || key.isBlank() || key.length() > 64) throw new IllegalArgumentException("key must be 1..64 chars");
    }
}
