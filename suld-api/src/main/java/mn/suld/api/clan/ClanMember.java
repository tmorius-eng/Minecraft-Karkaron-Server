package mn.suld.api.clan;

import java.time.Instant;
import java.util.UUID;

/**
 * One member of a clan (immutable; the clan replaces entries on change).
 *
 * @param contribution lifetime clan EXP this member has earned for the clan
 */
public record ClanMember(UUID playerId, String name, ClanRank rank, long contribution, Instant joinedAt) {

    public ClanMember withRank(ClanRank newRank) {
        return new ClanMember(playerId, name, newRank, contribution, joinedAt);
    }

    public ClanMember withName(String newName) {
        return new ClanMember(playerId, newName, rank, contribution, joinedAt);
    }

    public ClanMember plusContribution(long amount) {
        return new ClanMember(playerId, name, rank, contribution + amount, joinedAt);
    }
}
