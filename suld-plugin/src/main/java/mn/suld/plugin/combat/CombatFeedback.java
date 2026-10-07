package mn.suld.plugin.combat;

import mn.suld.api.mob.MobDefinition;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.mob.MobService;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wynncraft-style combat feedback: floating damage numbers over SÜLD mobs (seen only by the attacker; gold ✦ for
 * critical hits) and a live health bar in the mob's name.
 */
public final class CombatFeedback implements Listener {

    private static final int SEGMENTS = 10;

    /** The QA suite sets this while it fires thousands of test hits: no floating numbers or name bars then. */
    public static volatile boolean quiet;

    private final Plugin plugin;
    private final MobService mobs;

    public CombatFeedback(Plugin plugin, MobService mobs) {
        this.plugin = plugin;
        this.mobs = mobs;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity mob) || !mobs.isSuldMob(mob)) return;
        if (e.isCancelled()) { // cancelled after the crit was rolled: drop the flag, show nothing
            CombatListener.CRIT_HITS.remove(mob.getUniqueId());
            return;
        }
        if (quiet) {
            CombatListener.CRIT_HITS.remove(mob.getUniqueId());
            return;
        }
        Entity damager = e.getDamager();
        if (damager instanceof Projectile p && p.getShooter() instanceof Player shooter) damager = shooter;
        boolean crit = CombatListener.CRIT_HITS.remove(mob.getUniqueId());
        if (damager instanceof Player player) number(player, mob, e.getFinalDamage(), crit);
        Bukkit.getScheduler().runTask(plugin, () -> nameBar(mob));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof LivingEntity mob && mobs.isSuldMob(mob)) Bukkit.getScheduler().runTask(plugin, () -> nameBar(mob));
    }

    private void number(Player viewer, LivingEntity mob, double damage, boolean crit) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location at = mob.getLocation().add(r.nextDouble(-0.4, 0.4), mob.getHeight() + 0.3, r.nextDouble(-0.4, 0.4));
        String text = (crit ? "✦ " : "") + String.format(Locale.ROOT, damage >= 10 ? "%.0f" : "%.1f", damage);
        TextDisplay d = mob.getWorld().spawn(at, TextDisplay.class, t -> {
            t.text(Component.text(text, crit ? TextColor.fromHexString("#FFD24A") : NamedTextColor.RED, TextDecoration.BOLD));
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            if (crit) t.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(),
                    new org.joml.AxisAngle4f(), new org.joml.Vector3f(1.4f, 1.4f, 1.4f), new org.joml.AxisAngle4f()));
        });
        viewer.showEntity(plugin, d);
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!d.isValid() || ++ticks > 16) {
                    d.remove();
                    cancel();
                    return;
                }
                d.teleport(d.getLocation().add(0, 0.05, 0));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** "Говийн Чоно [Lvl 2] ▮▮▮▮▮▮▯▯▯▯" — green, yellow under 50 %, red under 25 %. */
    private void nameBar(LivingEntity mob) {
        if (!mob.isValid() || mob.isDead()) return;
        if (mob.getScoreboardTags().contains(mn.suld.plugin.model.ModelService.HOST_TAG)) return; // a rig: no name over its invisible host
        String id = mobs.mobId(mob).orElse(null);
        MobDefinition def = id == null ? null : SuldContent.mobFor(id);
        if (def == null) return;
        double max = MobService.maxHealth(mob);
        double frac = max <= 0 ? 0 : Math.max(0, Math.min(1, mob.getHealth() / max));
        int full = (int) Math.ceil(frac * SEGMENTS);
        NamedTextColor c = frac > 0.5 ? NamedTextColor.GREEN : frac > 0.25 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        mob.customName(Component.text(def.displayName(), Messages.BRAND)
                .append(Component.text(" [Lvl " + def.level() + "] ", NamedTextColor.WHITE))
                .append(Component.text("▮".repeat(full), c))
                .append(Component.text("▯".repeat(SEGMENTS - full), NamedTextColor.DARK_GRAY)));
    }
}
