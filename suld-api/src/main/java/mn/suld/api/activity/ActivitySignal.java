package mn.suld.api.activity;

/**
 * Activity signals (docs/ACTIVE_PLAYTIME_SPEC.md "Design"). A one-minute window is active with one STRONG signal, or
 * with signals from at least two different families. Chat, menus and standing still are not signals at all.
 */
public enum ActivitySignal {
    /** Damage dealt to a mob. */
    DAMAGE_DEALT(Family.COMBAT, Strength.STRONG, ActivityCategory.COMBAT),
    /** Damage taken from a mob (alone for 3+ minutes: a mob-farm AFK, see the tracker). */
    DAMAGE_TAKEN(Family.COMBAT, Strength.STRONG, ActivityCategory.COMBAT),
    KILL(Family.COMBAT, Strength.STRONG, ActivityCategory.COMBAT),
    SPELL(Family.COMBAT, Strength.STRONG, ActivityCategory.COMBAT),
    QUEST(Family.QUEST, Strength.STRONG, ActivityCategory.QUEST),
    DUNGEON(Family.QUEST, Strength.STRONG, ActivityCategory.DUNGEON),
    DISCOVERY(Family.EXPLORATION, Strength.STRONG, ActivityCategory.EXPLORATION),
    /** First visit of a 16×16 area this session. */
    NEW_AREA(Family.EXPLORATION, Strength.STRONG, ActivityCategory.EXPLORATION),
    CRAFT(Family.CRAFTING, Strength.NORMAL, ActivityCategory.CRAFTING),
    TRADE(Family.CRAFTING, Strength.NORMAL, ActivityCategory.CRAFTING),
    /** Raised by the tracker itself when the minute's own-feet displacement exceeds 6 blocks. */
    MOVE(Family.MOVEMENT, Strength.NORMAL, ActivityCategory.OTHER),
    /** Inventory clicks outside SÜLD menus. */
    INVENTORY(Family.INTERACTION, Strength.WEAK, ActivityCategory.OTHER),
    /** Block break / place outside the city. */
    BLOCK(Family.INTERACTION, Strength.WEAK, ActivityCategory.OTHER);

    public enum Family { COMBAT, QUEST, EXPLORATION, CRAFTING, MOVEMENT, INTERACTION }

    public enum Strength { STRONG, NORMAL, WEAK }

    private final Family family;
    private final Strength strength;
    private final ActivityCategory category;

    ActivitySignal(Family family, Strength strength, ActivityCategory category) {
        this.family = family;
        this.strength = strength;
        this.category = category;
    }

    public Family family() {
        return family;
    }

    public Strength strength() {
        return strength;
    }

    public ActivityCategory category() {
        return category;
    }
}
