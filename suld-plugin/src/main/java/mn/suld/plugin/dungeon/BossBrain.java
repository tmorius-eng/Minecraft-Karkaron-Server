package mn.suld.plugin.dungeon;

import org.bukkit.entity.LivingEntity;

/**
 * A boss's abilities (reusable for every boss): {@link BossService} keeps phases and enrage and calls the brain
 * about twice a second while the fight lasts. Brains telegraph, trigger the rig's clips (ModelService) and apply
 * their damage on the clip's event tick, so what players see and what hits them line up.
 */
public interface BossBrain {

    /** About every 10 ticks during the fight. */
    void tick(LivingEntity boss, int phaseIndex, boolean enraged);

    /** A new phase began (also on enrage). */
    default void onPhase(LivingEntity boss, int phaseIndex, boolean enraged) {
    }

    /** The fight is over (death or cleanup). */
    default void end(LivingEntity boss) {
    }
}
