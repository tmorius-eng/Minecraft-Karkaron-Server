package mn.suld.api.style;

/**
 * Staff and supporter badges, shown before the rank badge (highest first). Granted by permission
 * {@code suld.badge.<id>} — set them on LuckPerms groups ({@code lp group admin permission set suld.badge.admin}).
 */
public enum StaffBadge {
    OWNER("owner", "Эзэн", "ЭЗЭН", "#FFC83C"),
    ADMIN("admin", "Админ", "АДМИН", "#FF4A4A"),
    DEVELOPER("developer", "Хөгжүүлэгч", "DEV", "#4AFFB4"),
    MOD("mod", "Модератор", "МОД", "#4F8BFF"),
    HELPER("helper", "Туслагч", "ТУСЛАГЧ", "#5AD2FF"),
    STREAMER("streamer", "Стример", "СТРИМЕР", "#B06BFF"),
    SPONSOR("sponsor", "Дэмжигч", "ДЭМЖИГЧ", "#FF6BB0");

    private final String id;
    private final String displayName;
    private final String badgeLabel;
    private final String color;

    StaffBadge(String id, String displayName, String badgeLabel, String color) {
        this.id = id;
        this.displayName = displayName;
        this.badgeLabel = badgeLabel;
        this.color = color;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String badgeLabel() { return badgeLabel; }
    public String color() { return color; }
    public String permission() { return "suld.badge." + id; }

    /** Staff (moderation rights) as opposed to supporters (streamer, sponsor). */
    public boolean staff() {
        return ordinal() <= HELPER.ordinal();
    }
}
