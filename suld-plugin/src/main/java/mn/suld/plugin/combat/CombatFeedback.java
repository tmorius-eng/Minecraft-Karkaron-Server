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

    /** Told about every hit a player lands on a SÜLD mob: its design damage and whether it was critical (training grounds). */
    public interface HitObserver {
        void hit(Player attacker, LivingEntity victim, double damage, boolean crit, EntityDamageByEntityEvent e);
    }

    public static volatile HitObserver observer;

    /**
     * Mobs whose numbers name their source (the training grounds' dummies, docs/TRAINING_GROUNDS.md): «Тэнгэрийн Цавчилт
     * 1 930» in the spell colour, «⚡ Цавчин Тайлах 640» for a passive, «★» for an ultimate. Elsewhere a number stays a number.
     */
    public static volatile java.util.function.Predicate<Entity> labelled = e -> false;

    private static final TextColor SPELL = TextColor.fromHexString("#5FE3FF"), PASSIVE = TextColor.fromHexString("#FF6BE8"),
            ULTIMATE = TextColor.fromHexString("#FF9A3C"), BURN = TextColor.fromHexString("#FFB347"), GOLD = TextColor.fromHexString("#FFD24A");

    private final Plugin plugin;
    private final MobService mobs;

    private static volatile CombatFeedback instance;

    public CombatFeedback(Plugin plugin, MobService mobs) {
        this.plugin = plugin;
        this.mobs = mobs;
        instance = this;
    }

    /** The running instance (the training grounds show burn ticks through it), or null before enable. */
    public static CombatFeedback instance() {
        return instance;
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
        // spell damage never crits: a passive's hit inside a weapon hit must not take that hit's crit mark
        boolean crit = !CombatListener.isSpellDamage() && CombatListener.CRIT_HITS.remove(mob.getUniqueId());
        if (damager instanceof Player player) {
            double dmg = MobService.trueDamage(e);
            HitObserver o = observer;
            if (o != null) o.hit(player, mob, dmg, crit, e);
            number(player, mob, dmg, crit);
        }
        queueBar(mob);
    }

    /** A burn tick (or another hit without an attacker) on a labelled mob, shown to {@code viewer} (training grounds). */
    public void burn(Player viewer, LivingEntity mob, double damage) {
        if (!quiet) show(viewer, mob, Component.text("🔥 " + mn.suld.api.training.TrainingText.number(damage), BURN), 1f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof LivingEntity mob && mobs.isSuldMob(mob)) queueBar(mob);
    }

    /** Mobs whose name bar changed this tick: one update per mob per tick, however many hits it took. */
    private final java.util.Set<LivingEntity> bars = new java.util.LinkedHashSet<>();

    private void queueBar(LivingEntity mob) {
        boolean first = bars.isEmpty();
        bars.add(mob);
        if (first) Bukkit.getScheduler().runTask(plugin, () -> {
            java.util.List<LivingEntity> due = new java.util.ArrayList<>(bars);
            bars.clear();
            for (LivingEntity m : due) nameBar(m);
        });
    }

    private void number(Player viewer, LivingEntity mob, double damage, boolean crit) {
        if (labelled.test(mob)) {
            labelledNumber(viewer, mob, damage, crit);
            return;
        }
        String text = (crit ? "✦ " : "") + String.format(Locale.ROOT, damage >= 10 ? "%.0f" : "%.1f", damage);
        show(viewer, mob, Component.text(text, crit ? GOLD : NamedTextColor.RED, TextDecoration.BOLD), crit ? 1.4f : 1f);
    }

    /** The training grounds' number: the source's name and colour (HitSource), crits bigger and gold. */
    private void labelledNumber(Player viewer, LivingEntity mob, double damage, boolean crit) {
        String n = mn.suld.api.training.TrainingText.number(damage);
        mn.suld.api.training.TrainingStats.Source src = HitSource.current();
        Component c;
        float scale = 1f;
        if (src != null && src.kind() == mn.suld.api.training.TrainingStats.Kind.SPELL) {
            c = Component.text(src.name() + " ", SPELL).append(Component.text(n, SPELL, TextDecoration.BOLD));
            scale = 1.1f;
        } else if (src != null && src.kind() == mn.suld.api.training.TrainingStats.Kind.PASSIVE) {
            c = Component.text("⚡ " + src.name() + " ", PASSIVE).append(Component.text(n, PASSIVE, TextDecoration.BOLD));
        } else if (src != null && src.kind() == mn.suld.api.training.TrainingStats.Kind.ULTIMATE) {
            c = Component.text("★ " + src.name() + " ", ULTIMATE).append(Component.text(n, ULTIMATE, TextDecoration.BOLD));
            scale = 1.3f;
        } else if (crit) {
            c = Component.text("✦ " + n, GOLD, TextDecoration.BOLD);
            scale = 1.5f;
        } else {
            c = Component.text(n, NamedTextColor.WHITE, TextDecoration.BOLD);
        }
        show(viewer, mob, c, scale);
    }

    private void show(Player viewer, LivingEntity mob, Component text, float scale) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // the training Warden (×1.6, a 4.6-block giant) gets its numbers spread around its chest, not far over its head
        boolean tall = mob.getHeight() > 3 && labelled.test(mob);
        double up = tall ? mob.getHeight() * 0.6 + r.nextDouble(0, 0.8) : mob.getHeight() + 0.3;
        double spread = tall ? 1.1 : 0.4;
        Location at = mob.getLocation().add(r.nextDouble(-spread, spread), up, r.nextDouble(-spread, spread));
        TextDisplay d = mob.getWorld().spawn(at, TextDisplay.class, t -> {
            t.text(text);
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            if (scale != 1f) t.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(),
                    new org.joml.AxisAngle4f(), new org.joml.Vector3f(scale, scale, scale), new org.joml.AxisAngle4f()));
        });
        viewer.showEntity(plugin, d);
        // the rise is one client-side interpolation (no teleport per tick, no runnable per hit), removed after 16 ticks
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!d.isValid()) return;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(15);
            org.bukkit.util.Transformation tr = d.getTransformation();
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(0, 0.8f, 0), tr.getLeftRotation(), tr.getScale(), tr.getRightRotation()));
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, d::remove, 17L);
    }

    /** "Говийн Чоно [Lvl 2] ▮▮▮▮▮▮▯▯▯▯" — green, yellow under 50 %, red under 25 %. */
    private void nameBar(LivingEntity mob) {
        if (!mob.isValid() || mob.isDead() || labelled.test(mob)) return; // a training dummy keeps its own name
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
