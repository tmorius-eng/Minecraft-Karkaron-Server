package mn.suld.api.item;

import mn.suld.api.skill.tree.StatKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * Stats an item can carry. Every stat is wired into real gameplay: all but {@link #DAMAGE} feed the player's
 * {@link StatKey} pipeline (the same one the skill tree uses, measured in combat by the QA suite); {@link #DAMAGE} is
 * flat attack added to the SÜLD hit/spell formula. Percent stats are stored in percent points (5 = 5%).
 */
public enum ItemStat {
    MAX_HEALTH("max_health", "Дээд амь", false, StatKey.HEALTH, "health"),
    HEALTH_REGEN("health_regen", "Амь сэргэлт /сек", false, StatKey.HEALTH_REGEN, null),
    DAMAGE("damage", "Хохирол", false, null, "attack"),
    ARMOR("armor", "Хуяг", false, StatKey.ARMOR, null),
    MOVE_SPEED("move_speed", "Хөдөлгөөний хурд", true, StatKey.MOVE_PCT, null),
    ATTACK_SPEED("attack_speed", "Довтолгооны хурд", true, StatKey.ATTACK_SPEED_PCT, null),
    CRIT_CHANCE("crit_chance", "Чухал цохилтын магадлал", true, StatKey.CRIT_CHANCE, null),
    CRIT_DAMAGE("crit_damage", "Чухал цохилтын хүч", true, StatKey.CRIT_DAMAGE, null),
    DODGE("dodge", "Мултрах", true, StatKey.DODGE_PCT, null),
    LIFESTEAL("lifesteal", "Амь сорох", true, StatKey.LIFESTEAL, null),
    XP_GAIN("xp_gain", "Туршлага", true, StatKey.EXP_PCT, null),
    LOOT_CHANCE("loot_chance", "Олзны боломж", true, StatKey.LOOT_PCT, null),
    COOLDOWN_REDUCTION("cooldown_reduction", "Хүлээлт багасах", true, StatKey.COOLDOWN_REDUCTION, null),
    RESOURCE_REGEN("resource_regen", "Нөөц сэргэлт /сек", false, StatKey.RESOURCE_REGEN, null),
    RESOURCE_MAX("resource_max", "Нөөцийн багтаамж", false, StatKey.RESOURCE_MAX, "resource"),
    SPELL_DAMAGE("spell_damage", "Шидийн хүч", true, StatKey.SPELL_DAMAGE, null);

    private final String id;
    private final String label;
    private final boolean percent;
    private final StatKey key;
    private final String legacyId;

    ItemStat(String id, String label, boolean percent, StatKey key, String legacyId) {
        this.id = id;
        this.label = label;
        this.percent = percent;
        this.key = key;
        this.legacyId = legacyId;
    }

    public @NotNull String id() {
        return id;
    }

    public @NotNull String label() {
        return label;
    }

    /**
     * Whole numbers only: the game keeps this stat as an integer (the resource pool), so a rolled "+10.1" would be a
     * tooltip promising more than the player gets. Generated values of these stats are rounded down.
     */
    public boolean integral() {
        return this == RESOURCE_MAX;
    }

    /** Shown and stored as percent points. */
    public boolean percent() {
        return percent;
    }

    /** The player stat this item stat adds to, or null for {@link #DAMAGE} (flat attack). */
    public @Nullable StatKey statKey() {
        return key;
    }

    /** "+5%" / "+12" / "+0.5". */
    public String format(double value) {
        String sign = value >= 0 ? "+" : "-";
        double a = Math.abs(value);
        if (integral()) a = Math.floor(a + 1e-9); // items made before whole-number rolls still show what they give
        String num = a == Math.rint(a) ? String.valueOf((long) a) : String.format(Locale.ROOT, "%.1f", a);
        return sign + num + (percent ? "%" : "");
    }

    /** By id; also accepts the ids used before schema 2 (attack, health, resource). */
    public static @NotNull Optional<ItemStat> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String needle = id.trim().toLowerCase(Locale.ROOT);
        for (ItemStat s : values()) {
            if (s.id.equals(needle) || needle.equals(s.legacyId) || s.name().toLowerCase(Locale.ROOT).equals(needle)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
