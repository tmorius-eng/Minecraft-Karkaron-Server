package mn.suld.plugin.item;

import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemType;
import mn.suld.plugin.SuldServices;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/**
 * A SÜLD material keeps its SÜLD life (docs/MASTERY_AND_PROFESSIONS.md). The crafts' materials stand on vanilla items
 * that the game would otherwise place, eat or burn (wool, flowers, string, raw ore, carpet); the older ones on items
 * a furnace, a brewing stand or the crafting grid would take (glowstone dust, a fermented eye, leather). None of that
 * happens to a stack the item system knows as a MATERIAL: it is not placed, eaten, potted, composted, tied on as a
 * lead, used as a vanilla ingredient, smelted or burnt as fuel. Consumables (the Orb of Oblivion) keep their own use.
 */
public final class MaterialGuard implements Listener {

    /** Blocks that take the held item on a right click (and so would swallow a SÜLD material). */
    private static final Set<Material> TAKERS = EnumSet.of(Material.FLOWER_POT, Material.COMPOSTER);

    private final SuldServices services;

    public MaterialGuard(SuldServices services) {
        this.services = services;
    }

    /** Whether a stack is a SÜLD material (not a consumable, not gear). */
    boolean material(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        return services.items().read(it)
                .flatMap(i -> services.itemService().catalog().item(i.definitionId()))
                .map(ItemDefinition::type)
                .filter(t -> t == ItemType.MATERIAL)
                .isPresent();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (material(e.getItemInHand())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        if (material(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUseOnBlock(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || !TAKERS.contains(b.getType()) && !b.getType().name().startsWith("POTTED_")) return;
        if (material(e.getItem())) e.setUseItemInHand(Event.Result.DENY);
    }

    /** Feeding, dyeing, breeding: an animal never eats a SÜLD material (the crafts' pens guard their own animals too). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAnimal(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Animals)) return;
        ItemStack hand = e.getPlayer().getInventory().getItem(e.getHand());
        if (material(hand)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent e) {
        ItemStack hand = e.getPlayer().getInventory().getItem(e.getHand());
        if (material(hand)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack in : e.getInventory().getMatrix()) {
            if (material(in)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent e) {
        if (material(e.getSource())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFuel(FurnaceBurnEvent e) {
        if (material(e.getFuel())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        if (material(e.getContents().getIngredient())) e.setCancelled(true);
    }
}
