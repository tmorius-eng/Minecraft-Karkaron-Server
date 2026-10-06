package mn.suld.api.region;

import java.util.List;

/**
 * A named area of the SÜLD world with its own rules and content.
 *
 * @param priority       higher wins where shapes overlap (the town sits on top of the steppe)
 * @param safeZone       no PvP and no hostile spawning
 * @param buildProtected only admins may break/place blocks
 * @param mobIds         SÜLD mobs that spawn naturally around players here
 * @param discoveryExp   EXP granted the first time a player enters (exactly once, ever)
 */
public record RegionDefinition(
        String id,
        String displayName,
        String description,
        RegionShape shape,
        int priority,
        int minLevel,
        int maxLevel,
        boolean safeZone,
        boolean buildProtected,
        List<String> mobIds,
        long discoveryExp) {

    public RegionDefinition {
        if (id == null || !id.matches("region\\.[a-z0-9_]{2,40}")) {
            throw new IllegalArgumentException("region id must look like region.some_name");
        }
        if (minLevel < 1 || maxLevel < minLevel) {
            throw new IllegalArgumentException("level range invalid");
        }
        mobIds = mobIds == null ? List.of() : List.copyOf(mobIds);
        if (safeZone && !mobIds.isEmpty()) {
            throw new IllegalArgumentException("a safe zone cannot spawn hostile mobs");
        }
    }

    public String levelBand() {
        return minLevel + "–" + maxLevel;
    }
}
