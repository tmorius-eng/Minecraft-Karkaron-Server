package mn.suld.plugin.style;

import mn.suld.api.style.Cosmetic;
import mn.suld.api.style.PlayerStyle;
import mn.suld.plugin.SuldServices;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Particle cosmetics — purely visual, they change nothing in the game. An <b>aura</b> circles the player, a
 * <b>trail</b> is left where they walk or ride, a <b>kill effect</b> plays where a SÜLD monster falls. Each
 * cosmetic's {@code style} names its effect (the keys below). {@link #preview} lets a player try one before buying.
 */
public final class CosmeticEffects implements Listener {

    private static final Color GOLD = Color.fromRGB(255, 210, 74);
    private static final Color SKY = Color.fromRGB(80, 170, 255);
    private static final Color DUST = Color.fromRGB(176, 140, 96);

    private final Plugin plugin;
    private final SuldServices services;
    private final Map<UUID, Location> lastSeen = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> previewing = new ConcurrentHashMap<>();
    private long tick;

    public CosmeticEffects(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::run, 40L, 3L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastSeen.remove(e.getPlayer().getUniqueId());
        previewing.remove(e.getPlayer().getUniqueId());
    }

    private boolean visible(Player p) {
        return p.isValid() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR && !p.hasPotionEffect(PotionEffectType.INVISIBILITY)
                && !services.isSoul.test(p.getUniqueId());
    }

