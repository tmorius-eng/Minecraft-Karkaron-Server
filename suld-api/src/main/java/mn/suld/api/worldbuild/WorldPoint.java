package mn.suld.api.worldbuild;

/**
 * A gameplay point (spawn, NPC, merchant, fast travel, secret...) in city-local coordinates; y is
 * the block the player's feet occupy. Loaded from {@code assets/world/<city>/points.json}.
 */
public record WorldPoint(String id, String type, String district, int x, int y, int z, float yaw,
                         boolean required, String slice) {
}
