package mn.suld.plugin.death;

import mn.suld.api.config.DeathSettings;
import mn.suld.api.death.DeathRules;
import mn.suld.api.item.ItemInstance;
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
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The hardcore death system ({@code death:} in config.yml), applying {@link DeathRules}:
 * <ul>
 *   <li>Soulbound gear is always kept: class weapons, armour and the menu item. Relics are left to the relic system.</li>
 *   <li>A configured fraction of the other stacks drops where the player died; the rest is kept.</li>
 *   <li>A fraction of the progress into the current level is lost (never a level); no vanilla EXP orbs.</li>
 *   <li>Kept damageable gear loses a fraction of its durability (never breaks).</li>
 *   <li>The player respawns as a <b>Сүнс</b> (soul) for {@code soul-state-seconds}: slowed, cannot deal or take damage,
 *       mobs ignore them; a boss bar counts down. With {@code allow-free-revive} the soul recovers on its own,
 *       otherwise it waits for {@code /revive}.</li>
 * </ul>
 */
public final class DeathService implements Listener {

    private static final TextColor SOUL = TextColor.fromHexString("#9FD8FF");

    private final Plugin plugin;
    private final SuldServices services;
    private final NamespacedKey menuKey;
    /** Soul state: player → epoch millis it ends (Long.MAX_VALUE = until revived). */
    private final Map<UUID, Long> souls = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    /** What the last death cost, shown after respawn. */
    private final Map<UUID, Component> summaries = new ConcurrentHashMap<>();

    public DeathService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        this.menuKey = new NamespacedKey(plugin, "menu_item");
    }

    private DeathSettings settings() {
        return services.config().death();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    // ---- death -------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        DeathSettings s = settings();
        if (!s.enabled() || e.getKeepInventory()) return;
        Player p = e.getEntity();

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
        PlayerProfile profile = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (profile != null) {
            Progression before = profile.progression();
            Progression after = DeathRules.afterDeath(before, s);
            expLost = before.expIntoLevel() - after.expIntoLevel();
            if (expLost > 0) profile.progression(after);
        }

        int droppedItems = dropped.stream().mapToInt(ItemStack::getAmount).sum();
        Component summary = Component.text("☠ Үхлийн үнэ: ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text("-" + expLost + " EXP", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" · " + dropped.size() + " багц (" + droppedItems + " ш) унасан", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(worn > 0 ? " · " + worn + " хэрэгсэл элэгдсэн" : "", NamedTextColor.WHITE, TextDecoration.BOLD));
        summaries.put(p.getUniqueId(), summary);
        services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of("player_death", p.getUniqueId(),
                Map.<String, Object>of("exp_lost", expLost, "stacks_dropped", dropped.size())));
    }

    /** Class weapons, armour and the menu item never drop. */
    private boolean isSoulbound(ItemStack it) {
        if (it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(menuKey, PersistentDataType.BYTE)) {
            return true;
        }
        String name = it.getType().name();
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS") || name.equals("ELYTRA") || name.equals("SHIELD")) {
            return true;
        }
        ItemInstance ii = services.items().read(it).orElse(null);
        return ii != null && (ii.soulbound() || ii.definitionId().startsWith("weapon.class.") || ii.definitionId().startsWith("weapon.surgamj_"));
    }

    /** Applies death wear to a damageable item in place; true if it was worn. */
    private boolean wear(ItemStack it, DeathSettings s) {
        int max = it.getType().getMaxDurability();
        if (max <= 0 || !(it.getItemMeta() instanceof Damageable d) || d.isUnbreakable()) return false;
        int next = DeathRules.wear(d.getDamage(), max, s);
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
            if (s.soulStateSeconds() > 0) {
                long until = s.allowFreeRevive() ? System.currentTimeMillis() + s.soulStateSeconds() * 1000L : Long.MAX_VALUE;
                enterSoul(p, until);
            }
        });
    }

    private void enterSoul(Player p, long until) {
        souls.put(p.getUniqueId(), until);
        applySoulEffects(p);
        BossBar bar = bars.computeIfAbsent(p.getUniqueId(),
                id -> BossBar.bossBar(Component.empty(), 1f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10));
        p.showBossBar(bar);
        updateBar(p, bar, until);
        p.showTitle(Title.title(
                Component.text("СҮНС", SOUL, TextDecoration.BOLD),
                Component.text(until == Long.MAX_VALUE ? "Админ таныг амилуулахыг хүлээ (/revive)" : "Сүнс тань бие рүүгээ буцаж байна…",
                        NamedTextColor.WHITE, TextDecoration.BOLD),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
        p.playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.8f);
    }

    private void applySoulEffects(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 4, false, false, true));
    }

    private void updateBar(Player p, BossBar bar, long until) {
        if (until == Long.MAX_VALUE) {
            bar.name(Component.text("☠ Сүнс — /revive хүлээж байна", SOUL, TextDecoration.BOLD));
            bar.progress(1f);
            return;
        }
        long left = Math.max(0, until - System.currentTimeMillis());
        int total = Math.max(1, settings().soulStateSeconds());
        bar.name(Component.text("☠ Сүнс — " + (left + 999) / 1000 + " сек", SOUL, TextDecoration.BOLD));
        bar.progress(Math.max(0f, Math.min(1f, left / (total * 1000f))));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> en : souls.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) {
                if (en.getValue() <= now) souls.remove(en.getKey());
                continue;
            }
            if (en.getValue() <= now) {
                revive(p, false);
                continue;
            }
            applySoulEffects(p);
            BossBar bar = bars.get(p.getUniqueId());
            if (bar != null) updateBar(p, bar, en.getValue());
            p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.01);
        }
    }

    /** Plugin disable: take the countdown bars off the players' screens. */
    public void shutdown() {
        for (Map.Entry<UUID, BossBar> en : bars.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null) p.hideBossBar(en.getValue());
        }
        bars.clear();
        souls.clear();
    }

    public boolean isSoul(UUID player) {
        return souls.containsKey(player);
    }

    /** Ends the soul state (timer or {@code /revive}); true if the player was a soul. */
    public boolean revive(Player p, boolean byAdmin) {
        if (souls.remove(p.getUniqueId()) == null) return false;
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) p.hideBossBar(bar);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.removePotionEffect(PotionEffectType.WEAKNESS);
        if (!byAdmin) {
            p.showTitle(Title.title(Component.text("АМИЛЛАА", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text("Тэнгэр таныг ивээг", NamedTextColor.WHITE, TextDecoration.BOLD),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
            p.sendMessage(Messages.accent("Сүнс тань бие рүүгээ буцлаа. Болгоомжтой яваарай."));
        }
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.2);
        return true;
    }

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

    /** Relogging does not end the soul state; the timer keeps running while offline. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Long until = souls.get(e.getPlayer().getUniqueId());
        if (until != null && until <= System.currentTimeMillis()) {
            souls.remove(e.getPlayer().getUniqueId());
            return;
        }
        if (until != null) Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline() && souls.containsKey(e.getPlayer().getUniqueId())) enterSoul(e.getPlayer(), until);
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        BossBar bar = bars.remove(e.getPlayer().getUniqueId());
        if (bar != null) e.getPlayer().hideBossBar(bar);
        summaries.remove(e.getPlayer().getUniqueId());
        Long until = souls.get(e.getPlayer().getUniqueId());
        if (until != null && until != Long.MAX_VALUE && until <= System.currentTimeMillis()) souls.remove(e.getPlayer().getUniqueId());
    }
}
