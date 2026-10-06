package mn.suld.api.dungeon;

import mn.suld.api.mob.MobDefinition;

import java.util.List;

/**
 * Extended mob definition for a dungeon boss: wraps a {@link MobDefinition} and
 * adds phase-escalation data and an enrage timer.
 *
 * <p>Phases are listed in <b>descending</b> threshold order, starting with the
 * opening phase at threshold {@code 1.0} (e.g. 1.0 → 0.6 → 0.3). A phase is
 * active while the boss's HP fraction is {@code <=} its threshold; the active
 * phase is the last one in the list that satisfies that.
 *
 * @param mob            underlying mob definition (entity type, health, attack, tier, etc.)
 * @param phases         phases in descending threshold order; the first must be 1.0
 * @param enrageSeconds  if the boss is still alive after this many seconds it is
 *                       forced into the final phase; 0 = no timer
 */
public record BossDefinition(
        MobDefinition mob,
        List<BossPhase> phases,
        int enrageSeconds) {

    public BossDefinition {
        if (phases == null || phases.isEmpty())
            throw new IllegalArgumentException("A boss must have at least one phase");
        if (phases.get(0).healthThresholdPct() != 1.0)
            throw new IllegalArgumentException("The first boss phase must have threshold 1.0");
        for (int i = 1; i < phases.size(); i++) {
            if (phases.get(i).healthThresholdPct() >= phases.get(i - 1).healthThresholdPct())
                throw new IllegalArgumentException("Boss phases must be in strictly descending threshold order");
        }
        if (enrageSeconds < 0)
            throw new IllegalArgumentException("enrageSeconds must be >= 0");
        phases = List.copyOf(phases);
    }

    public String id() { return mob.id(); }
    public String displayName() { return mob.displayName(); }

    /** Index of the active phase for the given HP fraction [0, 1]. */
    public int activePhaseIndex(double hpFraction) {
        int active = 0;
        for (int i = 0; i < phases.size(); i++) {
            if (hpFraction <= phases.get(i).healthThresholdPct()) {
                active = i;
            }
        }
        return active;
    }

    /** The active phase for the given HP fraction [0, 1]. */
    public BossPhase activePhase(double hpFraction) {
        return phases.get(activePhaseIndex(hpFraction));
    }

    public int finalPhaseIndex() { return phases.size() - 1; }
}
