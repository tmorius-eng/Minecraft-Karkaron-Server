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

        Fight(BossDefinition def, Consumer<PhaseChange> onPhase) {
            this.def = def;
            this.onPhase = onPhase;
        }
    }

    private final Map<UUID, Fight> fights = new HashMap<>();

    public void register(LivingEntity boss, BossDefinition def, Consumer<PhaseChange> onPhase) {
        fights.put(boss.getUniqueId(), new Fight(def, onPhase));
    }

    public void unregister(UUID bossId) {
        fights.remove(bossId);
    }

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

    /** Boss melee hits scale with the current phase (and are SÜLD-defined, not vanilla). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossAttack(EntityDamageByEntityEvent event) {
        Fight f = fights.get(event.getDamager().getUniqueId());
        if (f == null || !(event.getEntity() instanceof Player)) {
            return;
        }
        BossPhase phase = f.def.phases().get(f.phaseIndex);
        event.setDamage(f.def.mob().scaledAttack() * phase.attackMultiplier());
    }

    private void escalate(LivingEntity boss, Fight f, int targetIndex, boolean enraged) {
        if (targetIndex <= f.phaseIndex) {
            return; // phases never regress
        }
        f.phaseIndex = targetIndex;
        BossPhase phase = f.def.phases().get(targetIndex);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2f, 0.8f);
        if (targetIndex == f.def.finalPhaseIndex()) {
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        }
        f.onPhase.accept(new PhaseChange(boss, f.def, targetIndex, phase, enraged));
    }

    /** Remove all tracked fights (shutdown). */
    public void clear() {
        fights.clear();
    }
}
