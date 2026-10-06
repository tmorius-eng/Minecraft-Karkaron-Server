package mn.suld.api.worldbuild;

/**
 * A named snap point on a module (door, road end, wall end, stair top...). Placements can be
 * aligned by connectors, and the validator checks that walkable connectors are reachable.
 *
 * @param walkable true when a player should be able to stand here (validated for reachability)
 */
public record Connector(String name, int x, int y, int z, Facing facing, boolean walkable) {
}
