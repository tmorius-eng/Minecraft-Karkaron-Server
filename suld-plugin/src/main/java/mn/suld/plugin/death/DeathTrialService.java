package mn.suld.plugin.death;

import mn.suld.api.death.DeathRecord;
import mn.suld.api.death.DeathTrial;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * «Тэнгэрийн сорил» ({@code /trial}, docs/DEATH_AND_RECOVERY.md): a soul calls three spirit wolves where it stands and
 * fights them. Win in time → the lock ends now (state RECOVERED, actor "trial", the wound still applies). Time out, fall
 * or leave → the wolves vanish and the soul waits out its lock or tries again after the cooldown. The wolves are vanilla
 * wolves tagged for one soul: no SÜLD mob, so no loot, EXP, quest, task or campaign credit; nobody else can hit them and
 * they bite nobody else. During the trial {@link DeathService} lets the soul fight them and be bitten (never below 1 HP).
 */
public final class DeathTrialService implements Listener, TabExecutor {

    public static final String TAG = "suld_trial";

    private final Plugin plugin;
    private final SuldServices services;
    private final DeathService deaths;
    private final DeathTrial rules = DeathTrial.DEFAULT;

    private final Map<UUID, Run> runs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFail = new ConcurrentHashMap<>();

    private static final class Run {
        final List<UUID> wolves = new ArrayList<>();
        final long startedAt = System.currentTimeMillis();
        int killed;
        boolean fallen;
        BukkitTask ticker;
    }

    public DeathTrialService(Plugin plugin, SuldServices services, DeathService deaths) {
        this.plugin = plugin;
        this.services = services;
        this.deaths = deaths;
    }

    /** True while {@code soul} fights its trial. */
    public boolean inTrial(UUID soul) {
        return runs.containsKey(soul);
    }

    /** The soul whose trial wolf {@code e} is, or null. */
    public static UUID ownerOf(Entity e) {
        for (String t : e.getScoreboardTags()) if (t.startsWith(TAG + ":")) return UUID.fromString(t.substring(TAG.length() + 1));
        return null;
    }

    /** A trial bite that would take the soul to 0: the trial is lost instead (called by DeathService). */
    void fell(Player p) {
        Run r = runs.get(p.getUniqueId());
        if (r != null) r.fallen = true;
    }

    private void start(Player p) {
        UUID id = p.getUniqueId();
        if (!deaths.isSoul(id)) {
            p.sendMessage(Messages.info("Сорил зөвхөн сүнсэнд: та амьд байна."));
            return;
        }
        if (runs.containsKey(id)) {
            p.sendMessage(Messages.info("Сорил аль хэдийн эхэлсэн."));
            return;
        }
        Long failed = lastFail.get(id);
        long wait = failed == null ? 0 : rules.waitSeconds((System.currentTimeMillis() - failed) / 1000.0);
        if (wait > 0) {
            p.sendMessage(Messages.error("Сүнс амрах ёстой: " + (wait / 60 + 1) + " минутын дараа дахин оролдоно уу."));
            return;
        }
        if (services.inCity(p.getLocation())) {
            p.sendMessage(Messages.error("Хархорумд сорил болохгүй: хотын хаалгаар гараад /trial."));
            return;
        }
        int level = services.profiles().cached(id).map(pr -> pr.progression().level()).orElse(1);
        Run r = new Run();
        Location at = p.getLocation();
        for (int i = 0; i < rules.wolves(); i++) {
            double a = Math.PI * 2 * i / rules.wolves();
            Location l = at.clone().add(Math.cos(a) * 6, 0, Math.sin(a) * 6);
            l.setY(l.getWorld().getHighestBlockYAt(l) + 1);
            Wolf w = l.getWorld().spawn(l, Wolf.class, wolf -> {
                wolf.setPersistent(false);
                wolf.setRemoveWhenFarAway(false);
                wolf.addScoreboardTag(TAG);
                wolf.addScoreboardTag(TAG + ":" + id);
                wolf.customName(Component.text("Сүнсний чоно", NamedTextColor.AQUA, TextDecoration.BOLD));
                wolf.setCustomNameVisible(true);
                wolf.setGlowing(true);
                var hp = wolf.getAttribute(Attribute.MAX_HEALTH);
                if (hp != null) hp.setBaseValue(DeathTrial.wolfHealth(level));
                wolf.setHealth(DeathTrial.wolfHealth(level));
                var bite = wolf.getAttribute(Attribute.ATTACK_DAMAGE);
                if (bite != null) bite.setBaseValue(DeathTrial.wolfBite(level));
                wolf.setAngry(true);
            });
            w.setTarget(p);
            r.wolves.add(w.getUniqueId());
        }
        runs.put(id, r);
        p.showTitle(net.kyori.adventure.title.Title.title(Component.text("Тэнгэрийн сорил", NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text(rules.wolves() + " сүнсний чоныг " + rules.seconds() / 60 + " минутад дийл", NamedTextColor.WHITE)));
        p.playSound(p.getLocation(), Sound.ENTITY_WOLF_GROWL, 1f, 0.8f);
        r.ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(p.getUniqueId()), 20L, 20L);
    }

