package mn.suld.api.clan;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A persistent clan (овог). Mutated only on the main thread through {@link ClanRegistry};
 * persisted from {@link #snapshot()}. Exactly one member always holds {@link ClanRank#LEADER}.
 */
public final class Clan {

    private final UUID id;
    private final String name;
    private final String tag;
    private final Instant createdAt;
    private final Map<UUID, ClanMember> members = new LinkedHashMap<>();
    private long exp;
    private long version;

    private Clan(UUID id, String name, String tag, Instant createdAt, long exp, long version) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.createdAt = createdAt;
        this.exp = exp;
        this.version = version;
    }

    static Clan found(UUID founder, String founderName, String name, String tag, Instant now) {
        Clan clan = new Clan(UUID.randomUUID(), name, tag, now, 0, 0);
        clan.members.put(founder, new ClanMember(founder, founderName, ClanRank.LEADER, 0, now));
        return clan;
    }

    /** Rebuild from storage. Requires exactly one leader. */
    public static Clan restore(UUID id, String name, String tag, Instant createdAt, long exp, long version,
                               Collection<ClanMember> members) {
        Clan clan = new Clan(id, name, tag, createdAt, exp, version);
        for (ClanMember m : members) {
            clan.members.put(m.playerId(), m);
        }
        long leaders = clan.members.values().stream().filter(m -> m.rank() == ClanRank.LEADER).count();
        if (leaders != 1) {
            throw new IllegalStateException("Clan " + tag + " must have exactly one leader, found " + leaders);
        }
        return clan;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String tag() { return tag; }
    public Instant createdAt() { return createdAt; }
    public long exp() { return exp; }
    public long version() { return version; }
    public int level() { return ClanProgression.levelFor(exp); }
    public int capacity() { return ClanProgression.capacity(level()); }
    public int size() { return members.size(); }
    public boolean isFull() { return members.size() >= capacity(); }
    public boolean contains(UUID player) { return members.containsKey(player); }
    public Optional<ClanMember> member(UUID player) { return Optional.ofNullable(members.get(player)); }
    public List<ClanMember> members() { return List.copyOf(members.values()); }

    public ClanMember leader() {
        return members.values().stream().filter(m -> m.rank() == ClanRank.LEADER).findFirst()
                .orElseThrow(() -> new IllegalStateException("clan without leader: " + tag));
    }

    boolean add(UUID player, String playerName, Instant now) {
        if (isFull() || members.containsKey(player)) return false;
        members.put(player, new ClanMember(player, playerName, ClanRank.MEMBER, 0, now));
        touch();
        return true;
    }

    boolean remove(UUID player) {
        boolean removed = members.remove(player) != null;
        if (removed) touch();
        return removed;
    }

    void setRank(UUID player, ClanRank rank) {
        members.computeIfPresent(player, (k, m) -> m.withRank(rank));
        touch();
    }

    void transferLeadership(UUID newLeader) {
        UUID old = leader().playerId();
        members.computeIfPresent(old, (k, m) -> m.withRank(ClanRank.OFFICER));
        members.computeIfPresent(newLeader, (k, m) -> m.withRank(ClanRank.LEADER));
        touch();
    }

    void rename(UUID player, String newName) {
        ClanMember m = members.get(player);
        if (m != null && !m.name().equals(newName)) {
            members.put(player, m.withName(newName));
            touch();
        }
    }

    /** Add clan EXP credited to a member. @return number of clan levels gained (usually 0). */
    int contribute(UUID player, long amount) {
        if (amount <= 0) return 0;
        int before = level();
        exp += amount;
        members.computeIfPresent(player, (k, m) -> m.plusContribution(amount));
        touch();
        return level() - before;
    }

    private void touch() {
        version++;
    }

    public ClanSnapshot snapshot() {
        return new ClanSnapshot(id, name, tag, createdAt, exp, version, List.copyOf(members.values()));
    }

    @Override
    public String toString() {
        return "Clan{[" + tag + "] " + name + ", lvl " + level() + ", " + size() + "/" + capacity() + "}";
    }
}
