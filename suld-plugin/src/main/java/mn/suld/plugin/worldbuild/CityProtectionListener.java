package mn.suld.plugin.worldbuild;

import mn.suld.api.zone.CityRules;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kharkhorum is a safe zone (rules in {@link CityRules}, extent from {@link WorldBuildService}).
 * Staff with {@code suld.admin.world} edit it only in build mode ({@code /worldbuild edit}).
 * <p>Integration with the rest of SÜLD:
 * <ul>
 *   <li>PvP is off in the city for everyone, relic bearers included: Kharkhorum is a sanctuary, and relics
 *       are contested in the wilderness only (docs/RELICS.md);</li>
 *   <li>SÜLD's own mobs (dungeons, world events, custom spawns) are never refused or swept; dungeons and
 *       world events themselves refuse to run inside the city;</li>
 *   <li>SÜLD NPCs and the city's animals are protected; NPC clicks are handled by the NPC service.</li>
 * </ul>
 */
public final class CityProtectionListener implements Listener {

    private static final long NOTICE_COOLDOWN_MS = 3000;

    private final Plugin plugin;
    private final SuldServices services;
    private final WorldBuildService city;
    private final Map<UUID, Long> lastNotice = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> insideState = new ConcurrentHashMap<>();

    public CityProtectionListener(Plugin plugin, SuldServices services, WorldBuildService city) {
        this.plugin = plugin;
        this.services = services;
        this.city = city;
    }