    private void tick(UUID id) {
        Run r = runs.get(id);
        if (r == null) return;
        Player p = Bukkit.getPlayer(id);
        double elapsed = (System.currentTimeMillis() - r.startedAt) / 1000.0;
        DeathTrial.Outcome o = rules.outcome(r.killed, elapsed, r.fallen, p == null || !p.isOnline());
        if (o == DeathTrial.Outcome.RUNNING) {
            if (p != null) p.sendActionBar(Component.text("Сорил: " + r.killed + "/" + rules.wolves() + " · "
                    + Math.max(0, (int) (rules.seconds() - elapsed)) + " с", NamedTextColor.AQUA));
            for (UUID w : r.wolves) if (Bukkit.getEntity(w) instanceof Wolf wolf && p != null && wolf.getTarget() != p) wolf.setTarget(p);
            return;
        }
        end(id, o);
    }

    private void end(UUID id, DeathTrial.Outcome o) {
        Run r = runs.remove(id);
        if (r == null) return;
        if (r.ticker != null) r.ticker.cancel();
        for (UUID w : r.wolves) {
            Entity e = Bukkit.getEntity(w);
            if (e != null) e.remove();
        }
        Player p = Bukkit.getPlayer(id);
        if (o == DeathTrial.Outcome.WON) {
            deaths.recover(id, DeathRecord.State.RECOVERED, "trial").thenAccept(had -> Bukkit.getScheduler().runTask(plugin, () -> {
                Player pl = Bukkit.getPlayer(id);
                if (pl == null) return;
                pl.sendMessage(Messages.success("Тэнгэр ивээлээ: сорилыг давж, сүнс бие махбодтоо буцлаа (шарх үлдэнэ)."));
                pl.getWorld().spawnParticle(Particle.END_ROD, pl.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.02);
                pl.playSound(pl.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
            }));
            return;
        }
        lastFail.put(id, System.currentTimeMillis());
        if (p != null) p.sendMessage(Messages.error(switch (o) {
            case TIMED_OUT -> "Хугацаа дууслаа: сорил бүтсэнгүй. " + rules.cooldownSeconds() / 60 + " минутын дараа дахин /trial.";
            case FALLEN -> "Сүнсний чоно дийлэв: сорил бүтсэнгүй. " + rules.cooldownSeconds() / 60 + " минутын дараа дахин /trial.";
            default -> "Сорил тасарлаа.";
        }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWolfDeath(EntityDeathEvent e) {
        UUID owner = ownerOf(e.getEntity());
        if (owner == null) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        Run r = runs.get(owner);
        if (r != null && r.wolves.contains(e.getEntity().getUniqueId())) {
            r.killed++;
            Player p = Bukkit.getPlayer(owner);
            if (p != null) p.playSound(p.getLocation(), Sound.ENTITY_WOLF_HURT, 1f, 1f);
            if (r.killed >= rules.wolves()) end(owner, DeathTrial.Outcome.WON);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (runs.containsKey(e.getPlayer().getUniqueId())) end(e.getPlayer().getUniqueId(), DeathTrial.Outcome.LEFT);
    }

    /** Plugin disable: no wolf outlives the server (they are not persistent either). */
    public void shutdown() {
        for (UUID id : new ArrayList<>(runs.keySet())) end(id, DeathTrial.Outcome.LEFT);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (s instanceof Player p) start(p);
        else s.sendMessage("Players only.");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        return List.of();
    }
}
