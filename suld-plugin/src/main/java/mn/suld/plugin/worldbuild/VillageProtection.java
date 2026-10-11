package mn.suld.plugin.worldbuild;

import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.util.BoundingBox;

/**
 * The steppe's settlements are sacred ground (docs/world/WILD_MOBS_AND_STORY_MAP.md): in the overworld nobody can hurt
 * a villager or a wandering trader (players, mobs, fire, falls — only the void and /kill), zombies cannot turn them,
 * and the houses of every generated village cannot be broken, built over, burned, flooded or blown up. Trading and
 * opening doors still work. {@code suld.admin.world} bypasses the block rules.
 */
public final class VillageProtection implements Listener {

    private static boolean overworld(Location l) {
        return l != null && l.getWorld() != null && l.getWorld().equals(Bukkit.getWorlds().get(0));
    }

    private static boolean villager(Entity e) {
        return e instanceof org.bukkit.entity.Villager || e instanceof org.bukkit.entity.WanderingTrader;
    }

    /** Inside the bounding box of a generated village (any biome's village structure) in the overworld. */
    public static boolean inVillage(Block b) {
        if (!overworld(b.getLocation())) return false;
        for (GeneratedStructure s : b.getChunk().getStructures()) {
            NamespacedKey key = org.bukkit.Registry.STRUCTURE.getKey(s.getStructure());
            if (key == null || !key.getKey().startsWith("village")) continue;
            BoundingBox box = s.getBoundingBox();
            if (box.contains(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5)) return true;
        }
        return false;
    }

    private static boolean bypass(Player p) {
        return p != null && p.hasPermission("suld.admin.world");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!villager(e.getEntity()) || !overworld(e.getEntity().getLocation())) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID || e.getCause() == EntityDamageEvent.DamageCause.KILL) return;
        e.setCancelled(true);
        if (e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent by && by.getDamager() instanceof Player p) {
            p.sendActionBar(Messages.error("Тосгоны иргэд Тэнгэрийн ивээлд — тэднийг хөнөөж болохгүй."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConvert(EntityTransformEvent e) {
        if (villager(e.getEntity()) && overworld(e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!bypass(e.getPlayer()) && inVillage(e.getBlock())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Messages.error("Тосгоны байшин хамгаалалттай."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!bypass(e.getPlayer()) && inVillage(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (!bypass(e.getPlayer()) && inVillage(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!bypass(e.getPlayer()) && inVillage(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(VillageProtection::inVillage);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(VillageProtection::inVillage);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (inVillage(e.getBlock())) e.setCancelled(true);
    }

    /** Endermen, ravagers and the like do not tear village blocks out either (crops still grow and get harvested). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobGrief(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof Player || e.getEntity() instanceof org.bukkit.entity.Villager
                || e.getEntity() instanceof org.bukkit.entity.FallingBlock) return;
        if (inVillage(e.getBlock())) e.setCancelled(true);
    }
}