    /** Periodic sweep: hostile mobs that walked in are sent away (removed). */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 200L, 200L);
    }

    // ------------------------------------------------------------------ helpers

    private boolean in(Location l) {
        return l != null && l.getWorld() != null && city.contains(l.getWorld().getName(), l.getBlockX(), l.getBlockZ());
    }

    private boolean in(Block b) {
        return city.contains(b.getWorld().getName(), b.getX(), b.getZ());
    }

    private boolean builder(Player p) {
        return p.hasPermission("suld.admin.world") && city.isEditor(p.getUniqueId());
    }

    private void notice(Player p, String text) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(p.getUniqueId());
        if (last != null && now - last < NOTICE_COOLDOWN_MS) return;
        lastNotice.put(p.getUniqueId(), now);
        services.hud().toast(p, Component.text("🛡 " + text, NamedTextColor.GOLD));
    }

    private static boolean isNpc(Entity e) {
        return e.getPersistentDataContainer().getKeys().stream().anyMatch(k -> k.getKey().equals("city_npc") || k.getKey().equals("city_ambient"));
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) return p;
        }
        if (damager instanceof org.bukkit.entity.TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        if (damager instanceof org.bukkit.entity.AreaEffectCloud c && c.getSource() instanceof Player p) return p;
        return null;
    }

    // ------------------------------------------------------------------ blocks

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (in(e.getBlock()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
            notice(e.getPlayer(), "Хархорум хамгаалагдсан хот — энд барьж, нурааж болохгүй.");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (in(e.getBlock()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
            notice(e.getPlayer(), "Хархорум хамгаалагдсан хот — гэрээ хотын хананы гадна барина уу.");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (in(e.getBlockClicked().getRelative(e.getBlockFace())) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (in(e.getBlockClicked()) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSign(SignChangeEvent e) {
        if (in(e.getBlock()) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || !in(b) || builder(e.getPlayer())) return;
        if (e.getAction() == Action.PHYSICAL) {
            Material m = b.getType();
            if (m == Material.FARMLAND || m == Material.TURTLE_EGG || m == Material.SNIFFER_EGG) e.setCancelled(true);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String block = b.getBlockData().getAsString();
        boolean blockOk = CityRules.allowRightClick(block);
        boolean itemOk = e.getItem() == null || !CityRules.isWorldChangingItem(e.getItem().getType().getKey().toString());
        if (!blockOk) e.setUseInteractedBlock(Event.Result.DENY);
        if (!itemOk) e.setUseItemInHand(Event.Result.DENY);
        if (!blockOk || !itemOk) {
            notice(e.getPlayer(), blockOk ? "Энэ зүйлийг хотод хэрэглэхгүй." : "Хотын эд зүйл — гар хүрэхгүй.");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent e) {
        if (in(e.getBlock()) && (e.getPlayer() == null || !builder(e.getPlayer()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (in(e.getBlock()) && (e.getPlayer() == null || !builder(e.getPlayer()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        if (!in(e.getEntity().getLocation())) return;
        if (e instanceof org.bukkit.event.hanging.HangingBreakByEntityEvent be && be.getRemover() instanceof Player p && builder(p)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        Entity t = e.getRightClicked();
        if (!in(t.getLocation()) || builder(e.getPlayer()) || isNpc(t) && t instanceof Villager) return; // NPC service handles NPCs
        if (t instanceof Hanging || t instanceof ArmorStand || isNpc(t)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (in(e.getRightClicked().getLocation()) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent e) {
        if (in(e.getEntity().getLocation()) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ world physics

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!in(e.getBlock())) return;
        if (e.getPlayer() != null && builder(e.getPlayer())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (in(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (in(e.getBlock())) e.setCancelled(true); // fire, grass, mycelium, vines: the city keeps its look
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        if (in(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        if (in(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        if (in(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (in(e.getToBlock())) e.setCancelled(true); // liquids neither flow in nor reshape the canal
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (in(e.getBlock()) || e.getBlocks().stream().anyMatch(this::in)
                || e.getBlocks().stream().anyMatch(b -> in(b.getRelative(e.getDirection())))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (in(e.getBlock()) || e.getBlocks().stream().anyMatch(this::in)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::in);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::in);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (!in(e.getBlock())) return;
        if (e.getEntity() instanceof Player p && builder(p)) return;
        if (e.getEntity() instanceof FallingBlock) return;
        e.setCancelled(true); // endermen, ravagers, withers, sheep eating grass, trampling
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent e) {
        if (in(e.getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onRaid(RaidTriggerEvent e) {
        if (in(e.getPlayer().getLocation())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ creatures and combat

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        LivingEntity ent = e.getEntity();
        boolean hostile = ent instanceof Enemy;
        if (!hostile || services.mobs().isSuldMob(ent)) return;
        Location l = e.getLocation();
        if (city.near(l.getWorld().getName(), l.getBlockX(), l.getBlockZ(), 8)
                && CityRules.refusesSpawn(e.getSpawnReason().name(), true)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        Player attacker = attacker(e.getDamager());
        if (attacker == null) {
            // mobs never hurt the city's NPCs or animals
            if (isNpc(victim)) e.setCancelled(true);
            return;
        }
        if (victim instanceof Player target) {
            if (target.equals(attacker)) return;
            boolean cityFight = in(target.getLocation()) || in(attacker.getLocation());
            if (!cityFight) return;
            e.setCancelled(true);
            if (e.getDamager() instanceof AbstractArrow arrow) arrow.remove();
            notice(attacker, "Хархорум бол аюулгүй бүс — тоглогчтой тулалдахгүй.");
            return;
        }
        if (in(victim.getLocation()) && !builder(attacker)
                && (isNpc(victim) || victim instanceof Animals || victim instanceof Villager
                || victim instanceof Tameable t && t.isTamed() || victim instanceof Hanging || victim instanceof ArmorStand)) {
            e.setCancelled(true);
            notice(attacker, "Хотын амьтан, хүмүүсийг хамгаалдаг.");
        }
    }

    private void sweep() {
        if (!city.isBuilt()) return;
        for (World w : Bukkit.getWorlds()) {
            for (LivingEntity ent : w.getLivingEntities()) {
                if (!(ent instanceof Enemy) || services.mobs().isSuldMob(ent) || ent.customName() != null || ent.isLeashed()
                        || ent instanceof Tameable t && t.isTamed()) continue;
                Location l = ent.getLocation();
                if (city.contains(w.getName(), l.getBlockX(), l.getBlockZ())) ent.remove();
            }
        }
    }

    // ------------------------------------------------------------------ arrival

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (e.getFrom().getBlockX() == to.getBlockX() && e.getFrom().getBlockZ() == to.getBlockZ()) return;
        boolean inside = in(to);
        Boolean before = insideState.put(e.getPlayer().getUniqueId(), inside);
        if (before == null || before == inside) return;
        Player p = e.getPlayer();
        Title.Times times = Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1600), Duration.ofMillis(500));
        if (inside) {
            p.showTitle(Title.title(Component.text("ХАРХОРУМ", Messages.BRAND),
                    Component.text("Аюулгүй бүс · Safe zone", NamedTextColor.WHITE), times));
        } else {
            p.showTitle(Title.title(Component.text("Тал нутаг", NamedTextColor.RED),
                    Component.text("Аюултай бүс · Danger", NamedTextColor.WHITE), times));
        }
    }
}
