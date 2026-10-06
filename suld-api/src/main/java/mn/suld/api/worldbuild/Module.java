package mn.suld.api.worldbuild;

import java.util.List;

/**
 * A reusable, parametric building block of the city (a gate, a stall, a yurt, a road segment).
 * Modules write in their own local coordinates: origin at the module's anchor, y = 0 is the ground
 * surface block the module stands on (players stand at y = 1), the module faces SOUTH (+z) unless a
 * placement rotates it. They must be deterministic: same parameters and seed, same blocks.
 */
public interface Module {

    String id();

    Layer layer();

    void build(ModuleCanvas canvas, ModuleContext ctx);

    /** Named snap points in local coordinates (doors, road ends...). */
    default List<Connector> connectors(ModuleContext ctx) {
        return List.of();
    }
}
