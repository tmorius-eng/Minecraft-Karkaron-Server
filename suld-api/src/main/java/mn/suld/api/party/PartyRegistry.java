package mn.suld.api.party;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure bookkeeping for parties: membership index, invites with expiry, and the
 * rules around who may invite/kick/leave. No Minecraft types, so every rule is
 * unit-tested. Single-threaded (main-thread) use is assumed.
 */
public final class PartyRegistry {

    /** Result of an operation plus the party it affected, if any. */
    public record Action(PartyResult result, Party party) {
        public boolean ok() {
            return result == PartyResult.OK || result == PartyResult.INVITED
                    || result == PartyResult.JOINED || result == PartyResult.LEFT
                    || result == PartyResult.KICKED || result == PartyResult.PROMOTED;
        }
    }

    private record Invite(UUID partyId, UUID inviter, Instant expiresAt) {
    }

    private final Clock clock;
    private final int maxPartySize;
    private final Duration inviteTtl;
    private final Map<UUID, Party> parties = new HashMap<>();
    private final Map<UUID, UUID> memberToParty = new HashMap<>();
    private final Map<UUID, Invite> invitesByInvitee = new HashMap<>();

    public PartyRegistry(Clock clock, int maxPartySize, Duration inviteTtl) {
        this.clock = clock;
        this.maxPartySize = maxPartySize;
        this.inviteTtl = inviteTtl;
    }

    public Optional<Party> partyOf(UUID player) {
        UUID id = memberToParty.get(player);
        return id == null ? Optional.empty() : Optional.ofNullable(parties.get(id));
    }

    public Optional<Party> party(UUID partyId) {
        return Optional.ofNullable(parties.get(partyId));
    }

    /** The player's party, creating a one-person party if they have none (solo dungeon runs). */
    public Party ensureParty(UUID player) {
        return partyOf(player).orElseGet(() -> {
            Party created = Party.create(player, maxPartySize);
            parties.put(created.id(), created);
            memberToParty.put(player, created.id());
            return created;
        });
    }

    public boolean hasPendingInvite(UUID invitee) {
        Invite inv = invitesByInvitee.get(invitee);
        return inv != null && clock.instant().isBefore(inv.expiresAt());
    }

    /** Inviter invites target; creates the inviter's party on first invite. */
    public Action invite(UUID inviter, UUID target) {
        if (inviter.equals(target)) {
            return new Action(PartyResult.SELF_TARGET, null);
        }
        if (memberToParty.containsKey(target)) {
            return new Action(PartyResult.TARGET_IN_PARTY, null);
        }
        Party party = partyOf(inviter).orElse(null);
        if (party != null) {
            if (!party.isLeader(inviter)) {
                return new Action(PartyResult.NOT_LEADER, party);
            }
            if (party.state() == PartyState.IN_DUNGEON) {
                return new Action(PartyResult.PARTY_BUSY, party);
            }
            if (party.isFull()) {
                return new Action(PartyResult.PARTY_FULL, party);
            }
        } else {
            party = Party.create(inviter, maxPartySize);
            parties.put(party.id(), party);
            memberToParty.put(inviter, party.id());
        }
        invitesByInvitee.put(target, new Invite(party.id(), inviter, clock.instant().plus(inviteTtl)));
        return new Action(PartyResult.INVITED, party);
    }

    /** Accept the pending invite addressed to {@code player}. */
    public Action accept(UUID player) {
        Invite inv = invitesByInvitee.remove(player);
        if (inv == null) {
            return new Action(PartyResult.NO_INVITE, null);
        }
        if (!clock.instant().isBefore(inv.expiresAt())) {
            return new Action(PartyResult.INVITE_EXPIRED, null);
        }
        if (memberToParty.containsKey(player)) {
            return new Action(PartyResult.ALREADY_IN_PARTY, partyOf(player).orElse(null));
        }
        Party party = parties.get(inv.partyId());
        if (party == null || party.isDisbanded()) {
            return new Action(PartyResult.PARTY_GONE, null);
        }
        if (party.state() == PartyState.IN_DUNGEON) {
            return new Action(PartyResult.PARTY_BUSY, party);
        }
        if (!party.addMember(player)) {
            return new Action(PartyResult.PARTY_FULL, party);
        }
        memberToParty.put(player, party.id());
        return new Action(PartyResult.JOINED, party);
    }

    /** Player leaves (or is removed from) their party; empty parties are dropped. */
    public Action leave(UUID player) {
        Party party = partyOf(player).orElse(null);
        if (party == null) {
            return new Action(PartyResult.NOT_IN_PARTY, null);
        }
        party.removeMember(player);
        memberToParty.remove(player);
        if (party.isDisbanded()) {
            parties.remove(party.id());
        }
        return new Action(PartyResult.LEFT, party);
    }

    public Action kick(UUID leader, UUID target) {
        Party party = partyOf(leader).orElse(null);
        if (party == null) {
            return new Action(PartyResult.NOT_IN_PARTY, null);
        }
        if (!party.isLeader(leader)) {
            return new Action(PartyResult.NOT_LEADER, party);
        }
        if (leader.equals(target)) {
            return new Action(PartyResult.SELF_TARGET, party);
        }
        if (!party.contains(target)) {
            return new Action(PartyResult.NOT_A_MEMBER, party);
        }
        party.removeMember(target);
        memberToParty.remove(target);
        return new Action(PartyResult.KICKED, party);
    }

    public Action promote(UUID leader, UUID target) {
        Party party = partyOf(leader).orElse(null);
        if (party == null) {
            return new Action(PartyResult.NOT_IN_PARTY, null);
        }
        if (!party.isLeader(leader)) {
            return new Action(PartyResult.NOT_LEADER, party);
        }
        if (!party.setLeader(target)) {
            return new Action(PartyResult.NOT_A_MEMBER, party);
        }
        return new Action(PartyResult.PROMOTED, party);
    }

    /** Disband a party outright (leader command or dungeon cleanup). */
    public void disband(Party party) {
        for (UUID member : party.members()) {
            memberToParty.remove(member);
        }
        party.disband();
        parties.remove(party.id());
        invitesByInvitee.values().removeIf(inv -> inv.partyId().equals(party.id()));
    }

    public int partyCount() {
        return parties.size();
    }
}
