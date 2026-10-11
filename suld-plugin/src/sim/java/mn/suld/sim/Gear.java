package mn.suld.sim;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.EquipSlot;
import mn.suld.api.item.Equipment;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemStat;
import mn.suld.api.item.StatRange;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * What a simulated player wears, evaluated by the real {@link Equipment#compute} (stats, sets, inactive checks) and
 * {@link Equipment#gearScore}. The proposed "gear power" ({@link #itemPower}) also counts roll quality, so a badly
 * rolled legendary can lose to a well rolled epic.
 */
public final class Gear {

    /** A worn item. {@code classGear} marks soulbound class armour/weapon (never replaced by loot in proposed rules). */
    public record Piece(ItemDefinition def, ItemInstance item, double power, boolean classGear) {
    }

    private final ItemCatalog catalog;
    private final EnumMap<EquipSlot, Piece> worn = new EnumMap<>(EquipSlot.class);
    private Equipment.Bonus bonus = Equipment.Bonus.NONE;
    private boolean dirty = true;

    public Gear(ItemCatalog catalog) {
        this.catalog = catalog;
    }

    public Map<EquipSlot, Piece> worn() {
        return worn;
    }

    public Piece get(EquipSlot s) {
        return worn.get(s);
    }

    public void put(EquipSlot s, Piece p) {
        if (p == null) worn.remove(s);
        else worn.put(s, p);
        dirty = true;
    }

    /** Stats of everything worn, through the real aggregation. */
    public Equipment.Bonus bonus(UUID id, PlayerClass c, int level) {
        if (dirty) {
            Map<EquipSlot, ItemInstance> items = new EnumMap<>(EquipSlot.class);
            for (Map.Entry<EquipSlot, Piece> e : worn.entrySet()) items.put(e.getKey(), e.getValue().item());
            bonus = Equipment.compute(catalog, items, new Equipment.Wearer(id, c, level), java.util.Set.of());
            dirty = false;
        }
        return bonus;
    }

    public void levelChanged() {
        dirty = true; // items waiting for the level become active
    }

    /** The live HUD gear score. */
    public int gearScore() {
        Map<EquipSlot, ItemInstance> items = new EnumMap<>(EquipSlot.class);
        for (Map.Entry<EquipSlot, Piece> e : worn.entrySet()) items.put(e.getKey(), e.getValue().item());
        return Equipment.gearScore(items, bonus.inactive());
    }

    /** The proposed gear power: the sum of item power of everything worn. */
    public double gearPower() {
        double gp = 0;
        for (Piece p : worn.values()) gp += p.power();
        return gp;
    }

    public ItemRarity highest() {
        ItemRarity best = null;
        for (Piece p : worn.values()) if (best == null || p.item().rarity().ordinal() > best.ordinal()) best = p.item().rarity();
        return best == null ? ItemRarity.COMMON : best;
    }

    /** The middle rarity of what is worn ("highest normal rarity"). */
    public ItemRarity median() {
        int[] counts = new int[ItemRarity.values().length];
        int n = 0;
        for (Piece p : worn.values()) {
            counts[p.item().rarity().ordinal()]++;
            n++;
        }
        if (n == 0) return ItemRarity.COMMON;
        int seen = 0;
        for (int i = 0; i < counts.length; i++) {
            seen += counts[i];
            if (seen * 2 >= n) return ItemRarity.values()[i];
        }
        return ItemRarity.COMMON;
    }

    /**
     * Item power = item level × the rarity's stat multiplier × roll quality (0.75 at the bottom of every range,
     * 1.0 at the top). Quality is read from the real rolled stats against the definition's ranges.
     */
    public static double itemPower(ItemDefinition def, ItemInstance i) {
        return mn.suld.api.balance.GearPower.item(def, i);
    }

    /** Average position of each rolled stat inside its range for the item's rarity and level, 0..1. */
    public static double quality(ItemDefinition def, ItemInstance i) {
        return mn.suld.api.balance.GearPower.quality(def, i);
    }
}
