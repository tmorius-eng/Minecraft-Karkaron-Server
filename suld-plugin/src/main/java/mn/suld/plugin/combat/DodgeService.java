package mn.suld.plugin.combat;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.plugin.SuldServices;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Сөхрөх (docs/COMBAT_FEEL.md): jumping while sneaking dashes the player a few blocks in the direction they move (or
 * back from where they look when standing still), and for a moment ({@link #IFRAME_MS}) hits from mobs, bosses and
 * their abilities miss. It is how a telegraphed slam or nova is escaped at the last instant. Cooldown 3 s (the
 * Хүлэгчин, the light cavalry class, 2 s). Not while mounted, gliding, flying, swimming or as a soul.
 */
public final class DodgeService implements Listener {

    static final long IFRAME_MS = 400, COOLDOWN_MS = 3000, COOLDOWN_KHULEGCHIN_MS = 2000;

    private final SuldServices services;
    private final Map<UUID, Long> ready = new HashMap<>();
    private final Map<UUID, Long> immuneUntil = new HashMap<>();

    public DodgeService(SuldServices services) {
        this.services = services;
    }

    @EventHandler(ignoreCancelled = true)
    public void onJump(com.destroystokyo.paper.event.player.PlayerJumpEvent e) {
        Player p = e.getPlayer();
        if (!p.isSneaking() || p.isInsideVehicle() || p.isGliding() || p.isFlying() || p.isSwimming() || p.isInWater()) return;
        if (services.isSoul.test(p.getUniqueId())) return;
        long now = System.currentTimeMillis();
        Long at = ready.get(p.getUniqueId());
        if (at != null && now < at) {
            p.sendActionBar(Component.text("Сөхрөх — " + String.format(java.util.Locale.ROOT, "%.1f", (at - now) / 1000.0) + " с", NamedTextColor.GRAY));
            return;
        }
        PlayerClass c = services.profiles().cached(p.getUniqueId()).flatMap(pr -> pr.playerClass()).orElse(null);
        ready.put(p.getUniqueId(), now + (c == PlayerClass.KHULEGCHIN ? COOLDOWN_KHULEGCHIN_MS : COOLDOWN_MS));
        immuneUntil.put(p.getUniqueId(), now + IFRAME_MS);
        // the direction the player is moving in; standing still: a step back from where they look
        Vector move = e.getTo().toVector().subtract(e.getFrom().toVector()).setY(0);
        if (move.lengthSquared() < 1.0e-4) move = p.getLocation().getDirection().setY(0).multiply(-1);
        if (move.lengthSquared() < 1.0e-4) move = new Vector(0, 0, 1);
        p.setVelocity(move.normalize().multiply(1.05).setY(0.22));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.3, 0), 10, 0.3, 0.05, 0.3, 0.03);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.6f, 1.6f);
    }

    /** During the dodge, hits from anything but the void and /kill miss (a fall from the dash still counts after it). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Long until = immuneUntil.get(p.getUniqueId());
        if (until == null) return;
        if (System.currentTimeMillis() > until) {
            immuneUntil.remove(p.getUniqueId());
            return;
        }
        EntityDamageEvent.DamageCause cause = e.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.KILL
                || cause == EntityDamageEvent.DamageCause.FALL || cause == EntityDamageEvent.DamageCause.STARVATION) return;
        e.setCancelled(true);
        p.getWorld().spawnParticle(Particle.ENCHANTED_HIT, p.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        immuneUntil.remove(e.getPlayer().getUniqueId());
        ready.remove(e.getPlayer().getUniqueId());
    }
}
