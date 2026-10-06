package mn.suld.api.worldbuild;

/**
 * One compiled block: the concrete block-data string, the build pass that places it, its layer and
 * the placement (instance index) that owns it; -1 = terrain.
 */
public record Cell(String block, Pass pass, Layer layer, int owner) {
}
