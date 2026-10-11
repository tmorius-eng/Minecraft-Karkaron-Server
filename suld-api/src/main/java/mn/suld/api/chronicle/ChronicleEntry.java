package mn.suld.api.chronicle;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * One line of the Хроник ({@code suld_chronicle}). Append-only: an entry is never deleted, an admin can only hide it.
 *
 * @param id         the row id, 0 before it is written
 * @param subjectKey what the entry is about (a dungeon id, site id, relic key or cycle id)
 * @param player     the hero, or null for a server-wide entry
 * @param party      the names of everyone who shared the deed (the clearing party, the campaign's top three)
 * @param value      the record's measure (seconds, units), or null
 * @param detail     a short Mongolian line for the display
 * @param cycleId    the Хуралдай cycle the entry happened in
 */
public record ChronicleEntry(long id, ChronicleKind kind, String subjectKey, @Nullable UUID player, String playerName,
                             List<String> party, String clanTag, @Nullable Long value, String detail, String cycleId, long at) {

    public ChronicleEntry {
        if (kind == null) throw new IllegalArgumentException("kind");
        if (subjectKey == null || subjectKey.isBlank() || subjectKey.length() > 64) throw new IllegalArgumentException("subjectKey 1..64");
        playerName = playerName == null ? "" : playerName;
        party = party == null ? List.of() : List.copyOf(party);
        clanTag = clanTag == null ? "" : clanTag;
        detail = detail == null ? "" : (detail.length() > 255 ? detail.substring(0, 255) : detail);
        cycleId = cycleId == null ? "" : cycleId;
    }

    public static ChronicleEntry of(ChronicleKind kind, String subjectKey, @Nullable UUID player, String playerName, List<String> party,
                                    String clanTag, @Nullable Long value, String detail, String cycleId, long at) {
        return new ChronicleEntry(0, kind, subjectKey, player, playerName, party, clanTag, value, detail, cycleId, at);
    }

    public ChronicleEntry withId(long newId) {
        return new ChronicleEntry(newId, kind, subjectKey, player, playerName, party, clanTag, value, detail, cycleId, at);
    }
}
