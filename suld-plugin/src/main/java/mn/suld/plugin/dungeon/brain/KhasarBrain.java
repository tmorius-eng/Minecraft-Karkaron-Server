package mn.suld.plugin.dungeon.brain;

import mn.suld.api.mob.MobDefinition;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.dungeon.BossBrain;
import mn.suld.plugin.dungeon.BossService;
import mn.suld.plugin.model.ModelInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Хасар, the ancient cave wolf-beast (docs/bosses/KHASAR.md — ORIGINAL FICTION). Abilities by phase:
 * <ul>
 *   <li>all phases: <b>bite combo</b> (close, cone in front) and a telegraphed <b>pounce</b> (leap at a target 6–14
 *       blocks away, impact ring on landing);</li>
 *   <li>phase 2 (Уурласан): <b>stone-shatter roar</b> (cone knock-back, slow, rock debris);</li>
 *   <li>phase 3 (Галзуурсан): <b>howl</b> (two cave wolves answer) and <b>frenzy</b> (speed, ember eyes, frost breath).</li>
 * </ul>
 * Each ability plays its rig clip and lands on the clip's event tick ({@code bite_hit}, {@code pounce_land},
 * {@code roar_wave}, {@code howl}); without the clip (no rig) it lands after a fixed wind-up, so the fight works the
 * same either way. Particles stay well under the 80/tick budget.
 */
public final class KhasarBrain implements BossBrain {

    private final org.bukkit.plugin.Plugin plugin;
    private final SuldServices services;
    private final MobDefinition self;
    private final MobDefinition summon;
    private int t;            // half-seconds since the fight began
    private int nextBite = 2;
    private int nextPounce = 8;
    private int nextRoar = 10;
    private int nextHowl = 6;
    private boolean busy;
    private int busyUntil;

    public KhasarBrain(org.bukkit.plugin.Plugin plugin, SuldServices services, MobDefinition self, MobDefinition summon) {
        this.plugin = plugin;
        this.services = services;
        this.self = self;
        this.summon = summon;
    }

    @Override
    public void tick(LivingEntity boss, int phase, boolean enraged) {
        t++;
        if (busy && t >= busyUntil) busy = false;
        if (phase >= 2 && t % 2 == 0) frenzyFx(boss);
        if (busy) return;
        List<Player> near = targets(boss, 16);
        if (near.isEmpty()) return;
        Player target = near.get(0);
        double d = target.getLocation().distance(boss.getLocation());
        if (phase >= 2 && t >= nextHowl) {
            howl(boss);
            nextHowl = t + 50;
        } else if (phase >= 1 && t >= nextRoar && d <= 9) {
            roar(boss);
            nextRoar = t + 28;
        } else if (t >= nextPounce && d >= 6 && d <= 14) {
            pounce(boss, target);
            nextPounce = t + (phase >= 2 ? 14 : 20);
        } else if (t >= nextBite && d <= 4) {
            bite(boss, phase);
            nextBite = t + (phase >= 2 ? 4 : 6);
        }
    }

    @Override
    public void onPhase(LivingEntity boss, int phase, boolean enraged) {
        playOr(boss, "phase_change", 0, null);
        boss.getWorld().spawnParticle(Particle.BLOCK, boss.getLocation().add(0, 0.2, 0), 60, 2, 0.2, 2, 0, Material.STONE.createBlockData());
        if (phase >= 2) {
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
            for (Player p : targets(boss, 24)) p.sendActionBar(Component.text("Хасар галзуурлаа!", NamedTextColor.DARK_RED));
        }
    }

    // --------------------------------------------------------------------------------------------- abilities

