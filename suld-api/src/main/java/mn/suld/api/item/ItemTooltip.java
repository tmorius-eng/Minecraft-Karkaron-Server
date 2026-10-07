package mn.suld.api.item;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.skill.tree.Effect;
import mn.suld.api.skill.tree.EffectText;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The text of an item's tooltip, as styled lines (the plugin maps styles to colours). Pure, so every line is tested.
 * Order: name, rarity · type, requirements, base stats, affixes, unique effects, set, binding, durability, source,
 * value, comparison with the equipped item.
 */
public final class ItemTooltip {

    private ItemTooltip() {
    }

    public enum Style { NAME, RARITY, TYPE, OK, FAIL, STAT, AFFIX, UNIQUE, SET, SET_ACTIVE, SET_INACTIVE, BINDING, INFO, FLAVOR, UP, DOWN, SAME }

    public record Line(Style style, String text) {
    }

    /** Who is looking: decides the colour of requirement lines and the "your set" count. Any field may be null. */
    public record Viewer(UUID id, PlayerClass clazz, int level, Set<String> wornSetPieces) {
        public static final Viewer NOBODY = new Viewer(null, null, 0, Set.of());
    }

    /** The display name: prefix affix + definition name + suffix affix (+N for upgrades). */
    public static String name(ItemCatalog catalog, ItemDefinition def, ItemInstance i) {
        String prefix = null, suffix = null;
        for (RolledAffix ra : i.affixes()) {
            Affix a = catalog.affix(ra.affixId()).orElse(null);
            if (a == null) continue;
            if (a.prefix() && prefix == null) prefix = a.name();
            if (!a.prefix() && suffix == null) suffix = a.name();
        }
        StringBuilder sb = new StringBuilder();
        if (prefix != null) sb.append(prefix).append(' ');
        sb.append(def.displayName());
        if (suffix != null) sb.append(" · ").append(suffix);
        if (i.upgradeLevel() > 0) sb.append(" +").append(i.upgradeLevel());
        return sb.toString();
    }

    public static List<Line> lines(ItemCatalog catalog, ItemDefinition def, ItemInstance i, Viewer v, int durability, int maxDurability,
                                   ItemInstance compareTo) {
        List<Line> out = new ArrayList<>();
        out.add(new Line(Style.NAME, name(catalog, def, i)));
        out.add(new Line(Style.RARITY, i.rarity().displayName() + " · " + def.type().label() + (def.equippable() ? " · Зэрэг " + i.itemLevel() : "")));
        if (def.equippable()) {
            int req = Equipment.requiredLevel(def, i);
            out.add(new Line(v.level() <= 0 || v.level() >= req ? Style.OK : Style.FAIL, "Шаардлагатай түвшин: " + req));
            if (!def.classes().isEmpty()) {
                StringBuilder c = new StringBuilder();
                for (PlayerClass pc : def.classes()) c.append(c.length() == 0 ? "" : ", ").append(pc.displayName());
                out.add(new Line(v.clazz() == null || def.allows(v.clazz()) ? Style.OK : Style.FAIL, "Анги: " + c));
            }
        }
        if (!i.stats().isEmpty()) {
            out.add(new Line(Style.INFO, ""));
            for (Map.Entry<ItemStat, Double> e : i.stats().entrySet()) {
                out.add(new Line(Style.STAT, "  " + e.getKey().format(e.getValue()) + " " + e.getKey().label()));
            }
        }
        for (RolledAffix ra : i.affixes()) {
            Affix a = catalog.affix(ra.affixId()).orElse(null);
            if (a == null) continue;
            String what = a.stat() != null ? a.stat().format(ra.value()) + " " + a.stat().label()
                    : EffectText.mod(new Effect.SpellMod(a.spell(), a.modKey(), ra.value()));
            out.add(new Line(Style.AFFIX, "  ◇ " + what));
        }
        for (Effect fx : def.effects()) {
            String t = switch (fx) {
                case Effect.Proc p -> EffectText.proc(p);
                case Effect.SpellMod m -> EffectText.mod(m);
                case Effect.Stat s -> EffectText.stat(s);
                default -> null;
            };
            if (t != null) out.add(new Line(Style.UNIQUE, "  ✦ " + t));
        }
        catalog.setOf(def).ifPresent(set -> {
            Set<String> worn = v.wornSetPieces() == null ? Set.of() : v.wornSetPieces();
            int have = 0;
            for (String p : set.pieces()) if (worn.contains(p)) have++;
            out.add(new Line(Style.INFO, ""));
            out.add(new Line(Style.SET, "Иж бүрдэл: " + set.name() + " (" + have + "/" + set.pieces().size() + ")"));
            for (Map.Entry<Integer, ItemSet.Bonus> b : set.bonuses().entrySet()) {
                out.add(new Line(have >= b.getKey() ? Style.SET_ACTIVE : Style.SET_INACTIVE, "  (" + b.getKey() + ") " + bonusText(b.getValue())));
            }
        });
        out.add(new Line(Style.INFO, ""));
        Binding binding = def.bindingAt(i.rarity());
        if (i.soulbound()) out.add(new Line(Style.BINDING, "✦ " + Binding.SOULBOUND.label()));
        else if (i.bound()) out.add(new Line(Style.BINDING, "✦ Холбогдсон" + (v.id() != null && !v.id().equals(i.boundTo()) ? " (өөр тоглогчид)" : "")));
        else if (binding != Binding.NONE) out.add(new Line(Style.BINDING, binding.label()));
        if (!def.tradable() || i.soulbound()) out.add(new Line(Style.INFO, "Арилжаалах боломжгүй"));
        if (maxDurability > 0) {
            out.add(new Line(durability <= 0 ? Style.FAIL : Style.INFO, "Бат бөх: " + Math.max(0, durability) + " / " + maxDurability
                    + (durability <= 0 ? " — эвдэрсэн" : "")));
        }
        if (!def.lore().isEmpty()) out.add(new Line(Style.FLAVOR, def.lore()));
        if (i.rarity().isServerUnique()) out.add(new Line(Style.UNIQUE, "✦ ДЭЛХИЙД ГАНЦ · 1 / 1"));
        long price = ItemEconomy.sellPrice(def, i);
        if (price > 0) out.add(new Line(Style.INFO, "Үнэ: " + price + " ₮"));
        if (compareTo != null) out.addAll(compare(catalog, i, compareTo));
        return out;
    }

