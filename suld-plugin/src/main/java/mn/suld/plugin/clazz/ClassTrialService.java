package mn.suld.plugin.clazz;

import mn.suld.api.audit.AuditEvent;
import mn.suld.api.clazz.ClassChangeRules;
import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.dungeon.DungeonHalls;
import mn.suld.plugin.hud.HudService;
import mn.suld.plugin.item.PlayerDataSaves;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * «Ангийн Сорилт» (class trial, docs/CLASS_CHANGE.md): try another class for five minutes on practice dummies before
 * changing to it. Nothing of the trial lasts:
 * <ul>
 *   <li>the profile records the trial ({@code PlayerProfile.beginClassTrial}) before anything else happens, so a
 *   crash, a disconnect or a restart ends it on the next join; the end puts the real class back with its own
 *   progress, leaves the trial class's stored progress exactly as before, keeps the cooldown, and never lets coins
 *   or level end above their start;</li>
 *   <li>the arena is a private platform in the halls world, far from every dungeon slot; the dummies are vanilla
 *   husks without AI that never die (no drop, no EXP, no SÜLD mob, so no quest, task, mastery or campaign credit);</li>
 *   <li>the trial class's soulbound gear is taken away at the end, items dropped in the arena are removed, every
 *   command but {@code /classtrial leave} is refused, and the player cannot be hurt.</li>
 * </ul>
 */
public final class ClassTrialService implements Listener, org.bukkit.command.TabExecutor {

    static final long DURATION_MS = 5 * 60_000, COOLDOWN_MS = 2 * 60_000;
    public static final String TAG = "suld_class_trial";
    static final int ARENA_R = 9, BASE_X = -4096, SPACING = 96, Y = 100;
    /** The practice dummies' SÜLD mob id (not content: no loot, EXP or credit) and design health. */
    static final String DUMMY_MOB = "mob.sorilyn_dures";
    static final double DUMMY_HEALTH = 10_000_000;

    private final Plugin plugin;
    private final SuldServices services;
    private final ClassChangeService change;
    private final NamespacedKey returnKey;
    private final Map<UUID, Run> runs = new HashMap<>();
    private final Map<UUID, Long> lastEnd = new HashMap<>();

    private static final class Run {
        final int slot;
        final Location origin, center;
        final double share;
        final List<UUID> dummies = new ArrayList<>();
        final long until;
        double damage;
        BukkitTask ticker;

        Run(int slot, Location origin, Location center, double share, long until) {
            this.slot = slot;
            this.origin = origin;
            this.center = center;
            this.share = share;
            this.until = until;
        }
    }

    public ClassTrialService(Plugin plugin, SuldServices services, ClassChangeService change) {
        this.plugin = plugin;
        this.services = services;
        this.change = change;
        this.returnKey = new NamespacedKey(plugin, "class_trial_return");
    }

    /** Staff support (/suld classreset): the 2 minutes between trials cleared. */
    public void clearCooldown(UUID id) {
        lastEnd.remove(id);
    }

    public boolean inTrial(UUID id) {
        return runs.containsKey(id);
    }

