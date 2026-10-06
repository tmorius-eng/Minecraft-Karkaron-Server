package mn.suld.api.worldbuild;

/** One block placement relative to a blueprint origin; {@code block} is a Minecraft block-data string. */
public record BlockSpec(int x, int y, int z, String block) {
}
