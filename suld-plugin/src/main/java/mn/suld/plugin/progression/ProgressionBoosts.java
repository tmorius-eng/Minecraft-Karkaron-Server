package mn.suld.plugin.progression;

import mn.suld.plugin.SuldServices;

import java.util.UUID;

/**
 * The single place personal EXP bonuses are combined (clan level + borne relic). Bonuses add
 * up rather than multiply, so stacking stays predictable: base × (1 + clan + relic).
 */
public final class ProgressionBoosts {

    private final SuldServices services;

    public ProgressionBoosts(SuldServices services) {
        this.services = services;
    }

    public double bonus(UUID player) {
        return services.clans().expBonus(player) + services.relics().expBonus(player);
    }

    public long apply(UUID player, long baseExp) {
        return Math.round(baseExp * (1.0 + bonus(player)));
    }
}
