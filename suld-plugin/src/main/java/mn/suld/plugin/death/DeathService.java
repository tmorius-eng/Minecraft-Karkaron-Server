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
 *   <li><b>Cost</b> ({@link DeathRules}): soulbound gear is always kept; a fraction of the other stacks drops; a
 *       fraction of the progress into the current level is lost (never a level); kept gear wears.</li>
 *   <li><b>Lock</b>: every death is a persisted {@link DeathRecord} ({@code suld_death_state}). The player is a
 *       <b>Сүнс</b> (soul) until {@code locked_until} — a real-world timestamp that grows with level
 *       ({@link DeathLock}); restarts, reconnects and offline time do not change it. A soul cannot fight, be hurt,
 *       loot, build, open containers, cast, ride, enter dungeons or trade; chat, menus and NPCs work.</li>
 *   <li><b>Wound</b>: when the lock ends, the class gear carries one more {@link Wound} step (−5 % effective stats,
 *       at most −15 %, through {@code Equipment.Wearer.boundFactor}). Each step heals after active play
 *       ({@code death.wound.heal-minutes} active minutes, validated by the ActivePlaytime tracker).</li>
 * </ul>
 * Admin actions ({@code /deathrevive}, {@code /deathreset}) are audited. There is no paid path.
 */
public final class DeathService implements Listener {

    private static final TextColor SOUL = TextColor.fromHexString("#9FD8FF");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ulaanbaatar");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZONE);
    /** "The lock only ends by an admin" (allow-free-revive: false): 100 years. */
    private static final long FOREVER = TimeUnit.DAYS.toMillis(36_500);

    private final Plugin plugin;
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

    public DeathService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
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

        // SÜLD progression replaces vanilla EXP: no orbs, keep the vanilla bar (the HUD drives it).
        e.setDroppedExp(0);
        e.setKeepLevel(true);
        long expLost = 0;
        int level = 1;
        PlayerProfile profile = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (profile != null) {
            Progression before = profile.progression();
            level = before.level();
            Progression after = DeathRules.afterDeath(before, s);
            expLost = before.expIntoLevel() - after.expIntoLevel();
            if (expLost > 0) profile.progression(after);
        }

        long now = System.currentTimeMillis();
        long lockMs = s.allowFreeRevive() ? Math.max(s.soulStateSeconds() * 1000L, DeathLock.millis(s, level, 0)) : FOREVER;
        long until = now + lockMs;
        lock(p, level, now, until, cause(p));

        int droppedItems = dropped.stream().mapToInt(ItemStack::getAmount).sum();
        Wound next = wound(p.getUniqueId()).add(s);
        Component summary = Component.text("☠ Үхлийн үнэ: ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text("-" + expLost + " EXP", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" · " + dropped.size() + " багц (" + droppedItems + " ш) унасан", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(worn > 0 ? " · " + worn + " хэрэгсэл элэгдсэн" : "", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("⌛ Сэргэх: ", SOUL, TextDecoration.BOLD))
                .append(Component.text(s.allowFreeRevive() ? WHEN.format(Instant.ofEpochMilli(until)) + " (" + DeathLock.format(lockMs) + ")"
                        : "админ амилуулна", NamedTextColor.WHITE))
                .append(Component.text(" · дараа нь ангийн хуяг/зэвсэг −" + next.percent(s) + " % (шарх, идэвхтэй тоглолтоор эдгэнэ)",
                        NamedTextColor.GRAY));
        summaries.put(p.getUniqueId(), summary);
        services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of("player_death", p.getUniqueId(),
                Map.<String, Object>of("exp_lost", expLost, "stacks_dropped", dropped.size(), "lock_ms", lockMs, "level", level)));
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

    /** Store the lock (async, ordered); the in-memory state is authoritative for this server right away. */
    private void lock(Player p, int level, long now, long until, String cause) {
        UUID id = p.getUniqueId();
        Location l = p.getLocation();
        DeathRecord rec = new DeathRecord(UUID.randomUUID(), id, 0, now, until, l.getWorld() == null ? "" : l.getWorld().getName(),
                l.getBlockX(), l.getBlockY(), l.getBlockZ(), cause, level, 0, wound(id).stacks(), DeathRecord.State.LOCKED, null, 0);
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
        });
        locks.put(id, stored);
        lockedUntil.put(id, until);
    }

    /** Class weapons, armour and the menu item never drop. */
    private boolean isSoulbound(ItemStack it) {
        if (it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(menuKey, PersistentDataType.BYTE)) {
            return true;
        }
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
        p.showTitle(Title.title(
                Component.text("ТА УНАЛАА", SOUL, TextDecoration.BOLD),
                Component.text(forever ? "Админ таныг амилуулахыг хүлээ" : "Сүнс тань " + DeathLock.format(until - System.currentTimeMillis()) + " дараа буцна",
                        NamedTextColor.WHITE, TextDecoration.BOLD),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(700))));
        p.playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.8f);
    }

    private void applySoulEffects(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 4, false, false, true));
    }

    private void updateBar(Player p, BossBar bar, long until) {
        long left = Math.max(0, until - System.currentTimeMillis());
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
        if (damager instanceof Player p && isSoul(p.getUniqueId())) e.setCancelled(true);
    }

    /** …and nothing can hurt a soul. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSoulDamaged(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && isSoul(p.getUniqueId())
                && e.getCause() != EntityDamageEvent.DamageCause.VOID && e.getCause() != EntityDamageEvent.DamageCause.KILL) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
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

    /** Containers, merchants, anvils… are closed to a soul; SÜLD menus and the player's own inventory stay open. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSoulOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !isSoul(p.getUniqueId())) return;
        InventoryType t = e.getInventory().getType();
        if (t == InventoryType.PLAYER || t == InventoryType.CRAFTING) return;
        if (e.getInventory().getHolder() instanceof mn.suld.plugin.gui.Menu) return;
        if (e.getInventory().getHolder() instanceof mn.suld.plugin.gui.ClassSelectionHolder) return;
        e.setCancelled(true);
        p.sendMessage(Messages.error("Сүнс эд зүйлд хүрч чадахгүй. Сэргэх хүртэл: " + DeathLock.format(lockRemaining(p.getUniqueId()))));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        BossBar bar = bars.remove(id);
        if (bar != null) e.getPlayer().hideBossBar(bar);
        summaries.remove(id);
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