    /** "Compared with what you wear": each stat that differs, up or down. */
    public static List<Line> compare(ItemCatalog catalog, ItemInstance hovered, ItemInstance equipped) {
        Map<ItemStat, Double> a = Equipment.itemStats(catalog, hovered);
        Map<ItemStat, Double> b = Equipment.itemStats(catalog, equipped);
        Set<ItemStat> all = new LinkedHashSet<>(a.keySet());
        all.addAll(b.keySet());
        Map<ItemStat, Double> diff = new EnumMap<>(ItemStat.class);
        for (ItemStat s : all) {
            double d = ItemGenerator.round(a.getOrDefault(s, 0.0) - b.getOrDefault(s, 0.0));
            if (d != 0) diff.put(s, d);
        }
        List<Line> out = new ArrayList<>();
        out.add(new Line(Style.INFO, ""));
        out.add(new Line(Style.INFO, "Өмссөнтэй харьцуулбал:"));
        if (diff.isEmpty()) out.add(new Line(Style.SAME, "  ижил"));
        for (Map.Entry<ItemStat, Double> e : diff.entrySet()) {
            out.add(new Line(e.getValue() > 0 ? Style.UP : Style.DOWN, "  " + (e.getValue() > 0 ? "▲ " : "▼ ") + e.getKey().format(e.getValue()) + " " + e.getKey().label()));
        }
        return out;
    }

    public static String bonusText(ItemSet.Bonus b) {
        if (!b.description().isEmpty()) return b.description();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<ItemStat, Double> e : b.stats().entrySet()) sb.append(sb.length() == 0 ? "" : ", ").append(e.getKey().format(e.getValue())).append(' ').append(e.getKey().label());
        for (Effect fx : b.effects()) {
            String t = fx instanceof Effect.Proc p ? EffectText.proc(p) : fx instanceof Effect.SpellMod m ? EffectText.mod(m) : fx instanceof Effect.Stat s ? EffectText.stat(s) : "";
            if (!t.isEmpty()) sb.append(sb.length() == 0 ? "" : ", ").append(t);
        }
        return sb.toString();
    }
}
