package mn.suld.api.clan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable copy of a clan taken on the main thread, safe to hand to an async save. */
public record ClanSnapshot(UUID id, String name, String tag, Instant createdAt, long exp, long version,
                           List<ClanMember> members) {
    public ClanSnapshot {
        members = List.copyOf(members);
    }
}
