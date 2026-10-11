package mn.suld.api.campaign;

import java.util.Set;

/**
 * What a campaign asks the whole server to do during the week. {@code target} is the shared goal in units;
 * {@code targets} are the content ids that count (mobs, a dungeon, sites, an item).
 */
public record CampaignGoal(Type type, Set<String> targets, long target) {

    public enum Type {
        /** units = kills of any listed mob */
        KILL_MOBS,
        /** units = items of the listed id handed to the campaign steward */
        DELIVER_ITEMS,
        /** units = completed runs of the listed dungeon (one per participant) */
        CLEAR_DUNGEON,
        /** units = first visits of the listed historic sites (one per player per site) */
        DISCOVER_SITES;

        /** The contribution source this goal listens to. */
        public static Type parse(String s) {
            for (Type t : values()) if (t.name().equalsIgnoreCase(s)) return t;
            throw new IllegalArgumentException("unknown goal type " + s);
        }
    }

    public CampaignGoal {
        if (type == null) throw new IllegalArgumentException("goal type");
        if (targets == null || targets.isEmpty()) throw new IllegalArgumentException("a goal needs at least one target id");
        if (target <= 0) throw new IllegalArgumentException("target must be > 0");
        targets = Set.copyOf(targets);
    }

    /** Whether an event of this {@code type} about content {@code id} counts toward the goal. */
    public boolean counts(Type eventType, String id) {
        return type == eventType && targets.contains(id);
    }

    /** The Mongolian noun of a unit, for progress lines. */
    public String unitWord() {
        return switch (type) {
            case KILL_MOBS -> "устгал";
            case DELIVER_ITEMS -> "хүргэлт";
            case CLEAR_DUNGEON -> "цэвэрлэгээ";
            case DISCOVER_SITES -> "нээлт";
        };
    }
}
