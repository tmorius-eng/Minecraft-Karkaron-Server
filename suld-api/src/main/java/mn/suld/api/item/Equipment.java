package mn.suld.api.item;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.skill.Spell;
import mn.suld.api.skill.tree.Effect;
import mn.suld.api.skill.tree.ModKey;
import mn.suld.api.skill.tree.StatKey;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What a player's equipment adds up to. Pure: given the item in each slot, the player and the catalog, decides which
 * items are active (right slot, class, level, binding, not broken), sums their stats and affixes, counts set pieces
 * and returns the bonus the stat pipeline applies.
 */
public final class Equipment {

    private Equipment() {
    }

    /** Who is wearing it. */
    public record Wearer(UUID id, PlayerClass clazz, int level) {
    }

    /** Why an item in a slot gives nothing. */
    public enum Inactive {
        UNKNOWN_ITEM("танигдахгүй эд зүйл"),
        WRONG_SLOT("энэ байрлалд тохирохгүй"),
        CLASS("өөр ангийнх"),
        LEVEL("түвшин хүрэхгүй"),
        BOUND_TO_OTHER("өөр тоглогчид холбоотой"),
        BROKEN("эвдэрсэн — засуулна уу");

        private final String text;

        Inactive(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }
    }

    /** A set the wearer has pieces of. */
    public record SetProgress(ItemSet set, int worn, List<ItemSet.Bonus> active) {
    }

    /**
     * Everything the equipment adds.
     *
     * @param itemStats   total per item stat (base + affixes + set bonuses), the tooltip/GUI view
     * @param statKeys    the same mapped onto the player's stat pipeline (everything but flat damage)
     * @param flatDamage  flat attack added to the SÜLD hit formula
     * @param mods        spell modifiers (class spell affixes, unique effects, set bonuses)
     * @param procs       passive spells (unique effects, set bonuses)
     * @param inactive    slots whose item gives nothing, and why
     * @param sets        set progress
     */
    public record Bonus(Map<ItemStat, Double> itemStats, Map<StatKey, Double> statKeys, double flatDamage,
                        Map<Spell, Map<ModKey, Double>> mods, List<Effect.Proc> procs, Map<EquipSlot, Inactive> inactive,
                        List<SetProgress> sets) {
        public static final Bonus NONE = new Bonus(Map.of(), Map.of(), 0, Map.of(), List.of(), Map.of(), List.of());

        public double stat(ItemStat s) {
            return itemStats.getOrDefault(s, 0.0);
        }
    }

    /** Level a player needs to use this item: the definition's requirement and the item's own level. */
    public static int requiredLevel(ItemDefinition def, ItemInstance i) {
        return Math.max(def.levelReq(), i.itemLevel());
    }

    /** Why this player cannot use the item in this slot, or empty when they can. */
    public static Optional<Inactive> check(ItemCatalog catalog, EquipSlot slot, ItemInstance i, Wearer w, boolean broken) {
        ItemDefinition def = catalog.item(i.definitionId()).orElse(null);
        if (def == null) return Optional.of(Inactive.UNKNOWN_ITEM);
        if (!def.type().slots().contains(slot)) return Optional.of(Inactive.WRONG_SLOT);
        if (!def.allows(w.clazz())) return Optional.of(Inactive.CLASS);
        if (w.level() < requiredLevel(def, i)) return Optional.of(Inactive.LEVEL);
        if (i.boundTo() != null && !i.boundTo().equals(w.id())) return Optional.of(Inactive.BOUND_TO_OTHER);
        if (broken) return Optional.of(Inactive.BROKEN);
        return Optional.empty();
    }

    /** Stats of one item: rolled base stats plus stat affixes. */
    public static Map<ItemStat, Double> itemStats(ItemCatalog catalog, ItemInstance i) {
        Map<ItemStat, Double> out = new EnumMap<>(ItemStat.class);
        i.stats().forEach((s, v) -> out.put(s, whole(s, v)));
        for (RolledAffix ra : i.affixes()) {
            catalog.affix(ra.affixId()).filter(a -> a.stat() != null).ifPresent(a -> out.merge(a.stat(), whole(a.stat(), ra.value()), Double::sum));
        }
        return out;
    }

    /** Whole-number stats count whole (items rolled before that rule may carry a fraction the game cannot give). */
    private static double whole(ItemStat s, double v) {
        return s.integral() ? Math.floor(v + 1e-9) : v;
    }

