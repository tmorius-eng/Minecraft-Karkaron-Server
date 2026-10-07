package mn.suld.api.item;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.json.Json;
import mn.suld.api.loot.LootTable;
import mn.suld.api.loot.LootTier;
import mn.suld.api.loot.RarityBand;
import mn.suld.api.skill.Spell;
import mn.suld.api.skill.tree.Effect;
import mn.suld.api.skill.tree.ModKey;
import mn.suld.api.skill.tree.SkillTreeLoader;
import mn.suld.api.skill.tree.SkillTreeLoader.Issue;
import mn.suld.api.skill.tree.SpellMods;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Reads the item data files ({@code items/index.json} and the files it lists) and validates every field: a problem is
 * reported with the exact file, field path and reason, and a catalog with any problem is not used (the server keeps the
 * previous one on a reload). Same rules and helpers as the skill tree loader.
 */
public final class ItemCatalogLoader {

    public static final int FORMAT_VERSION = 1;
    private static final Pattern MATERIAL = Pattern.compile("minecraft:[a-z0-9_]+");
    private static final Pattern AFFIX_ID = Pattern.compile("[a-z][a-z0-9_]{1,31}");

    private ItemCatalogLoader() {
    }

    public record Result(ItemCatalog catalog, List<Issue> issues) {
        public boolean ok() {
            return issues.isEmpty();
        }
    }

