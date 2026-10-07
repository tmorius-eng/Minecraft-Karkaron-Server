package mn.suld.api.death;

import mn.suld.api.config.DeathSettings;
import mn.suld.api.progression.Progression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Hardcore-PvE death decisions (pure; the plugin applies them). A death costs a fraction of the progress into the
 * current level (never a level), drops a fraction of the non-soulbound item stacks, and wears the kept gear.
 * Soulbound items (class weapons, relic tokens, the menu) are always kept.
 */
public final class DeathRules {

    private DeathRules() {
    }

    /** Progress after a death: {@code expIntoLevel} reduced by the configured fraction; the level never drops. */
    public static Progression afterDeath(Progression p, DeathSettings s) {
        if (!s.enabled()) return p;
        long lost = (long) Math.floor(p.expIntoLevel() * s.expLossFraction());
        return new Progression(p.level(), Math.max(0, p.expIntoLevel() - lost));
    }

    /**
     * Which of {@code droppable} stack indices are lost (dropped where the player died): a random
     * {@code lootLossFraction} of them, chosen with {@code rng}. The fraction is rounded at random (2.75 stacks =
     * 2, plus a third with 75 % chance), so a small inventory still carries the expected loss instead of none.
     * Empty when loot drop is off.
     */
    public static List<Integer> lostStacks(List<Integer> droppable, DeathSettings s, Random rng) {
        if (!s.enabled() || !s.dropLoot() || droppable.isEmpty()) return List.of();
        double exact = droppable.size() * s.lootLossFraction();
        int n = (int) Math.floor(exact);
        List<Integer> shuffled = new ArrayList<>(droppable);
        Collections.shuffle(shuffled, rng);
        double frac = exact - n;
        if (frac > 1e-9 && rng.nextDouble() < frac) n = Math.min(droppable.size(), n + 1);
        List<Integer> out = new ArrayList<>(shuffled.subList(0, n));
        Collections.sort(out);
        return out;
    }

    /** New damage value for a kept item: + fraction of its max durability, capped one short of breaking. */
    public static int wear(int damage, int maxDurability, DeathSettings s) {
        if (!s.enabled() || maxDurability <= 0) return damage;
        int add = (int) Math.ceil(maxDurability * s.durabilityDamageFraction());
        return Math.min(maxDurability - 1, damage + add);
    }
}
