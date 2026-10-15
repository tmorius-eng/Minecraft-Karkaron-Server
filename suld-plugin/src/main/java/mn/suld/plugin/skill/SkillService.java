package mn.suld.plugin.skill;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.skill.ComboTracker;
import mn.suld.api.skill.ResourcePool;
import mn.suld.api.skill.Spell;
import mn.suld.api.skill.tree.KeystoneKind;
import mn.suld.api.skill.tree.ModKey;
import mn.suld.api.skill.tree.SkillBuild;
import mn.suld.api.skill.tree.StatKey;
import mn.suld.api.skill.tree.TriggerEvent;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.combat.CombatListener;
import mn.suld.plugin.ui.Glyphs;
import mn.suld.plugin.ui.StyleFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class spells, Wynncraft style: while holding your class weapon, a three-click combo (R-L-R, R-R-R, R-L-L, R-R-L;
 * the archer starts with L) casts one of your four spells if your level unlocked it and you have the resource.
 * Spells hurt only hostile mobs, never players. The action bar shows HP, the class resource bar and the combo.
 */
public final class SkillService implements Listener {

    private static final long CLICK_DEBOUNCE_MS = 60;

    private final Plugin plugin;
    private final SuldServices services;
    private final Map<UUID, ComboTracker> combos = new ConcurrentHashMap<>();
    private final Map<UUID, ResourcePool> pools = new ConcurrentHashMap<>();
    private final Map<String, Long> lastClickByType = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCast = new ConcurrentHashMap<>();
    /** When each spell may be cast again, per player. */
    private final Map<UUID, Map<Spell, Long>> spellReady = new ConcurrentHashMap<>();
    /** Counts every execution of a spell body (including echoes): the QA suite measures echo chance with it. */
    public final java.util.concurrent.atomic.AtomicInteger executions = new java.util.concurrent.atomic.AtomicInteger();
    /** Counts enemies a spell touched (each gets the modifier riders once); the QA suite derives expected totals from it. */
    public final java.util.concurrent.atomic.AtomicInteger riderCalls = new java.util.concurrent.atomic.AtomicInteger();
    /** The caster's ATK when the spell was cast: swapping weapons while it is still flying changes nothing. */
    private final Map<UUID, Double> castAttack = new ConcurrentHashMap<>();

