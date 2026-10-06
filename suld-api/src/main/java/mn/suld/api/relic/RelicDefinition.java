package mn.suld.api.relic;

/**
 * A world-unique relic: exactly one exists on the whole server.
 *
 * @param key             stable identity, the {@code item_key} primary key (e.g. "relic.khukh_suld")
 * @param displayName     Mongolian name
 * @param lore            one-line legend shown in the tooltip and on discovery
 * @param baseMaterial    vanilla material the physical copy is rendered on (resource pack overrides it)
 * @param customModelData resource-pack model id
 * @param minLevel        character level needed to claim it at its shrine
 * @param expBonus        personal EXP bonus while bearing it (0.25 = +25%)
 */
public record RelicDefinition(
        String key,
        String displayName,
        String lore,
        String baseMaterial,
        int customModelData,
        int minLevel,
        double expBonus) {

    public RelicDefinition {
        if (key == null || !key.matches("relic\\.[a-z0-9_]{2,40}")) {
            throw new IllegalArgumentException("relic key must look like relic.some_name");
        }
        if (minLevel < 1 || expBonus < 0 || expBonus > 1) {
            throw new IllegalArgumentException("minLevel >= 1 and 0 <= expBonus <= 1");
        }
    }
}
