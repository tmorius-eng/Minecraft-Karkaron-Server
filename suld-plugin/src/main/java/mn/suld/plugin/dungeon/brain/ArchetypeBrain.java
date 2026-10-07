package mn.suld.plugin.dungeon.brain;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import mn.suld.api.mob.MobDefinition;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.combat.CombatListener;
import mn.suld.plugin.dungeon.BossBrain;
import mn.suld.plugin.dungeon.BossService;
import mn.suld.plugin.dungeon.DungeonService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Abilities for every boss that has no hand-written brain (docs/bosses/BOSS_ABILITIES.md): a boss is a set of
 * archetype abilities plus a flavour (colour, particle, sound, the mob it summons, the status it inflicts). Every
 * ability is telegraphed before it lands (a red ring decal on the ground or a wind-up of the rig's attack clip), so a
 * player who watches can always dodge; damage comes from the boss's scaled attack, applied through
 * {@link BossService#abilityDamage} like Хасар's.
 * <ul>
 *   <li>CLEAVE: a frontal 100° swing within 5 blocks, on the rig's attack event.</li>
 *   <li>SLAM: a ring appears under the farthest player; 1.5 s later everything inside 3.5 blocks is struck and lifted.</li>
 *   <li>CHARGE: the boss dashes at a player 6–14 blocks away and strikes whoever stands in its path.</li>
 *   <li>BARRAGE: 3–5 rings under players and around them; 1.2 s later each bursts (ice spikes, lightning, water).</li>
 *   <li>NOVA (from phase 2): a burst around the boss with the flavour's status (slowness, blindness, pull or push).</li>
 *   <li>SUMMON (from phase 2): two adds, never more than four alive, owned by the run.</li>
 * </ul>
 */
public final class ArchetypeBrain implements BossBrain {

    public enum Ability { CLEAVE, SLAM, CHARGE, BARRAGE, NOVA, SUMMON }

    public enum Status { NONE, SLOW, BLIND, PULL, PUSH }

    /** How a boss looks and feels when it uses its abilities. */
    public record Flavour(String title, Set<Ability> abilities, int rgb, Particle particle, Material dust, Sound sound,
                          Status status, MobDefinition summon) {
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final MobDefinition self;
    private final Flavour f;
    private int t;
    private int busyUntil;
    private final int[] next = new int[Ability.values().length];
    private final List<ItemDisplay> decals = new ArrayList<>();

    public ArchetypeBrain(Plugin plugin, SuldServices services, MobDefinition self, Flavour flavour) {
        this.plugin = plugin;
        this.services = services;
        this.self = self;
        this.f = flavour;
        next[Ability.CLEAVE.ordinal()] = 3;
        next[Ability.SLAM.ordinal()] = 8;
        next[Ability.CHARGE.ordinal()] = 12;
        next[Ability.BARRAGE.ordinal()] = 10;
        next[Ability.NOVA.ordinal()] = 6;
        next[Ability.SUMMON.ordinal()] = 4;
    }

    public static Set<Ability> of(Ability... a) {
        return a.length == 0 ? EnumSet.noneOf(Ability.class) : EnumSet.of(a[0], a);
    }

    private boolean has(Ability a) {
        return f.abilities().contains(a);
    }

    private boolean ready(Ability a) {
        return has(a) && t >= next[a.ordinal()];
    }

    private void cool(Ability a, int halfSeconds, int phase) {
        next[a.ordinal()] = t + Math.max(2, phase >= 2 ? (int) (halfSeconds * 0.7) : halfSeconds);
    }

    @Override
    public void tick(LivingEntity boss, int phase, boolean enraged) {
        t++;
        if (t < busyUntil) return;
        List<Player> near = targets(boss.getLocation(), 22);
        if (near.isEmpty()) return;
        near.sort(java.util.Comparator.comparingDouble(p -> p.getLocation().distanceSquared(boss.getLocation())));
        Player closest = near.get(0), farthest = near.get(near.size() - 1);
        double d = closest.getLocation().distance(boss.getLocation());
        if (phase >= 1 && ready(Ability.SUMMON)) {
            summon(boss);
            cool(Ability.SUMMON, 40, phase);
        } else if (phase >= 1 && ready(Ability.NOVA) && d <= 7) {
            nova(boss);
            cool(Ability.NOVA, 22, phase);
        } else if (ready(Ability.BARRAGE)) {
            barrage(boss, near, phase);
            cool(Ability.BARRAGE, 18, phase);
        } else if (ready(Ability.CHARGE) && farthest.getLocation().distance(boss.getLocation()) >= 6) {
            charge(boss, farthest);
            cool(Ability.CHARGE, 20, phase);
        } else if (ready(Ability.SLAM)) {
            slam(boss, farthest);
            cool(Ability.SLAM, 16, phase);
        } else if (ready(Ability.CLEAVE) && d <= 5) {
            cleave(boss, phase);
            cool(Ability.CLEAVE, 6, phase);
        }
    }

    @Override
    public void onPhase(LivingEntity boss, int phase, boolean enraged) {
        boss.getWorld().playSound(boss.getLocation(), f.sound(), 2f, 0.6f);
        burst(boss.getLocation().add(0, 1, 0), 60, 1.6);
        if (phase >= 2) boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        for (Player p : targets(boss.getLocation(), 28)) {
            warn(p, Component.text(f.title() + (enraged ? " галзуурлаа!" : phase >= 2 ? " бүх хүчээ гаргалаа!" : " уурлалаа!"), NamedTextColor.DARK_RED));
        }
    }

    @Override
    public void end(LivingEntity boss) {
        for (ItemDisplay d : decals) d.remove();
        decals.clear();
    }

    // ------------------------------------------------------------------ abilities

    private void cleave(LivingEntity boss, int phase) {
        busyUntil = t + 3;
        double dmg = self.scaledAttack() * (phase >= 2 ? 1.35 : 1.1);
        play(boss, "attack", 7, () -> {
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.6f);
            Vector fwd = boss.getLocation().getDirection().setY(0).normalize();
            for (int i = 1; i <= 4; i++) burst(boss.getLocation().add(fwd.clone().multiply(i)).add(0, 1, 0), 4, 0.6);
            for (Player p : cone(boss, 5, 100)) hit(boss, p, dmg, 0.6);
        });
    }

    private void slam(LivingEntity boss, Player target) {
        busyUntil = t + 4;
        Location at = target.getLocation().clone();
        telegraph(at, 3.5, 30);
        warn(target, Component.text("⚠ Хөл доор чинь цохилт ирж байна — зайл!", NamedTextColor.RED));
        double dmg = self.scaledAttack() * 1.5;
        later(30, () -> {
            boss.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.7f);
            burst(at.clone().add(0, 0.3, 0), 50, 1.8);
            for (Player p : targets(at, 3.5)) {
                hit(boss, p, dmg, 0.3);
                p.setVelocity(p.getVelocity().setY(0.7));
            }
        });
    }

    private void charge(LivingEntity boss, Player target) {
        busyUntil = t + 5;
        Location from = boss.getLocation();
        Vector dir = target.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (dir.lengthSquared() < 1) return;
        double len = Math.min(14, dir.length());
        dir.normalize();
        // the lane is drawn first: a line of dust where it will run
        for (int i = 1; i <= (int) len; i++) dust(from.clone().add(dir.clone().multiply(i)).add(0, 0.2, 0), 3);
        warn(target, Component.text("⚠ " + f.title() + " давхиж ирж байна!", NamedTextColor.RED));
        boss.getWorld().playSound(from, Sound.ENTITY_RAVAGER_ROAR, 1.4f, 0.8f);
        double dmg = self.scaledAttack() * 1.4;
        later(20, () -> {
            if (!boss.isValid()) return;
            boss.setVelocity(dir.clone().multiply(Math.min(2.2, 0.18 * len + 0.6)).setY(0.15));
            Set<java.util.UUID> struck = new java.util.HashSet<>();
            for (int k = 1; k <= 8; k++) {
                later(k * 2, () -> {
                    if (!boss.isValid()) return;
                    burst(boss.getLocation().add(0, 0.5, 0), 6, 0.5);
                    for (Player p : targets(boss.getLocation(), 2.2)) if (struck.add(p.getUniqueId())) hit(boss, p, dmg, 1.2);
                });
            }
        });
    }

    private void barrage(LivingEntity boss, List<Player> near, int phase) {
        busyUntil = t + 4;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int n = Math.min(5, 2 + near.size() + (phase >= 2 ? 1 : 0));
        List<Location> spots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Player p = near.get(i % near.size());
            Location l = p.getLocation().clone().add(i < near.size() ? 0 : r.nextDouble(-4, 4), 0, i < near.size() ? 0 : r.nextDouble(-4, 4));
            spots.add(l);
            telegraph(l, 2.2, 24);
        }
        double dmg = self.scaledAttack() * 1.2;
        play(boss, "attack", 0, null);
        later(24, () -> {
            for (Location l : spots) {
                burst(l.clone().add(0, 0.5, 0), 30, 1.0);
                if (f.particle() == Particle.ELECTRIC_SPARK) l.getWorld().strikeLightningEffect(l);
                l.getWorld().playSound(l, f.sound(), 0.8f, 1.4f);
                for (Player p : targets(l, 2.2)) hit(boss, p, dmg, 0.4);
            }
        });
    }

    private void nova(LivingEntity boss) {
        busyUntil = t + 4;
        Location c = boss.getLocation();
        telegraph(c, 7, 24);
        for (Player p : targets(c, 9)) warn(p, Component.text("⚠ " + f.title() + " хүчээ цуглуулж байна!", NamedTextColor.GOLD));
        double dmg = self.scaledAttack() * 0.9;
        later(24, () -> {
            if (!boss.isValid()) return;
            boss.getWorld().playSound(boss.getLocation(), f.sound(), 2f, 0.5f);
            for (int i = 0; i < 3; i++) burst(boss.getLocation().add(0, 0.5 + i * 0.6, 0), 40, 2.5 + i);
            for (Player p : targets(boss.getLocation(), 7)) {
                hit(boss, p, dmg, 0);
                Vector to = boss.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                switch (f.status()) {
                    case SLOW -> p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
                    case BLIND -> p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                    case PULL -> {
                        if (to.lengthSquared() > 1) p.setVelocity(to.normalize().multiply(0.9).setY(0.2));
                    }
                    case PUSH -> {
                        if (to.lengthSquared() > 1e-3) p.setVelocity(to.normalize().multiply(-1.4).setY(0.45));
                    }
                    default -> {
                    }
                }
            }
        });
    }

    private void summon(LivingEntity boss) {
        if (f.summon() == null) return;
        busyUntil = t + 3;
        long alive = boss.getWorld().getNearbyEntities(boss.getLocation(), 32, 16, 32,
                e -> e.isValid() && e.getScoreboardTags().contains(CombatListener.SUMMON_TAG)).size();
        if (alive >= 4) return;
        for (Player p : targets(boss.getLocation(), 28)) warn(p, Component.text(f.title() + " туслагчдаа дуудлаа!", NamedTextColor.GOLD));
        boss.getWorld().playSound(boss.getLocation(), f.sound(), 1.6f, 0.8f);
        for (int i = 0; i < Math.min(2, 4 - alive); i++) {
            Location l = boss.getLocation().add(i == 0 ? 3 : -3, 0, 2);
            burst(l.clone().add(0, 0.6, 0), 20, 0.6);
            LivingEntity add = services.mobs().spawn(f.summon(), l);
            add.addScoreboardTag(DungeonService.DUNGEON_TAG);
            add.addScoreboardTag(CombatListener.SUMMON_TAG);
            services.dungeons().adopt(boss.getUniqueId(), add);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** A red ring decal on the ground for {@code ticks}, sized to the danger radius, plus a particle rim. */
    private void telegraph(Location at, double radius, int ticks) {
        Location l = at.clone();
        l.setYaw(0);
        l.setPitch(0);
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("suld", "entity/vfx/ring"));
        it.setItemMeta(meta);
        it.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addColor(Color.fromRGB(0xE03A2A)).build());
        float s = (float) (radius * 2);
        ItemDisplay d = l.getWorld().spawn(l.add(0, 0.08, 0), ItemDisplay.class, e -> {
            e.setItemStack(it);
            e.setPersistent(false);
            e.addScoreboardTag(mn.suld.plugin.combat.CombatFeel.TAG);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s * 0.3f, 1, s * 0.3f), new Quaternionf()));
        });
        decals.add(d);
        later(1, () -> {
            if (!d.isValid()) return;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(Math.max(1, ticks - 2));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, 1, s), new Quaternionf()));
        });
        later(ticks, () -> {
            d.remove();
            decals.remove(d);
        });
        for (int i = 0; i < 24; i++) {
            double a = 2 * Math.PI * i / 24;
            dust(at.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius), 1);
        }
    }

    private void burst(Location at, int count, double spread) {
        if (f.dust() != null) {
            at.getWorld().spawnParticle(Particle.BLOCK, at, count, spread, 0.3, spread, 0, f.dust().createBlockData());
        }
        at.getWorld().spawnParticle(f.particle(), at, Math.max(4, count / 3), spread * 0.6, 0.4, spread * 0.6, 0.02);
    }

    private void dust(Location at, int count) {
        at.getWorld().spawnParticle(Particle.DUST, at, count, 0.05, 0, 0.05, 0, new Particle.DustOptions(Color.fromRGB(f.rgb()), 1.4f));
    }

    private void play(LivingEntity boss, String clip, int fallbackTicks, Runnable land) {
        boolean[] done = {false};
        boolean played = services.models != null && services.models.play(boss, clip, (inst, ev) -> {
            if (!done[0] && land != null && boss.isValid()) {
                done[0] = true;
                land.run();
            }
        });
        if (land != null && !played) {
            later(Math.max(1, fallbackTicks), () -> {
                if (!done[0] && boss.isValid() && !boss.isDead()) {
                    done[0] = true;
                    land.run();
                }
            });
        }
    }

    private void later(long ticks, Runnable r) {
        plugin.getServer().getScheduler().runTaskLater(plugin, r, ticks);
    }

    private void warn(Player p, Component message) {
        if (services.hud() != null) services.hud().toast(p, message, 1800);
        else p.sendActionBar(message);
    }

    private void hit(LivingEntity boss, Player p, double dmg, double knock) {
        if (!boss.isValid() || services.isSoul.test(p.getUniqueId())) return;
        BossService.abilityDamage = true;
        try {
            p.damage(dmg, boss);
        } finally {
            BossService.abilityDamage = false;
        }
        Vector away = p.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
        if (knock > 0 && away.lengthSquared() > 1e-4) p.setVelocity(away.normalize().multiply(knock).setY(0.3 + knock * 0.1));
    }

    private static List<Player> targets(Location at, double r) {
        List<Player> out = new ArrayList<>();
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.isDead()) continue;
            if (p.getLocation().distanceSquared(at) <= r * r) out.add(p);
        }
        return out;
    }

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
}