    public static Bonus compute(ItemCatalog catalog, Map<EquipSlot, ItemInstance> worn, Wearer w, Set<EquipSlot> broken) {
        Map<ItemStat, Double> stats = new EnumMap<>(ItemStat.class);
        Map<Spell, Map<ModKey, Double>> mods = new EnumMap<>(Spell.class);
        List<Effect.Proc> procs = new ArrayList<>();
        Map<StatKey, Double> extraKeys = new EnumMap<>(StatKey.class); // stat effects without an item stat (thorns...)
        Map<EquipSlot, Inactive> inactive = new EnumMap<>(EquipSlot.class);
        Map<String, Set<String>> setPieces = new LinkedHashMap<>();
        for (Map.Entry<EquipSlot, ItemInstance> e : worn.entrySet()) {
            ItemInstance i = e.getValue();
            if (i == null) continue;
            Optional<Inactive> why = check(catalog, e.getKey(), i, w, broken != null && broken.contains(e.getKey()));
            if (why.isPresent()) {
                inactive.put(e.getKey(), why.get());
                continue;
            }
            ItemDefinition def = catalog.require(i.definitionId());
            itemStats(catalog, i).forEach((k, v) -> stats.merge(k, v, Double::sum));
            for (RolledAffix ra : i.affixes()) {
                catalog.affix(ra.affixId()).filter(a -> a.spell() != null)
                        .ifPresent(a -> mods.computeIfAbsent(a.spell(), x -> new EnumMap<>(ModKey.class)).merge(a.modKey(), ra.value(), Double::sum));
            }
            addEffects(def.effects(), stats, extraKeys, mods, procs);
            if (def.setId() != null) setPieces.computeIfAbsent(def.setId(), x -> new LinkedHashSet<>()).add(def.id());
        }
        List<SetProgress> sets = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : setPieces.entrySet()) {
            ItemSet set = catalog.set(e.getKey()).orElse(null);
            if (set == null) continue;
            int count = e.getValue().size();
            List<ItemSet.Bonus> active = set.active(count);
            for (ItemSet.Bonus b : active) {
                b.stats().forEach((k, v) -> stats.merge(k, v, Double::sum));
                addEffects(b.effects(), stats, extraKeys, mods, procs);
            }
            sets.add(new SetProgress(set, count, active));
        }
        Map<StatKey, Double> keys = new EnumMap<>(StatKey.class);
        keys.putAll(extraKeys);
        double flat = 0;
        for (Map.Entry<ItemStat, Double> e : stats.entrySet()) {
            if (e.getKey().statKey() == null) flat += e.getValue();
            else keys.merge(e.getKey().statKey(), e.getValue(), Double::sum);
        }
        return new Bonus(java.util.Collections.unmodifiableMap(stats), java.util.Collections.unmodifiableMap(keys), flat,
                java.util.Collections.unmodifiableMap(mods), List.copyOf(procs), java.util.Collections.unmodifiableMap(inactive), List.copyOf(sets));
    }

    private static void addEffects(List<Effect> effects, Map<ItemStat, Double> stats, Map<StatKey, Double> extraKeys,
                                   Map<Spell, Map<ModKey, Double>> mods, List<Effect.Proc> procs) {
        for (Effect fx : effects) {
            switch (fx) {
                case Effect.Proc p -> procs.add(p);
                case Effect.SpellMod m -> mods.computeIfAbsent(m.spell(), x -> new EnumMap<>(ModKey.class)).merge(m.key(), m.value(), Double::sum);
                case Effect.Stat s -> {
                    ItemStat mapped = null;
                    for (ItemStat is : ItemStat.values()) if (is.statKey() == s.key()) mapped = is;
                    if (mapped != null) stats.merge(mapped, s.value(), Double::sum);
                    else extraKeys.merge(s.key(), s.value(), Double::sum);
                }
                default -> {
                    // ultimates and keystones are skill-tree only (the loader refuses them on items)
                }
            }
        }
    }

    /** A gear score for the HUD: the sum of item levels weighted by rarity. */
    public static int gearScore(Map<EquipSlot, ItemInstance> worn, Map<EquipSlot, Inactive> inactive) {
        double score = 0;
        for (Map.Entry<EquipSlot, ItemInstance> e : worn.entrySet()) {
            if (e.getValue() == null || inactive.containsKey(e.getKey())) continue;
            score += e.getValue().itemLevel() * (1 + 0.25 * e.getValue().rarity().ordinal());
        }
        return (int) Math.round(score);
    }
}
