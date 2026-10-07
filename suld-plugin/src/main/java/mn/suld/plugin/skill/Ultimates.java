package mn.suld.plugin.skill;

import mn.suld.api.skill.tree.Ultimate;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.combat.CombatListener;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The fifteen ultimates. Each is a bounded effect (a short task that ends by itself, a hit cap per cast, capped
 * particle counts) so a crowded fight cannot overload the server. Damage is ATK-based and goes through
 * {@link SkillService#damage} so it never retriggers hit procs.
 */
final class Ultimates {

    private final Plugin plugin;
    private final SuldServices services;
    private final SkillService skills;
    private final SkillTreeService tree;
    /** Players whose reflective ultimate is active: id -> end time. */
    private final java.util.Map<UUID, Long> thorns = new java.util.concurrent.ConcurrentHashMap<>();

    Ultimates(Plugin plugin, SuldServices services, SkillService skills, SkillTreeService tree) {
        this.plugin = plugin;
        this.services = services;
        this.skills = skills;
        this.tree = tree;
    }

    void forget(UUID id) {
        thorns.remove(id);
    }

    /** Extra thorns percent while Бамбайн Хэрэм lasts. */
    double extraThorns(UUID id) {
        Long until = thorns.get(id);
        if (until == null) return 0;
        if (System.currentTimeMillis() > until) {
            thorns.remove(id);
            return 0;
        }
        return 30;
    }

    private double atk(Player p) {
        return CombatListener.attackOf(services, p) * tree.spellDamageMultiplier(p);
    }

    private void effect(Player p, PotionEffectType type, int seconds, int amplifier) {
        p.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, false, false, true));
    }

    void cast(Player p, Ultimate u) {
        switch (u) {
            case CHINGISIIN_UUR -> {
                effect(p, PotionEffectType.STRENGTH, 12, 1);
                effect(p, PotionEffectType.RESISTANCE, 12, 0);
                effect(p, PotionEffectType.REGENERATION, 12, 1);
                aura(p, Color.fromRGB(255, 60, 40), Sound.ENTITY_RAVAGER_ROAR, 0.6f);
            }
            case BUKHNII_NURAL -> {
                Location c = p.getLocation();
                for (LivingEntity e : skills.enemiesAround(p, c, 8)) {
                    skills.damage(p, e, atk(p) * 6);
                    e.setVelocity(new Vector(0, 0.9, 0));
                }
                ring(c, 8, Particle.EXPLOSION, 14);
                c.getWorld().spawnParticle(Particle.BLOCK, c, 80, 3, 0.2, 3, c.clone().subtract(0, 1, 0).getBlock().getBlockData());
                c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
            }
            case UKHEL_UNDER -> {
                effect(p, PotionEffectType.RESISTANCE, 8, 3);
                tree.shield(p, 20, 160);
                aura(p, Color.fromRGB(255, 220, 90), Sound.ITEM_TOTEM_USE, 1f);
            }
            case SUM_BORON -> arrowRain(p);
            case KHETIIN_KHARVAACH -> {
                effect(p, PotionEffectType.SPEED, 10, 1);
                CombatListener.EMPOWERED_ARROWS.put(p.getUniqueId(), 999);
                CombatListener.EMPOWERED_UNTIL.put(p.getUniqueId(), System.currentTimeMillis() + 10_000);
                aura(p, Color.fromRGB(120, 230, 120), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.3f);
            }
            case UKHLIIN_TEMDEG -> {
                int n = 0;
                for (LivingEntity e : skills.enemiesAround(p, p.getLocation(), 24)) {
                    if (n++ >= 30) break;
                    tree.mark(e, 40, 10);
                    e.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false));
                }
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.6f, 1.6f);
            }
            case OVGODIIN_ZALBIRAL -> {
                for (Player ally : p.getWorld().getPlayers()) {
                    if (ally.getLocation().distanceSquared(p.getLocation()) > 14 * 14) continue;
                    tree.healFrom(p, ally, 100);
                    for (PotionEffectType t : new PotionEffectType[]{PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.SLOWNESS,
                            PotionEffectType.WEAKNESS, PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA, PotionEffectType.HUNGER}) {
                        ally.removePotionEffect(t);
                    }
                    ally.setFireTicks(0);
                    ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 2, 0), 8, 0.5, 0.3, 0.5);
                }
                ring(p.getLocation(), 14, Particle.END_ROD, 30);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
            }
            case TENGERIIN_SHIITGEL -> {
                List<LivingEntity> targets = new ArrayList<>(skills.enemiesAround(p, p.getLocation(), 20));
                targets.sort((a, b) -> Double.compare(a.getLocation().distanceSquared(p.getLocation()), b.getLocation().distanceSquared(p.getLocation())));
                int i = 0;
                for (LivingEntity e : targets.subList(0, Math.min(8, targets.size()))) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!p.isOnline() || !e.isValid()) return;
                        e.getWorld().strikeLightningEffect(e.getLocation());
                        skills.damage(p, e, atk(p) * 5);
                    }, 6L * i++);
                }
            }
            case SUNSNII_KHUL -> {
                effect(p, PotionEffectType.SPEED, 8, 2);
                effect(p, PotionEffectType.RESISTANCE, 8, 1);
                effect(p, PotionEffectType.REGENERATION, 8, 2);
                aura(p, Color.fromRGB(170, 110, 255), Sound.PARTICLE_SOUL_ESCAPE, 1f);
            }
            case KHAILSAN_DALAI -> pulses(p, 5, 7, Particle.FLAME, 0.8, true);
            case BAMBAIN_KHEREM -> {
                effect(p, PotionEffectType.RESISTANCE, 8, 2);
                tree.shield(p, 16, 160);
                thorns.put(p.getUniqueId(), System.currentTimeMillis() + 8_000);
                aura(p, Color.fromRGB(190, 190, 200), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.8f);
            }
            case MYANGAN_ALKH -> anvils(p);
            case SHUURGA_DAVKHILT -> dashes(p);
            case SALKHINY_GEGEEN -> {
                effect(p, PotionEffectType.SPEED, 10, 3);
                effect(p, PotionEffectType.STRENGTH, 10, 0);
                effect(p, PotionEffectType.JUMP_BOOST, 10, 1);
                aura(p, Color.fromRGB(200, 230, 255), Sound.ENTITY_BREEZE_WIND_BURST, 1f);
            }
            case MYANGAN_MORI -> stampedes(p);
        }
    }

    private void aura(Player p, Color color, Sound sound, float pitch) {
        Location c = p.getLocation().add(0, 1, 0);
        p.getWorld().spawnParticle(Particle.DUST, c, 50, 0.7, 0.9, 0.7, 0, new Particle.DustOptions(color, 1.6f));
        p.getWorld().spawnParticle(Particle.FLASH, c, 1, 0, 0, 0, 0, Color.WHITE);
        p.getWorld().playSound(p.getLocation(), sound, 1f, pitch);
    }

    private void ring(Location c, double radius, Particle particle, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            c.getWorld().spawnParticle(particle, c.clone().add(Math.cos(a) * radius, 0.3, Math.sin(a) * radius), 1, 0, 0, 0, 0);
        }
    }

    private Location target(Player p, double range) {
        RayTraceResult r = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), range, FluidCollisionMode.NEVER, true);
        return r == null ? p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(10)) : r.getHitPosition().toLocation(p.getWorld());
    }

    private void arrowRain(Player p) {
        Location c = target(p, 28);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t++ >= 20) {
                    cancel();
                    return;
                }
                for (int i = 0; i < 3; i++) {
                    Location hit = c.clone().add(ThreadLocalRandom.current().nextDouble(-5, 5), 0, ThreadLocalRandom.current().nextDouble(-5, 5));
                    for (double h = 8; h > 0; h -= 2) hit.getWorld().spawnParticle(Particle.CRIT, hit.clone().add(0, h, 0), 1, 0, 0, 0, 0);
                    hit.getWorld().spawnParticle(Particle.ENCHANTED_HIT, hit, 6, 0.3, 0.1, 0.3, 0.1);
                    for (LivingEntity e : skills.enemiesAround(p, hit, 1.8)) skills.damage(p, e, atk(p) * 0.9);
                }
                if (t % 4 == 0) c.getWorld().playSound(c, Sound.ENTITY_ARROW_HIT, 0.8f, 1.2f);
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void pulses(Player p, int seconds, double radius, Particle fx, double mult, boolean burn) {
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t++ >= seconds * 2) {
                    cancel();
                    return;
                }
                Location c = p.getLocation();
                for (LivingEntity e : skills.enemiesAround(p, c, radius)) {
                    skills.damage(p, e, atk(p) * mult);
                    if (burn) e.setFireTicks(80);
                }
                ring(c, radius, fx, 24);
                c.getWorld().spawnParticle(Particle.LAVA, c, 4, radius / 2, 0.2, radius / 2);
                if (t % 2 == 0) c.getWorld().playSound(c, Sound.BLOCK_LAVA_POP, 1f, 0.8f);
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void anvils(Player p) {
        List<LivingEntity> enemies = new ArrayList<>(skills.enemiesAround(p, p.getLocation(), 18));
        new BukkitRunnable() {
            int n;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || n++ >= 10) {
                    cancel();
                    return;
                }
                Location at;
                if (!enemies.isEmpty()) {
                    LivingEntity e = enemies.get(ThreadLocalRandom.current().nextInt(enemies.size()));
                    at = e.isValid() ? e.getLocation() : p.getLocation().add(ThreadLocalRandom.current().nextDouble(-6, 6), 0, ThreadLocalRandom.current().nextDouble(-6, 6));
                } else {
                    at = p.getLocation().add(ThreadLocalRandom.current().nextDouble(-6, 6), 0, ThreadLocalRandom.current().nextDouble(-6, 6));
                }
                for (double h = 7; h > 0; h -= 1.5) at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, h, 0), 3, 0.2, 0.1, 0.2, 0, new Particle.DustOptions(Color.fromRGB(110, 110, 120), 1.6f));
                for (LivingEntity e : skills.enemiesAround(p, at, 2.5)) skills.damage(p, e, atk(p) * 3);
                at.getWorld().spawnParticle(Particle.EXPLOSION, at, 1);
                at.getWorld().playSound(at, Sound.BLOCK_ANVIL_LAND, 1f, 0.6f);
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private void dashes(Player p) {
        new BukkitRunnable() {
            int dash;
            int step = 99;
            final Set<UUID> hit = new HashSet<>();

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (step >= 9) {
                    if (dash++ >= 3) {
                        cancel();
                        return;
                    }
                    step = 0;
                    hit.clear();
                    p.setVelocity(p.getLocation().getDirection().setY(0).normalize().multiply(1.7).setY(0.1));
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_HORSE_GALLOP, 1f, 1.3f);
                }
                step++;
                for (LivingEntity e : skills.enemiesAround(p, p.getLocation(), 2.2)) {
                    if (hit.add(e.getUniqueId())) skills.damage(p, e, atk(p) * 2);
                }
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 4, 0.3, 0.1, 0.3, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void stampedes(Player p) {
        Location start = p.getLocation();
        List<Vector> dirs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            dirs.add(new Vector(Math.cos(a), 0, Math.sin(a)));
        }
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t++ > 14) {
                    cancel();
                    return;
                }
                for (Vector d : dirs) {
                    Location at = start.clone().add(d.clone().multiply(t * 0.8));
                    at.getWorld().spawnParticle(Particle.CLOUD, at, 3, 0.4, 0.2, 0.4, 0.02);
                    at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(Color.fromRGB(200, 230, 255), 1.4f));
                    for (LivingEntity e : skills.enemiesAround(p, at, 2.2)) {
                        if (hit.add(e.getUniqueId())) {
                            skills.damage(p, e, atk(p) * 2.5);
                            e.setVelocity(d.clone().multiply(0.8).setY(0.4));
                        }
                    }
                }
                if (t % 4 == 0) start.getWorld().playSound(start, Sound.ENTITY_HORSE_GALLOP, 1f, 0.8f);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
