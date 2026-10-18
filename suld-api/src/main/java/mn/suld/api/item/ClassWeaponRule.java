package mn.suld.api.item;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.loot.LootEngine;

/**
 * The one place that says which weapon counts for what. Three questions, three answers, each used where it belongs:
 * <ul>
 *   <li>{@link #isClassWeapon(String, PlayerClass)}: the class's own weapon ({@code weapon.class.<class>.<tier>}), the
 *   soulbound one that grows with the level (the Мэргэн quiver holds arrows for this bow only);</li>
 *   <li>{@link #fightsWith}: a weapon the class fights and casts with: its own class weapon, or any SÜLD weapon of
 *   the class's affinity types that the item allows for it (combos, spells, the Мэргэн shot);</li>
 *   <li>{@link #isWeapon}: any SÜLD weapon at all (lock-on, the attack swing).</li>
 * </ul>
 */
public final class ClassWeaponRule {

    /** The definition id prefix of every class weapon. */
    public static final String PREFIX = "weapon.class.";

    private ClassWeaponRule() {
    }

    /** A class weapon of any class. */
    public static boolean isClassWeapon(String definitionId) {
        return definitionId != null && definitionId.startsWith(PREFIX);
    }

    /** The class weapon of {@code c}. */
    public static boolean isClassWeapon(String definitionId, PlayerClass c) {
        return definitionId != null && c != null && definitionId.startsWith(PREFIX + c.id() + ".");
    }

    /** Any SÜLD weapon ({@code def} null: not a SÜLD item, or one this catalog no longer has). */
    public static boolean isWeapon(String definitionId, ItemDefinition def) {
        return isClassWeapon(definitionId) || def != null && def.type().category() == ItemType.Category.WEAPON;
    }

    /** A weapon {@code c} fights and casts with: its class weapon, or a weapon of its affinity that allows it. */
    public static boolean fightsWith(String definitionId, ItemDefinition def, PlayerClass c) {
        if (isClassWeapon(definitionId, c)) return true;
        if (def == null || def.type().category() != ItemType.Category.WEAPON) return false;
        return LootEngine.affinity(c).contains(def.type()) && def.allows(c);
    }
}
