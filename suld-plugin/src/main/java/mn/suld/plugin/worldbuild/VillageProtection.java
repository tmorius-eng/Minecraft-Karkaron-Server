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
 * The steppe's settlements and monuments are protected ground (docs/world/WILD_MOBS_AND_STORY_MAP.md,
 * docs/world/HISTORIC_SITES.md). In the overworld nobody can hurt a villager or a wandering trader (players, mobs,
 * fire, falls — only the void and /kill) and zombies cannot turn them. The blocks of every generated structure
 * (villages, temples, outposts, ruins, ships, mansions, monuments, mineshafts, strongholds, ancient cities...) and of
 * every registered SÜLD place (historic sites, ovoo: {@link #protect}) cannot be broken, built over, burned, flooded,
 * pushed by pistons or blown up. Trading, chests and doors still work. {@code suld.admin.world} bypasses the block
 * rules.
 */
public final class VillageProtection implements Listener {

    private static boolean overworld(Location l) {
        return l != null && l.getWorld() != null && l.getWorld().equals(Bukkit.getWorlds().get(0));
    }

    private static boolean villager(Entity e) {
        return e instanceof org.bukkit.entity.Villager || e instanceof org.bukkit.entity.WanderingTrader;
    }

    /** Registered SÜLD places (historic sites, ovoo): id → box, all in the overworld. */
    private static final java.util.Map<String, BoundingBox> PLACES = new java.util.concurrent.ConcurrentHashMap<>();

    /** Protect a SÜLD place's blocks (inclusive block bounds) in the overworld; the same id replaces its box. */
    public static void protect(String id, int x1, int y1, int z1, int x2, int y2, int z2) {
        PLACES.put(id, new BoundingBox(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, Math.max(z1, z2) + 1));
    }

    /** Inside the bounding box of a generated village (any biome's village structure) in the overworld. */
    public static boolean inVillage(Block b) {
        return inStructure(b, true);
    }

    /** A chunk's structure boxes (village flag, box), cached: structures never move; flow and piston events are frequent. */
    private record Box(boolean village, BoundingBox box) {
    }

    private static final java.util.Map<Long, java.util.List<Box>> CHUNK_BOXES = new java.util.LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Long, java.util.List<Box>> eldest) {
            return size() > 8192;
        }
    };

    private static java.util.List<Box> boxes(org.bukkit.Chunk c) {
        long key = ((long) c.getX() << 32) ^ (c.getZ() & 0xffffffffL);
        synchronized (CHUNK_BOXES) {
            java.util.List<Box> cached = CHUNK_BOXES.get(key);
            if (cached != null) return cached;
        }
        java.util.List<Box> out = new java.util.ArrayList<>();
        for (GeneratedStructure s : c.getStructures()) {
            NamespacedKey k = org.bukkit.Registry.STRUCTURE.getKey(s.getStructure());
            out.add(new Box(k != null && k.getKey().startsWith("village"), s.getBoundingBox()));
        }
        synchronized (CHUNK_BOXES) {
            CHUNK_BOXES.put(key, out);
        }
        return out;
    }

    /** Inside any generated structure, or (villagesOnly) a village. */
    static boolean inStructure(Block b, boolean villagesOnly) {
        if (!overworld(b.getLocation())) return false;
        double x = b.getX() + 0.5, y = b.getY() + 0.5, z = b.getZ() + 0.5;
        for (Box bx : boxes(b.getChunk())) {
            if (villagesOnly && !bx.village()) continue;
            if (bx.box().contains(x, y, z)) return true;
        }
        return false;
    }

    /** A protected block: inside a generated structure or a registered SÜLD place. */
    public static boolean guarded(Block b) {
        if (!overworld(b.getLocation())) return false;
        double x = b.getX() + 0.5, y = b.getY() + 0.5, z = b.getZ() + 0.5;
        for (BoundingBox box : PLACES.values()) if (box.contains(x, y, z)) return true;
        return inStructure(b, false);
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
        if (!bypass(e.getPlayer()) && guarded(e.getBlock())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Messages.error("Энэ газар хамгаалалттай — эвдэж болохгүй."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!bypass(e.getPlayer()) && guarded(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (!bypass(e.getPlayer()) && guarded(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!bypass(e.getPlayer()) && guarded(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(VillageProtection::guarded);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(VillageProtection::guarded);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (guarded(e.getBlock())) e.setCancelled(true);
    }

    /** Endermen, ravagers and the like do not tear village blocks out either (crops still grow and get harvested). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobGrief(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof Player || e.getEntity() instanceof org.bukkit.entity.Villager
                || e.getEntity() instanceof org.bukkit.entity.FallingBlock) return;
        if (guarded(e.getBlock())) e.setCancelled(true);
    }

    /** Pistons cannot push or pull protected blocks, nor push blocks into them. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(org.bukkit.event.block.BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (guarded(b) || guarded(b.getRelative(e.getDirection()))) {
                e.setCancelled(true);
                return;
            }
        }
        if (guarded(e.getBlock().getRelative(e.getDirection()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (guarded(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    /** No fire lit or spread inside protected ground (a campfire or a furnace inside still works: they are not fires). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(org.bukkit.event.block.BlockIgniteEvent e) {
        if (bypass(e.getPlayer())) return;
        if (guarded(e.getBlock())) e.setCancelled(true);
    }

    /** Lava and water poured outside do not flow in. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(org.bukkit.event.block.BlockFromToEvent e) {
        if (!guarded(e.getBlock()) && guarded(e.getToBlock())) e.setCancelled(true);
    }
}
