package mn.suld.api.party;

/** Outcome of a {@link PartyRegistry} operation. */
public enum PartyResult {
    OK,
    INVITED,
    JOINED,
    LEFT,
    KICKED,
    PROMOTED,
    SELF_TARGET,
    TARGET_IN_PARTY,
    ALREADY_IN_PARTY,
    NOT_IN_PARTY,
    NOT_LEADER,
    NOT_A_MEMBER,
    PARTY_FULL,
    PARTY_BUSY,
    NO_INVITE,
    INVITE_EXPIRED,
    PARTY_GONE
}
