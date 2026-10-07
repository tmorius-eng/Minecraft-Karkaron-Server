package mn.suld.plugin.combat;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.entity.LookAnchor;
import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.ItemInstance;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Combat feel (docs/COMBAT_FEEL.md): lock-on targeting and the per-class attack decals.
 * <p>
 * <b>Lock-on.</b> Q with the class weapon in hand (it can never be dropped anyway) locks onto the creature nearest the
 * crosshair within 24 blocks and in sight; Q again (or the target dying, leaving 32 blocks or sight for 2 s) releases
 * it. While locked the camera eases onto the target every tick ({@code Player#lookAt}, so the client turns smoothly
 * and every aimed skill, arrow and beam goes where the player looks), the HUD target frame follows it, and a reticle
 * only the locking player can see floats over it. Sneak + Q cycles to the next target.
 * <p>
 * <b>Attack decals.</b> A full-strength swing with the class weapon draws the class's motion as a short-lived
 * {@link ItemDisplay} decal (one entity, 5 ticks, interpolated by the client): Баатар a red horizontal sweep, Дархан an
 * ember overhead chop, Хүлэгчин a steel thrust, Бөө a violet spirit arc, Мэргэн a gold release flash at the bow.
 * Decals are visible to everyone nearby (a fight should read from outside), cost one entity each and are removed on
 * a timer and on shutdown.
 */
public final class CombatFeel implements Listener {

    public static final String TAG = "suld_vfx";
    private static final double LOCK_RANGE = 24, KEEP_RANGE = 32;
    private static final double LOCK_CONE_COS = Math.cos(Math.toRadians(40));
    private static final long LOST_SIGHT_MS = 2000;
    private static final double EASE = 0.45; // fraction of the remaining turn per tick

    private final Plugin plugin;
    private final SuldServices services;
    private final Map<UUID, Lock> locks = new HashMap<>();

    private static final class Lock {
        final UUID target;
        final ItemDisplay reticle;
        long lostSince;

        Lock(UUID target, ItemDisplay reticle) {
            this.target = target;
            this.reticle = reticle;
        }
    }

    public CombatFeel(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        // decals or reticles from a crash: gone on start
        Bukkit.getWorlds().forEach(w -> w.getEntitiesByClass(ItemDisplay.class).forEach(d -> {
            if (d.getScoreboardTags().contains(TAG)) d.remove();
        }));
    }

    public void shutdown() {
        for (Lock l : locks.values()) l.reticle.remove();
        locks.clear();
    }

    /** The locked target of {@code p}, if any (the HUD prefers it). */
    public Optional<LivingEntity> target(Player p) {
        Lock l = locks.get(p.getUniqueId());
        return l != null && Bukkit.getEntity(l.target) instanceof LivingEntity le && le.isValid() ? Optional.of(le) : Optional.empty();
    }

    private boolean classWeapon(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return false;
        Optional<ItemInstance> i = services.items().read(it);
        return i.isPresent() && i.get().definitionId().startsWith("weapon.class.");
    }

    // ------------------------------------------------------------------ lock-on

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onQ(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (p.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) return; // a container screen: not a key press
        if (!classWeapon(e.getItemDrop().getItemStack())) return;
        // Q in hand empties the selected slot; a throw out of the inventory screen or off the cursor does not
        if (p.getInventory().getItemInMainHand().getType() != Material.AIR) return;
        e.setCancelled(true); // never dropped; the soulbound notice is not needed for the lock key
        if (p.isSneaking() && locks.containsKey(p.getUniqueId())) {
            cycle(p);
        } else if (locks.containsKey(p.getUniqueId())) {
            release(p, true);
        } else {
            lock(p, null);
        }
    }

    private boolean lockable(Player p, Entity e) {
        if (!(e instanceof LivingEntity le) || e instanceof Player || e instanceof ArmorStand || !le.isValid() || le.isDead()) return false;
        if (e.getType().name().equals("MANNEQUIN")) return false; // city NPCs
        if (e instanceof Tameable t && t.getOwnerUniqueId() != null) return false; // someone's horse or wolf
        if (le.isInvisible() && !e.getScoreboardTags().contains(mn.suld.plugin.model.ModelService.HOST_TAG)) return false;
        return e.getWorld().equals(p.getWorld());
    }

    /** The best target in front of the player: smallest angle to the crosshair, closer wins ties. */
    private LivingEntity pick(Player p, UUID except) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : p.getNearbyEntities(LOCK_RANGE, LOCK_RANGE / 2, LOCK_RANGE)) {
            if (!lockable(p, e) || e.getUniqueId().equals(except)) continue;
            LivingEntity le = (LivingEntity) e;
            Vector to = le.getEyeLocation().toVector().subtract(eye.toVector());
            double dist = to.length();
            if (dist < 0.5 || dist > LOCK_RANGE) continue;
            double cos = to.multiply(1 / dist).dot(dir);
            if (cos < LOCK_CONE_COS || !p.hasLineOfSight(le)) continue;
            double score = (1 - cos) * 40 + dist * 0.15;
            if (score < bestScore) {
                bestScore = score;
                best = le;
            }
        }
        return best;
    }

    private void lock(Player p, UUID except) {
        LivingEntity t = pick(p, except);
        if (t == null) {
            p.sendActionBar(Messages.info("Түгжих бай алга — харсан зүгт 24 блок дотор"));
            return;
        }
        ItemDisplay reticle = decal(t.getLocation().add(0, t.getHeight() + 0.6, 0), "reticle", 0xFFD25A, 0.7f);
        reticle.setBillboard(Display.Billboard.CENTER);
        reticle.setVisibleByDefault(false);
        p.showEntity(plugin, reticle);
        reticle.setTeleportDuration(2);
        locks.put(p.getUniqueId(), new Lock(t.getUniqueId(), reticle));
        p.playSound(p.getLocation(), Sound.ITEM_SPYGLASS_USE, 0.8f, 1.4f);
    }

    private void cycle(Player p) {
        Lock old = locks.get(p.getUniqueId());
        UUID except = old == null ? null : old.target;
        release(p, false);
        lock(p, except);
    }

    private void release(Player p, boolean sound) {
        Lock l = locks.remove(p.getUniqueId());
        if (l == null) return;
        l.reticle.remove();
        if (sound) p.playSound(p.getLocation(), Sound.ITEM_SPYGLASS_STOP_USING, 0.8f, 1.2f);
    }

    private void tick() {
        if (locks.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (UUID id : locks.keySet().toArray(new UUID[0])) {
            Player p = Bukkit.getPlayer(id);
            Lock l = locks.get(id);
            if (p == null) {
                l.reticle.remove();
                locks.remove(id);
                continue;
            }
            Entity e = Bukkit.getEntity(l.target);
            if (!(e instanceof LivingEntity t) || !t.isValid() || t.isDead() || !t.getWorld().equals(p.getWorld())
                    || t.getLocation().distanceSquared(p.getLocation()) > KEEP_RANGE * KEEP_RANGE || p.isDead()) {
                release(p, true);
                continue;
            }
            if (!p.hasLineOfSight(t)) {
                if (l.lostSince == 0) l.lostSince = now;
                if (now - l.lostSince > LOST_SIGHT_MS) {
                    release(p, true);
                    continue;
                }
            } else {
                l.lostSince = 0;
            }
            // ease the camera: aim a fraction of the remaining angle each tick, so it glides on and then holds
            Location eye = p.getEyeLocation();
            Vector want = t.getLocation().add(0, t.getHeight() * 0.7, 0).toVector().subtract(eye.toVector());
            if (want.lengthSquared() > 1e-4) {
                want.normalize();
                Vector cur = eye.getDirection();
                Vector next = cur.clone().multiply(1 - EASE).add(want.clone().multiply(EASE));
                // on target (within 2°): send nothing, so the player's own mouse is not fought tick after tick
                if (cur.angle(want) >= Math.toRadians(2)) {
                    Vector at = eye.toVector().add(next.normalize().multiply(8));
                    p.lookAt(at.getX(), at.getY(), at.getZ(), LookAnchor.EYES);
                }
            }
            l.reticle.teleport(t.getLocation().add(0, t.getHeight() + 0.6, 0));
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        UUID dead = e.getEntity().getUniqueId();
        for (UUID id : locks.keySet().toArray(new UUID[0])) {
            Lock l = locks.get(id);
            Player p = Bukkit.getPlayer(id);
            if (l != null && l.target.equals(dead) && p != null) release(p, true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Lock l = locks.remove(e.getPlayer().getUniqueId());
        if (l != null) l.reticle.remove();
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        release(e.getPlayer(), false);
    }

    // ------------------------------------------------------------------ attack decals

    private final Map<UUID, Long> lastDecal = new HashMap<>();

    /** A hit: the attack strength is read here, before vanilla resets it (the swing packet arrives after the attack). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        decal(e.getPlayer());
    }

    /** A swing at the air (a miss) still shows the motion. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        decal(e.getPlayer());
    }

    private void decal(Player p) {
        if (p.getAttackCooldown() < 0.9f) return; // a spam-click: no decal, like no full-strength hit
        if (!classWeapon(p.getInventory().getItemInMainHand())) return;
        long now = System.currentTimeMillis();
        Long last = lastDecal.get(p.getUniqueId());
        if (last != null && now - last < 250) return;
        lastDecal.put(p.getUniqueId(), now);
        PlayerClass c = services.profiles().cached(p.getUniqueId()).flatMap(pr -> pr.playerClass()).orElse(null);
        if (c == null) return;
        swing(p, c);
    }

    /** Мэргэн: a gold ring flashes at the bow on a full draw with the class bow. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(org.bukkit.event.entity.EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getForce() < 0.9f || !classWeapon(e.getBow())) return;
        Location eye = p.getEyeLocation();
        Location at = eye.clone().add(eye.getDirection().multiply(1.2));
        at.setPitch(0);
        sweep(at, "ring", 0xF2D27A, 0.4f, 1.4f, 0, 0);
    }

    @EventHandler
    public void onQuitDecal(PlayerQuitEvent e) {
        lastDecal.remove(e.getPlayer().getUniqueId());
    }

    /** The class's swing: one decal in front of the chest, turned and grown by the client over 5 ticks. */
    void swing(Player p, PlayerClass c) {
        Location base = p.getLocation().add(0, p.getHeight() * 0.62, 0);
        float yaw = p.getLocation().getYaw();
        Vector fwd = new Vector(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        Location at = base.clone().add(fwd.clone().multiply(1.1));
        at.setYaw(yaw);
        at.setPitch(0);
        switch (c) {
            case BAATAR -> sweep(at, "slash_h", 0xFF5A46, 2.4f, 2.9f, -55, 55);
            case DARKHAN -> sweep(at, "slash_v", 0xFF9A3C, 2.2f, 2.6f, -40, 30);
            case KHULEGCHIN -> thrust(at, fwd, 0xDDE6F0);
            case BOO -> sweep(at, "slash_h", 0xAA6EFF, 2.0f, 2.6f, 50, -50);
            case MERGEN -> {
                return; // the bow's release flash is onShoot
            }
        }
        p.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.45f, c == PlayerClass.BOO ? 1.5f : 1.1f);
    }

    /** A decal that turns from {@code fromDeg} to {@code toDeg} around its own vertical axis while growing. */
    private void sweep(Location at, String model, int rgb, float s0, float s1, float fromDeg, float toDeg) {
        ItemDisplay d = decal(at, model, rgb, s0);
        d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f((float) Math.toRadians(fromDeg), 0, 1, 0)),
                new Vector3f(s0, s0, s0), new Quaternionf()));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!d.isValid()) return;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(4);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f((float) Math.toRadians(toDeg), 0, 1, 0)),
                    new Vector3f(s1, s1, s1), new Quaternionf()));
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, d::remove, 6L);
    }

    private void thrust(Location at, Vector fwd, int rgb) {
        ItemDisplay d = decal(at.clone().subtract(fwd.clone().multiply(0.6)), "thrust", rgb, 1f);
        d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.5f, 1f, 0.8f), new Quaternionf()));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!d.isValid()) return;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(3);
            d.setTransformation(new Transformation(new Vector3f(0, 0, 1.4f), new Quaternionf(), new Vector3f(0.7f, 1f, 2.6f), new Quaternionf()));
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, d::remove, 5L);
    }

    private ItemDisplay decal(Location at, String model, int rgb, float scale) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("suld", "entity/vfx/" + model));
        it.setItemMeta(meta);
        it.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addColor(Color.fromRGB(rgb)).build());
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(it);
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(0.5f); // 32 blocks
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
        });
    }
}
