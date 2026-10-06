package mn.suld.api.worldbuild;

/**
 * Coarse physical classification of block-data strings, shared by the compiler (connection
 * resolution), the validators (support, walkability) and the in-world checker. Conservative:
 * anything unknown is treated as a full solid cube.
 */
public final class BlockKinds {

    private BlockKinds() {
    }

    private static String name(String block) {
        String id = BlockStates.id(block);
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    public static boolean isAir(String block) {
        if (block == null) return true;
        String n = name(block);
        return n.equals("air") || n.equals("cave_air") || n.equals("void_air");
    }

    public static boolean isLiquid(String block) {
        String n = name(block);
        return n.equals("water") || n.equals("lava") || n.equals("bubble_column");
    }

    /** A player's body can occupy this block (air, plants, carpets, open-able doors, lights on walls...). */
    public static boolean isPassable(String block) {
        if (isAir(block)) return true;
        String n = name(block);
        if (n.endsWith("_door") || n.endsWith("_fence_gate")) return true; // players open them
        return n.endsWith("_carpet") || n.endsWith("_banner") || n.endsWith("_sign") || n.endsWith("_button")
                || n.endsWith("_pressure_plate") || n.endsWith("torch") || n.equals("ladder") || n.equals("vine")
                || n.endsWith("_sapling") || n.equals("short_grass") || n.equals("tall_grass") || n.equals("fern")
                || n.equals("large_fern") || n.equals("dead_bush") || n.endsWith("_tulip") || n.equals("dandelion")
                || n.equals("poppy") || n.equals("cornflower") || n.equals("oxeye_daisy") || n.equals("azure_bluet")
                || n.equals("allium") || n.equals("blue_orchid") || n.equals("lily_of_the_valley") || n.equals("pink_petals")
                || n.equals("rail") || n.equals("string") || n.equals("tripwire") || n.equals("snow")
                || n.equals("light") || n.equals("structure_void") || n.endsWith("_wall_banner") || n.equals("lever")
                || n.equals("redstone_wire") || n.equals("sweet_berry_bush") || n.equals("cobweb") || n.equals("leaf_litter")
                || n.equals("wildflowers") || n.equals("bush") || n.equals("firefly_bush") || n.equals("short_dry_grass")
                || n.equals("tall_dry_grass");
    }

    /** Taller than 1 block for collision (fences, walls): a player cannot step or jump onto it. */
    public static boolean isTall(String block) {
        String n = name(block);
        return (n.endsWith("_fence") || n.endsWith("_wall")) && !n.endsWith("_wall_banner") && !n.endsWith("_wall_sign")
                && !n.endsWith("_wall_torch") && !n.endsWith("_wall_head") && !n.endsWith("_wall_skull");
    }

    /** A player can stand on top of this block. */
    public static boolean isStandable(String block) {
        if (isPassable(block) || isLiquid(block) || isTall(block)) return false;
        String n = name(block);
        if (n.endsWith("_trapdoor")) return !block.contains("open=true");
        return !n.endsWith("_pane") && !n.equals("iron_bars") && !n.equals("chain") && !n.endsWith("_chain")
                && !n.equals("lantern") && !n.equals("soul_lantern") && !n.endsWith("_rod");
    }

    /** A full, sturdy cube (fences, walls and panes connect to it; supports hanging/attached blocks). */
    public static boolean isFullCube(String block) {
        if (isAir(block) || isLiquid(block) || isPassable(block) || isTall(block)) return false;
        String n = name(block);
        return !(n.endsWith("_stairs") || n.endsWith("_slab") || n.endsWith("_pane") || n.equals("iron_bars")
                || n.endsWith("_trapdoor") || n.contains("lantern") || n.endsWith("campfire") || n.endsWith("chain")
                || n.endsWith("_rod") || n.equals("anvil") || n.equals("chipped_anvil") || n.equals("damaged_anvil")
                || n.equals("grindstone") || n.equals("bell") || n.endsWith("_bed") || n.equals("cauldron")
                || n.equals("water_cauldron") || n.equals("lectern") || n.equals("enchanting_table")
                || n.equals("stonecutter") || n.equals("brewing_stand") || n.endsWith("_candle") || n.equals("candle")
                || n.equals("flower_pot") || n.startsWith("potted_") || n.equals("dirt_path") || n.equals("farmland")
                || n.equals("chest") || n.equals("trapped_chest") || n.endsWith("_head")
                || n.endsWith("_skull") || n.equals("decorated_pot") || n.equals("scaffolding") || n.endsWith("_leaves")
                || n.equals("composter") || n.equals("hopper") || n.equals("end_rod") || n.equals("conduit")
                || n.equals("daylight_detector") || n.contains("amethyst_bud") || n.equals("pointed_dripstone"));
    }

    /** Fence-like connection family, or null if the block does not form connections. */
    public static String connectionFamily(String block) {
        String n = name(block);
        if (n.endsWith("_wall") && isTall(block)) return "wall";
        if (n.equals("nether_brick_fence")) return "nether_fence";
        if (n.endsWith("_fence")) return "fence";
        if (n.endsWith("_pane") || n.equals("iron_bars")) return "pane";
        return null;
    }
}