    public SkillService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long t = mn.suld.plugin.perf.PerfProbe.start();
            regen();
            mn.suld.plugin.perf.PerfProbe.stop("skill.regen_tick", t);
        }, 20L, 20L);
        // cooldowns kept across a relog: drop those of offline players once they have run out
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            spellReady.entrySet().removeIf(e -> Bukkit.getPlayer(e.getKey()) == null
                    && e.getValue().values().stream().allMatch(at -> at <= now));
        }, 6000L, 6000L);
    }

    private PlayerClass clazzOf(Player p) {
        return services.profiles().cached(p.getUniqueId()).flatMap(PlayerProfile::playerClass).orElse(null);
    }

    private int levelOf(Player p) {
        return services.profiles().cached(p.getUniqueId()).map(pr -> pr.progression().level()).orElse(1);
    }

    /** The spell's rank power for this caster (I ×1, II ×1.15 from level 40, III ×1.30 from 55). */
    private double rankPower(Player p, Spell s) {
        return Spell.rankPower(s.rankAt(levelOf(p)));
    }

    private SkillBuild buildOf(Player p) {
        return SkillTreeService.build(services, p);
    }

    private int poolSize(Player p) {
        PlayerClass c = clazzOf(p);
        SkillBuild b = buildOf(p);
        return (c == null ? 100 : c.resourceMax()) + (int) b.stat(StatKey.RESOURCE_MAX) + (b.has(KeystoneKind.TENGERTEI_KHOLBOGDOKH) ? 40 : 0);
    }

    public ResourcePool pool(Player p) {
        return pools.computeIfAbsent(p.getUniqueId(), k -> new ResourcePool(poolSize(p)));
    }

    /** The skill tree changed the pool's size: keep the current amount (no free refill), cap it at the new size. */
    public void rebuildPool(Player p) {
        ResourcePool old = pools.get(p.getUniqueId());
        int size = poolSize(p);
        if (old == null || old.max() == size) return;
        ResourcePool next = new ResourcePool(size);
        next.spend(size - Math.min(size, old.value()));
        pools.put(p.getUniqueId(), next);
    }

    /**
     * A class change (docs/CLASS_CHANGE.md): the new class starts with an empty resource pool (Уур does not become
     * Төвлөрөл), no half-typed combo, and every spell on a {@code lockMs} cooldown, so a spell cast just before the
     * change is not followed by another one at once.
     */
    public void afterClassChange(Player p, long lockMs) {
        UUID id = p.getUniqueId();
        combos.remove(id);
        lastCast.remove(id);
        castAttack.remove(id);
        CombatListener.EMPOWERED_ARROWS.remove(id);
        CombatListener.EMPOWERED_UNTIL.remove(id);
        ResourcePool empty = new ResourcePool(poolSize(p));
        empty.spend(empty.max());
        pools.put(id, empty);
        long until = System.currentTimeMillis() + lockMs;
        Map<Spell, Long> ready = spellReady.computeIfAbsent(id, k -> new java.util.EnumMap<>(Spell.class));
        for (Spell s : Spell.values()) ready.merge(s, until, Math::max);
    }

    /** A player's spell waits and resource, kept aside while they train and put back after (docs/TRAINING_GROUNDS.md). */
    public record Held(Map<Spell, Long> ready, int resource) {
    }

    public Held hold(Player p) {
        Map<Spell, Long> m = spellReady.get(p.getUniqueId());
        ResourcePool pool = pools.get(p.getUniqueId());
        return new Held(m == null ? Map.of() : Map.copyOf(m), pool == null ? 0 : pool.value());
    }

    /** The training grounds: no spell waits, a full pool, no half-typed combo, no empowered arrows. */
    public void fresh(Player p) {
        UUID id = p.getUniqueId();
        combos.remove(id);
        lastCast.remove(id);
        castAttack.remove(id);
        spellReady.remove(id);
        CombatListener.EMPOWERED_ARROWS.remove(id);
        CombatListener.EMPOWERED_UNTIL.remove(id);
        pools.remove(id);
        pool(p).gain(1e9);
    }

    /** Back from the training grounds: the waits and the resource the player had (capped by the pool of their class). */
    public void restore(Player p, Held h) {
        UUID id = p.getUniqueId();
        combos.remove(id);
        lastCast.remove(id);
        castAttack.remove(id);
        CombatListener.EMPOWERED_ARROWS.remove(id);
        CombatListener.EMPOWERED_UNTIL.remove(id);
        spellReady.remove(id);
        if (!h.ready().isEmpty()) spellReady.put(id, new java.util.EnumMap<>(h.ready()));
        pools.remove(id);
        ResourcePool pool = pool(p);
        pool.spend(pool.value());
        pool.gain(Math.min(pool.max(), h.resource()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        combos.remove(id);
        lastCast.remove(id);
        // spell cooldowns survive a relog (like the pool): only the expired ones are dropped
        Map<Spell, Long> ready = spellReady.get(id);
        if (ready != null) {
            long now = System.currentTimeMillis();
            ready.values().removeIf(t -> t <= now);
            if (ready.isEmpty()) spellReady.remove(id, ready);
        }
        castAttack.remove(id);
        lastClickByType.keySet().removeIf(k -> k.startsWith(id.toString()));
        drawStart.remove(id);
        drawRepeat.remove(id);
        dryAt.remove(id);
        rightGap.remove(id);
        regenBoosts.remove(id);
        CombatListener.EMPOWERED_ARROWS.remove(id);
        CombatListener.EMPOWERED_UNTIL.remove(id);
        // the resource pool is kept: logging out and in again must not refill Хил / Сүнс mid-fight
    }

    // ------------------------------------------------------------------ clicks

    private boolean holdsClassWeapon(Player p, PlayerClass c) {
        return services.items().read(p.getInventory().getItemInMainHand()).map(i -> {
            if (i.definitionId().startsWith("weapon.class." + c.id() + ".")) return true;
            mn.suld.api.item.ItemDefinition def = mn.suld.plugin.content.SuldContent.definitionFor(i.definitionId());
            if (def == null || def.type().category() != mn.suld.api.item.ItemType.Category.WEAPON) return false;
            if (!mn.suld.api.loot.LootEngine.affinity(c).contains(def.type()) || !def.allows(c)) return false;
            return services.equipment() == null || !services.equipment().bonus(p).inactive().containsKey(mn.suld.api.item.EquipSlot.MAIN_HAND);
        }).orElse(false);
    }

    public boolean holdsClassWeapon(Player p) {
        PlayerClass c = clazzOf(p);
        return c != null && holdsClassWeapon(p, c);
    }

    /**
     * A right click on a block is often "cancelled" by block protection (the city denies using most blocks): that says
     * nothing about casting, so cancelled interacts still count (the class weapon check decides).
     */
    /*
     * No input opens a window here. Sneak + right click with the class weapon used to open the skill-tree map, and
     * players sneak all through a fight (Shift + Space dodges, Shift + F casts the fifth spell, Shift + Q cycles the
     * lock): a combo's R, a bow draw or a right click on a mob while sneaking threw them onto the tree stage mid-fight.
     * The tree opens from /skills and the menu only (NoAccidentalWindowTest); every click while sneaking is a click.
     */

    /** Мэргэн: one draw per second (a full draw's pace, so the class balance stays as simulated). */
    static final long SHOT_MS = 1_000;
    private final Map<UUID, Long> lastShot = new ConcurrentHashMap<>();
    /** Released within this many ticks: a quick tap, shot at {@link #TAP_FORCE}. */
    static final int QUICK_TAP_TICKS = 4;
    /**
     * Held this many ticks: a full draw. The pack's bow models (assets/minecraft/items/bow.json) show pulling_1 from
     * use_duration 0.65 s and pulling_2 from 0.9 s = 18 ticks: full force arrives with the fully drawn model, and a
     * full draw still fits the one-second shot pace.
     */
    static final int FULL_DRAW_TICKS = 18;
    /** A draw nobody lets go of looses by itself after 5 s (a client that never sends the release cannot hang it). */
    static final int MAX_DRAW_TICKS = 100;
    static final float TAP_FORCE = 0.6f;
    /** The client re-sends a held right click every 4 ticks when it is not drawing: two missed repeats = let go. */
    static final int REPEAT_GAP_TICKS = 6;
    /** Мэргэн: server tick a class-bow draw started, while it is drawn (WP-01). */
    private final Map<UUID, Integer> drawStart = new ConcurrentHashMap<>();
    /** Server tick of the last held-right-click repeat seen during a draw (only a client that is not drawing sends them). */
    private final Map<UUID, Integer> drawRepeat = new ConcurrentHashMap<>();
    /** Last dry-string cue (WP-03), so a held click on cooldown does not click every 200 ms. */
    private final Map<UUID, Long> dryAt = new ConcurrentHashMap<>();
    /** True while {@link #shoot} announces its own arrow (any other shot of the class bow is a stray vanilla one). */
    private boolean firing;

    /**
     * The draw's force from how long it was held: a quick tap shoots at {@link #TAP_FORCE}, a longer hold grows to a
     * full 1.0 at {@link #FULL_DRAW_TICKS} (never below the tap, so holding is never worse than tapping).
     */
    static float drawForce(int ticksHeld) {
        if (ticksHeld < QUICK_TAP_TICKS) return TAP_FORCE;
        return Math.min(1.0f, Math.max(TAP_FORCE, ticksHeld / (float) FULL_DRAW_TICKS));
    }

    /**
     * Hold to draw, let go to shoot (WP-01/WP-02). The vanilla use is still refused (the arrows are in the quiver, and a
     * vanilla draw would shoot a real arrow): the server starts the use itself, the client mirrors it and the bow model
     * walks pulling_0 → pulling_2. While a combo is under way the click is only the combo's R, never a draw.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onBowUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getItem() == null || e.getItem().getType() != org.bukkit.Material.BOW) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (clazzOf(p) != PlayerClass.MERGEN || !holdsClassWeapon(p, PlayerClass.MERGEN)) return;
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        if (paused.test(p)) return; // a sky window's click is its own
        UUID id = p.getUniqueId();
        if (drawStart.containsKey(id)) { // WP-06: a held click's repeat — one draw at a time
            drawRepeat.put(id, Bukkit.getCurrentTick());
            return;
        }
        ComboTracker t = combos.get(id);
        long now = System.currentTimeMillis();
        if (t != null && !t.current(now).isEmpty()) return;
        if (soulBlocked(id)) return;
        Long last = lastShot.get(id);
        if (last != null && now - last < SHOT_MS) {
            dryString(p, SHOT_MS - (now - last));
            return;
        }
        lastShot.put(id, now);
        startDraw(p);
    }

    /** WP-03: the bow is not ready — a dry string click and the wait, at most every 0.4 s. */
    private void dryString(Player p, long leftMs) {
        long now = System.currentTimeMillis();
        Long last = dryAt.put(p.getUniqueId(), now);
        if (last != null && now - last < 400) return;
        p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.35f, 1.6f);
        say(p, "§7Нум — " + String.format(java.util.Locale.ROOT, "%.1f", leftMs / 1000.0) + " сек");
    }

    private void startDraw(Player p) {
        UUID id = p.getUniqueId();
        int start = Bukkit.getCurrentTick();
        drawStart.put(id, start);
        drawRepeat.remove(id);
        p.startUsingItem(EquipmentSlot.HAND);
        // the fallback: a release the server never hears about (a switched item, a client that did not mirror the draw)
        new BukkitRunnable() {
            @Override
            public void run() {
                Integer now = drawStart.get(id);
                if (now == null || now != start) { // already released (PlayerStopUsingItemEvent) or a newer draw
                    cancel();
                    return;
                }
                if (!p.isOnline() || p.isDead() || !holdsClassWeapon(p, PlayerClass.MERGEN)) {
                    cancelDraw(p);
                    cancel();
                    return;
                }
                int tick = Bukkit.getCurrentTick();
                int held = tick - start;
                Integer repeat = drawRepeat.get(id);
                if (held >= MAX_DRAW_TICKS || held >= 2 && !p.isHandRaised()) {
                    release(p, held);
                    cancel();
                } else if (repeat != null && tick - repeat > REPEAT_GAP_TICKS) {
                    release(p, repeat - start); // the repeats stopped: the button came up at the last one
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** The draw ends without a shot (the bow was put away, a left click began a combo). */
    private void cancelDraw(Player p) {
        if (drawStart.remove(p.getUniqueId()) == null) return;
        drawRepeat.remove(p.getUniqueId());
        if (p.isOnline() && p.isHandRaised()) p.clearActiveItem();
    }

    /** The draw is let go after {@code held} ticks: the arrow flies at the draw's force. */
    private void release(Player p, int held) {
        UUID id = p.getUniqueId();
        if (drawStart.remove(id) == null) return;
        drawRepeat.remove(id);
        if (p.isHandRaised()) p.clearActiveItem();
        if (soulBlocked(id)) return;
        shoot(p, drawForce(held));
    }

    /** The client let go of the right button: the draw's own release (Paper fires this from the vanilla release). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBowRelease(io.papermc.paper.event.player.PlayerStopUsingItemEvent e) {
        Player p = e.getPlayer();
        Integer start = drawStart.get(p.getUniqueId());
        if (start == null) return;
        release(p, Math.max(e.getTicksHeldFor(),Bukkit.getCurrentTick() - start));
    }

    /** A real arrow left in the bag (a full quiver overflow) must not fly from the class bow besides SÜLD's own. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onStrayClassBowShot(org.bukkit.event.entity.EntityShootBowEvent e) {
        if (firing || !(e.getEntity() instanceof Player p) || clazzOf(p) != PlayerClass.MERGEN || !holdsClassWeapon(p, PlayerClass.MERGEN)) return;
        e.setConsumeItem(false);
        e.setCancelled(true);
    }

    /**
     * A soul may not fight — except in its «Тэнгэрийн сорил», where DeathService.onSoulAttack lets its hits, arrows and
     * spells reach only its own wolves.
     */
    private boolean soulBlocked(UUID id) {
        return services.isSoul.test(id) && !services.inDeathTrial.test(id);
    }

    /**
     * An arrow drawn with {@code force} (0..1: speed 3·force, the vanilla full-draw damage 6 that CombatListener scales
     * from). The force is written on the arrow at launch (WP-08): a long shot slowed by the air keeps its damage.
     */
    private void shoot(Player p, float force) {
        Arrow a = p.launchProjectile(Arrow.class, p.getEyeLocation().getDirection().multiply(3.0 * force));
        a.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
        a.setDamage(2.0);
        a.setCritical(false);
        CombatListener.markDraw(a, force);
        // the shot as every listener knows it (CombatFeel's release ring on a full draw, logging plugins)
        var shot = new org.bukkit.event.entity.EntityShootBowEvent(p, p.getInventory().getItemInMainHand(), new org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW), a,
                EquipmentSlot.HAND, force, false);
        firing = true;
        try {
            Bukkit.getPluginManager().callEvent(shot);
        } finally {
            firing = false;
        }
        if (shot.isCancelled()) {
            a.remove();
            return;
        }
        // no arm swing on release: a server swing of the main hand comes back from the client as a left click (a phantom 'L')
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.9f, 0.95f + 0.2f * force);
        p.getWorld().spawnParticle(org.bukkit.Particle.CRIT, p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(1.2)), 6, 0.1, 0.1, 0.1, 0.05);
        if (CombatListener.empowered(p.getUniqueId())) empoweredTrail(a);
    }

    /** WP-07: an arrow Чонын Нүд (or Чонын Сүнс) empowers trails soul fire until it lands (at most 3 s). */
    private void empoweredTrail(Arrow a) {
        a.addScoreboardTag("suld_empowered");
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!a.isValid() || a.isInBlock() || a.isOnGround() || ++ticks > 60) {
                    cancel();
                    return;
                }
                Location at = a.getLocation();
                at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at, 2, 0.03, 0.03, 0.03, 0.005);
                at.getWorld().spawnParticle(Particle.CRIT, at, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) click(e.getPlayer(), 'L');
        else if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) click(e.getPlayer(), 'R');
    }

    /** A left click always swings the arm (air, block or entity); the debounce merges it with the other events. */
    private final Map<UUID, Integer> lastDropTick = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDropTrack(org.bukkit.event.player.PlayerDropItemEvent e) {
        lastDropTick.put(e.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(org.bukkit.event.player.PlayerAnimationEvent e) {
        if (e.getAnimationType() != org.bukkit.event.player.PlayerAnimationType.ARM_SWING) return;
        Integer dt = lastDropTick.get(e.getPlayer().getUniqueId());
        if (dt != null && dt == Bukkit.getCurrentTick()) return;
        click(e.getPlayer(), 'L');
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        click(e.getPlayer(), 'R');
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        if (CombatListener.isSpellDamage()) return;
        if (e.getDamager() instanceof Player p) {
            click(p, 'L');
            PlayerClass c = clazzOf(p);
            if (c == PlayerClass.BAATAR) pool(p).gain(4 * masteryGain(p, c));   // Хил grows in battle
            if (c == PlayerClass.DARKHAN) pool(p).gain(3 * masteryGain(p, c));  // the forge heats with every blow
        }
    }

    /**
     * Set by SuldPlugin: true while the player is in a sky window (the menu, the skill tree, the atlas). Its clicks are
     * the window's own: none of them makes a combo or shoots (the windows cancel the interaction, but the swing still
     * reached here, and a click on a tab could cast a spell up on the stage — map inspection 2026-10-13).
     */
    public volatile java.util.function.Predicate<Player> paused = p -> false;

    private void click(Player p, char c) {
        if (paused.test(p)) return;
        PlayerClass clazz = clazzOf(p);
        if (clazz == null || !holdsClassWeapon(p, clazz)) return;
        long now = System.currentTimeMillis();
        // one physical click can fire two events (interact + swing, or hit + swing): keep the first
        String key = p.getUniqueId() + ":" + c;
        Long last = lastClickByType.put(key, now);
        if (last != null && now - last < CLICK_DEBOUNCE_MS) return;
        // SULD-F08: a right-click block/entity interact also fires ARM_SWING ('L') in the same tick; ignore 'L' within 30 ms of 'R'
        if (c == 'L') {
            Long lastR = lastClickByType.get(p.getUniqueId() + ":R");
            if (lastR != null && now - lastR < 30) return;
            cancelDraw(p); // a left click while the bow is drawn lets the draw go and starts the archer's combo
        } else {
            if (drawStart.containsKey(p.getUniqueId())) return; // WP-06: the held right button of a draw is not a combo R
            long gap = last == null ? Long.MAX_VALUE : now - last;
            Long before = rightGap.put(p.getUniqueId(), gap);
            if (heldRepeat(before, gap)) return; // WP-06: a held button repeating every 4 ticks never types R-R-R
        }
        if (c == 'R' && p.isHandRaised()) return; // WP-06: ignore 4-tick hold-right-click auto-repeat packets while using an item
        ComboTracker t = combos.computeIfAbsent(p.getUniqueId(), k -> new ComboTracker(clazz));
        if (t.clazz() != clazz) {
            t = new ComboTracker(clazz);
            combos.put(p.getUniqueId(), t);
        }
        ComboTracker.Result r = t.click(c, now);
        if (r.kind() != ComboTracker.Kind.IGNORED && c == 'R') rightClickCue(p, clazz);
        if (r.kind() == ComboTracker.Kind.PROGRESS) {
            if (c == 'R' && clazz != PlayerClass.MERGEN) p.swingOffHand(); // WP-05: visual hand motion on melee/staff R combo step
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, r.combo().length() == 1 ? 1.4f : 1.7f);
            services.hud().refresh(p);
        } else if (r.kind() == ComboTracker.Kind.COMPLETE) {
            t.spellFor(r.combo()).ifPresent(s -> cast(p, s));
        }
    }

    /** The gap before each player's last right click, to tell a held button from clicks. */
    private final Map<UUID, Long> rightGap = new ConcurrentHashMap<>();
    /** A held right button repeats every 4 ticks (200 ms); the server sees the gaps on tick boundaries. */
    static final long HOLD_MIN_MS = 170, HOLD_MAX_MS = 230;

    /**
     * True for a right click that is a held button's repeat: it and the click before it both came one 4-tick repeat
     * after the previous one. The first two clicks of a hold still count (a held R starts R-R), the third never does,
     * so holding the button cannot cast R-R-R. A player clicking R-R-R on their own almost never hits the exact
     * 4-tick rhythm twice in a row.
     */
    static boolean heldRepeat(Long gapBefore, long gap) {
        return gapBefore != null && gapBefore >= HOLD_MIN_MS && gapBefore <= HOLD_MAX_MS && gap >= HOLD_MIN_MS && gap <= HOLD_MAX_MS;
    }

    /**
     * WP-05: a combo's right click shows on screen. The off hand swings (first and third person); a main-hand swing
     * would come back from the client as a left click and type a phantom 'L'. Бөө's staff chimes, the blades whisper.
     */
    private void rightClickCue(Player p, PlayerClass clazz) {
        p.swingOffHand();
        switch (clazz) {
            case BOO -> p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.5f);
            case MERGEN -> p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 0.3f, 1.6f);
            default -> p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.25f, 1.7f);
        }
    }

    // ------------------------------------------------------------------ casting

    public enum CastResult { CAST, NO_PROFILE, SOUL, LOCKED, GAP, COOLDOWN, NO_RESOURCE, NO_TARGET }

    private void cast(Player p, Spell s) {
        castDirect(p, s, false);
    }

    /** Seconds left before {@code s} can be cast again (0 = ready). */
    public double cooldownLeft(Player p, Spell s) {
        Map<Spell, Long> m = spellReady.get(p.getUniqueId());
        Long at = m == null ? null : m.get(s);
        return at == null ? 0 : Math.max(0, (at - System.currentTimeMillis()) / 1000.0);
    }

    /** The cooldown of {@code s} for this build: base seconds reduced by the cooldown-reduction stat (at most 60%). */
    public static double cooldownSeconds(SkillBuild b, Spell s) {
        return s.cooldownSeconds() * (1 - Math.min(60, b.stat(StatKey.COOLDOWN_REDUCTION)) / 100.0);
    }

    /** The cooldown at a caster level: rank III casts 10 % sooner. */
    public static double cooldownSeconds(SkillBuild b, Spell s, int level) {
        return cooldownSeconds(b, s) * Spell.rankCooldown(s.rankAt(level));
    }

    /**
     * Cast {@code s} for {@code p} with all the normal rules (soul, level, anti-spam gap, cooldown, resource).
     * {@code ignoreTiming} skips the anti-spam gap and the cooldown (used by the QA suite to cast repeatedly).
     */
    public CastResult castDirect(Player p, Spell s, boolean ignoreTiming) {
        long t = mn.suld.plugin.perf.PerfProbe.start();
        try {
            CastResult r = castDirect0(p, s, ignoreTiming);
            if (r == CastResult.CAST && services.activity != null) services.activity.signal(p.getUniqueId(), mn.suld.api.activity.ActivitySignal.SPELL);
            if (r == CastResult.CAST && !ignoreTiming && services.classArmor != null) services.classArmor.spellCast(p, s); // QA casts never count
            return r;
        } finally {
            mn.suld.plugin.perf.PerfProbe.stop("skill.cast", t);
        }
    }

    /** Told about every spell cast, just before its effect runs (the training grounds count its hits). Main thread. */
    public volatile java.util.function.BiConsumer<Player, Spell> castListener = (p, s) -> { };

    /** The damage factor of {@code s} for this player now: spell × modifiers × spell damage × rank (0 for a spell without damage). */
    public double powerOf(Player p, Spell s) {
        SkillBuild b = buildOf(p);
        return s.damageMultiplier() * (1 + b.mod(s, ModKey.DAMAGE_PCT) / 100.0) * services.skillTree().spellDamageMultiplier(p) * rankPower(p, s);
    }

    private CastResult castDirect0(Player p, Spell s, boolean ignoreTiming) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return CastResult.NO_PROFILE;
        if (soulBlocked(p.getUniqueId())) { // a soul cannot cast (but fights its own trial wolves with everything)
            say(p, "§bСүнс — ид шид хэрэглэх боломжгүй");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f); // every refused cast sounds the same (Phase 5)
            return CastResult.SOUL;
        }
        if (pr.progression().level() < s.unlockLevel()) {
            say(p, "§c" + s.displayName() + " — түвшин " + s.unlockLevel());
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            return CastResult.LOCKED;
        }
        long now = System.currentTimeMillis();
        Long lc = lastCast.get(p.getUniqueId());
        if (!ignoreTiming && lc != null && now - lc < 400) return CastResult.GAP;
        SkillBuild build = buildOf(p);
        if (!ignoreTiming) {
            double left = cooldownLeft(p, s);
            if (left > 0) {
                say(p, "§c" + s.displayName() + " — " + String.format(java.util.Locale.ROOT, "%.1f", left) + " сек хүлээнэ");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                return CastResult.COOLDOWN;
            }
        }
        // a single-target spell with nothing to aim at is refused before it costs anything or starts its cooldown
        // (audit 2026-10-14: Онгоны Дуудлага used to charge, start the cooldown and hand back only the base cost)
        if (s == Spell.ONGONY_DUUDLAGA && ongonTarget(p) == null) {
            say(p, "§c" + s.displayName() + " — зорилго алга (20 м дотор, урагшаа)");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            return CastResult.NO_TARGET;
        }
        int cost = costOf(build, s);
        if (!pool(p).spend(cost)) {
            say(p, "§c" + s.displayName() + " — нөөц хүрэлцэхгүй (" + cost + ")");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            return CastResult.NO_RESOURCE;
        }
        spellReady.computeIfAbsent(p.getUniqueId(), k -> new java.util.EnumMap<>(Spell.class))
                .put(s, now + (long) (cooldownSeconds(build, s, pr.progression().level()) * 1000));
        lastCast.put(p.getUniqueId(), now);
        castAttack.put(p.getUniqueId(), CombatListener.attackOf(services, p));
        int rank = s.rankAt(pr.progression().level());
        say(p, "§e✦ " + s.displayName() + (rank > 1 ? " " + Spell.roman(rank) : ""));
        castListener.accept(p, s); // before the effect: the training grounds count the hits of this cast
        run(p, s);
        double echo = build.mod(s, ModKey.ECHO_PCT);
        if (echo > 0 && java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100 < echo) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && !p.isDead()) {
                    p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.6f);
                    run(p, s);
                }
            }, 12L);
        }
        double shield = build.mod(s, ModKey.SHIELD);
        if (shield > 0) services.skillTree().shieldAdd(p, shield, 120);
        double haste = build.mod(s, ModKey.HASTE);
        if (haste > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) (haste * 20), 1, false, false, true));
        services.skillTree().fire(p, TriggerEvent.CAST, null);
        return CastResult.CAST;
    }

    /** Mana cost after spell modifiers and cooldown/cost reduction (never below 1). */
    private static int costOf(SkillBuild b, Spell s) {
        double factor = 1 + b.mod(s, ModKey.COST_PCT) / 100.0 - b.stat(StatKey.COST_REDUCTION) / 100.0
                + (b.has(KeystoneKind.KHATUU_ZORIG) ? 0.25 : 0);
        return Math.max(1, (int) Math.round(s.cost() * Math.max(0.2, factor)));
    }

    private void run(Player p, Spell s) {
        executions.incrementAndGet();
        switch (s) {
            case TENGER_TSAVCHILT -> cone(p, s, 4.5, 0.45, Particle.SWEEP_ATTACK, Sound.ENTITY_PLAYER_ATTACK_SWEEP);
            case DAINY_KHASHGIRAAN -> warCry(p, s);
            case DOVTLOKH_USRELT -> leap(p, s);
            case KHAAN_KHAMGAALALT -> buff(p, Sound.ITEM_TOTEM_USE, Particle.TOTEM_OF_UNDYING,
                    new PotionEffect(PotionEffectType.ABSORPTION, 200, 1), new PotionEffect(PotionEffectType.RESISTANCE, 120, 1));
            case CHONYN_NUD -> wolfEye(p, s);
            case OLON_SUM -> volley(p);
            case UKHRAKH_USRELT -> disengage(p, s);
            case TENGERIIN_SUM -> beam(p, s, 30, Particle.END_ROD, Sound.ENTITY_ILLUSIONER_CAST_SPELL);
            case SUNSNII_ZALBIRAL -> heal(p, s, 8, 8);
            case ONGONY_DUUDLAGA -> ongon(p, s);
            case KHENGERGIIN_DUU -> drum(p, s);
            case TENGERIIN_KHAALGA -> skyGate(p, s);
            case GALYN_DAVTALT -> slam(p, s);
            case GAN_BAMBAI -> buff(p, Sound.ITEM_ARMOR_EQUIP_NETHERITE, Particle.CRIT,
                    new PotionEffect(PotionEffectType.RESISTANCE, 120, 1));
            case KHAILSAN_TUMUR -> moltenSpray(p, s);
            case DARKHANY_DARANGUI -> anvil(p, s);
            case KHURDAN_DOVTOLGOO -> charge(p, s);
            case SALKHINY_KHURD -> buff(p, Sound.ENTITY_BREEZE_WIND_BURST, Particle.CLOUD,
                    new PotionEffect(PotionEffectType.SPEED, 120, 2));
            case ZHADNY_SHIDELT -> beam(p, s, 18, Particle.CRIT, Sound.ITEM_TRIDENT_THROW);
            case KHULGIIN_DAIRALT -> stampede(p, s);
            case SULDIIN_TSOKHILT -> suldStrike(p, s);
            case SUMNY_BOROO -> arrowRain(p, s);
            case KHAR_SALKHI -> blackWind(p, s);
            case GALT_UUL -> volcano(p, s);
            case KHIIMORIIN_DAVKHILT -> windHorse(p, s);
        }
    }

    // ------------------------------------------------------------------ the fifth spells (level 50, Shift+F)

    /** Shift+F with the class weapon casts the fifth spell (F alone stays the ultimate). */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onShiftF(org.bukkit.event.player.PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!p.isSneaking() || !holdsClassWeapon(p)) return;
        PlayerClass c = clazzOf(p);
        Spell s = c == null ? null : Spell.shiftF(c).orElse(null);
        if (s == null) return;
        e.setCancelled(true); // the hands never swap, and the ultimate handler skips a cancelled event
        cast(p, s);
    }

    /** Баатар: the banner slams the ground; everyone around is thrown back, the hero is hardened 5 s. */
    private void suldStrike(Player p, Spell s) {
        Location c = p.getLocation();
        for (LivingEntity e : around(p, c, r(p, s, 5.5))) {
            hurt(p, e, s);
            Vector away = horizontal(e.getLocation().toVector().subtract(c.toVector()));
            e.setVelocity(away.multiply(0.9).setY(0.45));
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1));
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            c.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 4.5, 0.2, Math.sin(a) * 4.5), 2, 0.1, 0.1, 0.1, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 205, 70), 1.6f));
        }
        c.getWorld().spawnParticle(Particle.EXPLOSION, c, 2, 0.6, 0.1, 0.6);
        c.getWorld().spawnParticle(Particle.BLOCK, c, 40, 2, 0.1, 2, 0, Material.DIRT.createBlockData());
        c.getWorld().playSound(c, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.8f);
        c.getWorld().playSound(c, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.6f);
    }

    /** Мэргэн: five waves of arrows fall on the aimed spot, half a second apart. */
    private void arrowRain(Player p, Spell s) {
        Location c = targetPoint(p, 24);
        double rr = r(p, s, 4.5);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t >= 5) {
                    cancel();
                    return;
                }
                t++;
                for (int i = 0; i < 18; i++) {
                    double a = Math.random() * Math.PI * 2, d = Math.sqrt(Math.random()) * rr;
                    Location at = c.clone().add(Math.cos(a) * d, 4, Math.sin(a) * d);
                    c.getWorld().spawnParticle(Particle.CRIT, at, 0, 0, -1.5, 0, 1);
                }
                for (LivingEntity e : around(p, c, rr)) hurt(p, e, s);
                c.getWorld().playSound(c, Sound.ENTITY_ARROW_SHOOT, 1f, 0.7f + 0.1f * t);
                c.getWorld().playSound(c, Sound.ENTITY_ARROW_HIT, 0.8f, 1.1f);
            }
        }.runTaskTimer(plugin, 6L, 10L);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.6f);
    }

    /** Бөө: a black wind of the spirits pulls the enemies in and strikes five times; each strike heals the shaman. */
    private void blackWind(Player p, Spell s) {
        Location c = targetPoint(p, 16);
        double rr = r(p, s, 5.5);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t >= 5) {
                    cancel();
                    return;
                }
                t++;
                int hits = 0;
                for (LivingEntity e : around(p, c, rr)) {
                    Vector in = horizontal(c.toVector().subtract(e.getLocation().toVector()));
                    e.setVelocity(in.multiply(0.45).setY(0.12));
                    hurt(p, e, s);
                    hits++;
                }
                if (hits > 0) services.skillTree().heal(p, Math.min(4, hits) * 0.8 * rankPower(p, s));
                for (int i = 0; i < 20; i++) {
                    double a = Math.PI * 2 * i / 20 + t * 0.6, rad = rr * (1 - (i % 5) * 0.15);
                    c.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * rad, 0.3 + (i % 5) * 0.4, Math.sin(a) * rad), 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(70, 30, 110), 1.8f));
                }
                c.getWorld().spawnParticle(Particle.SOUL, c.clone().add(0, 1, 0), 8, rr / 3, 0.6, rr / 3, 0.02);
                c.getWorld().playSound(c, Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.5f + 0.1f * t);
            }
        }.runTaskTimer(plugin, 4L, 12L);
    }

    /** Дархан: the aimed ground smoulders, then erupts: everyone is thrown up and set on fire. */
    private void volcano(Player p, Spell s) {
        Location c = targetPoint(p, 20);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (t++ < 16) {
                    c.getWorld().spawnParticle(Particle.LAVA, c, 2, 1.5, 0.1, 1.5);
                    c.getWorld().spawnParticle(Particle.LARGE_SMOKE, c, 3, 1.5, 0.1, 1.5, 0.01);
                    if (t % 5 == 0) c.getWorld().playSound(c, Sound.BLOCK_LAVA_POP, 1f, 0.6f);
                    return;
                }
                for (LivingEntity e : around(p, c, r(p, s, 4.5))) {
                    hurt(p, e, s);
                    ignite(p, e, s, 100);
                    e.setVelocity(e.getVelocity().add(new Vector(0, 0.8, 0)));
                }
                c.getWorld().spawnParticle(Particle.EXPLOSION, c, 4, 1, 0.3, 1);
                c.getWorld().spawnParticle(Particle.FLAME, c, 60, 1.5, 1.5, 1.5, 0.08);
                c.getWorld().spawnParticle(Particle.LAVA, c, 25, 2, 0.5, 2);
                c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Хүлэгчин: the wind horse rises; a long charge strikes everything on the way, then 6 s of speed. */
    private void windHorse(Player p, Spell s) {
        services.movement.launch(p, flat(p).multiply(2.1).setY(0.2), "windHorse", mn.suld.plugin.anticheat.MovementExemptions.WINDHORSE_TICKS);
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t++ > 14) {
                    cancel();
                    return;
                }
                if (t == 6) services.movement.launch(p, flat(p).multiply(1.6).setY(0.1), "windHorse", mn.suld.plugin.anticheat.MovementExemptions.WINDHORSE_TICKS);
                for (LivingEntity e : around(p, p.getLocation(), r(p, s, 2.4))) if (hit.add(e.getUniqueId())) {
                    hurt(p, e, s);
                    e.setVelocity(flat(p).multiply(0.6).setY(0.5));
                }
                p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 6, 0.6, 0.5, 0.6, 0,
                        new Particle.DustOptions(Color.fromRGB(120, 200, 255), 1.5f));
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 3, 0.3, 0.1, 0.3, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 2));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_HORSE_ANGRY, 1f, 1.1f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_CHARGE, 1f, 0.8f);
    }

    public void say(Player p, String text) {
        services.hud().toast(p, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text), 1500);
    }

    // ------------------------------------------------------------------ read by the HUD

    /** The player's class, or null before one is chosen. */
    public PlayerClass clazz(Player p) {
        return clazzOf(p);
    }

    /** The click combo being typed ("LR"), or empty. */
    public String comboInProgress(Player p) {
        ComboTracker t = combos.get(p.getUniqueId());
        return t == null ? "" : t.current(System.currentTimeMillis());
    }

    /** What casting {@code s} costs this player now (spell modifiers included). */
    public int costFor(Player p, Spell s) {
        return costOf(buildOf(p), s);
    }

    // ------------------------------------------------------------------ spell helpers

    private boolean hostile(Entity e, Player caster) {
        return e instanceof LivingEntity le && le.isValid() && !(e instanceof Player) && e != caster
                && (services.mobs().isSuldMob(e) || e instanceof Monster || mn.suld.plugin.death.DeathTrialService.ownerOf(e) != null)
                && !e.getScoreboardTags().contains("city_npc");
    }

    private void hurt(Player p, LivingEntity target, Spell s) {
        Long cast = lastCast.get(p.getUniqueId());
        Double snap = castAttack.get(p.getUniqueId());
        boolean fresh = cast != null && snap != null && System.currentTimeMillis() - cast < 5_000;
        SkillBuild b = buildOf(p);
        double mult = s.damageMultiplier() * (1 + b.mod(s, ModKey.DAMAGE_PCT) / 100.0) * services.skillTree().spellDamageMultiplier(p)
                * rankPower(p, s);
        double dmg = (fresh ? snap : CombatListener.attackOf(services, p)) * mult;
        mn.suld.plugin.combat.HitSource.run(mn.suld.plugin.combat.HitSource.spell(s), () -> CombatListener.asSpell(() -> target.damage(dmg, p)));
        riders(p, target, s);
        services.skillTree().fire(p, TriggerEvent.SPELL_HIT, target);
    }

    /** Everything a spell modifier adds to an enemy the spell touched (burn, slow, mark, pull, life, refund...). */
    public void riders(Player p, LivingEntity target, Spell s) {
        riderCalls.incrementAndGet();
        SkillBuild b = buildOf(p);
        if (b.isEmpty() || !target.isValid()) return;
        double burn = burnOf(b, s);
        if (burn > 0) target.setFireTicks(Math.max(target.getFireTicks(), (int) (burn * 20)));
        double slow = b.mod(s, ModKey.SLOW);
        if (slow > 0) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (slow * 20), 1));
        double weaken = b.mod(s, ModKey.WEAKEN);
        if (weaken > 0) target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, (int) (weaken * 20), 0));
        double vuln = b.mod(s, ModKey.VULN);
        if (vuln > 0) services.skillTree().mark(target, vuln, 4);
        double heal = b.mod(s, ModKey.HEAL_ON_HIT);
        if (heal > 0) services.skillTree().heal(p, heal);
        double refund = b.mod(s, ModKey.REFUND);
        if (refund > 0) pool(p).gain(refund);
        double up = b.mod(s, ModKey.KNOCKUP);
        if (up > 0) target.setVelocity(target.getVelocity().add(new Vector(0, 0.25 + 0.1 * up, 0)));
        double pull = b.mod(s, ModKey.PULL);
        if (pull > 0) {
            Vector to = p.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
            if (to.lengthSquared() > 1) target.setVelocity(to.normalize().multiply(0.5 * pull).setY(0.2));
        }
    }

    /**
     * The horizontal direction the player faces, from the yaw only. (Taking the view vector and zeroing its height gives a
     * zero vector, and then NaN, when the player looks straight up or down.)
     */
    static Vector flat(Player p) {
        double yaw = Math.toRadians(p.getLocation().getYaw());
        return new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    private static Vector horizontal(Vector v) {
        Vector h = v.clone().setY(0);
        return h.lengthSquared() < 1e-6 ? new Vector(0, 0, 0) : h.normalize();
    }

    private double r(Player p, Spell s, double radius) {
        return radius * (1 + buildOf(p).mod(s, ModKey.RADIUS_PCT) / 100.0);
    }

    /** Enemies of the player around a point (used by procs and ultimates too). */
    public List<LivingEntity> enemiesAround(Player p, Location c, double radius) {
        return around(p, c, radius);
    }

    /** Damage from a passive spell or ultimate: ATK-based, never replaced by the hit formula, never triggering hit procs. */
    public void damage(Player p, LivingEntity target, double amount) {
        if (!target.isValid()) return;
        CombatListener.asSpell(() -> target.damage(amount, p));
    }

    /** Enemies whose body (the centre of their hitbox, not their feet) is within {@code r} of a point on a ray. */
    private List<LivingEntity> aroundBody(Player p, Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : c.getWorld().getNearbyEntities(c, r + 1, r + 1, r + 1)) {
            if (hostile(e, p) && e.getBoundingBox().getCenter().distanceSquared(c.toVector()) <= r * r) out.add((LivingEntity) e);
        }
        return out;
    }

    private List<LivingEntity> around(Player p, Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : c.getWorld().getNearbyEntities(c, r, r, r)) {
            if (hostile(e, p) && e.getLocation().distanceSquared(c) <= r * r) out.add((LivingEntity) e);
        }
        return out;
    }

    private static Color color(Player ignored, Spell s) {
        return switch (s.clazz()) {
            case BAATAR -> Color.fromRGB(255, 90, 70);
            case MERGEN -> Color.fromRGB(120, 230, 120);
            case BOO -> Color.fromRGB(170, 110, 255);
            case DARKHAN -> Color.fromRGB(255, 150, 50);
            case KHULEGCHIN -> Color.fromRGB(90, 170, 255);
        };
    }

    private void cone(Player p, Spell s, double range, double dot, Particle fx, Sound sound) {
        Vector dir = flat(p);
        Location c = p.getLocation().add(0, 1, 0);
        for (LivingEntity e : around(p, c, r(p, s, range))) {
            Vector to = e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            if (to.lengthSquared() < 0.01 || horizontal(to).dot(dir) >= dot) {
                hurt(p, e, s);
                e.setVelocity(horizontal(to).multiply(0.6).setY(0.3));
            }
        }
        for (int i = -2; i <= 2; i++) {
            Vector v = dir.clone().rotateAroundY(Math.toRadians(i * 22)).multiply(2.2);
            p.getWorld().spawnParticle(fx, c.clone().add(v), 1);
        }
        p.getWorld().spawnParticle(Particle.DUST, c.clone().add(dir.clone().multiply(2)), 20, 1.2, 0.3, 1.2, 0,
                new Particle.DustOptions(color(p, s), 1.3f));
        p.getWorld().playSound(c, sound, 1f, 0.9f);
    }

    private void warCry(Player p, Spell s) {
        for (LivingEntity e : around(p, p.getLocation(), r(p, s, 9))) {
            if (e instanceof Mob m) m.setTarget(p);
            riders(p, e, s);
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 160, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 0));
        p.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, p.getLocation().add(0, 2, 0), 8, 0.6, 0.3, 0.6);
        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 40, 2, 0.5, 2, 0,
                new Particle.DustOptions(Color.fromRGB(255, 60, 40), 1.4f));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.7f, 1.3f);
    }

    private void leap(Player p, Spell s) {
        services.movement.launch(p, flat(p).multiply(1.3).setY(0.65), "leap", mn.suld.plugin.anticheat.MovementExemptions.LEAP_TICKS);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 1f, 1f);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                t++;
                if ((t > 5 && p.getLocation().clone().subtract(0, 0.2, 0).getBlock().getType().isSolid()) || t > 40 || !p.isOnline()) {
                    Location c = p.getLocation();
                    for (LivingEntity e : around(p, c, r(p, s, 3.8))) {
                        hurt(p, e, s);
                        e.setVelocity(new Vector(0, 0.55, 0));
                    }
                    c.getWorld().spawnParticle(Particle.EXPLOSION, c, 2);
                    c.getWorld().spawnParticle(Particle.BLOCK, c, 40, 1.5, 0.1, 1.5, c.clone().subtract(0, 1, 0).getBlock().getBlockData());
                    c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void buff(Player p, Sound sound, Particle fx, PotionEffect... effects) {
        for (PotionEffect e : effects) p.addPotionEffect(e);
        p.getWorld().spawnParticle(fx, p.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.1);
        p.getWorld().playSound(p.getLocation(), sound, 0.8f, 1.1f);
    }

    private void wolfEye(Player p, Spell s) {
        for (LivingEntity e : around(p, p.getLocation(), r(p, s, 24))) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0));
            riders(p, e, s);
        }
        CombatListener.EMPOWERED_ARROWS.put(p.getUniqueId(), 3);
        CombatListener.EMPOWERED_UNTIL.put(p.getUniqueId(), System.currentTimeMillis() + 12_000);
        p.getWorld().spawnParticle(Particle.DUST, p.getEyeLocation(), 20, 0.4, 0.2, 0.4, 0, new Particle.DustOptions(Color.fromRGB(255, 220, 80), 1.2f));
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 0.5f, 1.4f);
    }

    private void volley(Player p) {
        Vector dir = p.getEyeLocation().getDirection();
        for (int i = -2; i <= 2; i++) {
            Arrow a = p.launchProjectile(Arrow.class, dir.clone().rotateAroundY(Math.toRadians(i * 7)).multiply(2.6));
            a.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
            a.addScoreboardTag("suld_volley");
            a.setCritical(true);
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.8f);
    }

    private void disengage(Player p, Spell s) {
        for (LivingEntity e : around(p, p.getLocation(), r(p, s, 5))) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            riders(p, e, s);
        }
        services.movement.launch(p, flat(p).multiply(-1.2).setY(0.45), "disengage", mn.suld.plugin.anticheat.MovementExemptions.DISENGAGE_TICKS);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 20, 0.5, 0.2, 0.5, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 1.2f);
    }

    private void beam(Player p, Spell s, double range, Particle fx, Sound sound) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult block = p.getWorld().rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        double len = block == null ? range : block.getHitPosition().distance(eye.toVector());
        Set<UUID> hit = new HashSet<>();
        for (double d = 0.5; d <= len; d += 0.5) {
            Location at = eye.clone().add(dir.clone().multiply(d));
            p.getWorld().spawnParticle(fx, at, 1, 0, 0, 0, 0);
            for (LivingEntity e : aroundBody(p, at, 1.2)) if (hit.add(e.getUniqueId())) hurt(p, e, s);
        }
        p.getWorld().playSound(eye, sound, 1f, 1.2f);
    }

    private void heal(Player p, Spell s, double hp, double radius) {
        double rr = r(p, s, radius);
        double amount = hp * (1 + buildOf(p).mod(s, ModKey.DAMAGE_PCT) / 100.0) * rankPower(p, s);
        for (Player ally : p.getWorld().getPlayers()) {
            if (ally.getLocation().distanceSquared(p.getLocation()) > rr * rr) continue;
            services.skillTree().healFrom(p, ally, amount);
            ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 2, 0), 5, 0.4, 0.3, 0.4);
        }
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
    }

    /** Онгоны Дуудлага's target: the nearest enemy within 20 blocks in front of the caster (cone of ~72°), or null. */
    private LivingEntity ongonTarget(Player p) {
        LivingEntity target = null;
        double best = 20 * 20;
        Vector dir = p.getEyeLocation().getDirection();
        for (LivingEntity e : around(p, p.getLocation(), 20)) {
            Vector to = e.getEyeLocation().toVector().subtract(p.getEyeLocation().toVector());
            double d = to.lengthSquared();
            if (d > 1e-6 && d < best && to.normalize().dot(dir) > 0.3) {
                best = d;
                target = e;
            }
        }
        return target;
    }

    private void ongon(Player p, Spell s) {
        LivingEntity target = ongonTarget(p);
        if (target == null) {
            // only an echo (12 ticks after the cast) or a target that died this tick gets here: the cast was checked to
            // have one before it was paid (castDirect0). No refund: an echo is free, and refunding it gave resource for nothing
            say(p, "§7Онгон зорилгогүй...");
            return;
        }
        LivingEntity t = target;
        new BukkitRunnable() {
            Location at = p.getEyeLocation();
            int n;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (!t.isValid() || n++ > 40) {
                    cancel();
                    return;
                }
                Vector step = t.getEyeLocation().toVector().subtract(at.toVector());
                if (step.length() < 0.8) {
                    hurt(p, t, s);
                    t.getWorld().spawnParticle(Particle.SOUL, t.getEyeLocation(), 15, 0.3, 0.3, 0.3, 0.05);
                    t.getWorld().playSound(t.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 1f);
                    cancel();
                    return;
                }
                at.add(step.normalize().multiply(0.8));
                at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at, 2, 0.05, 0.05, 0.05, 0);
                at.getWorld().spawnParticle(Particle.DUST, at, 2, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.fromRGB(170, 110, 255), 1f));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drum(Player p, Spell s) {
        new BukkitRunnable() {
            int beats;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (!p.isOnline() || beats++ >= 3) {
                    cancel();
                    return;
                }
                Location c = p.getLocation();
                for (LivingEntity e : around(p, c, r(p, s, 6))) {
                    hurt(p, e, s);
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
                }
                for (int i = 0; i < 24; i++) {
                    double a = Math.PI * 2 * i / 24;
                    c.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 5, 0.3, Math.sin(a) * 5), 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(170, 110, 255), 1.4f));
                }
                c.getWorld().playSound(c, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.2f, 0.7f);
            }
        }.runTaskTimer(plugin, 0L, 15L);
    }

    private Location targetPoint(Player p, double range) {
        RayTraceResult r = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), range, FluidCollisionMode.NEVER, true);
        return r == null ? p.getLocation().add(flat(p).multiply(8))
                : r.getHitPosition().toLocation(p.getWorld());
    }

    private void skyGate(Player p, Spell s) {
        Location c = targetPoint(p, 20);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (t++ < 20) {
                    for (int i = 0; i < 12; i++) {
                        double a = Math.PI * 2 * i / 12 + t * 0.2;
                        c.getWorld().spawnParticle(Particle.END_ROD, c.clone().add(Math.cos(a) * 4, 0.2 + t * 0.05, Math.sin(a) * 4), 1, 0, 0, 0, 0);
                    }
                    return;
                }
                for (LivingEntity e : around(p, c, r(p, s, 5))) hurt(p, e, s);
                for (Player ally : c.getWorld().getPlayers()) {
                    if (ally.getLocation().distanceSquared(c) < Math.pow(r(p, s, 6), 2)) services.skillTree().healFrom(p, ally, 6 * (1 + buildOf(p).mod(s, ModKey.DAMAGE_PCT) / 100.0) * rankPower(p, s));
                }
                c.getWorld().spawnParticle(Particle.FLASH, c, 1, 0, 0, 0, 0, Color.WHITE);
                c.getWorld().spawnParticle(Particle.DUST, c, 80, 2.5, 1, 2.5, 0, new Particle.DustOptions(Color.fromRGB(150, 235, 255), 1.6f));
                c.getWorld().playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static double burnOf(SkillBuild b, Spell s) {
        return b.mod(s, ModKey.BURN) + (b.has(KeystoneKind.ALTAN_DOSH) && s.damageMultiplier() > 0 ? 3 : 0);
    }

    /** The spell's own burning (ticks) plus whatever burning the player's nodes add to it; never shortens a fire already burning. */
    private void ignite(Player p, LivingEntity e, Spell s, int baseTicks) {
        int extra = (int) (burnOf(buildOf(p), s) * 20);
        e.setFireTicks(Math.max(e.getFireTicks(), baseTicks + extra));
    }

    private void slam(Player p, Spell s) {
        Location c = p.getLocation();
        for (LivingEntity e : around(p, c, r(p, s, 4))) {
            hurt(p, e, s);
            ignite(p, e, s, 60);
        }
        c.getWorld().spawnParticle(Particle.FLAME, c, 60, 2, 0.2, 2, 0.05);
        c.getWorld().spawnParticle(Particle.LAVA, c, 10, 1.5, 0.2, 1.5);
        c.getWorld().playSound(c, Sound.BLOCK_ANVIL_LAND, 0.9f, 0.7f);
    }

    private void moltenSpray(Player p, Spell s) {
        new BukkitRunnable() {
            int n;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (!p.isOnline() || n++ >= 3) {
                    cancel();
                    return;
                }
                Vector dir = flat(p);
                Location c = p.getLocation().add(0, 1, 0);
                for (LivingEntity e : around(p, c, r(p, s, 6))) {
                    Vector to = e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (to.lengthSquared() > 0.01 && to.normalize().dot(dir) > 0.6) {
                        hurt(p, e, s);
                        ignite(p, e, s, 40);
                    }
                }
                for (double d = 1; d < 6; d += 0.5) c.getWorld().spawnParticle(Particle.FLAME, c.clone().add(dir.clone().multiply(d)), 3, 0.3 * d / 3, 0.2, 0.3 * d / 3, 0.01);
                c.getWorld().playSound(c, Sound.ITEM_FIRECHARGE_USE, 0.7f, 1.2f);
            }
        }.runTaskTimer(plugin, 0L, 6L);
    }

    private void anvil(Player p, Spell s) {
        Location c = targetPoint(p, 20);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (t++ < 16) {
                    c.getWorld().spawnParticle(Particle.DUST, c.clone().add(0, 8 - t * 0.5, 0), 6, 0.4, 0.2, 0.4, 0,
                            new Particle.DustOptions(Color.fromRGB(110, 110, 120), 1.6f));
                    return;
                }
                for (LivingEntity e : around(p, c, r(p, s, 3.5))) hurt(p, e, s);
                c.getWorld().spawnParticle(Particle.EXPLOSION, c, 3, 0.5, 0.2, 0.5);
                c.getWorld().spawnParticle(Particle.LAVA, c, 15, 1.5, 0.3, 1.5);
                c.getWorld().playSound(c, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.5f);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void charge(Player p, Spell s) {
        services.movement.launch(p, flat(p).multiply(1.6).setY(0.15), "charge", mn.suld.plugin.anticheat.MovementExemptions.CHARGE_TICKS);
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (!p.isOnline() || t++ > 10) {
                    cancel();
                    return;
                }
                for (LivingEntity e : around(p, p.getLocation(), r(p, s, 1.9))) if (hit.add(e.getUniqueId())) hurt(p, e, s);
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 3, 0.2, 0.1, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_HORSE_GALLOP, 1f, 1.2f);
    }

    private void stampede(Player p, Spell s) {
        Vector dir = flat(p);
        Location start = p.getLocation();
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (t++ > 20) {
                    cancel();
                    return;
                }
                Location at = start.clone().add(dir.clone().multiply(t * 0.7));
                for (LivingEntity e : around(p, at, r(p, s, 2.2))) if (hit.add(e.getUniqueId())) {
                    hurt(p, e, s);
                    e.setVelocity(dir.clone().multiply(0.8).setY(0.4));
                }
                at.getWorld().spawnParticle(Particle.CLOUD, at, 6, 1.2, 0.3, 1.2, 0.02);
                at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 8, 1, 0.5, 1, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 230, 255), 1.4f));
                if (t % 4 == 0) at.getWorld().playSound(at, Sound.ENTITY_HORSE_GALLOP, 1f, 0.8f);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ resource + action bar

    private void regen() {
        for (Player p : Bukkit.getOnlinePlayers()) regenOne(p);
    }

    /** Player id -> {extra regeneration share, until ms}: Айраг's +10 % (IT-12), refreshed by the next flask. */
    private final Map<UUID, double[]> regenBoosts = new ConcurrentHashMap<>();

    /** Faster resource regeneration for {@code seconds}: a new boost replaces the old one (never stacks). */
    public void boostRegen(Player p, double share, int seconds) {
        regenBoosts.put(p.getUniqueId(), new double[] {share, System.currentTimeMillis() + seconds * 1000L});
    }

    private double regenBoost(Player p) {
        double[] b = regenBoosts.get(p.getUniqueId());
        if (b == null) return 0;
        if (b[1] < System.currentTimeMillis()) {
            regenBoosts.remove(p.getUniqueId(), b);
            return 0;
        }
        return b[0];
    }

    /** One second of resource regeneration for a player (also what the QA suite measures). */
    /** The player's armour mastery rank (perks of docs/ARMOR_PROGRESSION.md). */
    private int masteryRank(Player p) {
        return services.profiles().cached(p.getUniqueId()).map(pr -> pr.classGear().mastery()).orElse(0);
    }

    private double masteryGain(Player p, PlayerClass c) {
        return mn.suld.api.classgear.MasteryPerks.resourceGain(c, masteryRank(p));
    }

    public void regenOne(Player p) {
        PlayerClass c = clazzOf(p);
        if (c == null) return;
        double rate = switch (c) {
            case BAATAR -> 2;
            case MERGEN -> 5;
            case BOO -> 6;
            case DARKHAN -> 3;
            case KHULEGCHIN -> p.isSprinting() ? 9 : 4;
        };
        SkillBuild b = buildOf(p);
        // «Галт Ган» in a heroic run halves it (docs/HEROIC_DUNGEONS.md)
        double drought = services.heroic == null ? 1.0 : services.heroic.regenFactor(p);
        pool(p).gain((rate + b.stat(StatKey.RESOURCE_REGEN) + (b.has(KeystoneKind.TENGERTEI_KHOLBOGDOKH) ? 3 : 0))
                * mn.suld.api.classgear.MasteryPerks.resourceRegen(c, masteryRank(p)) * drought * (1 + regenBoost(p)));
        double hp = b.stat(StatKey.HEALTH_REGEN);
        if (hp > 0 && services.skillTree() != null) services.skillTree().regenerate(p, hp);
    }

}
