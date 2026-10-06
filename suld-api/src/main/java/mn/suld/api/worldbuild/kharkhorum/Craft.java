package mn.suld.api.worldbuild.kharkhorum;

import mn.suld.api.worldbuild.Connector;
import mn.suld.api.worldbuild.Facing;
import mn.suld.api.worldbuild.Layer;
import mn.suld.api.worldbuild.Module;
import mn.suld.api.worldbuild.ModuleCanvas;
import mn.suld.api.worldbuild.ModuleContext;
import mn.suld.api.worldbuild.Pass;

import java.util.List;
import java.util.SplittableRandom;

/** The crafting quarter: the blacksmith's forge. */
final class Craft {

    private Craft() {
    }

    /**
     * blacksmith.forge — Дархны Газар: an 11 × 9 forge, front (+z) open to the lane under a
     * lean-to. Enclosed back workshop of earth walls under a tiled hip roof, a brick hearth and
     * chimney, anvil in front of the smith (who stands at the origin), grindstone, quench trough,
     * smithing table, blast furnace, coal and ore stock. Soot-darkened stone around the chimney.
     */
    static final Module FORGE = new Module() {
        public String id() { return "blacksmith.forge"; }
        public Layer layer() { return Layer.STRUCTURE; }

        public List<Connector> connectors(ModuleContext ctx) {
            return List.of(new Connector("front", 0, 1, 6, Facing.SOUTH, true));
        }

        public void build(ModuleCanvas c, ModuleContext ctx) {
            SplittableRandom r = ctx.random();
            c.pass(Pass.SHELLS);
            for (int x = -5; x <= 5; x++)
                for (int z = -4; z <= 4; z++)
                    c.set(x, 0, z, r.nextInt(4) == 0 ? "minecraft:cobblestone" : r.nextInt(3) == 0 ? "@packed" : "minecraft:stone_bricks");
            // back workshop walls (z -4 and the sides back to z -1)
            for (int y = 1; y <= 4; y++) {
                for (int x = -5; x <= 5; x++) c.set(x, y, -4, y == 4 ? "@beam[axis=x]" : Kit.earth(r));
                for (int z = -3; z <= -1; z++) {
                    c.set(-5, y, z, y == 4 ? "@beam[axis=z]" : Kit.earth(r));
                    c.set(5, y, z, y == 4 ? "@beam[axis=z]" : Kit.earth(r));
                }
                for (int sx : new int[]{-5, 5}) c.set(sx, y, 0, "@beam[axis=y]");
                for (int sx : new int[]{-5, -1, 1, 5}) c.set(sx, y, 4, y == 4 ? "@beam[axis=x]" : "@beam[axis=y]");
            }
            c.set(-5, 2, -2, "@glass");
            c.set(5, 2, -2, "@glass");
            // beams carrying the lean-to
            for (int x = -5; x <= 5; x++) c.set(x, 4, 4, "@beam[axis=x]");
            for (int z = 0; z <= 4; z++) {
                c.set(-5, 4, z, "@beam[axis=z]");
                c.set(5, 4, z, "@beam[axis=z]");
            }
            // hearth and chimney (x -1..1, z -4..-2), flue at (0, *, -3)
            for (int x = -1; x <= 1; x++)
                for (int z = -4; z <= -2; z++)
                    for (int y = 1; y <= 10; y++) {
                        boolean flue = x == 0 && z == -3 && y >= 1;
                        boolean mouth = x == 0 && z == -2 && y <= 2;
                        if (flue || mouth) c.air(x, y, z);
                        else c.set(x, y, z, y >= 9 ? "minecraft:polished_blackstone_bricks" : r.nextInt(5) == 0 ? "minecraft:cracked_stone_bricks" : "minecraft:bricks");
                    }
            c.set(0, 1, -3, "@fire[lit=true]");
            c.set(-1, 5, -2, "minecraft:coal_block");
            c.set(1, 6, -4, "minecraft:coal_block");
            // roofs: hip over the workshop, tiled lean-to over the open front
            Kit.hipRoof(c, -6, -5, 6, 0, 5, null, true);
            c.pass(Pass.ROOFS_DETAIL);
            for (int x = -6; x <= 6; x++) {
                for (int z = 1; z <= 4; z++) c.set(x, 5, z, "@roof_slab[type=bottom]");
                c.set(x, 5, 5, Kit.stairs("@roof_stairs", Facing.NORTH, false));
            }
            // tools of the trade
            c.pass(Pass.INTERIORS);
            c.set(0, 1, 2, "minecraft:anvil[facing=east]");
            c.set(-3, 1, 2, "minecraft:grindstone[face=floor,facing=north]");
            c.set(3, 1, 2, "minecraft:water_cauldron[level=3]");
            c.set(4, 1, 2, "minecraft:water_cauldron[level=3]");
            c.set(3, 1, -2, "minecraft:smithing_table");
            c.set(-3, 1, -3, "minecraft:blast_furnace[facing=south,lit=true]");
            c.set(-4, 1, -3, "minecraft:coal_block");
            c.set(-4, 1, -2, "minecraft:coal_block");
            c.set(-4, 2, -3, "minecraft:coal_block");
            c.set(4, 1, -3, "minecraft:chest[facing=west]");
            c.set(4, 2, -3, "minecraft:barrel[facing=up]");
            c.set(2, 1, -3, "minecraft:lava_cauldron");
            c.set(-2, 3, -3, "minecraft:iron_bars");
            c.set(2, 3, -3, "minecraft:iron_bars");
            c.set(-3, 3, -3, "minecraft:chain[axis=y]");
            // stock outside
            c.pass(Pass.DECORATION);
            c.set(-6, 1, 2, "minecraft:raw_iron_block");
            c.set(-6, 1, 3, "minecraft:coal_block");
            c.set(-6, 2, 3, "minecraft:spruce_log[axis=z]");
            c.set(6, 1, 3, "minecraft:barrel[facing=up]");
            c.set(6, 1, 2, "minecraft:spruce_log[axis=z]");
            c.set(6, 2, 2, "minecraft:spruce_log[axis=z]");
            c.pass(Pass.LIGHTING);
            c.set(-3, 4, 2, "@light[hanging=true]");
            c.set(3, 4, 2, "@light[hanging=true]");
        }
    };
}