    /** Bundled data in the jar ({@code /items/}). */
    public static SkillTreeLoader.Source classpath() {
        return name -> {
            try (InputStream in = ItemCatalogLoader.class.getResourceAsStream("/items/" + name)) {
                if (in == null) throw new NoSuchFileException("/items/" + name);
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        };
    }

    public static SkillTreeLoader.Source directory(Path dir) {
        return name -> Files.readString(dir.resolve(name), StandardCharsets.UTF_8);
    }

    public static Result load(SkillTreeLoader.Source src) {
        List<Issue> issues = new ArrayList<>();
        Map<String, Object> index = readObject(src, "index.json", issues);
        if (index == null) return new Result(ItemCatalog.EMPTY, issues);
        int version = SkillTreeLoader.intIn("index.json", "", index, "version", 1, 99, true, 0, issues);
        if (version != FORMAT_VERSION && issues.isEmpty()) issues.add(new Issue("index.json", "version", "format " + version + " is not supported (expected " + FORMAT_VERSION + ")"));

        // affixes first: items do not reference them, but the generator check below needs them
        List<Affix> affixes = new ArrayList<>();
        String affixFile = SkillTreeLoader.str("index.json", "", index, "affixes", true, issues);
        if (affixFile != null) {
            Map<String, Object> af = readObject(src, affixFile, issues);
            if (af != null) affixes = affixes(affixFile, af, issues);
        }

        List<ItemDefinition> items = new ArrayList<>();
        Map<String, String> itemFile = new LinkedHashMap<>();
        Object files = index.get("items");
        if (!(files instanceof List<?> list) || list.isEmpty()) {
            issues.add(new Issue("index.json", "items", "must list the item files"));
        } else {
            for (Object f : list) {
                if (!(f instanceof String name)) {
                    issues.add(new Issue("index.json", "items", "file names must be text"));
                    continue;
                }
                Map<String, Object> doc = readObject(src, name, issues);
                if (doc == null) continue;
                Object arr = doc.get("items");
                if (!(arr instanceof List<?> entries)) {
                    issues.add(new Issue(name, "items", "missing (an array of items)"));
                    continue;
                }
                for (int i = 0; i < entries.size(); i++) {
                    ItemDefinition d = item(name, "items[" + i + "]", entries.get(i), issues);
                    if (d == null) continue;
                    if (itemFile.containsKey(d.id())) issues.add(new Issue(name, "items[" + i + "].id", "duplicate id " + d.id() + " (also in " + itemFile.get(d.id()) + ")"));
                    else {
                        itemFile.put(d.id(), name);
                        items.add(d);
                    }
                }
            }
        }
        Map<String, ItemDefinition> byId = new LinkedHashMap<>();
        for (ItemDefinition d : items) byId.put(d.id(), d);

        List<ItemSet> sets = new ArrayList<>();
        String setFile = SkillTreeLoader.str("index.json", "", index, "sets", true, issues);
        if (setFile != null) {
            Map<String, Object> sf = readObject(src, setFile, issues);
            if (sf != null) sets = sets(setFile, sf, byId, issues);
        }
        Set<String> setIds = new HashSet<>();
        for (ItemSet s : sets) setIds.add(s.id());
        for (ItemDefinition d : items) {
            if (d.setId() != null && !setIds.contains(d.setId())) issues.add(new Issue(itemFile.get(d.id()), d.id() + ".set", "unknown set " + d.setId()));
        }

        List<RarityBand> bands = new ArrayList<>();
        String tierFile = SkillTreeLoader.str("index.json", "", index, "tiers", true, issues);
        if (tierFile != null) {
            Map<String, Object> tf = readObject(src, tierFile, issues);
            if (tf != null) bands = bands(tierFile, tf, issues);
        }

        List<LootTable> tables = new ArrayList<>();
        String lootFile = SkillTreeLoader.str("index.json", "", index, "loot", true, issues);
        if (lootFile != null) {
            Map<String, Object> lf = readObject(src, lootFile, issues);
            if (lf != null) tables = tables(lootFile, lf, byId, issues);
        }

        List<Recipe> recipes = new ArrayList<>();
        String recipeFile = SkillTreeLoader.str("index.json", "", index, "recipes", true, issues);
        if (recipeFile != null) {
            Map<String, Object> rf = readObject(src, recipeFile, issues);
            if (rf != null) recipes = recipes(recipeFile, rf, byId, issues);
        }

        List<String> salvage = new ArrayList<>();
        Object sv = index.get("salvage");
        if (!(sv instanceof List<?> sl) || sl.size() != 3) {
            issues.add(new Issue("index.json", "salvage", "must list three salvage materials (common–uncommon, rare–epic, legendary+)"));
        } else {
            for (int i = 0; i < sl.size(); i++) {
                Object o = sl.get(i);
                ItemDefinition d = o instanceof String s ? byId.get(s) : null;
                if (d == null) issues.add(new Issue("index.json", "salvage[" + i + "]", "unknown item " + o));
                else if (d.type() != ItemType.MATERIAL) issues.add(new Issue("index.json", "salvage[" + i + "]", d.id() + " is not a material"));
                else salvage.add(d.id());
            }
        }

        ItemCatalog catalog = new ItemCatalog(items, affixes, sets, tables, bands, recipes, salvage);
        for (ItemDefinition d : items) {
            if (d.equippable() && d.rarity() != ItemRarity.UNIQUE && !d.stackable()) {
                for (ItemRarity r : ItemRarity.values()) {
                    if (!d.canRoll(r) || r.minAffixes() == 0) continue;
                    long fit = affixes.stream().filter(a -> a.fits(d, r)).map(a -> a.stat() != null ? a.stat().name() : a.spell() + "." + a.modKey()).distinct().count();
                    if (fit < r.minAffixes()) {
                        issues.add(new Issue(itemFile.get(d.id()), d.id() + ".maxRarity", r.id() + " needs " + r.minAffixes() + " affixes but only " + fit + " fit a " + d.type()));
                        break;
                    }
                }
            }
        }
        return new Result(catalog, List.copyOf(issues));
    }

    // ------------------------------------------------------------------ items

    private static ItemDefinition item(String file, String at, Object o, List<Issue> issues) {
        if (!(o instanceof Map<?, ?> raw)) {
            issues.add(new Issue(file, at, "must be an object"));
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) raw;
        int before = issues.size();
        String id = SkillTreeLoader.str(file, at, m, "id", true, issues);
        if (id != null) {
            at = id;
            if (!ItemDefinition.ID.matcher(id).matches()) issues.add(new Issue(file, at + ".id", "must look like kind.name (lower case, dots, digits, _)"));
        }
        String name = SkillTreeLoader.str(file, at, m, "name", true, issues);
        if (name != null && name.isBlank()) issues.add(new Issue(file, at + ".name", "must not be empty"));
        String lore = SkillTreeLoader.str(file, at, m, "lore", false, issues);
        String material = SkillTreeLoader.str(file, at, m, "material", true, issues);
        if (material != null && !MATERIAL.matcher(material).matches()) issues.add(new Issue(file, at + ".material", "must look like minecraft:iron_sword"));
        int model = SkillTreeLoader.intIn(file, at, m, "model", 0, 9_999_999, false, 0, issues);
        ItemType type = SkillTreeLoader.enumOf(ItemType.class, SkillTreeLoader.str(file, at, m, "type", true, issues), file, at + ".type", issues);
        if (type != null && material != null && !type.acceptsMaterial(material)) {
            issues.add(new Issue(file, at + ".material", material + " cannot be a " + type + " (the game would not put it in the " + type.slots() + " slot)"));
        }
        ItemRarity rarity = rarity(file, at + ".rarity", SkillTreeLoader.str(file, at, m, "rarity", true, issues), issues);
        String maxR = SkillTreeLoader.str(file, at, m, "maxRarity", false, issues);
        ItemRarity maxRarity = maxR == null ? rarity : rarity(file, at + ".maxRarity", maxR, issues);
        if (rarity != null && maxRarity != null && maxRarity.ordinal() < rarity.ordinal()) issues.add(new Issue(file, at + ".maxRarity", "below rarity"));
        if (maxRarity == ItemRarity.UNIQUE && rarity != ItemRarity.UNIQUE) issues.add(new Issue(file, at + ".maxRarity", "unique items cannot roll; give rarity unique (a relic)"));
        int level = SkillTreeLoader.intIn(file, at, m, "level", 1, ItemDefinition.MAX_LEVEL, true, 1, issues);
        Set<PlayerClass> classes = EnumSet.noneOf(PlayerClass.class);
        Object cl = m.get("classes");
        if (cl != null) {
            if (!(cl instanceof List<?> l)) issues.add(new Issue(file, at + ".classes", "must be a list of class ids"));
            else for (Object c : l) {
                PlayerClass pc = c instanceof String s ? PlayerClass.byId(s).orElse(null) : null;
                if (pc == null) issues.add(new Issue(file, at + ".classes", "unknown class " + c));
                else classes.add(pc);
            }
        }
        Map<ItemStat, StatRange> stats = new EnumMap<>(ItemStat.class);
        Object st = m.get("stats");
        if (st != null) {
            if (!(st instanceof Map<?, ?> sm)) issues.add(new Issue(file, at + ".stats", "must be an object of stat: value or [min, max]"));
            else for (Map.Entry<?, ?> e : sm.entrySet()) {
                ItemStat s = ItemStat.byId(String.valueOf(e.getKey())).orElse(null);
                String path = at + ".stats." + e.getKey();
                if (s == null) {
                    issues.add(new Issue(file, path, "unknown stat (" + statNames() + ")"));
                    continue;
                }
                StatRange r = range(file, path, e.getValue(), issues);
                if (r != null) stats.put(s, r);
            }
        }
        Map<ItemStat, Double> perLevel = new EnumMap<>(ItemStat.class);
        Object pl = m.get("perLevel");
        if (pl != null) {
            if (!(pl instanceof Map<?, ?> pm)) issues.add(new Issue(file, at + ".perLevel", "must be an object of stat: number"));
            else for (Map.Entry<?, ?> e : pm.entrySet()) {
                ItemStat s = ItemStat.byId(String.valueOf(e.getKey())).orElse(null);
                String path = at + ".perLevel." + e.getKey();
                if (s == null) issues.add(new Issue(file, path, "unknown stat"));
                else if (!stats.containsKey(s)) issues.add(new Issue(file, path, "growth for a stat the item does not have"));
                else if (!(e.getValue() instanceof Number n) || n.doubleValue() < 0 || n.doubleValue() > 100) issues.add(new Issue(file, path, "must be a number 0..100"));
                else perLevel.put(s, n.doubleValue());
            }
        }
        List<Effect> effects = new ArrayList<>();
        Object fx = m.get("effects");
        if (fx != null) {
            if (!(fx instanceof List<?> fl)) issues.add(new Issue(file, at + ".effects", "must be a list"));
            else for (int i = 0; i < fl.size(); i++) {
                PlayerClass only = classes.size() == 1 ? classes.iterator().next() : null;
                Effect e = SkillTreeLoader.parseEffect(file, at + ".effects[" + i + "]", fl.get(i), only, issues);
                if (e == null) continue;
                if (e instanceof Effect.UnlockUltimate || e instanceof Effect.Keystone) {
                    issues.add(new Issue(file, at + ".effects[" + i + "]", "ultimates and keystones come from the skill tree, not from items"));
                } else if (e instanceof Effect.SpellMod sm && only == null) {
                    issues.add(new Issue(file, at + ".effects[" + i + "]", "a spell modifier needs the item to be for exactly one class"));
                } else {
                    effects.add(e);
                }
            }
        }
        int durability = SkillTreeLoader.intIn(file, at, m, "durability", 0, 100_000, false, 0, issues);
        Binding binding = Binding.NONE;
        String b = SkillTreeLoader.str(file, at, m, "binding", false, issues);
        if (b != null) binding = Binding.byName(b).orElse(null);
        if (binding == null) {
            issues.add(new Issue(file, at + ".binding", "unknown binding " + b + " (NONE, ON_PICKUP, ON_EQUIP, SOULBOUND)"));
            binding = Binding.NONE;
        }
        boolean tradable = !Boolean.FALSE.equals(m.get("tradable"));
        int stack = SkillTreeLoader.intIn(file, at, m, "stack", 1, 64, false, 1, issues);
        int sell = SkillTreeLoader.intIn(file, at, m, "sell", 0, 1_000_000, false, 0, issues);
        String set = SkillTreeLoader.str(file, at, m, "set", false, issues);
        boolean lootable = !Boolean.FALSE.equals(m.get("lootable"));
        if (type != null) {
            if (type.equippable() && stack > 1) issues.add(new Issue(file, at + ".stack", "equipment does not stack"));
            if (!type.equippable() && (!stats.isEmpty() || !effects.isEmpty())) issues.add(new Issue(file, at + ".stats", "stats on an item that cannot be equipped would do nothing"));
            if (!type.wears() && durability > 0) issues.add(new Issue(file, at + ".durability", type + " does not wear"));
            if (type == ItemType.RELIC && rarity != ItemRarity.UNIQUE) issues.add(new Issue(file, at + ".rarity", "relics are unique"));
        }
        if (rarity == ItemRarity.UNIQUE) {
            if (type != ItemType.RELIC) issues.add(new Issue(file, at + ".type", "unique items are relics (type RELIC), owned through the relic system"));
            if (id != null && !id.startsWith("relic.")) issues.add(new Issue(file, at + ".id", "a unique item's id is its relic key (relic.…)"));
            if (lootable) issues.add(new Issue(file, at + ".lootable", "unique items can never be loot: set lootable false"));
        }
        if (issues.size() != before || id == null || type == null || rarity == null || maxRarity == null) return null;
        try {
            return new ItemDefinition(id, name, lore, material, model, type, rarity, maxRarity, level, classes, stats, perLevel, effects,
                    durability, binding, tradable, stack, sell, set, lootable);
        } catch (IllegalArgumentException e) {
            issues.add(new Issue(file, at, e.getMessage()));
            return null;
        }
    }

    // ------------------------------------------------------------------ affixes

    private static List<Affix> affixes(String file, Map<String, Object> doc, List<Issue> issues) {
        List<Affix> out = new ArrayList<>();
        Object arr = doc.get("affixes");
        if (!(arr instanceof List<?> list)) {
            issues.add(new Issue(file, "affixes", "missing (an array)"));
            return out;
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            String at = "affixes[" + i + "]";
            if (!(list.get(i) instanceof Map<?, ?> raw)) {
                issues.add(new Issue(file, at, "must be an object"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) raw;
            int before = issues.size();
            String id = SkillTreeLoader.str(file, at, m, "id", true, issues);
            if (id != null) {
                at = id;
                if (!AFFIX_ID.matcher(id).matches()) issues.add(new Issue(file, at + ".id", "lower case letters, digits, _"));
                if (!ids.add(id)) issues.add(new Issue(file, at + ".id", "duplicate affix id"));
            }
            String name = SkillTreeLoader.str(file, at, m, "name", true, issues);
            String pos = SkillTreeLoader.str(file, at, m, "position", true, issues);
            if (pos != null && !pos.equals("prefix") && !pos.equals("suffix")) issues.add(new Issue(file, at + ".position", "prefix or suffix"));
            ItemStat stat = null;
            Spell spell = null;
            ModKey mod = null;
            if (m.containsKey("stat")) {
                stat = ItemStat.byId(String.valueOf(m.get("stat"))).orElse(null);
                if (stat == null) issues.add(new Issue(file, at + ".stat", "unknown stat (" + statNames() + ")"));
            } else if (m.containsKey("spell")) {
                spell = SkillTreeLoader.enumOf(Spell.class, SkillTreeLoader.str(file, at, m, "spell", true, issues), file, at + ".spell", issues);
                mod = SkillTreeLoader.enumOf(ModKey.class, SkillTreeLoader.str(file, at, m, "mod", true, issues), file, at + ".mod", issues);
                if (spell != null && mod != null && !SpellMods.supports(spell, mod)) issues.add(new Issue(file, at + ".mod", spell + " does not support " + mod));
            } else {
                issues.add(new Issue(file, at, "needs stat or spell+mod"));
            }
            double min = SkillTreeLoader.num(file, at, m, "min", -1000, 1000, issues);
            double max = SkillTreeLoader.num(file, at, m, "max", -1000, 1000, issues);
            if (max < min) issues.add(new Issue(file, at + ".max", "below min"));
            double perLevel = m.containsKey("perLevel") ? SkillTreeLoader.num(file, at, m, "perLevel", 0, 100, issues) : 0;
            ItemRarity minR = m.containsKey("minRarity") ? rarity(file, at + ".minRarity", SkillTreeLoader.str(file, at, m, "minRarity", true, issues), issues) : ItemRarity.UNCOMMON;
            if (minR == ItemRarity.UNIQUE || minR == ItemRarity.COMMON) issues.add(new Issue(file, at + ".minRarity", "common and unique items have no affixes"));
            Set<ItemType.Category> cats = EnumSet.noneOf(ItemType.Category.class);
            Object c = m.get("categories");
            if (!(c instanceof List<?> cl) || cl.isEmpty()) issues.add(new Issue(file, at + ".categories", "list the item categories (WEAPON, OFFHAND, ARMOR, JEWELRY)"));
            else for (Object x : cl) {
                ItemType.Category cat = SkillTreeLoader.enumOf(ItemType.Category.class, String.valueOf(x), file, at + ".categories", issues);
                if (cat == ItemType.Category.MATERIAL || cat == ItemType.Category.RELIC) issues.add(new Issue(file, at + ".categories", cat + " items have no affixes"));
                else if (cat != null) cats.add(cat);
            }
            int weight = SkillTreeLoader.intIn(file, at, m, "weight", 1, 10_000, true, 1, issues);
            if (issues.size() == before) out.add(new Affix(id, name, "prefix".equals(pos), stat, spell, mod, min, max, perLevel, minR, cats, weight));
        }
        return out;
    }

    // ------------------------------------------------------------------ sets

    private static List<ItemSet> sets(String file, Map<String, Object> doc, Map<String, ItemDefinition> items, List<Issue> issues) {
        List<ItemSet> out = new ArrayList<>();
        Object arr = doc.get("sets");
        if (!(arr instanceof List<?> list)) {
            issues.add(new Issue(file, "sets", "missing (an array)"));
            return out;
        }
        for (int i = 0; i < list.size(); i++) {
            String at = "sets[" + i + "]";
            if (!(list.get(i) instanceof Map<?, ?> raw)) {
                issues.add(new Issue(file, at, "must be an object"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) raw;
            int before = issues.size();
            String id = SkillTreeLoader.str(file, at, m, "id", true, issues);
            if (id != null) at = id;
            String name = SkillTreeLoader.str(file, at, m, "name", true, issues);
            List<String> pieces = new ArrayList<>();
            Set<EquipSlot> slots = EnumSet.noneOf(EquipSlot.class);
            Object p = m.get("pieces");
            if (!(p instanceof List<?> pl) || pl.size() < 2) issues.add(new Issue(file, at + ".pieces", "a set needs at least two pieces"));
            else for (Object x : pl) {
                ItemDefinition d = x instanceof String s ? items.get(s) : null;
                if (d == null) issues.add(new Issue(file, at + ".pieces", "unknown item " + x));
                else if (!id.equals(d.setId())) issues.add(new Issue(file, at + ".pieces", d.id() + " does not name this set in its \"set\" field"));
                else {
                    pieces.add(d.id());
                    if (d.type().slots().size() == 1 && !slots.addAll(d.type().slots())) {
                        issues.add(new Issue(file, at + ".pieces", d.id() + " needs the same slot as another piece: the set could never be complete"));
                    }
                }
            }
            NavigableBonuses bonuses = new NavigableBonuses();
            Object bo = m.get("bonuses");
            if (!(bo instanceof Map<?, ?> bm) || bm.isEmpty()) issues.add(new Issue(file, at + ".bonuses", "missing ({\"2\": {...}})"));
            else for (Map.Entry<?, ?> e : bm.entrySet()) {
                String path = at + ".bonuses." + e.getKey();
                int count;
                try {
                    count = Integer.parseInt(String.valueOf(e.getKey()));
                } catch (NumberFormatException ex) {
                    issues.add(new Issue(file, path, "the key is the number of pieces"));
                    continue;
                }
                if (count < 2 || count > pieces.size() && !pieces.isEmpty()) issues.add(new Issue(file, path, "needs 2.." + pieces.size() + " pieces"));
                if (!(e.getValue() instanceof Map<?, ?> rawB)) {
                    issues.add(new Issue(file, path, "must be an object"));
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> b = (Map<String, Object>) rawB;
                Map<ItemStat, Double> st = new EnumMap<>(ItemStat.class);
                if (b.get("stats") instanceof Map<?, ?> sm) {
                    for (Map.Entry<?, ?> se : sm.entrySet()) {
                        ItemStat s = ItemStat.byId(String.valueOf(se.getKey())).orElse(null);
                        if (s == null || !(se.getValue() instanceof Number n)) issues.add(new Issue(file, path + ".stats." + se.getKey(), "unknown stat or not a number"));
                        else st.put(s, n.doubleValue());
                    }
                }
                List<Effect> fx = new ArrayList<>();
                if (b.get("effects") instanceof List<?> fl) {
                    for (int k = 0; k < fl.size(); k++) {
                        Effect ef = SkillTreeLoader.parseEffect(file, path + ".effects[" + k + "]", fl.get(k), null, issues);
                        if (ef instanceof Effect.UnlockUltimate || ef instanceof Effect.Keystone || ef instanceof Effect.SpellMod) {
                            issues.add(new Issue(file, path + ".effects[" + k + "]", "set bonuses give stats and passive spells only"));
                        } else if (ef != null) {
                            fx.add(ef);
                        }
                    }
                }
                if (st.isEmpty() && fx.isEmpty()) issues.add(new Issue(file, path, "a bonus must give something"));
                bonuses.put(count, new ItemSet.Bonus(st, fx, b.get("text") instanceof String t ? t : ""));
            }
            if (issues.size() == before && id != null) out.add(new ItemSet(id, name, pieces, bonuses));
        }
        return out;
    }

    private static final class NavigableBonuses extends TreeMap<Integer, ItemSet.Bonus> {
    }

    // ------------------------------------------------------------------ rarity bands

    private static List<RarityBand> bands(String file, Map<String, Object> doc, List<Issue> issues) {
        List<RarityBand> out = new ArrayList<>();
        for (LootTier t : LootTier.values()) {
            Object o = doc.get(t.name());
            if (!(o instanceof Map<?, ?> m) || m.isEmpty()) {
                issues.add(new Issue(file, t.name(), "missing: every loot tier needs a rarity band"));
                continue;
            }
            Map<ItemRarity, Integer> w = new EnumMap<>(ItemRarity.class);
            for (Map.Entry<?, ?> e : m.entrySet()) {
                ItemRarity r = ItemRarity.byId(String.valueOf(e.getKey())).orElse(null);
                if (r == null) issues.add(new Issue(file, t.name() + "." + e.getKey(), "unknown rarity"));
                else if (r == ItemRarity.UNIQUE) issues.add(new Issue(file, t.name() + ".unique", "unique items never drop"));
                else if (!(e.getValue() instanceof Number n) || n.intValue() <= 0) issues.add(new Issue(file, t.name() + "." + e.getKey(), "weight must be a positive number"));
                else w.put(r, n.intValue());
            }
            out.add(new RarityBand(t, w));
        }
        for (String k : doc.keySet()) if (LootTier.byName(k).isEmpty()) issues.add(new Issue(file, k, "unknown loot tier"));
        return out;
    }

    // ------------------------------------------------------------------ loot tables

    private static List<LootTable> tables(String file, Map<String, Object> doc, Map<String, ItemDefinition> items, List<Issue> issues) {
        List<LootTable> out = new ArrayList<>();
        Object arr = doc.get("tables");
        if (!(arr instanceof List<?> list)) {
            issues.add(new Issue(file, "tables", "missing (an array)"));
            return out;
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            String at = "tables[" + i + "]";
            if (!(list.get(i) instanceof Map<?, ?> raw)) {
                issues.add(new Issue(file, at, "must be an object"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) raw;
            int before = issues.size();
            String id = SkillTreeLoader.str(file, at, m, "id", true, issues);
            if (id != null) {
                at = id;
                if (!ids.add(id)) issues.add(new Issue(file, at + ".id", "duplicate table id"));
            }
            LootTier tier = LootTier.NORMAL;
            String ts = SkillTreeLoader.str(file, at, m, "tier", false, issues);
            if (ts != null) tier = LootTier.byName(ts).orElse(null);
            if (tier == null) {
                issues.add(new Issue(file, at + ".tier", "unknown loot tier " + ts));
                tier = LootTier.NORMAL;
            }
            int[] rolls = pair(file, at + ".rolls", m.getOrDefault("rolls", List.of(1, 1)), 0, 20, issues);
            int nothing = SkillTreeLoader.intIn(file, at, m, "nothing", 0, 1_000_000, false, 0, issues);
            List<LootTable.Entry> guaranteed = entries(file, at + ".guaranteed", m.get("guaranteed"), items, false, issues);
            List<LootTable.Entry> entries = entries(file, at + ".entries", m.get("entries"), items, true, issues);
            List<LootTable.Rare> rare = new ArrayList<>();
            Object ra = m.get("rare");
            if (ra != null) {
                if (!(ra instanceof List<?> rl)) issues.add(new Issue(file, at + ".rare", "must be a list"));
                else for (int k = 0; k < rl.size(); k++) {
                    String path = at + ".rare[" + k + "]";
                    LootTable.Entry e = entry(file, path, rl.get(k), items, false, issues);
                    if (!(rl.get(k) instanceof Map<?, ?> rm) || !(rm.get("chance") instanceof Number c) || c.doubleValue() <= 0 || c.doubleValue() > 1) {
                        issues.add(new Issue(file, path + ".chance", "a chance in (0, 1]"));
                    } else if (e != null) {
                        rare.add(new LootTable.Rare(e, c.doubleValue()));
                    }
                }
            }
            if (entries.isEmpty() && guaranteed.isEmpty() && rare.isEmpty()) issues.add(new Issue(file, at, "an empty table"));
            if (rolls != null && rolls[1] > 0 && entries.isEmpty()) issues.add(new Issue(file, at + ".rolls", "rolls without entries"));
            if (issues.size() == before && id != null && rolls != null) out.add(new LootTable(id, tier, rolls[0], rolls[1], nothing, guaranteed, entries, rare));
        }
        return out;
    }

    private static List<LootTable.Entry> entries(String file, String at, Object o, Map<String, ItemDefinition> items, boolean weighted, List<Issue> issues) {
        List<LootTable.Entry> out = new ArrayList<>();
        if (o == null) return out;
        if (!(o instanceof List<?> l)) {
            issues.add(new Issue(file, at, "must be a list"));
            return out;
        }
        for (int i = 0; i < l.size(); i++) {
            LootTable.Entry e = entry(file, at + "[" + i + "]", l.get(i), items, weighted, issues);
            if (e != null) out.add(e);
        }
        return out;
    }

    private static LootTable.Entry entry(String file, String at, Object o, Map<String, ItemDefinition> items, boolean weighted, List<Issue> issues) {
        if (!(o instanceof Map<?, ?> raw)) {
            issues.add(new Issue(file, at, "must be an object"));
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) raw;
        int before = issues.size();
        String item = SkillTreeLoader.str(file, at, m, "item", false, issues);
        LootTable.Pool pool = null;
        ItemDefinition def = null;
        if (item != null) {
            def = items.get(item);
            if (def == null) issues.add(new Issue(file, at + ".item", "unknown item " + item));
            else if (def.rarity() == ItemRarity.UNIQUE) issues.add(new Issue(file, at + ".item", item + " is unique: unique items can never be loot"));
        } else if (m.get("pool") instanceof List<?> pl) {
            Set<ItemType.Category> cats = EnumSet.noneOf(ItemType.Category.class);
            for (Object x : pl) {
                ItemType.Category c = SkillTreeLoader.enumOf(ItemType.Category.class, String.valueOf(x), file, at + ".pool", issues);
                if (c == ItemType.Category.RELIC || c == ItemType.Category.MATERIAL) issues.add(new Issue(file, at + ".pool", c + " cannot be pooled"));
                else if (c != null) cats.add(c);
            }
            pool = new LootTable.Pool(cats);
        } else {
            issues.add(new Issue(file, at, "needs \"item\" or \"pool\""));
        }
        int weight = weighted ? SkillTreeLoader.intIn(file, at, m, "weight", 1, 1_000_000, true, 1, issues) : 1;
        int[] qty = pair(file, at + ".qty", m.getOrDefault("qty", List.of(1, 1)), 1, 64, issues);
        ItemRarity lo = null, hi = null;
        if (m.get("rarity") instanceof List<?> rl && rl.size() == 2) {
            lo = rarity(file, at + ".rarity", String.valueOf(rl.get(0)), issues);
            hi = rarity(file, at + ".rarity", String.valueOf(rl.get(1)), issues);
            if (lo != null && hi != null && hi.ordinal() < lo.ordinal()) issues.add(new Issue(file, at + ".rarity", "max below min"));
            if (hi == ItemRarity.UNIQUE) issues.add(new Issue(file, at + ".rarity", "unique items never drop"));
            if (def != null && lo != null && hi != null && (hi.ordinal() < def.rarity().ordinal() || lo.ordinal() > def.maxRarity().ordinal())) {
                issues.add(new Issue(file, at + ".rarity", def.id() + " can only be " + def.rarity().id() + ".." + def.maxRarity().id()));
            }
        } else if (m.containsKey("rarity")) {
            issues.add(new Issue(file, at + ".rarity", "must be [min, max]"));
        }
        int spread = SkillTreeLoader.intIn(file, at, m, "spread", 0, 20, false, 0, issues);
        Map<PlayerClass, Double> cw = new EnumMap<>(PlayerClass.class);
        if (m.get("classWeight") instanceof Map<?, ?> cm) {
            for (Map.Entry<?, ?> e : cm.entrySet()) {
                PlayerClass pc = PlayerClass.byId(String.valueOf(e.getKey())).orElse(null);
                if (pc == null || !(e.getValue() instanceof Number n) || n.doubleValue() < 0) issues.add(new Issue(file, at + ".classWeight." + e.getKey(), "class id: non-negative multiplier"));
                else cw.put(pc, n.doubleValue());
            }
        }
        if (def != null && def.stackable() && qty != null && qty[1] > def.maxStack()) issues.add(new Issue(file, at + ".qty", "more than a stack of " + def.maxStack()));
        if (issues.size() != before || qty == null) return null;
        return new LootTable.Entry(item, pool, weight, qty[0], qty[1], lo, hi, spread, cw);
    }

    // ------------------------------------------------------------------ recipes

    private static List<Recipe> recipes(String file, Map<String, Object> doc, Map<String, ItemDefinition> items, List<Issue> issues) {
        List<Recipe> out = new ArrayList<>();
        Object arr = doc.get("recipes");
        if (!(arr instanceof List<?> list)) {
            issues.add(new Issue(file, "recipes", "missing (an array)"));
            return out;
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            String at = "recipes[" + i + "]";
            if (!(list.get(i) instanceof Map<?, ?> raw)) {
                issues.add(new Issue(file, at, "must be an object"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) raw;
            int before = issues.size();
            String id = SkillTreeLoader.str(file, at, m, "id", true, issues);
            if (id != null) {
                at = id;
                if (!ids.add(id)) issues.add(new Issue(file, at + ".id", "duplicate recipe"));
            }
            String result = SkillTreeLoader.str(file, at, m, "result", true, issues);
            ItemDefinition def = result == null ? null : items.get(result);
            if (result != null && def == null) issues.add(new Issue(file, at + ".result", "unknown item " + result));
            if (def != null && def.rarity() == ItemRarity.UNIQUE) issues.add(new Issue(file, at + ".result", "unique items cannot be crafted"));
            Map<String, Integer> mats = new LinkedHashMap<>();
            if (!(m.get("materials") instanceof Map<?, ?> mm) || mm.isEmpty()) issues.add(new Issue(file, at + ".materials", "missing"));
            else for (Map.Entry<?, ?> e : mm.entrySet()) {
                ItemDefinition md = items.get(String.valueOf(e.getKey()));
                if (md == null || md.type() != ItemType.MATERIAL) issues.add(new Issue(file, at + ".materials." + e.getKey(), "not a known material"));
                else if (!(e.getValue() instanceof Number n) || n.intValue() < 1 || n.intValue() > 64) issues.add(new Issue(file, at + ".materials." + e.getKey(), "count 1..64"));
                else mats.put(md.id(), n.intValue());
            }
            int coins = SkillTreeLoader.intIn(file, at, m, "coins", 0, 1_000_000, false, 0, issues);
            int level = SkillTreeLoader.intIn(file, at, m, "level", 1, ItemDefinition.MAX_LEVEL, false, 1, issues);
            ItemRarity lo = def == null ? null : def.rarity(), hi = def == null ? null : def.maxRarity();
            if (m.get("rarity") instanceof List<?> rl && rl.size() == 2) {
                lo = rarity(file, at + ".rarity", String.valueOf(rl.get(0)), issues);
                hi = rarity(file, at + ".rarity", String.valueOf(rl.get(1)), issues);
            }
            if (def != null && lo != null && hi != null && (!def.canRoll(lo) || !def.canRoll(hi) || hi.ordinal() < lo.ordinal())) {
                issues.add(new Issue(file, at + ".rarity", def.id() + " can only be " + def.rarity().id() + ".." + def.maxRarity().id()));
            }
            if (issues.size() == before && id != null && def != null) out.add(new Recipe(id, result, mats, coins, Math.max(level, def.levelReq()), lo, hi));
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> readObject(SkillTreeLoader.Source src, String file, List<Issue> issues) {
        String text;
        try {
            text = src.read(file);
        } catch (NoSuchFileException e) {
            issues.add(new Issue(file, "", "file is missing"));
            return null;
        } catch (IOException e) {
            issues.add(new Issue(file, "", "cannot be read: " + e.getMessage()));
            return null;
        }
        try {
            return Json.object(Json.parse(text));
        } catch (IllegalArgumentException e) {
            issues.add(new Issue(file, "", "broken JSON: " + e.getMessage()));
            return null;
        }
    }

    private static ItemRarity rarity(String file, String path, String id, List<Issue> issues) {
        if (id == null) return null;
        ItemRarity r = ItemRarity.byId(id).orElse(null);
        if (r == null) issues.add(new Issue(file, path, "unknown rarity '" + id + "' (common, uncommon, rare, epic, legendary, ancient, mythic, unique)"));
        return r;
    }

    private static StatRange range(String file, String path, Object v, List<Issue> issues) {
        if (v instanceof Number n && Double.isFinite(n.doubleValue())) return StatRange.of(n.doubleValue());
        if (v instanceof List<?> l && l.size() == 2 && l.get(0) instanceof Number a && l.get(1) instanceof Number b) {
            if (b.doubleValue() < a.doubleValue()) {
                issues.add(new Issue(file, path, "max below min"));
                return null;
            }
            if (Math.abs(a.doubleValue()) > 10_000 || Math.abs(b.doubleValue()) > 10_000) {
                issues.add(new Issue(file, path, "outside -10000..10000"));
                return null;
            }
            return new StatRange(a.doubleValue(), b.doubleValue());
        }
        issues.add(new Issue(file, path, "must be a number or [min, max]"));
        return null;
    }

    private static int[] pair(String file, String path, Object v, int lo, int hi, List<Issue> issues) {
        if (v instanceof List<?> l && l.size() == 2 && l.get(0) instanceof Number a && l.get(1) instanceof Number b
                && a.doubleValue() == Math.rint(a.doubleValue()) && b.doubleValue() == Math.rint(b.doubleValue())) {
            int x = a.intValue(), y = b.intValue();
            if (x < lo || y > hi || y < x) {
                issues.add(new Issue(file, path, "[" + x + ", " + y + "] must be within " + lo + ".." + hi + ", min first"));
                return null;
            }
            return new int[]{x, y};
        }
        issues.add(new Issue(file, path, "must be [min, max] whole numbers"));
        return null;
    }

    private static String statNames() {
        StringBuilder sb = new StringBuilder();
        for (ItemStat s : ItemStat.values()) sb.append(sb.length() == 0 ? "" : ", ").append(s.id());
        return sb.toString();
    }
}