    private void bite(LivingEntity boss, int phase) {
        hold(3);
        double dmg = self.scaledAttack() * (phase >= 2 ? 1.4 : 1.15);
        playOr(boss, "bite", 4, () -> {
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WOLF_GROWL, 1.6f, 0.5f);
            for (Player p : cone(boss, 4, 70)) hit(boss, p, dmg, 0.4);
        });
    }

    private void pounce(LivingEntity boss, Player target) {
        hold(5);
        Location land = target.getLocation().clone();
        // telegraph: a ring where Хасар will land, and a warning
        ring(land, 3, Particle.DUST, new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 40, 30), 1.6f), 28);
        target.sendActionBar(Component.text("⚠ Хасар үсрэх гэж байна — зайл!", NamedTextColor.RED));
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_STEP, 1.5f, 0.6f);
        double dmg = self.scaledAttack() * 1.6;
        playOr(boss, "pounce", 10, () -> {
            boss.getWorld().spawnParticle(Particle.BLOCK, land, 50, 1.6, 0.2, 1.6, 0, Material.COBBLESTONE.createBlockData());
            boss.getWorld().playSound(land, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.6f);
            for (Player p : targets(land, 3.5)) hit(boss, p, dmg, 0.9);
        });
        // the leap itself (the host carries the rig along)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!boss.isValid()) return;
            Vector v = land.toVector().subtract(boss.getLocation().toVector());
            double len = Math.max(1, v.length());
            boss.setVelocity(v.multiply(0.16).setY(0.55 + Math.min(0.35, len * 0.02)));
        }, 6L);
    }

    private void roar(LivingEntity boss) {
        hold(5);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2f, 0.55f);
        double dmg = self.scaledAttack() * 0.6;
        playOr(boss, "roar", 8, () -> {
            Vector fwd = boss.getLocation().getDirection().setY(0).normalize();
            for (int i = 1; i <= 8; i++) {
                Location l = boss.getLocation().add(fwd.clone().multiply(i)).add(0, 0.3, 0);
                boss.getWorld().spawnParticle(Particle.BLOCK, l, 6, i * 0.25, 0.3, i * 0.25, 0, Material.STONE.createBlockData());
            }
            for (Player p : cone(boss, 8, 60)) {
                hit(boss, p, dmg, 1.6);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            }
        });
    }

    private void howl(LivingEntity boss) {
        hold(6);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WOLF_ANGRY_AMBIENT, 2f, 0.45f);
        for (Player p : targets(boss, 24)) p.sendActionBar(Component.text("Хасар улилаа — агуйн чононууд ирж байна!", NamedTextColor.GOLD));
        playOr(boss, "howl", 12, () -> {
            if (summon == null) return;
            for (int i = 0; i < 2; i++) {
                Location l = boss.getLocation().add(i == 0 ? 3 : -3, 0, 2);
                LivingEntity wolf = services.mobs().spawn(summon, l);
                wolf.addScoreboardTag(mn.suld.plugin.dungeon.DungeonService.DUNGEON_TAG);
                boss.getWorld().spawnParticle(Particle.LARGE_SMOKE, l.add(0, 0.5, 0), 12, 0.3, 0.4, 0.3, 0.02);
            }
        });
    }

    private void frenzyFx(LivingEntity boss) {
        ModelInstance m = rig(boss);
        Location head = m != null ? m.bone("head") : boss.getEyeLocation();
        boss.getWorld().spawnParticle(Particle.DUST, head, 2, 0.12, 0.08, 0.12, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 120, 40), 0.9f));
        if (Math.random() < 0.3) boss.getWorld().spawnParticle(Particle.SNOWFLAKE, head, 3, 0.2, 0.1, 0.2, 0.01);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private ModelInstance rig(LivingEntity boss) {
        return services.models == null ? null : services.models.of(boss).orElse(null);
    }

    /** Play the rig clip and run {@code land} on its first event; without a rig, after {@code fallbackTicks}. */
    private void playOr(LivingEntity boss, String clip, int fallbackTicks, Runnable land) {
        boolean[] done = {false};
        boolean played = services.models != null && services.models.play(boss, clip, (inst, ev) -> {
            if (!done[0] && land != null && boss.isValid()) {
                done[0] = true;
                land.run();
            }
        });
        boolean hasEvents = played && rig(boss) != null && rig(boss).model().clip(clip) != null && !rig(boss).model().clip(clip).events().isEmpty();
        if (land != null && !hasEvents) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!done[0] && boss.isValid() && !boss.isDead()) {
                    done[0] = true;
                    land.run();
                }
            }, Math.max(1, fallbackTicks));
        }
    }

    private void hold(int halfSeconds) {
        busy = true;
        busyUntil = t + halfSeconds;
    }

    private void hit(LivingEntity boss, Player p, double dmg, double knock) {
        if (services.isSoul.test(p.getUniqueId())) return;
        BossService.abilityDamage = true;
        try {
            p.damage(dmg, boss);
        } finally {
            BossService.abilityDamage = false;
        }
        Vector away = p.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
        if (away.lengthSquared() > 1e-4) p.setVelocity(away.normalize().multiply(knock).setY(0.35 + knock * 0.1));
    }

    private static List<Player> targets(LivingEntity boss, double r) {
        return targets(boss.getLocation(), r).stream()
                .sorted(java.util.Comparator.comparingDouble(p -> p.getLocation().distanceSquared(boss.getLocation()))).toList();
    }

    private static List<Player> targets(Location at, double r) {
        List<Player> out = new ArrayList<>();
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.isDead()) continue;
            if (p.getLocation().distanceSquared(at) <= r * r) out.add(p);
        }
        return out;
    }

    /** Players within {@code r} blocks in front of the boss inside a {@code deg}-degree cone. */
    private static List<Player> cone(LivingEntity boss, double r, double deg) {
        Vector fwd = boss.getLocation().getDirection().setY(0).normalize();
        double cos = Math.cos(Math.toRadians(deg / 2));
        List<Player> out = new ArrayList<>();
        for (Player p : targets(boss.getLocation(), r)) {
            Vector to = p.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
            if (to.lengthSquared() < 1.5 || to.normalize().dot(fwd) >= cos) out.add(p);
        }
        return out;
    }

    private static void ring(Location c, double r, Particle p, Object data, int points) {
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            c.getWorld().spawnParticle(p, c.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r), 1, 0, 0, 0, 0, data);
        }
    }
}