    private void run() {
        tick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerStyle st = services.styles().cached(p.getUniqueId()).orElse(null);
            if (st == null || !visible(p)) continue;
            String aura = st.equipped(Cosmetic.Category.AURA).map(Cosmetic::style).orElse(null);
            String trail = st.equipped(Cosmetic.Category.TRAIL).map(Cosmetic::style).orElse(null);
            if (aura != null) aura(p, aura);
            if (trail != null) trail(p, trail);
            lastSeen.put(p.getUniqueId(), p.getLocation());
        }
    }

    // ------------------------------------------------------------------ aura

    private void aura(Player p, String key) {
        Location base = p.getLocation();
        World w = base.getWorld();
        double t = tick * 0.42;
        for (int i = 0; i < 3; i++) {
            double ang = t + i * Math.PI * 2 / 3;
            double h = 1.0 + 0.75 * Math.sin(t * 0.6 + i * 1.7);
            Location at = base.clone().add(Math.cos(ang) * 0.9, h, Math.sin(ang) * 0.9);
            switch (key) {
                case "tal" -> w.spawnParticle(Particle.HAPPY_VILLAGER, at, 1, 0.05, 0.05, 0.05, 0);
                case "od" -> w.spawnParticle(Particle.END_ROD, at, 1, 0.02, 0.02, 0.02, 0.0);
                case "gal" -> {
                    w.spawnParticle(Particle.FLAME, at, 1, 0.03, 0.03, 0.03, 0.002);
                    if (i == 0 && tick % 4 == 0) w.spawnParticle(Particle.SMALL_FLAME, base.clone().add(0, 0.2, 0), 2, 0.3, 0.05, 0.3, 0.001);
                }
                case "tsas" -> w.spawnParticle(Particle.SNOWFLAKE, at, 2, 0.12, 0.12, 0.12, 0.0);
                case "altan" -> {
                    w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(GOLD, 1.1f));
                    if (i == 0 && tick % 5 == 0) w.spawnParticle(Particle.GLOW, at, 1, 0.1, 0.1, 0.1, 0);
                }
                case "tenger" -> {
                    w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(SKY, 1.2f));
                    if (i == 1) w.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 1, 0.02, 0.02, 0.02, 0.0);
                    if (i == 2 && tick % 3 == 0) w.spawnParticle(Particle.CLOUD, base.clone().add(0, 0.1, 0), 1, 0.3, 0.02, 0.3, 0.0);
                }
                case "suld" -> {
                    w.spawnParticle(Particle.DRAGON_BREATH, at, 1, 0.05, 0.05, 0.05, 0.0);
                    if (i == 0) w.spawnParticle(Particle.DUST, base.clone().add(0, 2.2 + 0.1 * Math.sin(t), 0), 1, 0, 0, 0, 0, new Particle.DustOptions(GOLD, 1.4f));
                }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------ trail

    private void trail(Player p, String key) {
        Location now = p.getLocation();
        Location before = lastSeen.get(p.getUniqueId());
        boolean moving = before != null && before.getWorld() == now.getWorld() && before.distanceSquared(now) > 0.03;
        Entity vehicle = p.getVehicle();
        Location feet = (vehicle != null ? vehicle.getLocation() : now).clone().add(0, 0.15, 0);
        if (!moving && vehicle == null) return;
        if (vehicle != null && before != null && before.getWorld() == now.getWorld() && before.distanceSquared(now) <= 0.03) return;
        World w = feet.getWorld();
        spawnTrail(w, feet, key);
    }

    private void spawnTrail(World w, Location feet, String key) {
        switch (key) {
            case "shuurkhai" -> w.spawnParticle(Particle.DUST, feet, 3, 0.25, 0.05, 0.25, 0, new Particle.DustOptions(DUST, 1.5f));
            case "tal" -> w.spawnParticle(Particle.HAPPY_VILLAGER, feet, 2, 0.25, 0.1, 0.25, 0);
            case "od" -> w.spawnParticle(Particle.END_ROD, feet, 2, 0.2, 0.05, 0.2, 0.01);
            case "gal" -> {
                w.spawnParticle(Particle.FLAME, feet, 2, 0.2, 0.02, 0.2, 0.002);
                w.spawnParticle(Particle.SMALL_FLAME, feet, 2, 0.25, 0.02, 0.25, 0.001);
            }
            case "tsas" -> {
                w.spawnParticle(Particle.SNOWFLAKE, feet, 3, 0.25, 0.05, 0.25, 0.0);
                w.spawnParticle(Particle.CLOUD, feet, 1, 0.2, 0.02, 0.2, 0.0);
            }
            case "zurkh" -> w.spawnParticle(Particle.HEART, feet.clone().add(0, 0.3, 0), 1, 0.3, 0.1, 0.3, 0);
            case "altan" -> {
                w.spawnParticle(Particle.DUST, feet, 3, 0.25, 0.05, 0.25, 0, new Particle.DustOptions(GOLD, 1.2f));
                w.spawnParticle(Particle.GLOW, feet, 1, 0.2, 0.05, 0.2, 0);
            }
            case "tenger" -> {
                w.spawnParticle(Particle.DUST, feet, 3, 0.25, 0.05, 0.25, 0, new Particle.DustOptions(SKY, 1.3f));
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, feet, 1, 0.2, 0.02, 0.2, 0.002);
                w.spawnParticle(Particle.CLOUD, feet, 1, 0.2, 0.02, 0.2, 0.0);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ kill effects

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();
        Player killer = mob.getKiller();
        if (killer == null || !services.mobs().isSuldMob(mob)) return;
        services.styles().cached(killer.getUniqueId()).flatMap(s -> s.equipped(Cosmetic.Category.KILL_EFFECT))
                .ifPresent(c -> kill(mob.getLocation().add(0, mob.getHeight() / 2, 0), c.style()));
    }

    private void kill(Location at, String key) {
        World w = at.getWorld();
        switch (key) {
            case "salyut" -> {
                w.spawnParticle(Particle.FIREWORK, at, 40, 0.5, 0.6, 0.5, 0.12);
                w.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.1f);
                w.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1.0f);
            }
            case "suns" -> {
                w.spawnParticle(Particle.SOUL, at, 18, 0.35, 0.3, 0.35, 0.06);
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 8, 0.3, 0.2, 0.3, 0.05);
                w.playSound(at, Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.9f);
            }
            case "gal" -> {
                w.spawnParticle(Particle.FLAME, at, 40, 0.4, 0.4, 0.4, 0.08);
                w.spawnParticle(Particle.LAVA, at, 6, 0.3, 0.3, 0.3, 0);
                w.playSound(at, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.8f);
            }
            case "tsas" -> {
                w.spawnParticle(Particle.SNOWFLAKE, at, 50, 0.45, 0.45, 0.45, 0.08);
                w.spawnParticle(Particle.CLOUD, at, 10, 0.3, 0.3, 0.3, 0.04);
                w.playSound(at, Sound.BLOCK_GLASS_BREAK, 0.7f, 1.4f);
            }
            case "altan" -> {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (int i = 0; i < 24; i++) {
                    w.spawnParticle(Particle.DUST, at.clone().add(r.nextDouble(-0.6, 0.6), 1.6 - i * 0.05, r.nextDouble(-0.6, 0.6)),
                            1, 0, 0, 0, 0, new Particle.DustOptions(GOLD, 1.3f));
                }
                w.spawnParticle(Particle.GLOW, at, 14, 0.4, 0.5, 0.4, 0.02);
                w.playSound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
            }
            case "tenger" -> {
                for (int i = 0; i < 14; i++) w.spawnParticle(Particle.END_ROD, at.clone().add(0, i * 0.3, 0), 2, 0.08, 0, 0.08, 0.0);
                w.spawnParticle(Particle.DUST, at, 20, 0.5, 0.5, 0.5, 0, new Particle.DustOptions(SKY, 1.4f));
                w.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.5f);
                w.playSound(at, Sound.ITEM_TRIDENT_THUNDER, 0.5f, 1.6f);
            }
            case "tsakhilgaan" -> {
                w.strikeLightningEffect(at);
                w.spawnParticle(Particle.ELECTRIC_SPARK, at, 30, 0.5, 0.6, 0.5, 0.2);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ preview

    /** Plays the cosmetic around the player for a few seconds (not available to a soul or in a dungeon run). */
    public void preview(Player p, Cosmetic c) {
        if (!visible(p) || services.dungeons().isInAnyRun(p.getUniqueId())) return;
        Cosmetic.Category cat = c.category();
        if (cat == Cosmetic.Category.KILL_EFFECT) {
            Location front = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(3)).add(0, 1, 0);
            kill(front, c.style());
            return;
        }
        if (cat != Cosmetic.Category.AURA && cat != Cosmetic.Category.TRAIL) return;
        UUID id = p.getUniqueId();
        int token = previewing.merge(id, 1, Integer::sum);
        int[] left = {26};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!p.isOnline() || previewing.getOrDefault(id, 0) != token || --left[0] < 0) {
                task.cancel();
                return;
            }
            tick++;
            if (cat == Cosmetic.Category.AURA) aura(p, c.style());
            else spawnTrail(p.getWorld(), p.getLocation().add(0, 0.15, 0), c.style());
        }, 1L, 3L);
    }
}
