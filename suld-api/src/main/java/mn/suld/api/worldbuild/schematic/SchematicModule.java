package mn.suld.api.worldbuild.schematic;

import mn.suld.api.worldbuild.BlockStates;
import mn.suld.api.worldbuild.Facing;
import mn.suld.api.worldbuild.Layer;
import mn.suld.api.worldbuild.Module;
import mn.suld.api.worldbuild.ModuleCanvas;
import mn.suld.api.worldbuild.ModuleContext;
import mn.suld.api.worldbuild.Pass;
import mn.suld.api.worldbuild.Transform;

import java.util.Map;

/**
 * An authored schematic used as a WorldBuilder module ({@code schem:<name>}). The volume is
 * centred on x/z, its lowest layer sits at module y = {@code -sink} (sink = how many layers of the
 * schematic are foundation below the ground), and it is turned so its front faces +z (the module
 * convention) from the {@code front} it was built with. Params at placement:
 * <ul>
 *   <li>{@code replace}: {"minecraft:old": "minecraft:new"} block substitutions (restyling);</li>
 *   <li>{@code clear}: also write air over the schematic's empty cells inside its bounds (so the
 *       site is clean); default true;</li>
 *   <li>{@code pass}: build pass (default SHELLS).</li>
 * </ul>
 */
public final class SchematicModule implements Module {

    private final String id;
    private final Schematic schem;
    private final Facing front;
    private final int sink;
    private final Layer layer;

    public SchematicModule(String id, Schematic schem, Facing front, int sink, Layer layer) {
        this.id = id;
        this.schem = schem;
        this.front = front;
        this.sink = sink;
        this.layer = layer;
    }

    @Override
    public String id() { return id; }

    @Override
    public Layer layer() { return layer; }

    public Schematic schematic() { return schem; }

    /** Rotation that turns the schematic's front to face +z (south). */
    Transform alignment() {
        int quarters = switch (front) {
            case SOUTH -> 0;
            case WEST -> 3;   // west → south is a 270° clockwise turn... (west→north→east→south)
            case NORTH -> 2;
            case EAST -> 1;   // east → south is 90° clockwise
        };
        return Transform.of(quarters * 90, false);
    }

    @Override
    public void build(ModuleCanvas c, ModuleContext ctx) {
        Transform t = alignment();
        int cx = schem.width() / 2, cz = schem.length() / 2;
        @SuppressWarnings("unchecked")
        Map<String, Object> replace = ctx.params().get("replace") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        boolean clear = ctx.flag("clear", true);
        c.pass(Pass.valueOf(ctx.string("pass", Pass.SHELLS.name())));
        if (clear) {
            for (int x = 0; x < schem.width(); x++)
                for (int y = sink; y < schem.height(); y++)
                    for (int z = 0; z < schem.length(); z++) {
                        int[] xz = t.apply(x - cx, z - cz);
                        c.air(xz[0], y - sink, xz[1]);
                    }
        }
        for (Map.Entry<Long, String> e : schem.blocks().entrySet()) {
            long k = e.getKey();
            int x = Schematic.kx(k) - cx, y = Schematic.ky(k) - sink, z = Schematic.kz(k) - cz;
            String b = e.getValue();
            Object r = replace.get(BlockStates.id(b));
            if (r instanceof String s) {
                b = s.contains("[") || !b.contains("[") ? s : s + b.substring(b.indexOf('['));
            }
            int[] xz = t.apply(x, z);
            c.set(xz[0], y, xz[1], BlockStates.transform(b, t));
        }
    }
}
