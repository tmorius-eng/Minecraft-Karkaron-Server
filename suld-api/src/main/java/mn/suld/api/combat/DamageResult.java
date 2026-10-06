package mn.suld.api.combat;

/**
 * Outcome of a single damage computation.
 *
 * @param rawDamage    attacker's pre-mitigation damage
 * @param finalDamage  damage after crit and armor mitigation ({@code >= 0})
 * @param critical     whether the hit critically struck
 * @param mitigated    amount removed by armor ({@code raw(+crit) - finalDamage})
 */
public record DamageResult(double rawDamage, double finalDamage, boolean critical, double mitigated) {
}
