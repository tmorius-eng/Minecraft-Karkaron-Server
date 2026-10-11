package mn.suld.api.party;

import mn.suld.api.clazz.PlayerClass;

/** What a party member brings (party finder): the shield, the healer, the damage. */
public enum PartyRole {
    TANK("Бамбай"), HEALER("Эмч"), DPS("Довтлогч");

    private final String displayName;

    PartyRole(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** The natural role of a class: Баатар holds the line, Бөө keeps the party up, everyone else hits. */
    public static PartyRole defaultFor(PlayerClass c) {
        if (c == null) return DPS;
        return switch (c) {
            case BAATAR -> TANK;
            case BOO -> HEALER;
            default -> DPS;
        };
    }

    public static PartyRole parse(String s) {
        if (s == null) return null;
        String v = s.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (v) {
            case "tank", "бамбай", "t" -> TANK;
            case "healer", "heal", "эмч", "h" -> HEALER;
            case "dps", "довтлогч", "d" -> DPS;
            default -> null;
        };
    }
}
