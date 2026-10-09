package mn.suld.plugin.dungeon;

import mn.suld.api.dungeon.BossDefinition;
import mn.suld.api.dungeon.BossPhase;
import mn.suld.plugin.mob.MobService;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Boss combat mechanics: HP-driven phase escalation, per-phase damage scaling,
 * and the enrage timer. Phase selection itself is pure ({@link BossDefinition});
 * this class only wires it to live entities. Phases only ever escalate.
 */
public final class BossService implements Listener {

    /** Fired when a boss enters a new phase (or is force-enraged). */
    public record PhaseChange(LivingEntity boss, BossDefinition def, int index, BossPhase phase, boolean enraged) {
    }

    /** Snapshot for HUD/boss bar. */
    public record Status(double hpFraction, int phaseIndex, String phaseName, boolean enraged) {
    }

    private static final class Fight {
        final BossDefinition def;
        final long startedMillis = System.currentTimeMillis();
        final Consumer<PhaseChange> onPhase;
        int phaseIndex = 0;
        boolean enraged = false;
        BossBrain brain;

        Fight(BossDefinition def, Consumer<PhaseChange> onPhase) {
            this.def = def;
            this.onPhase = onPhase;
        }
    }

    private final Map<UUID, Fight> fights = new HashMap<>();

    /** Abilities per boss mob id (registered by content; a boss without one fights with phases only). */
    private final Map<String, java.util.function.Function<LivingEntity, BossBrain>> brains = new HashMap<>();

    public void brain(String bossMobId, java.util.function.Function<LivingEntity, BossBrain> factory) {
        brains.put(bossMobId, factory);
    }

    public void register(LivingEntity boss, BossDefinition def, Consumer<PhaseChange> onPhase) {
        Fight f = new Fight(def, onPhase);
        var factory = brains.get(def.mob().id());
        if (factory != null) f.brain = factory.apply(boss);
        fights.put(boss.getUniqueId(), f);
    }

    public void unregister(UUID bossId) {
        Fight f = fights.remove(bossId);
        if (f != null && f.brain != null) {
            org.bukkit.entity.Entity e = org.bukkit.Bukkit.getEntity(bossId);
            if (e instanceof LivingEntity le) f.brain.end(le);
        }
    }

    /**
     * Set while a brain applies ability damage: the melee override below keeps its hands off, so an ability can hit
     * harder or softer than the phase's melee. Main thread.
     */
    public static boolean abilityDamage;

    public boolean isBoss(UUID entityId) {
        return fights.containsKey(entityId);
    }

    public Optional<Status> status(LivingEntity boss) {
        Fight f = fights.get(boss.getUniqueId());
        if (f == null) {
            return Optional.empty();
        }
        double max = MobService.maxHealth(boss);
        double frac = max <= 0 ? 0 : Math.max(0, Math.min(1, boss.getHealth() / max));
        return Optional.of(new Status(frac, f.phaseIndex, f.def.phases().get(f.phaseIndex).phaseName(), f.enraged));
    }

    /** Called about twice a second by the owning dungeon run: enforces the enrage timer. */
    public void tick(LivingEntity boss) {
        Fight f = fights.get(boss.getUniqueId());
        if (f != null && f.brain != null && boss.isValid() && !boss.isDead()) f.brain.tick(boss, f.phaseIndex, f.enraged);
        if (f == null || f.enraged || f.def.enrageSeconds() <= 0) {
            return;
        }
        long elapsed = (System.currentTimeMillis() - f.startedMillis) / 1000;
        if (elapsed >= f.def.enrageSeconds()) {
            f.enraged = true;
            escalate(boss, f, f.def.finalPhaseIndex(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDamaged(EntityDamageEvent event) {
        Fight f = fights.get(event.getEntity().getUniqueId());
        if (f == null || !(event.getEntity() instanceof LivingEntity boss)) {
            return;
        }
        double max = MobService.maxHealth(boss);
        if (max <= 0) {
            return;
        }
        double after = Math.max(0, boss.getHealth() - event.getFinalDamage());
        if (after <= 0) {
            return; // dying: the death handler takes over
        }
        escalate(boss, f, f.def.activePhaseIndex(after / max), false);
    }

    /**
     * Boss melee hits scale with the current phase (and are SÜLD-defined, not vanilla). NORMAL priority: this sets the
     * base hit, and the player's reductions and dodge (SkillTreeService, HIGH) apply to it afterwards.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBossAttack(EntityDamageByEntityEvent event) {
        Fight f = fights.get(event.getDamager().getUniqueId());
        if (f == null || !(event.getEntity() instanceof Player) || abilityDamage) {
            return;
        }
        BossPhase phase = f.def.phases().get(f.phaseIndex);
        event.setDamage(f.def.mob().scaledAttack() * phase.attackMultiplier() * (f.enraged ? ENRAGE_MULTIPLIER : 1.0));
    }

    /** Enrage (the fight ran past its time limit) hits this much harder on top of the phase, in any phase. */
    public static final double ENRAGE_MULTIPLIER = 1.25;

    private void escalate(LivingEntity boss, Fight f, int targetIndex, boolean enraged) {
        if (targetIndex <= f.phaseIndex && !enraged) {
            return; // phases never regress
        }
        // enrage also fires when the boss is already in its final phase (it used to do nothing there)
        f.phaseIndex = Math.max(f.phaseIndex, targetIndex);
        targetIndex = f.phaseIndex;
        BossPhase phase = f.def.phases().get(targetIndex);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2f, 0.8f);
        if (targetIndex == f.def.finalPhaseIndex()) {
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        }
        f.onPhase.accept(new PhaseChange(boss, f.def, targetIndex, phase, enraged));
        if (f.brain != null) f.brain.onPhase(boss, targetIndex, enraged);
    }

    /** Remove all tracked fights (shutdown). */
    public void clear() {
        fights.clear();
    }
}
