package mn.suld.api.clan;

/** Outcome of a {@link ClanRegistry} operation. */
public enum ClanResult {
    CREATED, INVITED, JOINED, LEFT, KICKED, PROMOTED, DEMOTED, TRANSFERRED, DISBANDED,
    ALREADY_IN_CLAN, NOT_IN_CLAN, TARGET_IN_CLAN, NOT_A_MEMBER, SELF_TARGET,
    INVALID_NAME, INVALID_TAG, NAME_TAKEN, TAG_TAKEN,
    NO_PERMISSION, LEADER_MUST_TRANSFER, CLAN_FULL, ALREADY_MAX_RANK, ALREADY_MIN_RANK,
    NO_INVITE, INVITE_EXPIRED, CLAN_GONE;

    public boolean success() {
        return switch (this) {
            case CREATED, INVITED, JOINED, LEFT, KICKED, PROMOTED, DEMOTED, TRANSFERRED, DISBANDED -> true;
            default -> false;
        };
    }
}