    /** Why a trial of {@code to} cannot start now, or null. */
    public String blocked(Player p, PlayerClass to) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null || pr.playerClass().isEmpty()) return ClassChangeRules.Refusal.NO_CLASS.text;
        if (runs.containsKey(p.getUniqueId()) || pr.classTrial() != null) return "Сорилт аль хэдийн явагдаж байна.";
        if (pr.playerClass().get() == to) return "Энэ бол таны одоогийн анги.";
        // the same hard rules as a change (city, combat, run, duel, event, soul, room); never the price or cooldown
        ClassChangeRules.Request r = change.request(p, pr, to);
        ClassChangeRules.Verdict v = ClassChangeRules.check(new ClassChangeRules.Request(r.from(), r.to(), 1, 0, r.coins(), 0, r.now(),
                0, r.lastCombat(), r.inCity(), r.inDungeon(), r.inDuel(), r.inWorldEvent(), r.soul(), r.dead(), r.freeSlots()));
        if (!v.allowed()) return v.refusal().text;
        long since = System.currentTimeMillis() - lastEnd.getOrDefault(p.getUniqueId(), 0L);
        if (since < COOLDOWN_MS) return "Дараагийн сорилт " + ClassChangeRules.format(COOLDOWN_MS - since) + "-ын дараа.";
        return null;
    }

    public boolean start(Player p, PlayerClass to) {
        String why = blocked(p, to);
        if (why != null) {
            p.sendMessage(Messages.error(why));
            return false;
        }
        World w = Bukkit.getWorld(DungeonHalls.WORLD);
        if (w == null) {
            p.sendMessage(Messages.error("Сорилтын талбай бэлэн биш байна."));
            return false;
        }
        // opened from the menu's sky window: back to the real spot first, so that is where the trial returns to
        mn.suld.plugin.gui.MenuSky sky = mn.suld.plugin.gui.MenuSky.instance();
        if (sky != null && sky.isOpen(p)) sky.close(p);
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElseThrow();
        PlayerClass from = pr.playerClass().orElseThrow();
        double share = ClassChangeService.healthShare(p);
        Location origin = p.getLocation().clone();
        int slot = freeSlot();
        Location center = new Location(w, BASE_X - slot * SPACING + 0.5, Y + 1, 0.5, 0f, 0f);

        // the real class's gear leaves (it comes back at the end), the player file first, then the profile records
        // the trial before the player is moved anywhere
        change.stripClassGear(p, from);
        PlayerDataSaves.now(p);
        if (!pr.beginClassTrial(to, System.currentTimeMillis())) return false;
        p.getPersistentDataContainer().set(returnKey, PersistentDataType.STRING, origin.getWorld().getName() + ";" + origin.getX() + ";"
                + origin.getY() + ";" + origin.getZ() + ";" + origin.getYaw() + ";" + origin.getPitch());
        services.profiles().save(pr);
        change.applyClass(p, pr, 1.0);

        Run run = new Run(slot, origin, center, share, System.currentTimeMillis() + DURATION_MS);
        runs.put(p.getUniqueId(), run);
        buildArena(center);
        p.teleport(center);
        p.setFallDistance(0);
        for (int i = 0; i < 3; i++) {
            double a = Math.PI * 2 * i / 3;
            Location at = center.clone().add(Math.cos(a) * 5, 0, Math.sin(a) * 5);
            at.setYaw((float) Math.toDegrees(Math.atan2(-(center.getX() - at.getX()), center.getZ() - at.getZ())));
            // a mannequin, not a monster: the region spawner never treats it as a stray mob, and it has no AI at all
            Mannequin d = w.spawn(at, Mannequin.class, h -> {
                h.setPersistent(false);
                h.setImmovable(true);
                h.setSilent(true);
                h.addScoreboardTag(TAG);
                // a SÜLD mob to the combat code (audit 2026-10-13): the SÜLD hit formula, spells, crits and passives reach it
                // and its numbers show; an id outside the content gives no loot, EXP or credit
                services.mobs().tagAs(h, DUMMY_MOB);
                h.customName(Component.text("Сургуулилтын дүрс", NamedTextColor.GOLD, TextDecoration.BOLD));
                h.setCustomNameVisible(true);
                h.setDescription(Component.text("Цохиж туршаарай", NamedTextColor.WHITE));
            });
            // design health far above any hit (MobService divides every hit by the rest): it never dies, every number is true
            services.mobs().resize(d, DUMMY_HEALTH);
            d.setHealth(mn.suld.plugin.mob.MobService.maxHealth(d));
            run.dummies.add(d.getUniqueId());
        }
        run.ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(p.getUniqueId()), 20L, 20L);
        p.showTitle(Title.title(Component.text("Ангийн Сорилт · " + to.displayName(), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("5 минут дамми дээр турш · /classtrial leave", NamedTextColor.WHITE)));
        p.sendMessage(Messages.info("Сорилт: " + to.displayName() + " ангийг 5 минут туршина. Энд олсон юу ч үлдэхгүй; "
                + "дуусахад " + from.displayName() + " руугаа, зогсож байсан газраа буцна. Гарах: /classtrial leave"));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "class.trial.start", p.getUniqueId().toString(), from.id() + " -> " + to.id()));
        return true;
    }

    private int freeSlot() {
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Run r : runs.values()) used.add(r.slot);
        int s = 0;
        while (used.contains(s)) s++;
        return s;
    }

    private void buildArena(Location c) {
        World w = c.getWorld();
        for (int x = -ARENA_R; x <= ARENA_R; x++) {
            for (int z = -ARENA_R; z <= ARENA_R; z++) {
                boolean edge = Math.abs(x) == ARENA_R || Math.abs(z) == ARENA_R;
                w.getBlockAt(c.getBlockX() + x, Y, c.getBlockZ() + z).setType(edge ? Material.CHISELED_DEEPSLATE : Material.POLISHED_DEEPSLATE, false);
                for (int y = 1; y <= 4; y++) w.getBlockAt(c.getBlockX() + x, Y + y, c.getBlockZ() + z).setType(edge ? Material.BARRIER : Material.AIR, false);
            }
        }
    }

    private void clearArena(Location c) {
        World w = c.getWorld();
        for (int x = -ARENA_R; x <= ARENA_R; x++) {
            for (int z = -ARENA_R; z <= ARENA_R; z++) {
                for (int y = 0; y <= 4; y++) w.getBlockAt(c.getBlockX() + x, Y + y, c.getBlockZ() + z).setType(Material.AIR, false);
            }
        }
        for (Entity e : w.getNearbyEntities(c, ARENA_R + 4, 8, ARENA_R + 4)) {
            if (e instanceof Item || e.getScoreboardTags().contains(TAG)) e.remove();
        }
    }

    private void tick(UUID id) {
        Run r = runs.get(id);
        Player p = Bukkit.getPlayer(id);
        if (r == null) return;
        if (p == null || !p.isOnline()) {
            return; // the quit handler ends it
        }
        long left = r.until - System.currentTimeMillis();
        if (left <= 0) {
            end(p, "Сорилтын хугацаа дууслаа.");
            return;
        }
        // keep the dummies whole and the player on the platform
        for (UUID d : r.dummies) if (Bukkit.getEntity(d) instanceof LivingEntity le) le.setHealth(mn.suld.plugin.mob.MobService.maxHealth(le));
        if (!p.getWorld().equals(r.center.getWorld()) || p.getLocation().distanceSquared(r.center) > (ARENA_R + 3) * (ARENA_R + 3)) p.teleport(r.center);
        HudService.notice(p, Component.text("Сорилт · " + ClassChangeRules.format(left) + " · хохирол " + Math.round(r.damage)
                + " · DPS " + Math.round(r.damage / Math.max(1, (DURATION_MS - left) / 1000.0)), NamedTextColor.GOLD));
    }

    /** End the trial now (leave, time out, quit, restart). Puts everything back. */
    public void end(Player p, String why) {
        Run r = runs.remove(p.getUniqueId());
        if (r != null) {
            if (r.ticker != null) r.ticker.cancel();
            for (UUID d : r.dummies) {
                Entity e = Bukkit.getEntity(d);
                if (e != null) e.remove();
            }
            clearArena(r.center);
        }
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null || pr.classTrial() == null) return;
        PlayerClass trialClass = pr.classTrial().to();
        change.stripClassGear(p, trialClass);
        PlayerDataSaves.now(p);
        pr.endClassTrial(System.currentTimeMillis());
        change.applyClass(p, pr, r == null ? ClassChangeService.healthShare(p) : r.share);
        PlayerDataSaves.now(p);
        services.profiles().save(pr);
        lastEnd.put(p.getUniqueId(), System.currentTimeMillis());
        Location back = r != null ? r.origin : storedReturn(p);
        p.getPersistentDataContainer().remove(returnKey);
        if (back != null) p.teleport(back);
        p.setFallDistance(0);
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "class.trial.end", p.getUniqueId().toString(),
                trialClass.id() + " -> " + pr.playerClass().map(PlayerClass::id).orElse("?") + " damage=" + (r == null ? 0 : Math.round(r.damage))));
        p.sendMessage(Messages.success(why + " " + pr.playerClass().map(PlayerClass::displayName).orElse("") + " ангидаа буцлаа. "
                + "Солих бол /class → «Анги солих»."));
    }

    private Location storedReturn(Player p) {
        String s = p.getPersistentDataContainer().get(returnKey, PersistentDataType.STRING);
        if (s == null) return null;
        String[] f = s.split(";");
        World w = f.length == 6 ? Bukkit.getWorld(f[0]) : null;
        if (w == null) return null;
        try {
            return new Location(w, Double.parseDouble(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3]), Float.parseFloat(f[4]), Float.parseFloat(f[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(runs.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) end(p, "Сервер унтарч байна.");
        }
    }

    /** {@code /classtrial <анги>} starts a trial, {@code /classtrial leave} ends it. */
    @Override
    public boolean onCommand(org.bukkit.command.CommandSender s, org.bukkit.command.Command c, String l, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return true;
        }
        if (a.length == 0) {
            p.sendMessage(Messages.info("/classtrial <baatar|mergen|boo|darkhan|khulegchin> · /classtrial leave"));
            return true;
        }
        if (a[0].equalsIgnoreCase("leave") || a[0].equalsIgnoreCase("гарах")) {
            if (runs.containsKey(p.getUniqueId())) end(p, "Сорилтоос гарлаа.");
            else p.sendMessage(Messages.info("Сорилт явагдаагүй байна."));
            return true;
        }
        PlayerClass to = PlayerClass.byId(a[0].toLowerCase(java.util.Locale.ROOT)).orElse(null);
        if (to == null) p.sendMessage(Messages.error("Тийм анги алга: " + a[0]));
        else start(p, to);
        return true;
    }

    @Override
    public List<String> onTabComplete(org.bukkit.command.CommandSender s, org.bukkit.command.Command c, String l, String[] a) {
        List<String> out = new ArrayList<>(List.of("leave"));
        for (PlayerClass pc : PlayerClass.values()) out.add(pc.id());
        return a.length == 1 ? out.stream().filter(x -> x.startsWith(a[0].toLowerCase(java.util.Locale.ROOT))).toList() : List.of();
    }

    // ------------------------------------------------------------------------------------------------ guards

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHitDummy(EntityDamageByEntityEvent e) {
        if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
        Player p = e.getDamager() instanceof Player pl ? pl
                : e.getDamager() instanceof org.bukkit.entity.Projectile pj && pj.getShooter() instanceof Player pl2 ? pl2 : null;
        Run r = p == null ? null : runs.get(p.getUniqueId());
        if (r != null && r.dummies.contains(e.getEntity().getUniqueId())) r.damage += mn.suld.plugin.mob.MobService.trueDamage(e);
    }

    /**
     * A dummy never dies (no drop, no EXP): its design health is far above any hit (a hit is divided by the rest of it,
     * MobService), and it is whole again a tick later. It used to zero any hit bigger than what was left: a strong spell
     * then showed nothing.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDummyDamage(EntityDamageEvent e) {
        if (!e.getEntity().getScoreboardTags().contains(TAG) || !(e.getEntity() instanceof LivingEntity le)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (le.isValid()) le.setHealth(mn.suld.plugin.mob.MobService.maxHealth(le));
        });
    }

    @EventHandler
    public void onDummyDeath(EntityDeathEvent e) {
        if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && runs.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!runs.containsKey(e.getPlayer().getUniqueId())) return;
        String m = e.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (m.startsWith("/classtrial") || m.startsWith("/soriult") || m.startsWith("/сорилт")) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(Messages.error("Сорилтын үеэр зөвхөн /classtrial leave."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (runs.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        PlayerProfile pr = services.profiles().cached(e.getPlayer().getUniqueId()).orElse(null);
        if (runs.containsKey(e.getPlayer().getUniqueId()) || (pr != null && pr.classTrial() != null)) {
            end(e.getPlayer(), "Сорилт тасарлаа.");
        }
    }

    /** SULD-F06: end a post-crash trial on the very next tick after join (no 40-tick unprotected window). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || runs.containsKey(p.getUniqueId())) return;
            PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
            if (pr != null && pr.classTrial() != null) end(p, "Өмнөх сорилт дууслаа.");
        });
    }
}
