package mn.suld.api.clan;

/** Clan ranks, ordered by authority. Mongolian titles follow the steppe hierarchy. */
public enum ClanRank {
    MEMBER(1, "Цэрэг"),
    OFFICER(2, "Түшмэл"),
    LEADER(3, "Ноён");

    private final int authority;
    private final String displayName;

    ClanRank(int authority, String displayName) {
        this.authority = authority;
        this.displayName = displayName;
    }

    public int authority() { return authority; }
    public String displayName() { return displayName; }

    public boolean outranks(ClanRank other) { return authority > other.authority; }
    public boolean canInvite() { return authority >= OFFICER.authority; }

    public static ClanRank byId(String id) {
        for (ClanRank r : values()) {
            if (r.name().equalsIgnoreCase(id)) return r;
        }
        return MEMBER;
    }
}
