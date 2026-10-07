package mn.suld.api.item;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.skill.Spell;
import mn.suld.api.skill.tree.ModKey;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * A random property an item can roll: either a stat ({@code stat}) or a change to one class spell ({@code spell} +
 * {@code modKey}). The value rolls in [{@code min}, {@code max}] plus {@code perLevel} per item level above 1; rarity
 * raises the low end of the roll ({@link ItemRarity#rollFloor()}).
 *
 * @param prefix    true: the name goes before the item name ("Хурц Илд"), false: after it ("Илд · Чонын")
 * @param minRarity lowest rarity that can roll it
 * @param categories item categories it can appear on
 * @param weight    relative chance among the eligible affixes
 */
public record Affix(String id, String name, boolean prefix, ItemStat stat, Spell spell, ModKey modKey,
                    double min, double max, double perLevel, ItemRarity minRarity, Set<ItemType.Category> categories, int weight) {

    public Affix {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if ((stat == null) == (spell == null)) throw new IllegalArgumentException(id + ": exactly one of stat or spell");
        if (spell != null && modKey == null) throw new IllegalArgumentException(id + ": spell affix needs modKey");
        if (max < min) throw new IllegalArgumentException(id + ": max < min");
        minRarity = minRarity == null ? ItemRarity.UNCOMMON : minRarity;
        categories = categories == null || categories.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(categories));
        weight = Math.max(1, weight);
    }

    /** A spell affix only fits an item of that spell's class. */
    public PlayerClass clazz() {
        return spell == null ? null : spell.clazz();
    }

    public boolean fits(ItemDefinition def, ItemRarity rarity) {
        if (!rarity.atLeast(minRarity)) return false;
        if (!categories.isEmpty() && !categories.contains(def.type().category())) return false;
        if (spell != null) return def.classes().size() == 1 && def.classes().contains(spell.clazz());
        return true;
    }

    public double low(int itemLevel) {
        return min + perLevel * Math.max(0, itemLevel - 1);
    }

    public double high(int itemLevel) {
        return max + perLevel * Math.max(0, itemLevel - 1);
    }
}
