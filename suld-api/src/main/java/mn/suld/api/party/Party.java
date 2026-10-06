package mn.suld.api.party;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * An in-memory party of players that can enter a dungeon together.
 * The leader is always the first member in iteration order.
 * Thread-safety: single-threaded main-thread access assumed (Bukkit scheduler).
 */
public final class Party {

    private final UUID id;
    private final int capacity;
    private final LinkedHashSet<UUID> members; // ordered; first = current leader
    private PartyState state;

    private Party(UUID id, UUID leader, int capacity) {
        this.id = id;
        this.capacity = capacity;
        this.members = new LinkedHashSet<>();
        this.members.add(leader);
        this.state = PartyState.FORMING;
    }

    /** Create a new party with the given player as leader. */
    public static Party create(UUID leader, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        return new Party(UUID.randomUUID(), leader, capacity);
    }

    public UUID id() { return id; }
    public int capacity() { return capacity; }
    public PartyState state() { return state; }

    /** The current leader — first member in the set. */
    public UUID leader() { return members.iterator().next(); }

    public boolean isLeader(UUID player) { return leader().equals(player); }
    public boolean contains(UUID player) { return members.contains(player); }
    public boolean isFull() { return members.size() >= capacity; }
    public int size() { return members.size(); }

    /** Immutable snapshot of members (includes leader). */
    public Set<UUID> members() { return Collections.unmodifiableSet(members); }

    /**
     * Add a player. Returns {@code false} if full, already a member, or disbanded.
     */
    public boolean addMember(UUID player) {
        if (state == PartyState.DISBANDED) return false;
        if (isFull()) return false;
        return members.add(player);
    }

    /**
     * Remove a player. If the removed player was the leader, the next member
     * becomes leader. Returns {@code true} if the member was present.
     * Disbands if the party drops to zero members.
     */
    public boolean removeMember(UUID player) {
        boolean removed = members.remove(player);
        if (removed && members.isEmpty()) {
            state = PartyState.DISBANDED;
        }
        return removed;
    }

    /**
     * Transfer leadership to another current member.
     * Returns {@code false} if the target is not a member.
     */
    public boolean setLeader(UUID newLeader) {
        if (!members.contains(newLeader)) return false;
        // Re-insert newLeader at the front by rebuilding the set.
        LinkedHashSet<UUID> reordered = new LinkedHashSet<>();
        reordered.add(newLeader);
        members.forEach(m -> { if (!m.equals(newLeader)) reordered.add(m); });
        members.clear();
        members.addAll(reordered);
        return true;
    }

    public void enterDungeon() {
        if (state == PartyState.FORMING || state == PartyState.READY) {
            state = PartyState.IN_DUNGEON;
        }
    }

    public void exitDungeon() {
        if (state == PartyState.IN_DUNGEON) {
            state = PartyState.FORMING;
        }
    }

    public void ready() {
        if (state == PartyState.FORMING) state = PartyState.READY;
    }

    public void disband() {
        state = PartyState.DISBANDED;
    }

    public boolean isDisbanded() { return state == PartyState.DISBANDED; }

    /** First member that is not the leader, if any. */
    public Optional<UUID> firstNonLeader() {
        return members.stream().filter(m -> !isLeader(m)).findFirst();
    }

    @Override
    public String toString() {
        return "Party{id=" + id + ", leader=" + leader() + ", size=" + size() + "/" + capacity + ", state=" + state + "}";
    }
}
