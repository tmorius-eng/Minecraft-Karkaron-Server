package mn.suld.plugin.worldboss;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.campaign.DayTime;
import mn.suld.api.dungeon.BossDefinition;
import mn.suld.api.mob.MobDefinition;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.ExpGainResult;
import mn.suld.api.progression.ExpSource;
import mn.suld.api.worldboss.WorldBossDefinition;
import mn.suld.api.worldboss.WorldBossFight;
import mn.suld.api.worldboss.WorldBossSchedule;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.Content;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.mob.MobService;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Presentation;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * World bosses (docs/WORLD_BOSSES.md): at the weekly hours of {@code worldboss.times} (server zone) a WORLD_BOSS-tier
 * mob rises at a historic site for the whole server. The fight reuses the dungeon boss engine (BossService phases,
 * enrage, the boss's brain); its health grows with the crowd that comes (WorldBossFight.healthScale). Damage ranks the
 * fighters, time at the fight rewards them: everyone eligible gets the full reward, the top three the podium on top,
 * the first and fastest kill go into the Хроник. Still standing at its duration, or an enraged minute later, it
 * withdraws. Only one world boss stands at a time; it is never saved with the world (a restart removes it).
 */
public final class WorldBossService implements Listener {

    /** Every world boss and its summons carry this; one left over from a crash is removed when its chunk loads. */
    public static final String TAG = "suld_world_boss";
    /** Fighters within this many blocks of the boss count as at the fight. */
    private static final double REACH = 48;
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final Plugin plugin;
    private final SuldServices services;
    private final boolean enabled;
    private final int announceMinutes;
    private final WorldBossSchedule schedule;
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);

    private WorldBossSchedule.Slot nextSlot;
    private boolean announced, warned;
    private Active active;
    private int tickCount;

    private static final class Active {
        final WorldBossDefinition def;
        final MobDefinition mob;
        final WorldBossFight fight;
        final LivingEntity boss;
        final Location home;
        final Chunk chunk;
        final Set<UUID> barShown = new HashSet<>();
        int sizedFor;
        String phase;

        Active(WorldBossDefinition def, MobDefinition mob, WorldBossFight fight, LivingEntity boss, Location home, Chunk chunk, int sizedFor) {
            this.def = def;
            this.mob = mob;
            this.fight = fight;
            this.boss = boss;
            this.home = home;
            this.chunk = chunk;
            this.sizedFor = sizedFor;
            this.phase = def.phases().get(0).phaseName();
        }
    }

    public WorldBossService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        var cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("worldboss.enabled", true);
        this.announceMinutes = Math.max(1, cfg.getInt("worldboss.announce-minutes", 10));
        List<DayTime> times = new ArrayList<>();
        for (String t : cfg.getStringList("worldboss.times")) {
            try {
                times.add(DayTime.parse(t));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[worldboss] worldboss.times: " + ex.getMessage() + " (skipped)");
            }
        }
        this.schedule = new WorldBossSchedule(times, services.zone());
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 10L);
    }

    public List<WorldBossDefinition> bosses() {
        return Content.pack().worldBosses();
    }

    public Optional<WorldBossFight> active() {
        return active == null ? Optional.empty() : Optional.of(active.fight);
    }

    /** The boss and the hour of the next scheduled rising, if any. */
    public Optional<String> nextText() {
        WorldBossSchedule.Slot s = enabled ? schedule.next(Instant.now()) : null;
        WorldBossDefinition d = s == null ? null : bossOf(s);
        if (d == null) return Optional.empty();
        ZonedDateTime at = s.at().atZone(services.zone());
        return Optional.of("«" + name(d) + "» — " + weekday(at.getDayOfWeek()) + " " + HOUR.format(at) + ", " + siteName(d.siteId()));
    }

    /** True while {@code entity} is the standing world boss (CombatListener leaves its kill to this service). */
    public boolean owns(UUID entity) {
        return active != null && active.boss.getUniqueId().equals(entity);
    }

    // ------------------------------------------------------------------ clock

    private void tick() {
        tickCount++;
        if (active != null) {
            fightTick();
            return;
        }
        if (!enabled || schedule.empty() || bosses().isEmpty()) return;
        Instant now = Instant.now();
        if (nextSlot == null) { // the next hour strictly after now: an hour missed while the server was down is skipped
            nextSlot = schedule.next(now);
            announced = warned = false;
            if (nextSlot == null) return;
        }
        WorldBossDefinition d = bossOf(nextSlot);
        if (d == null) return;
        long left = nextSlot.at().getEpochSecond() - now.getEpochSecond();
        if (!announced && left <= announceMinutes * 60L) {
            announced = true;
            Bukkit.broadcast(Messages.accent("⚔ " + Math.max(1, (left + 59) / 60) + " минутын дараа «" + name(d) + "» " + siteName(d.siteId())
                    + "-ийн дэргэд босно! Түвшин " + level(d) + " — бүгдээрээ хамтдаа."));
        }
        if (!warned && left <= 60) {
            warned = true;
            Bukkit.broadcast(Messages.error("⚔ «" + name(d) + "» нэг минутын дараа босно — " + siteName(d.siteId()) + "!"));
            for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.7f, 0.8f);
        }
        if (left < -300) { // held back over five minutes (an admin's boss stood): this hour is gone
            nextSlot = null;
            return;
        }
        if (left <= 0) {
            nextSlot = null; // the next call looks up the following hour
            spawn(d, "clock");
        }
    }

    private WorldBossDefinition bossOf(WorldBossSchedule.Slot s) {
        List<WorldBossDefinition> all = bosses();
        int i = WorldBossSchedule.bossFor(s, all.size());
        return i < 0 ? null : all.get(i);
    }

    // ------------------------------------------------------------------ spawn

    /** Raise {@code def} now; null when it rose, else why not (for the admin). Main thread. */
    public Component spawn(WorldBossDefinition def, String actor) {
        if (active != null) return Messages.error("«" + name(active.def) + "» аль хэдийн босоод байна.");
        MobDefinition mob = SuldContent.mobFor(def.mobId());
        if (mob == null) return Messages.error("Мангас " + def.mobId() + " алга.");
        var sites = services.historicSites;
        Location site = sites == null ? null : sites.where(def.siteId()).orElse(null);
        if (site == null || site.getWorld() == null) {
            plugin.getLogger().warning("[worldboss] site " + def.siteId() + " is not placed: " + def.id() + " does not rise");
            return Messages.error("Газар " + def.siteId() + " байршаагүй байна.");
        }
        World w = site.getWorld();
        int x = site.getBlockX() + def.dx(), z = site.getBlockZ() + def.dz();
        Chunk chunk = w.getChunkAt(x >> 4, z >> 4); // loads it; the ticket below keeps it while the boss stands
        chunk.addPluginChunkTicket(plugin);
        Location home = new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 1, z + 0.5);
        int crowd = expectedCrowd(mob);
        LivingEntity boss = services.mobs().spawn(mob, home, mob.scaledHealth() * WorldBossFight.healthScale(crowd));
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(false); // never saved with the world: a restart ends the fight
        boss.addScoreboardTag(TAG);
        boss.setGlowing(true);
        WorldBossFight fight = new WorldBossFight(def, UUID.randomUUID().toString().substring(0, 8), Instant.now());
        active = new Active(def, mob, fight, boss, home.clone(), chunk, crowd);
        services.bosses().register(boss, new BossDefinition(mob, def.phases(), def.enrageSeconds()), change -> {
            if (active == null || !active.boss.equals(change.boss())) return;
            active.phase = change.phase().phaseName();
            nearby(Messages.error(change.enraged()
                    ? "«" + name(def) + "» галзуурлаа! Нэг минутад унагахгүй бол ухарна."
                    : "«" + name(def) + "»: " + change.phase().phaseName() + " (х" + change.phase().attackMultiplier() + ")"));
        });
        Bukkit.broadcast(Messages.accent("⚔ «" + name(def) + "» " + siteName(def.siteId()) + "-ийн дэргэд бослоо! "
                + "Хамтдаа дарвал оролцсон бүгд шагнагдана. (" + (def.durationSeconds() / 60) + " минут)"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 0.7f);
            if (p.getWorld().equals(w) && p.getLocation().distanceSquared(home) < 160 * 160) {
                Presentation.banner(p, name(def).toUpperCase(java.util.Locale.ROOT), "Дэлхийн босс бослоо!", NamedTextColor.DARK_RED);
            }
        }
        services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.WORLD_BOSS_SPAWN, null,
                Map.of("boss", def.id(), "run", fight.runId(), "sized_for", crowd, "by", actor)));
        plugin.getLogger().info("[worldboss] " + def.id() + " rose at " + home.getBlockX() + " " + home.getBlockY() + " " + home.getBlockZ()
                + " for " + crowd + " (" + actor + ")");
        return null;
    }

    /** Whom to size the boss for at its rising: online players with a class within ten levels below it or above. */
    private int expectedCrowd(MobDefinition mob) {
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (fighter(p) && level(p) >= mob.level() - 10) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ fight

    private void fightTick() {
        Active a = active;
        LivingEntity boss = a.boss;
        if (!boss.isValid() || boss.isDead()) {
            if (!boss.isDead()) end(false, "the boss is gone"); // removed (a /kill of the entity, a plugin): no rewards
            return;
        }
        services.bosses().tick(boss);
        Instant now = Instant.now();
        if (a.fight.timedOut(now) || services.bosses().wipes(boss)) {
            end(false, a.fight.timedOut(now) ? "time" : "enrage");
            return;
        }
        // leash: pulled too far from where it rose, it walks home at once (no kiting it into a wall or a city)
        if (!boss.getWorld().equals(a.home.getWorld()) || boss.getLocation().distanceSquared(a.home) > (double) a.def.leash() * a.def.leash()) {
            boss.teleport(a.home);
            nearby(Messages.info("«" + name(a.def) + "» буцаж нутагтаа очлоо."));
        }
        if (tickCount % 2 != 0) return; // presence, crowd and the bar: once a second
        List<Player> here = new ArrayList<>();
        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(boss.getLocation()) > REACH * REACH) continue;
            if (fighter(p)) {
                here.add(p);
                a.fight.present(p.getUniqueId(), p.getName(), 1);
            }
        }
        // a bigger crowd than it was sized for: it grows, keeping the share of health it has left (never shrinks)
        if (here.size() > a.sizedFor && a.sizedFor < WorldBossFight.CROWD_MAX) {
            a.sizedFor = Math.min(WorldBossFight.CROWD_MAX, here.size());
            services.mobs().resize(boss, a.mob.scaledHealth() * WorldBossFight.healthScale(a.sizedFor));
            MobService.nameplate(boss, a.mob, MobService.trueHealth(boss), MobService.trueMaxHealth(boss));
        }
        updateBar(a, now);
    }

    private void updateBar(Active a, Instant now) {
        double max = MobService.trueMaxHealth(a.boss);
        float frac = (float) Math.max(0, Math.min(1, max <= 0 ? 0 : MobService.trueHealth(a.boss) / max));
        long left = Math.max(0, a.def.durationSeconds() - a.fight.elapsedSeconds(now));
        bar.progress(frac);
        bar.name(Component.text("⚔ " + name(a.def) + " · " + a.phase + " · " + Math.round(frac * 100) + "% · " + (left / 60) + ":"
                + String.format(java.util.Locale.ROOT, "%02d", left % 60), NamedTextColor.RED));
        Set<UUID> near = new HashSet<>();
        for (Player p : a.boss.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(a.boss.getLocation()) <= 96 * 96) near.add(p.getUniqueId());
        }
        for (UUID id : new ArrayList<>(a.barShown)) {
            if (near.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(bar);
            a.barShown.remove(id);
        }
        for (UUID id : near) {
            if (a.barShown.add(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.showBossBar(bar);
            }
        }
    }

    /** Damage that landed on the boss, in design units and at most the health it had (an overkill counts once). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossHit(EntityDamageByEntityEvent e) {
        Active a = active;
        if (a == null || !e.getEntity().getUniqueId().equals(a.boss.getUniqueId())) return;
        Player p = attacker(e.getDamager());
        if (p == null) return;
        double dealt = Math.min(MobService.trueDamage(e), MobService.trueHealth(a.boss));
        a.fight.damage(p.getUniqueId(), p.getName(), dealt);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBossDeath(EntityDeathEvent e) {
        Active a = active;
        if (a == null || !e.getEntity().getUniqueId().equals(a.boss.getUniqueId())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        end(true, "slain");
    }

    /** The boss bar for someone who joins next to the fight. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (active != null) active.barShown.remove(e.getPlayer().getUniqueId());
    }

    /** A world boss (or a summon of one) that outlived a crash is removed as its chunk loads. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity entity : e.getEntities()) {
            if (entity.getScoreboardTags().contains(TAG) && (active == null || !active.boss.getUniqueId().equals(entity.getUniqueId()))) entity.remove();
        }
    }

    /** A summon the boss's brain called: removed with the boss. False when no world boss stands as {@code boss}. */
    public boolean adopt(UUID boss, LivingEntity add) {
        if (!owns(boss)) return false;
        add.addScoreboardTag(TAG);
        return true;
    }

    // ------------------------------------------------------------------ end

    private void end(boolean slain, String reason) {
        Active a = active;
        if (a == null) return;
        active = null;
        Instant now = Instant.now();
        services.bosses().unregister(a.boss.getUniqueId());
        for (UUID id : a.barShown) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(bar);
        }
        for (Entity e : a.boss.getWorld().getNearbyEntities(a.boss.getLocation(), 64, 32, 64, x -> x.getScoreboardTags().contains(TAG))) {
            if (!e.equals(a.boss) || !slain) e.remove();
        }
        a.chunk.removePluginChunkTicket(plugin);
        long seconds = a.fight.elapsedSeconds(now);
        List<WorldBossFight.Reward> rewards = slain ? a.fight.rewards(now) : List.of();
        if (slain) pay(a, rewards, now);
        else Bukkit.broadcast(Messages.error("«" + name(a.def) + "» ухарлаа — энэ удаад дарж чадсангүй."));
        services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.WORLD_BOSS_END, null, Map.of("boss", a.def.id(), "run", a.fight.runId(),
                "slain", slain, "seconds", seconds, "fighters", a.fight.ranking().size(), "rewarded", rewards.size(), "sized_for", a.sizedFor)));
        plugin.getLogger().info("[worldboss] " + a.def.id() + " " + (slain ? "slain" : "withdrew") + " (" + reason + ") after " + seconds
                + " s; " + a.fight.ranking().size() + " fighters, " + rewards.size() + " rewarded");
    }

    private void pay(Active a, List<WorldBossFight.Reward> rewards, Instant now) {
        long seconds = a.fight.elapsedSeconds(now);
        Set<UUID> clans = new HashSet<>();
        List<Player> paidTop = new ArrayList<>();
        for (WorldBossFight.Reward r : rewards) {
            Player p = Bukkit.getPlayer(r.player());
            PlayerProfile profile = services.profiles().cached(r.player()).orElse(null);
            // offline, or a soul by now, at the payout: nothing (the hardcore rule of world events, no mail-in rewards)
            if (p == null || profile == null || !fighter(p)) continue;
            int from = profile.progression().level();
            long base = Math.max(a.def.rewardExp(), mn.suld.api.balance.Rewards.worldEventExp(services.progression().engine().curve(), from));
            long exp = services.boosts().apply(r.player(), Math.round(base * r.multiplier()));
            long coins = Math.round(a.def.rewardCoins() * r.multiplier());
            ExpGainResult gain = services.progression().grantExp(profile, exp, ExpSource.WORLD_EVENT);
            profile.addCurrency(coins);
            p.sendMessage(Messages.success("⚔ #" + r.rank() + " · " + Math.round(r.damage()) + " гэмтэл: +" + exp + " EXP, +" + coins + " зоос"
                    + (r.multiplier() > 1 ? " (×" + r.multiplier() + ")" : "")));
            if (gain.leveledUp()) Presentation.levelUp(p, from, gain.after().level());
            loot(p, profile, a, now);
            services.profiles().save(profile);
            services.profiles().cached(p.getUniqueId()).ifPresent(pr -> services.hud().update(p, pr));
            services.clans().clanOf(r.player()).map(c -> c.id()).ifPresent(cid -> {
                if (clans.add(cid)) services.clans().contribute(r.player(), a.def.clanBonus());
            });
            if (paidTop.size() < 3) paidTop.add(p);
        }
        List<String> top = new ArrayList<>();
        for (Player p : paidTop) top.add(p.getName());
        Bukkit.broadcast(Messages.accent("⚔ «" + name(a.def) + "» дарагдлаа! " + rewards.size() + " баатар шагнагдлаа"
                + (top.isEmpty() ? "." : " — тэргүүн: " + String.join(", ", top) + ".")));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 0.9f);
        if (!paidTop.isEmpty()) services.chronicle().worldBossDefeated(a.def.id(), name(a.def), paidTop, seconds);
    }

    private void loot(Player p, PlayerProfile profile, Active a, Instant now) {
        var items = services.itemService();
        if (items == null || profile.playerClass().isEmpty()) return;
        var tree = services.skillTree();
        mn.suld.api.loot.LootContext ctx = new mn.suld.api.loot.LootContext(a.mob.level(), mn.suld.api.loot.LootTier.WORLD_EVENT,
                profile.playerClass().get(), tree == null ? 0 : tree.lootPct(p), p.getUniqueId(),
                "worldboss:" + a.def.id() + ":" + a.fight.runId());
        for (mn.suld.api.loot.LootDrop d : items.filtered(p, items.roll(a.def.lootTable(), ctx))) {
            mn.suld.api.item.ItemInstance inst = d.item();
            p.getInventory().addItem(items.stack(inst, p, d.amount())).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
            p.sendMessage(Messages.info("Олз: " + mn.suld.api.item.ItemTooltip.name(items.catalog(), items.catalog().require(inst.definitionId()), inst)
                    + " [" + inst.rarity().displayName() + ", Зэрэг " + inst.itemLevel() + "]"));
            items.announce(p, inst);
        }
    }

    /** Admin: end the standing boss without rewards. */
    public boolean stop() {
        if (active == null) return false;
        end(false, "admin");
        return true;
    }

    /** Shutdown: the boss and its summons go, bars hide, the chunk is let go. */
    public void shutdown() {
        Active a = active;
        if (a == null) return;
        active = null;
        services.bosses().unregister(a.boss.getUniqueId());
        for (UUID id : a.barShown) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(bar);
        }
        for (Entity e : a.boss.getWorld().getNearbyEntities(a.boss.getLocation(), 64, 32, 64, x -> x.getScoreboardTags().contains(TAG))) e.remove();
        a.chunk.removePluginChunkTicket(plugin);
    }

    // ------------------------------------------------------------------ helpers

    /** Can fight for a reward: online, alive, not a soul, with a class (souls and the classless are never paid). */
    private boolean fighter(Player p) {
        if (p.isDead() || services.isSoul.test(p.getUniqueId())) return false;
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        return pr != null && pr.hasSelectedClass() && p.getGameMode() != org.bukkit.GameMode.SPECTATOR;
    }

    private int level(Player p) {
        return services.profiles().cached(p.getUniqueId()).map(pr -> pr.progression().level()).orElse(1);
    }

    private void nearby(Component line) {
        Active a = active;
        if (a == null) return;
        for (Player p : a.boss.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(a.boss.getLocation()) <= 96 * 96) p.sendMessage(line);
        }
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player p) return p;
        return null;
    }

    public static String name(WorldBossDefinition d) {
        MobDefinition m = SuldContent.mobFor(d.mobId());
        return m == null ? d.id() : m.displayName();
    }

    private static int level(WorldBossDefinition d) {
        MobDefinition m = SuldContent.mobFor(d.mobId());
        return m == null ? 1 : m.level();
    }

    private static String siteName(String siteId) {
        for (var s : Content.pack().historicSites()) if (s.id().equals(siteId)) return s.name();
        return siteId;
    }

    private static String weekday(java.time.DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "Даваа";
            case TUESDAY -> "Мягмар";
            case WEDNESDAY -> "Лхагва";
            case THURSDAY -> "Пүрэв";
            case FRIDAY -> "Баасан";
            case SATURDAY -> "Бямба";
            case SUNDAY -> "Ням";
        };
    }
}
