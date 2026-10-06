package mn.suld.api.zone;

import java.util.Locale;
import java.util.Set;

/**
 * The rules of the Kharkhorum safe zone, as pure decisions (unit-tested; the plugin listener only
 * asks these). Inside the city walls:
 * <ul>
 *   <li>no player breaks or places blocks (except staff in build mode);</li>
 *   <li>players may use what a visitor would use — doors, gates, buttons, levers, bells, crafting
 *       stations — but not the city's decorative storage, beds, lamps, pots, signs or trapdoor shutters;</li>
 *   <li>no hostile mob spawns naturally; no fire, explosions, liquids, pistons or mobs change blocks;</li>
 *   <li>no player-versus-player damage.</li>
 * </ul>
 */
public final class CityRules {

    private CityRules() {
    }

    /** Workstations a visitor may open (harmless GUIs). The anvil is excluded: using it damages it. */
    private static final Set<String> USABLE = Set.of(
            "crafting_table", "stonecutter", "grindstone", "smithing_table", "loom", "cartography_table",
            "ender_chest", "bell", "lever", "enchanting_table");

    /** Right-clicking this block in the city is allowed. */
    public static boolean mayUse(String block) {
        String n = name(block);
        if (n.endsWith("_door") && !n.equals("iron_door")) return true;
        if (n.endsWith("_fence_gate") || n.endsWith("_button") || n.endsWith("_pressure_plate")) return true;
        return USABLE.contains(n);
    }

    /** Whether a right-click on this block would change the block or open storage (and so must be refused). */
    public static boolean isInteractive(String block) {
        String n = name(block);
        return n.contains("chest") || n.equals("barrel") || n.endsWith("furnace") || n.equals("smoker")
                || n.equals("brewing_stand") || n.equals("hopper") || n.equals("dispenser") || n.equals("dropper")
                || n.equals("lectern") || n.endsWith("_bed") || n.equals("flower_pot") || n.startsWith("potted_")
                || n.contains("cauldron") || n.equals("composter") || n.equals("jukebox") || n.equals("note_block")
                || n.endsWith("_trapdoor") || n.endsWith("_sign") || n.equals("decorated_pot") || n.equals("chiseled_bookshelf")
                || n.endsWith("candle") || n.endsWith("candle_cake") || n.endsWith("campfire") || n.equals("cake")
                || n.equals("respawn_anchor") || n.equals("lodestone") || n.contains("anvil") || n.equals("beacon")
                || n.equals("daylight_detector") || n.equals("comparator") || n.equals("repeater") || n.equals("dragon_egg")
                || n.equals("bee_nest") || n.equals("beehive") || n.equals("crafter") || n.equals("vault")
                || n.equals("trial_spawner") || n.equals("shulker_box") || n.endsWith("_shulker_box")
                || n.equals("redstone_wire") || n.equals("sweet_berry_bush") || n.equals("cave_vines")
                || n.equals("end_portal_frame") || n.equals("lantern") || n.equals("soul_lantern") || n.equals("bookshelf");
    }

    /** The decision for a right-click on a block in the city. */
    public static boolean allowRightClick(String block) {
        return mayUse(block) || !isInteractive(block);
    }

    /** Items that change the world when used on a block (always refused in the city). */
    public static boolean isWorldChangingItem(String item) {
        String n = name(item);
        return n.endsWith("_spawn_egg") || n.equals("bone_meal") || n.equals("flint_and_steel") || n.equals("fire_charge")
                || n.endsWith("_bucket") && !n.equals("milk_bucket") || n.endsWith("_boat") || n.endsWith("_raft")
                || n.endsWith("minecart") || n.equals("armor_stand") || n.equals("end_crystal") || n.equals("item_frame")
                || n.equals("glow_item_frame") || n.equals("painting") || n.endsWith("_shovel") || n.endsWith("_axe")
                || n.endsWith("_hoe") || n.equals("honeycomb") || n.equals("ink_sac") || n.equals("glow_ink_sac")
                || n.endsWith("_dye") || n.equals("lead") || n.equals("shears") || n.equals("brush") || n.equals("wind_charge");
    }

    /** Natural/automatic spawn reasons the city refuses for hostile mobs (commands, eggs of staff, SÜLD spawns pass). */
    public static boolean refusesSpawn(String reason, boolean hostile) {
        if (!hostile) return false;
        return switch (reason.toUpperCase(Locale.ROOT)) {
            case "NATURAL", "PATROL", "REINFORCEMENTS", "VILLAGE_INVASION", "JOCKEY", "MOUNT", "RAID", "TRAP",
                 "SPAWNER", "SILVERFISH_BLOCK", "LIGHTNING", "DROWNED", "INFECTION", "ENDER_PEARL", "TRIAL_SPAWNER",
                 "BUILD_WITHER", "BUILD_IRONGOLEM", "BUILD_SNOWMAN", "SLIME_SPLIT", "EGG", "OMINOUS_ITEM_SPAWNER" -> true;
            default -> false;
        };
    }

    private static String name(String block) {
        String s = block.toLowerCase(Locale.ROOT);
        int b = s.indexOf('[');
        if (b >= 0) s = s.substring(0, b);
        return s.startsWith("minecraft:") ? s.substring(10) : s;
    }
}
