package mn.suld.api.party;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Pure listing of parties looking for members: what they run, how many they are, which roles they still want. */
public final class PartyFinder {

    /** One line of {@code /party find}. */
    public record Listing(UUID partyId, UUID leader, int size, int capacity, String dungeonId, Set<PartyRole> missingRoles) {
        public int freeSlots() {
            return capacity - size;
        }
    }

    private PartyFinder() {
    }

    /**
     * Recruiting, not full, not inside a dungeon; filtered to {@code dungeonId} when given. Parties still missing a role
     * come first, then the ones with more free seats, then the oldest listing.
     */
    public static List<Listing> listings(Collection<Party> parties, String dungeonId) {
        List<Listing> out = new ArrayList<>();
        for (Party p : parties) {
            if (!p.isRecruiting() || p.isFull() || p.isDisbanded() || p.state() == PartyState.IN_DUNGEON) continue;
            if (dungeonId != null && !dungeonId.isBlank() && !dungeonId.equals(p.targetDungeonId())) continue;
            Set<PartyRole> missing = EnumSet.allOf(PartyRole.class);
            missing.removeAll(p.roles().values());
            out.add(new Listing(p.id(), p.leader(), p.size(), p.capacity(), p.targetDungeonId(), missing));
        }
        out.sort(Comparator.<Listing>comparingInt(l -> -l.missingRoles().size()).thenComparingInt(l -> -l.freeSlots())
                .thenComparing(l -> l.partyId().toString()));
        return out;
    }
}
