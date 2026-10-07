package mn.suld.api.skill.tree;

/** A permanent stat a skill-tree node can raise. Values add up across all unlocked nodes. */
public enum StatKey {
    HEALTH("Дээд амь", "", true),
    MOVE_PCT("Хөдөлгөөний хурд", "%", true),
    ARMOR("Хуяг", "", true),
    ATTACK_PCT("Довтолгооны хүч", "%", true),
    CRIT_CHANCE("Чухал цохилтын магадлал", "%", true),
    CRIT_DAMAGE("Чухал цохилтын хүч", "%", true),
    SPELL_DAMAGE("Шидийн хүч", "%", true),
    DAMAGE_REDUCTION("Авах хохирол", "%", false),
    LIFESTEAL("Цохилтоос амь авах", "%", true),
    RESOURCE_MAX("Нөөцийн багтаамж", "", true),
    RESOURCE_REGEN("Нөөц сэргэлт (секундэд)", "", true),
    KB_RESIST("Түлхэлтийн эсэргүүцэл", "%", true),
    THORNS("Хариу хохирол", "%", true),
    COST_REDUCTION("Шидийн зардал", "%", false),
    HEAL_POWER("Эдгээх хүч", "%", true),
    EXP_PCT("Туршлага", "%", true),
    LOOT_PCT("Олзны боломж", "%", true),
    DODGE_PCT("Мултрах боломж", "%", true),
    COOLDOWN_REDUCTION("Хүлээлт, дуулал ба чадвар", "%", false),
    MINING_SPEED_PCT("Уул уурхайн хурд", "%", true);

    private final String label;
    private final String unit;
    private final boolean beneficial;

    StatKey(String label, String unit, boolean beneficial) {
        this.label = label;
        this.unit = unit;
        this.beneficial = beneficial;
    }

    public String label() { return label; }
    public String unit() { return unit; }

    /** Whether a positive value is shown as "+X" (true) or as a reduction "-X" (false). */
    public boolean raises() { return beneficial; }
}
