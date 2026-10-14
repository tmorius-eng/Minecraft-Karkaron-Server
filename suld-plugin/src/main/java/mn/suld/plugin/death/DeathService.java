package mn.suld.plugin.death;

import mn.suld.api.audit.AuditEvent;
import mn.suld.api.config.DeathSettings;
import mn.suld.api.death.DeathLock;
import mn.suld.api.death.DeathRecord;
import mn.suld.api.death.DeathRules;
import mn.suld.api.death.Wound;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.persistence.DeathRepository;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.Progression;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * The hardcore death system (docs/DEATH_AND_RECOVERY.md; {@code death:} in config.yml).
 *
 * <ul>
 *   <li><b>Beginners</b> (level ≤ {@code death.beginner-levels}, no Ascension; reform of 2026-10-13): a death costs
 *       nothing — every stack kept, no EXP, no wear, no soul, no wound.</li>
 *   <li><b>Cost</b> ({@link DeathRules}): soulbound gear is always kept; a fraction of the other stacks drops; a
 *       fraction of the progress into the current level is lost (never a level); kept gear wears.</li>
 *   <li><b>Soul</b>: every death is a persisted {@link DeathRecord} ({@code suld_death_state}). The player is a
 *       <b>Сүнс</b> (soul) until {@code locked_until}. Since 2026-10-13 ({@code death.lock.mode: UNTIL_RECOVERY})
 *       that is {@link DeathRecord#UNTIL_RECOVERY}: no timer, the soul comes back by acting — «Тэнгэрийн сорил»
 *       ({@link DeathTrialService}), the ovoo ritual (OvooService) or staff. The soul rises beside the ovoo nearest
 *       to where it fell, and the {@link SoulBeaconService} beam at that spot gives back part of the lost EXP. In
 *       {@code TIMED} mode the old real-world timestamp of the {@link DeathLock} curve applies. A soul cannot fight,
 *       be hurt, loot, build, open containers, cast, ride, enter dungeons or trade; chat, menus and NPCs work.</li>
 *   <li><b>Wound</b>: when the soul state ends, the class gear carries one more {@link Wound} step (−5 % effective
 *       stats, at most −15 %, through {@code Equipment.Wearer.boundFactor}). Each step heals after active play
 *       ({@code death.wound.heal-minutes} active minutes, validated by the ActivePlaytime tracker); a Сүнсний
 *       Хэлтэрхий from a won trial heals part of it at once.</li>
 * </ul>
 * Admin actions ({@code /deathrevive}, {@code /deathreset}) are audited. There is no paid path.
 */
public final class DeathService implements Listener {

    private static final TextColor SOUL = TextColor.fromHexString("#9FD8FF");
    private final DateTimeFormatter WHEN; // in the server's zone (SuldServices.zone)
    /** "The lock only ends by an admin" (allow-free-revive: false): 100 years. */
    private static final long FOREVER = TimeUnit.DAYS.toMillis(36_500);

    private final Plugin plugin;
    /** The SÜLD menu item's tag (gui/MenuListener): a soul may still open the menus with it. */
    private final org.bukkit.NamespacedKey menuItemKey;
    private final SuldServices services;
    private final DeathRepository repo;
    private final NamespacedKey menuKey;

    /** Open lock per player: the stored record once the insert completes. */
    private final Map<UUID, CompletableFuture<DeathRecord>> locks = new ConcurrentHashMap<>();
    /** Cached lock end (ms) for the cheap per-tick checks. */
    private final Map<UUID, Long> lockedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Wound> wounds = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyWounds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    /** What the last death cost, shown after respawn. */
    private final Map<UUID, Component> summaries = new ConcurrentHashMap<>();
    /** Where a soul without a timer fell: it rises beside the nearest ovoo (read once, at the respawn). */
    private final Map<UUID, Location> fellAt = new ConcurrentHashMap<>();

    /** «Сүнсний Гэрэлт» (set by SuldPlugin): the beam where a player fell. */
    public volatile SoulBeaconService beacons;

    public DeathService(Plugin plugin, SuldServices services) {
        this.WHEN = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(services.zone());
        this.plugin = plugin;
        this.menuItemKey = new org.bukkit.NamespacedKey(plugin, "menu_item");
        this.services = services;
        this.repo = services.deathRepository();
        this.menuKey = new NamespacedKey(plugin, "menu_item");
    }

    private DeathSettings settings() {
        return services.config().death();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::minute, 1200L, 1200L);
        // a reload with players online: load their state now
        for (Player p : Bukkit.getOnlinePlayers()) {
            preload(p.getUniqueId());
            Bukkit.getScheduler().runTaskLater(plugin, () -> applyOnJoin(p), 20L);
        }
    }

    // ---- loading -----------------------------------------------------------------------------------------------

    /** Load the open lock and the wound before the player joins (the async pre-login thread may block). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        // an unknown death lock must not let a soul play as the living: refuse the login until the state is readable
        if (!preload(e.getUniqueId())) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("Өгөгдөл ачаалж чадсангүй. Түр хүлээгээд дахин орно уу.", NamedTextColor.RED));
        }
    }

    /** Loads the death lock and the wound; false if storage did not answer. */
    private boolean preload(UUID id) {
        try {
            Optional<DeathRecord> lock = repo.openLock(id).get(5, TimeUnit.SECONDS);
            Wound w = repo.wound(id).get(5, TimeUnit.SECONDS);
            wounds.put(id, w);
            if (lock.isPresent()) {
                locks.put(id, CompletableFuture.completedFuture(lock.get()));
                lockedUntil.put(id, lock.get().lockedUntil());
            } else {
                locks.remove(id);
                lockedUntil.remove(id);
            }
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("death state of " + id + " could not be loaded: " + ex);
            return false;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyOnJoin(p), 20L);
    }

    private void applyOnJoin(Player p) {
        if (!p.isOnline()) return;
        Long until = lockedUntil.get(p.getUniqueId());
        if (until == null) {
            services.equipment().dirty(p); // the wound factor may differ from the default
            return;
        }
        if (until <= System.currentTimeMillis()) {
            recover(p.getUniqueId(), DeathRecord.State.RECOVERED, "system");
        } else if (until == DeathRecord.UNTIL_RECOVERY) {
            enterSoul(p, until);
            p.sendMessage(Messages.error("Та сүнс хэвээр байна. Амилах: /trial (Тэнгэрийн сорил) эсвэл ойрын овоог нар зөв "
                    + "гурвантаа тойрч цацал өргө."));
        } else {
            enterSoul(p, until);
            p.sendMessage(Messages.error("Та үхсэн хэвээр байна. Сэргэх: " + WHEN.format(Instant.ofEpochMilli(until))
                    + " (" + DeathLock.format(until - System.currentTimeMillis()) + ")."));
        }
    }

    // ---- death -------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        DeathSettings s = settings();
        if (!s.enabled() || e.getKeepInventory()) return;
        Player p = e.getEntity();
        services.session(p.getUniqueId()).deaths++;
        // SÜLD progression replaces vanilla EXP: no orbs, keep the vanilla bar (the HUD drives it).
        e.setDroppedExp(0);
        e.setKeepLevel(true);
        PlayerProfile profile = services.profiles().cached(p.getUniqueId()).orElse(null);
        int level = profile == null ? 1 : profile.progression().level();
        int ascension = profile == null ? 0 : profile.ascension(); // an ascended character's lock is the longest
        if (profile != null && DeathRules.protectedDeath(level, ascension, s)) {
            protectedDeath(e, p, level, ascension, s);
            return;
        }

        // Everything still in the drop list at HIGHEST (relics are already taken out by the relic system) is either
        // soulbound (kept), lost (dropped) or kept.
        List<ItemStack> drops = e.getDrops();
        List<ItemStack> keep = new ArrayList<>();
        List<Integer> droppable = new ArrayList<>();
        for (int i = 0; i < drops.size(); i++) {
            ItemStack it = drops.get(i);
            if (it == null || it.getType().isAir()) continue;
            if (isSoulbound(it)) keep.add(it);
            else droppable.add(i);
        }
        List<Integer> lost = DeathRules.lostStacks(droppable, s, ThreadLocalRandom.current());
        List<ItemStack> dropped = new ArrayList<>();
        for (int i : droppable) {
            if (lost.contains(i)) dropped.add(drops.get(i));
            else keep.add(drops.get(i));
        }
        int worn = 0;
        for (ItemStack it : keep) {
            if (wear(it, s)) worn++;
        }
        drops.clear();
        drops.addAll(dropped);
        e.getItemsToKeep().addAll(keep);

        long expLost = 0;
        if (profile != null) {
            Progression before = profile.progression();
            Progression after = DeathRules.afterDeath(before, s);
            expLost = before.expIntoLevel() - after.expIntoLevel();
            if (expLost > 0) {
                profile.progression(after);
                // saved now, with the lock row (external audit 2026-10-13 F04): a crash before the next autosave
                // gave the lost EXP back while the lock stayed
                services.profiles().save(profile);
            }
        }

        long now = System.currentTimeMillis();
        // a soul without a timer (2026-10-13): it comes back by «Тэнгэрийн сорил», the ovoo ritual or staff
        boolean untimed = s.untilRecovery() && s.allowFreeRevive();
        long lockMs = untimed ? 0 : s.allowFreeRevive() ? Math.max(s.soulStateSeconds() * 1000L, DeathLock.millis(s, level, ascension)) : FOREVER;
        long until = untimed ? DeathRecord.UNTIL_RECOVERY : now + lockMs;
        Location where = p.getLocation();
        UUID deathId = lock(p, level, ascension, now, until, cause(p));
        SoulBeaconService b = beacons;
        // a soul cannot walk back into a hall (it is closed to it afterwards): the beam stands at the gate it is sent to
        mn.suld.plugin.dungeon.DungeonHalls halls = services.dungeons().halls().orElse(null);
        Location beamAt = halls != null && halls.inHalls(where) ? halls.exitFor(p.getUniqueId()) : where;
        mn.suld.api.death.SoulBeacon beacon = b == null || expLost <= 0 ? null : b.place(p, deathId, beamAt, expLost);
        if (untimed) fellAt.put(p.getUniqueId(), where.clone());

        int droppedItems = dropped.stream().mapToInt(ItemStack::getAmount).sum();
        Wound next = wound(p.getUniqueId()).add(s);
        Component summary = Component.text("☠ Үхлийн үнэ: ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text("-" + expLost + " EXP", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" · " + dropped.size() + " багц (" + droppedItems + " ш) унасан", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(worn > 0 ? " · " + worn + " хэрэгсэл элэгдсэн" : "", NamedTextColor.WHITE, TextDecoration.BOLD));
        if (beacon != null) {
            summary = summary.append(Component.newline())
                    .append(Component.text("✦ Сүнсний Гэрэлт: ", SOUL, TextDecoration.BOLD))
                    .append(Component.text((beamAt == where ? "унасан газарт тань " : "агуйн хаалган дээр ") + Math.round(s.beaconMinutes()) + " минут гэрэлтэнэ — хүрвэл +"
                            + beacon.refund(s) + " EXP буцна (алдсаны " + Math.round(s.beaconRefund() * 100) + " %).", NamedTextColor.WHITE));
        }
        summary = summary.append(Component.newline());
        if (untimed) {
            summary = summary.append(Component.text("⌛ Амилах: ", SOUL, TextDecoration.BOLD))
                    .append(Component.text("/trial — Тэнгэрийн сорил, эсвэл овоог нар зөв гурвантаа тойрч цацал өргө (" + s.ritualSeconds() + " с)",
                            NamedTextColor.WHITE));
        } else {
            summary = summary.append(Component.text("⌛ Сэргэх: ", SOUL, TextDecoration.BOLD))
                    .append(Component.text(s.allowFreeRevive() ? WHEN.format(Instant.ofEpochMilli(until)) + " (" + DeathLock.format(lockMs) + ")"
                            : "админ амилуулна", NamedTextColor.WHITE));
        }
        summary = summary.append(Component.text(" · дараа нь ангийн хуяг/зэвсэг −" + next.percent(s) + " % (шарх, идэвхтэй тоглолтоор эдгэнэ)",
                NamedTextColor.GRAY));
        summaries.put(p.getUniqueId(), summary);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("exp_lost", expLost);
        data.put("stacks_dropped", dropped.size());
        data.put("lock_ms", untimed ? -1L : lockMs); // -1: no timer, the soul comes back by acting
        data.put("level", level);
        data.put("beacon", beacon != null);
        services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of("player_death", p.getUniqueId(), data));
    }

    /**
     * A beginner's death (level ≤ {@code death.beginner-levels}, no Ascension; owner 2026-10-13: «бүгд хамгаалагдана»):
     * nothing is lost — every stack is kept as it is (no wear), no EXP, no soul, no wound. Relics are already out of the
     * drop list (they go home as on any death). It is still stored, closed at once, so the history and the funnel's
     * first death see it.
     */
    private void protectedDeath(PlayerDeathEvent e, Player p, int level, int ascension, DeathSettings s) {
        List<ItemStack> drops = e.getDrops();
        for (ItemStack it : drops) {
            if (it != null && !it.getType().isAir()) e.getItemsToKeep().add(it);
        }
        drops.clear();
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        Location l = p.getLocation();
        String cause = cause(p);
        DeathRecord rec = new DeathRecord(UUID.randomUUID(), id, 0, now, now, l.getWorld() == null ? "" : l.getWorld().getName(),
                l.getBlockX(), l.getBlockY(), l.getBlockZ(), cause, level, ascension, wound(id).stacks(), DeathRecord.State.RECOVERED, now, 0);
        CompletableFuture<DeathRecord> prev = locks.get(id); // one write at a time per player (the death number)
        (prev == null ? CompletableFuture.<DeathRecord>completedFuture(null) : prev.exceptionally(ex -> null))
                .thenCompose(x -> repo.insert(rec))
                .whenComplete((r, ex) -> {
                    if (ex != null) plugin.getLogger().warning("protected death of " + p.getName() + " not stored: " + ex);
                    else if (r != null && r.seq() == 1) services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of(
                            mn.suld.api.analytics.AnalyticsEventType.FIRST_DEATH, id, Map.<String, Object>of("level", level, "cause", cause)));
                });
        summaries.put(id, Component.text("❤ Шинэ баатрын хамгаалалт: ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text("юу ч алдсангүй — EXP, эд зүйл, хуяг бүтэн, сүнс болохгүй.", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(s.beginnerLevels() + "-р түвшин хүртэл үхэл үнэгүй. " + (s.beginnerLevels() + 1)
                        + "-ээс: EXP-ийн хэсэг, зарим эд зүйл, шарх; сүнс /trial эсвэл овооны цацлаар амилна.", NamedTextColor.GRAY)));
        services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of("player_death", id,
                Map.<String, Object>of("exp_lost", 0L, "stacks_dropped", 0, "lock_ms", 0L, "level", level, "protected", true)));
    }

    private static String cause(Player p) {
        EntityDamageEvent last = p.getLastDamageCause();
        if (last == null) return "UNKNOWN";
        String c = last.getCause().name();
        if (last instanceof EntityDamageByEntityEvent by) {
            Entity d = by.getDamager();
            if (d instanceof Projectile pr && pr.getShooter() instanceof Entity sh) d = sh;
            c += ":" + d.getType().name();
        }
        return c;
    }

    /** Store the lock (async, ordered); the in-memory state is authoritative for this server right away. Returns its id. */
    private UUID lock(Player p, int level, int ascension, long now, long until, String cause) {
        UUID id = p.getUniqueId();
        Location l = p.getLocation();
        DeathRecord rec = new DeathRecord(UUID.randomUUID(), id, 0, now, until, l.getWorld() == null ? "" : l.getWorld().getName(),
                l.getBlockX(), l.getBlockY(), l.getBlockZ(), cause, level, ascension, wound(id).stacks(), DeathRecord.State.LOCKED, null, 0);
        CompletableFuture<DeathRecord> prev = locks.get(id);
        CompletableFuture<DeathRecord> stored = (prev == null ? CompletableFuture.<DeathRecord>completedFuture(null) : prev
                .exceptionally(ex -> null)
                .thenCompose(old -> old != null && old.locked()
                        // a lock that is still open (e.g. killed by /kill while a soul): close it first
                        ? repo.update(old.closed(DeathRecord.State.RECOVERED, now, old.woundAfter())).thenApply(ok -> null)
                        : CompletableFuture.completedFuture(null)))
                .thenCompose(x -> repo.insert(rec));
        stored.whenComplete((r, ex) -> {
            if (ex != null) plugin.getLogger().severe("death of " + p.getName() + " could not be stored: " + ex);
            // the funnel's first_death (ANA-001): the player's first stored death, once (the sink never blocks)
            else if (r != null && r.seq() == 1) services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of(
                    mn.suld.api.analytics.AnalyticsEventType.FIRST_DEATH, id, Map.<String, Object>of("level", level, "cause", cause)));
        });
        locks.put(id, stored);
        lockedUntil.put(id, until);
        DeathTrialService t = trial;
        if (t != null) t.forget(id); // a new death: no earlier trial speaks for it
        return rec.deathId();
    }

    /** Class weapons, armour, the menu item and a Сүнсний Хэлтэрхий (bound to its winner) never drop. */
    private boolean isSoulbound(ItemStack it) {
        if (it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(menuKey, PersistentDataType.BYTE)) {
            return true;
        }
        if (SoulShardService.isShard(it)) return true;
        ItemInstance ii = services.items().read(it).orElse(null);
        if (ii == null) {
            // vanilla armour without a SÜLD document (not SÜLD loot) keeps the old rule
            String name = it.getType().name();
            return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS")
                    || name.endsWith("_BOOTS") || name.equals("ELYTRA") || name.equals("SHIELD");
        }
        // SÜLD items: only soulbound gear is kept; loot armour drops like any other loot (it used to be kept by material)
        return mn.suld.plugin.item.SoulboundGuard.soulbound(ii) || ii.definitionId().startsWith("weapon.surgamj_");
    }

    /** Applies death wear to a damageable item in place; true if it was worn. */
    private boolean wear(ItemStack it, DeathSettings s) {
        int max = it.getType().getMaxDurability();
        if (!(it.getItemMeta() instanceof Damageable d) || d.isUnbreakable()) return false;
        boolean suld = services.items().isSuldItem(it);
        // SÜLD gear has its own maximum (one point above it is never used: at the maximum the item is broken, not gone)
        if (suld && d.hasMaxDamage()) max = d.getMaxDamage() - 1;
        if (max <= 0) return false;
        int next = DeathRules.wear(d.getDamage(), max, s);
        if (suld) next = Math.min(next, max);
        if (next == d.getDamage()) return false;
        d.setDamage(next);
        it.setItemMeta(d);
        return true;
    }

    // ---- soul state --------------------------------------------------------------------------------------------

    /**
     * A soul without a timer rises beside the ovoo nearest to where it fell (2026-10-13): the beam at that spot is a
     * walk away, the ovoo ritual is right there, and the trial can be called anywhere outside the city. A death in the
     * city, in the halls (DungeonHalls sends the soul to the gate) or in another world keeps the usual respawn.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSoulRise(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Location fell = fellAt.remove(p.getUniqueId());
        if (fell == null || fell.getWorld() == null || !fell.getWorld().equals(Bukkit.getWorlds().get(0))) return;
        if (!isSoul(p.getUniqueId()) || services.inCity(fell)) return;
        mn.suld.plugin.region.OvooService ovoos = services.ovoos;
        Location rise = ovoos == null ? null : ovoos.soulRise(fell);
        if (rise != null) e.setRespawnLocation(rise);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        DeathSettings s = settings();
        Player p = e.getPlayer();
        Component summary = summaries.remove(p.getUniqueId());
        if (!s.enabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (summary != null) p.sendMessage(summary);
            services.profiles().cached(p.getUniqueId()).ifPresent(pr -> services.hud().update(p, pr));
            Long until = lockedUntil.get(p.getUniqueId());
            if (until != null && until > System.currentTimeMillis()) enterSoul(p, until);
        });
    }

    private void enterSoul(Player p, long until) {
        applySoulEffects(p);
        BossBar bar = bars.computeIfAbsent(p.getUniqueId(),
                id -> BossBar.bossBar(Component.empty(), 1f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10));
        p.showBossBar(bar);
        updateBar(p, bar, until);
        boolean forever = until - System.currentTimeMillis() > FOREVER / 2;
        String sub = until == DeathRecord.UNTIL_RECOVERY ? "/trial эсвэл овоонд цацал өргөж амил"
                : forever ? "Админ таныг амилуулахыг хүлээ" : "Сүнс тань " + DeathLock.format(until - System.currentTimeMillis()) + " дараа буцна";
        p.showTitle(Title.title(
                Component.text("ТА УНАЛАА", SOUL, TextDecoration.BOLD),
                Component.text(sub, NamedTextColor.WHITE, TextDecoration.BOLD),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(700))));
        p.playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.8f);
    }

    private void applySoulEffects(Player p) {
        DeathTrialService t = trial;
        if (t != null && t.inTrial(p.getUniqueId())) {
            // the trial is a real fight: no soul slowness or weakness while it lasts
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.WEAKNESS);
            return;
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 4, false, false, true));
    }

    private void updateBar(Player p, BossBar bar, long until) {
        long now = System.currentTimeMillis();
        long left = Math.max(0, until - now);
        if (until == DeathRecord.UNTIL_RECOVERY) {
            // no timer: the bar says how to come back, and while the beam stands, where it is and how long it stays
            SoulBeaconService b = beacons;
            mn.suld.api.death.SoulBeacon beacon = b == null ? null : b.of(p.getUniqueId());
            String where = beacon == null ? null : b.status(p);
            DeathTrialService t = trial;
            String trialState = t != null && t.state(p.getUniqueId()) == mn.suld.api.death.SpiritTrialState.FAILED ? " · сорил бүтсэнгүй, дахин /trial" : "";
            bar.name(Component.text("☠ Сүнс — /trial эсвэл овооны цацал" + (where == null ? trialState : " · " + where), SOUL, TextDecoration.BOLD));
            bar.progress(beacon == null ? 1f
                    : Math.max(0f, Math.min(1f, beacon.remaining(now) / (float) Math.max(1, beacon.expiresAt() - beacon.createdAt()))));
            return;
        }
        if (left > FOREVER / 2) {
            bar.name(Component.text("☠ Сүнс — админ амилуулахыг хүлээж байна", SOUL, TextDecoration.BOLD));
            bar.progress(1f);
            return;
        }
        bar.name(Component.text("☠ Сүнс — " + DeathLock.format(left) + " · " + WHEN.format(Instant.ofEpochMilli(until)), SOUL, TextDecoration.BOLD));
        DeathRecord r = known(p.getUniqueId());
        long total = r == null ? Math.max(1, left) : Math.max(1, r.lockedUntil() - r.diedAt());
        bar.progress(Math.max(0f, Math.min(1f, left / (float) total)));
    }

    /** The stored record of the open lock, if the insert is done. */
    private DeathRecord known(UUID id) {
        CompletableFuture<DeathRecord> f = locks.get(id);
        return f != null && f.isDone() && !f.isCompletedExceptionally() ? f.getNow(null) : null;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> en : lockedUntil.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) continue; // offline: the timestamp keeps running; resolved at the next join
            if (p.isDead()) continue;
            if (en.getValue() <= now) {
                recover(p.getUniqueId(), DeathRecord.State.RECOVERED, "system");
                continue;
            }
            applySoulEffects(p);
            BossBar bar = bars.get(p.getUniqueId());
            if (bar == null) enterSoul(p, en.getValue());
            else updateBar(p, bar, en.getValue());
            p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.01);
        }
    }

    /** Once a minute: dirty wounds are saved (healing comes from {@link #activeMinute}). */
    private void minute() {
        flushWounds();
    }

    /** One validated active minute of a player (ActivePlaytimeService): an open wound heals by it. */
    public void activeMinute(Player p) {
        UUID id = p.getUniqueId();
        Wound w = wounds.getOrDefault(id, Wound.NONE);
        if (w.stacks() == 0 || isSoul(id)) return;
        DeathSettings s = settings();
        Wound healed = w.heal(s, 1);
        wounds.put(id, healed);
        dirtyWounds.add(id);
        if (healed.stacks() < w.stacks()) {
            services.equipment().dirty(p);
            p.sendMessage(Messages.success(healed.stacks() == 0 ? "Шарх бүрэн эдгэлээ — ангийн хуяг, зэвсэг бүрэн хүчтэй."
                    : "Шарх эдгэж байна: одоо −" + healed.percent(s) + " %."));
        }
    }

    /**
     * A Сүнсний Хэлтэрхий used: {@code minutes} of active-play healing at once. The wound is stored now (the caller has
     * already put the spent shard on disk, so a crash between the two can only lose the healing, never repeat it).
     */
    public Wound healWound(Player p, double minutes) {
        UUID id = p.getUniqueId();
        Wound w = wounds.getOrDefault(id, Wound.NONE);
        if (w.stacks() == 0 || minutes <= 0) return w;
        Wound healed = w.heal(settings(), minutes);
        wounds.put(id, healed);
        dirtyWounds.remove(id);
        repo.saveWound(id, healed).whenComplete((v, ex) -> {
            if (ex != null) {
                dirtyWounds.add(id); // the minute flush tries again
                plugin.getLogger().warning("wound of " + id + " not saved: " + ex);
            }
        });
        if (healed.stacks() < w.stacks()) services.equipment().dirty(p);
        return healed;
    }

    private void flushWounds() {
        for (UUID id : List.copyOf(dirtyWounds)) {
            dirtyWounds.remove(id);
            repo.saveWound(id, wounds.getOrDefault(id, Wound.NONE)).whenComplete((v, ex) -> {
                if (ex != null) plugin.getLogger().warning("wound of " + id + " not saved: " + ex);
            });
        }
    }

    /** Plugin disable: save wounds, take the countdown bars off the screens. Locks are already stored. */
    public void shutdown() {
        dirtyWounds.addAll(wounds.keySet());
        flushWounds();
        for (Map.Entry<UUID, BossBar> en : bars.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null) p.hideBossBar(en.getValue());
        }
        bars.clear();
    }

    public boolean isSoul(UUID player) {
        Long until = lockedUntil.get(player);
        return until != null && until > System.currentTimeMillis();
    }

    /** The open wound of an online (or recently seen) player. */
    public Wound wound(UUID player) {
        return wounds.getOrDefault(player, Wound.NONE);
    }

    /** Multiplier of the player's class-gear stats (Equipment.Wearer.boundFactor). */
    public double woundFactor(UUID player) {
        return wound(player).factor(settings());
    }

    /** Time left of the player's lock (0 if none). */
    public long lockRemaining(UUID player) {
        Long until = lockedUntil.get(player);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    // ---- recovery ----------------------------------------------------------------------------------------------

    /**
     * Ends the open lock of a player (online or not). {@code RECOVERED} and {@code ADMIN_REVIVED} add a wound step;
     * {@code RESET} clears the wound too. Returns false if the player had no open lock (RESET still clears the wound).
     */
    public CompletableFuture<Boolean> recover(UUID id, DeathRecord.State how, String actor) {
        DeathSettings s = settings();
        CompletableFuture<DeathRecord> pending = locks.remove(id);
        lockedUntil.remove(id);
        CompletableFuture<Optional<DeathRecord>> open = pending != null
                ? pending.handle((r, ex) -> Optional.ofNullable(ex == null ? r : null))
                : repo.openLock(id);
        Player online = Bukkit.getPlayer(id);
        boolean known = wounds.containsKey(id);
        CompletableFuture<Wound> current = known ? CompletableFuture.completedFuture(wounds.get(id)) : repo.wound(id);
        return open.thenCombine(current, (rec, w) -> new Object[]{rec, w}).thenCompose(pair -> {
            @SuppressWarnings("unchecked")
            Optional<DeathRecord> rec = (Optional<DeathRecord>) pair[0];
            Wound w = (Wound) pair[1];
            Wound next = how == DeathRecord.State.RESET ? Wound.NONE : rec.isPresent() ? w.add(s) : w;
            wounds.put(id, next);
            CompletableFuture<Void> save = repo.saveWound(id, next);
            CompletableFuture<Boolean> closed = rec.isPresent() && rec.get().locked()
                    ? repo.update(rec.get().closed(how, System.currentTimeMillis(), next.stacks()))
                    : CompletableFuture.completedFuture(false);
            if (!actor.equals("system") || how != DeathRecord.State.RECOVERED) {
                services.audit().record(AuditEvent.of(actor, "death." + how.name().toLowerCase(java.util.Locale.ROOT), id.toString(),
                        rec.map(r -> "death#" + r.seq() + " " + r.deathId()).orElse("no open lock") + " wound=" + next.stacks()));
            }
            return save.thenCombine(closed, (a, ok) -> rec.isPresent());
        }).whenComplete((had, ex) -> {
            if (ex != null) plugin.getLogger().warning("recovery of " + id + " failed: " + ex);
            if (online != null) Bukkit.getScheduler().runTask(plugin, () -> afterRecovery(online, how));
        });
    }

    private void afterRecovery(Player p, DeathRecord.State how) {
        if (!p.isOnline()) return;
        services.session(p.getUniqueId()).revives++;
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) p.hideBossBar(bar);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.removePotionEffect(PotionEffectType.WEAKNESS);
        services.equipment().dirty(p);
        DeathSettings s = settings();
        Wound w = wound(p.getUniqueId());
        p.showTitle(Title.title(Component.text("АМИЛЛАА", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(w.stacks() == 0 ? "Тэнгэр таныг ивээг" : "Шарх: ангийн хуяг, зэвсэг −" + w.percent(s) + " %",
                        NamedTextColor.WHITE, TextDecoration.BOLD),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
        if (how == DeathRecord.State.RESET) p.sendMessage(Messages.accent("Админ таны үхлийн төлөвийг цэвэрлэлээ."));
        else p.sendMessage(Messages.accent("Сүнс тань бие рүүгээ буцлаа." + (w.stacks() > 0
                ? " Шарх −" + w.percent(s) + " % — идэвхтэй тоглолтоор " + s.woundHealMinutes() + " минут тутамд нэг шат эдгэнэ." : "")));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.2);
    }

    /** {@code /revive}: an admin ends the lock (the wound is still applied). */
    public boolean revive(Player p, boolean byAdmin) {
        if (!isSoul(p.getUniqueId())) return false;
        recover(p.getUniqueId(), byAdmin ? DeathRecord.State.ADMIN_REVIVED : DeathRecord.State.RECOVERED, byAdmin ? "admin" : "system");
        return true;
    }

    // ---- what a soul cannot do ---------------------------------------------------------------------------------

    /** A soul cannot hurt anything (no PvE/PvP while recovering). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSoulAttack(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player shooter) damager = shooter;
        UUID wolfOwner = DeathTrialService.ownerOf(e.getEntity());
        if (wolfOwner != null) {
            // a trial wolf: only its own soul may hit it (no help, no stolen kill)
            if (!(damager instanceof Player p) || !p.getUniqueId().equals(wolfOwner)) e.setCancelled(true);
            return;
        }
        if (damager instanceof Player p && isSoul(p.getUniqueId())) e.setCancelled(true);
    }

    /** «Тэнгэрийн сорил» (set by SuldPlugin): its soul fights and is bitten by its own wolves only. */
    public volatile DeathTrialService trial;

    /** …and nothing can hurt a soul. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSoulDamaged(EntityDamageEvent e) {
        if (shieldsTrialWolf(DeathTrialService.ownerOf(e.getEntity()) != null, e instanceof EntityDamageByEntityEvent, e.getCause())) {
            e.setCancelled(true); // SULD-F09: a trial wolf falls to its soul only, never to lava, fire, cactus, water or a drop
            return;
        }
        if (!(e.getEntity() instanceof Player p) || !isSoul(p.getUniqueId())
                || e.getCause() == EntityDamageEvent.DamageCause.VOID || e.getCause() == EntityDamageEvent.DamageCause.KILL) {
            return;
        }
        DeathTrialService t = trial;
        if (t != null && t.inTrial(p.getUniqueId()) && e instanceof EntityDamageByEntityEvent by
                && p.getUniqueId().equals(DeathTrialService.ownerOf(by.getDamager()))) {
            // a bite from its own trial wolf: it hurts, but a soul never dies again — the trial is lost instead
            if (p.getHealth() - e.getFinalDamage() <= 1) {
                e.setCancelled(true);
                t.fell(p);
            }
            return;
        }
        e.setCancelled(true);
    }

    /** True when the world, not an entity, hurts a trial wolf (VOID and KILL still clear a stuck one). */
    static boolean shieldsTrialWolf(boolean trialWolf, boolean byEntity, EntityDamageEvent.DamageCause cause) {
        return trialWolf && !byEntity && cause != EntityDamageEvent.DamageCause.VOID && cause != EntityDamageEvent.DamageCause.KILL;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        UUID wolfOwner = DeathTrialService.ownerOf(e.getEntity());
        if (wolfOwner != null) {
            if (!(e.getTarget() instanceof Player p) || !p.getUniqueId().equals(wolfOwner)) e.setCancelled(true);
            return;
        }
        if (e.getTarget() instanceof Player p && isSoul(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSoulPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && isSoul(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSoulBreak(BlockBreakEvent e) {
        if (isSoul(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSoulPlace(BlockPlaceEvent e) {
        if (isSoul(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    /** A soul cannot drop, throw or use things: no item may change hands or be spent while recovering. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSoulDrop(org.bukkit.event.player.PlayerDropItemEvent e) {
        if (isSoul(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    /**
     * Blocks (doors, levers, beds…) and items (pearls, potions, food, buckets…) do nothing for a soul. The SÜLD menu
     * item still opens the menus, so a soul can read its death info and revive options.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSoulInteract(org.bukkit.event.player.PlayerInteractEvent e) {
        if (!isSoul(e.getPlayer().getUniqueId())) return;
        ItemStack it = e.getItem();
        boolean menu = it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(menuItemKey, org.bukkit.persistence.PersistentDataType.BYTE);
        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        if (!menu) e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSoulConsume(org.bukkit.event.player.PlayerItemConsumeEvent e) {
        if (isSoul(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSoulLaunch(org.bukkit.event.entity.ProjectileLaunchEvent e) {
        // in its «Тэнгэрийн сорил» a soul shoots (onSoulAttack lets its arrows reach only its own wolves)
        if (e.getEntity().getShooter() instanceof Player p && isSoul(p.getUniqueId())
                && (trial == null || !trial.inTrial(p.getUniqueId()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSoulEntityInteract(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (isSoul(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    /**
     * A soul may talk, read and revive, nothing else: no teleport (/spawn, /tpa, /home, /warp…), payment, trade,
     * dungeon, horse or shop until the lock is over. Admins are not limited.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSoulCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!isSoul(p.getUniqueId()) || p.hasPermission("suld.admin")) return;
        String label = e.getMessage().length() > 1 ? e.getMessage().substring(1).split(" ", 2)[0].toLowerCase(java.util.Locale.ROOT) : "";
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        org.bukkit.command.Command c = Bukkit.getCommandMap().getCommand(label);
        String name = c == null ? label : c.getName().toLowerCase(java.util.Locale.ROOT);
        if (SOUL_COMMANDS.contains(name) || SOUL_COMMANDS.contains(label)) return;
        e.setCancelled(true);
        p.sendMessage(Messages.error("Сүнс байхдаа энэ тушаалыг ашиглахгүй. " + wayBack(p.getUniqueId())));
    }

    /** How a soul comes back, for the refusals: «Амилах: /trial …» or, with a timer, how long is left. */
    private String wayBack(UUID id) {
        Long until = lockedUntil.get(id);
        if (until != null && until == DeathRecord.UNTIL_RECOVERY) return "Амилах: /trial эсвэл овооны цацал.";
        return "Сэргэх хүртэл: " + DeathLock.format(lockRemaining(id));
    }

    /** What a soul may still run: death and revive, chat and messages, help and reading its own state. */
    private static final java.util.Set<String> SOUL_COMMANDS = java.util.Set.of(
            "revive", "trial", "soril", "сорил", "skills", "skill", "deathstatus", "deathinfo", "help", "rules", "commands", "menu", "tutorial", "profile", "quest",
            "chat", "ch", "g", "l", "pc", "tr", "cc", "msg", "tell", "w", "whisper", "r", "reply", "mail",
            "discord", "website", "vote", "top", "balance", "cosmetics", "buy", "credits");

    /** Containers, merchants, anvils… are closed to a soul; SÜLD menus and the player's own inventory stay open. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSoulOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !isSoul(p.getUniqueId())) return;
        InventoryType t = e.getInventory().getType();
        if (t == InventoryType.PLAYER || t == InventoryType.CRAFTING) return;
        if (e.getInventory().getHolder() instanceof mn.suld.plugin.gui.Menu) return;
        if (e.getInventory().getHolder() instanceof mn.suld.plugin.gui.ClassSelectionHolder) return;
        e.setCancelled(true);
        p.sendMessage(Messages.error("Сүнс эд зүйлд хүрч чадахгүй. " + wayBack(p.getUniqueId())));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        BossBar bar = bars.remove(id);
        if (bar != null) e.getPlayer().hideBossBar(bar);
        summaries.remove(id);
        fellAt.remove(id);
        if (dirtyWounds.remove(id)) repo.saveWound(id, wound(id));
        // keep the lock and the wound cached: the timestamp keeps running, and a quick rejoin reuses them
    }

    // ---- admin views -------------------------------------------------------------------------------------------

    public CompletableFuture<List<DeathRecord>> history(UUID player, int limit) {
        return repo.recent(player, limit);
    }

    public CompletableFuture<Wound> storedWound(UUID player) {
        Wound w = wounds.get(player);
        return w != null ? CompletableFuture.completedFuture(w) : repo.wound(player);
    }
}
