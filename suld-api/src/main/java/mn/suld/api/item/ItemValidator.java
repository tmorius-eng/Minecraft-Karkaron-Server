package mn.suld.api.item;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Says whether an item could have been produced by this server's catalog. Anything else — an unknown id, a rarity the
 * definition cannot have, a stat above what the best roll at that rarity and level gives, an affix that does not exist
 * or does not fit, too many affixes, a level outside 1..60 — is a forged or corrupted item and is refused.
 * (A value below the range is not an exploit and is accepted: items made before a rebalance keep their stats.)
 */
public final class ItemValidator {

    /** Rounding allowance: generated values are rounded to one decimal. */
    private static final double EPS = 0.051;

    private final ItemCatalog catalog;

    public ItemValidator(ItemCatalog catalog) {
        this.catalog = catalog;
    }

    /** Problems found; empty = the item is genuine as far as the catalog can tell. */
    public List<String> problems(ItemInstance i) {
        List<String> out = new ArrayList<>();
        if (i.schemaVersion() != ItemInstance.SCHEMA_VERSION) out.add("unknown schema version " + i.schemaVersion());
        ItemDefinition def = catalog.item(i.definitionId()).orElse(null);
        if (def == null) {
            out.add("unknown item id " + i.definitionId());
            return out;
        }
        if (i.rarity() == ItemRarity.UNIQUE && def.rarity() != ItemRarity.UNIQUE) out.add("unique rarity on a non-unique item");
        else if (!def.canRoll(i.rarity())) out.add("rarity " + i.rarity().id() + " outside " + def.rarity().id() + ".." + def.maxRarity().id());
        if (i.itemLevel() < 1 || i.itemLevel() > ItemDefinition.MAX_LEVEL) out.add("item level " + i.itemLevel() + " outside 1.." + ItemDefinition.MAX_LEVEL);
        if (i.upgradeLevel() > ItemDefinition.MAX_LEVEL) out.add("upgrade level " + i.upgradeLevel());
        for (Map.Entry<ItemStat, Double> e : i.stats().entrySet()) {
            if (!def.stats().containsKey(e.getKey())) {
                out.add("stat " + e.getKey().id() + " is not on " + def.id());
                continue;
            }
            double max = def.maxStat(e.getKey(), i.rarity(), i.itemLevel());
            if (!Double.isFinite(e.getValue()) || e.getValue() > max + EPS) {
                out.add("impossible " + e.getKey().id() + " " + e.getValue() + " (best possible " + ItemGenerator.round(max) + ")");
            }
        }
        if (i.affixes().size() > i.rarity().maxAffixes()) out.add(i.affixes().size() + " affixes on " + i.rarity().id() + " (max " + i.rarity().maxAffixes() + ")");
        Set<String> seen = new HashSet<>();
        for (RolledAffix ra : i.affixes()) {
            Affix a = catalog.affix(ra.affixId()).orElse(null);
            if (a == null) {
                out.add("unknown affix " + ra.affixId());
                continue;
            }
            if (!seen.add(a.id())) out.add("affix " + a.id() + " twice");
            if (!a.fits(def, i.rarity())) out.add("affix " + a.id() + " cannot be on " + def.id() + " (" + i.rarity().id() + ")");
            double below = a.stat() != null && a.stat().integral() ? 1 : EPS; // whole-number stats are rounded down
            if (ra.value() > a.high(i.itemLevel()) + EPS || ra.value() < a.low(i.itemLevel()) - below) {
                out.add("affix " + a.id() + " value " + ra.value() + " outside " + ItemGenerator.round(a.low(i.itemLevel())) + ".." + ItemGenerator.round(a.high(i.itemLevel())));
            }
        }
        if (def.bindingAt(i.rarity()) == Binding.SOULBOUND && !i.soulbound()) out.add("must be soulbound");
        return out;
    }

    public boolean genuine(ItemInstance i) {
        return problems(i).isEmpty();
    }
}
