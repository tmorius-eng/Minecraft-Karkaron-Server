package mn.suld.api.item;

import mn.suld.api.skill.tree.Effect;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * An equipment set: a bonus becomes active once the player wears at least that many <b>different</b> pieces of it.
 * Bonuses add up (wearing 4 pieces gives the 2-, 3- and 4-piece bonuses).
 */
public record ItemSet(String id, String name, List<String> pieces, NavigableMap<Integer, Bonus> bonuses) {

    /** What one threshold adds: stats and effects (passive spells, spell modifiers). */
    public record Bonus(Map<ItemStat, Double> stats, List<Effect> effects, String description) {
        public Bonus {
            Map<ItemStat, Double> s = new EnumMap<>(ItemStat.class);
            if (stats != null) s.putAll(stats);
            stats = java.util.Collections.unmodifiableMap(s);
            effects = effects == null ? List.of() : List.copyOf(effects);
            description = description == null ? "" : description;
        }
    }

    public ItemSet {
        Objects.requireNonNull(id, "id");
        pieces = List.copyOf(pieces);
        bonuses = java.util.Collections.unmodifiableNavigableMap(new TreeMap<>(bonuses));
    }

    /** Bonuses active with {@code worn} different pieces. */
    public List<Bonus> active(int worn) {
        return List.copyOf(bonuses.headMap(worn, true).values());
    }
}
