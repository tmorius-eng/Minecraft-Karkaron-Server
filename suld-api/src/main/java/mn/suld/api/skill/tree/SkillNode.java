package mn.suld.api.skill.tree;

import mn.suld.api.skill.Spell;

import java.util.List;
import java.util.Objects;

/**
 * One node of a class tree, loaded from a data file. Persistence refers to nodes by {@code id}, never by position,
 * so content can be reordered or extended without breaking saved builds.
 *
 * @param cost      points per rank
 * @param maxRank   how many times it can be bought (stat and spell-modifier effects scale with the rank)
 * @param icon      a vanilla item id such as {@code IRON_SWORD}
 * @param requires  extra nodes (with a minimum rank) that must be learned in addition to a neighbouring node
 * @param hidden    not shown on the map until a neighbour is learned
 * @param secret    shown as "???" (effects concealed) until learned
 */
public record SkillNode(int index, String id, String name, String description, SkillCategory category, String icon,
                        int x, int y, int cost, int maxRank, int requiredLevel, List<Req> requires,
                        List<Effect> effects, List<String> tags, boolean keystone, boolean capstone,
                        boolean hidden, boolean secret, int version) {

    public record Req(String node, int rank) {
    }

    public SkillNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        description = description == null ? "" : description;
        category = category == null ? SkillCategory.UTILITY : category;
        icon = icon == null || icon.isBlank() ? "PAPER" : icon;
        requires = List.copyOf(requires);
        effects = List.copyOf(effects);
        tags = List.copyOf(tags);
    }

    public boolean root() { return index == 0; }

    /** Tag prefix of a satellite ("minor") node: {@code orbit:<hub id>}. Satellites hang off one grid node. */
    public static final String ORBIT = "orbit:";

    /** The hub of a satellite node, or null for a grid node. Satellites only show on the full-screen tree. */
    public String orbit() {
        for (String t : tags) if (t.startsWith(ORBIT)) return t.substring(ORBIT.length());
        return null;
    }

    public boolean satellite() {
        return orbit() != null;
    }

    /** The level needed: the node's own, and for a spell modifier also the level that unlocks that spell. */
    public int effectiveLevel() {
        int need = requiredLevel;
        for (Effect e : effects) {
            if (e instanceof Effect.SpellMod m) need = Math.max(need, m.spell().unlockLevel());
        }
        return need;
    }

    public boolean unlocksUltimate() {
        return effects.stream().anyMatch(e -> e instanceof Effect.UnlockUltimate);
    }

    public boolean hasProc() {
        return effects.stream().anyMatch(e -> e instanceof Effect.Proc);
    }

    public List<Spell> spells() {
        return effects.stream().filter(e -> e instanceof Effect.SpellMod).map(e -> ((Effect.SpellMod) e).spell()).distinct().toList();
    }
}
