package mn.suld.plugin.item;

import mn.suld.api.item.Consumables;
import mn.suld.api.item.Consumables.Consumable;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemType;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.death.DeathService;
import mn.suld.plugin.skill.SkillService;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The steppe consumables (audit 2026-10-14 IT-12 / IT-20, rules in {@link Consumables}): a right click with one in the
 * main hand uses it. Order, so a crash can never keep both the item and its effect: the checks first (cooldown, a
 * soul, the whetstone's target and place), then one is taken from the hand and the player file is written
 * ({@link PlayerDataSaves#now}), then the effect runs. The vanilla use (drinking the flask, planting the reed) never
 * happens. Cooldowns are per player and per consumable, kept for the session.
 */
public final class ConsumableService implements Listener {

    /** A whetstone needs a breather: none within this long of being hurt. */
    static final long REPAIR_COMBAT_MS = 5_000;

    private final Plugin plugin;
    private final SuldServices services;
    private final SkillService skills;
    private final DeathService deaths;
    /** "player:item" -> last use (ms). */
    private final Map<String, Long> lastUse = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHurt = new ConcurrentHashMap<>();
    /** Player id -> until (ms) of Арц Хүж's ward; read by CombatListener through {@link #wardedAgainst}. */
    private static final Map<UUID, Long> WARD_UNTIL = new ConcurrentHashMap<>();

    public ConsumableService(Plugin plugin, SuldServices services, SkillService skills, DeathService deaths) {
        this.plugin = plugin;
        this.services = services;
        this.skills = skills;
        this.deaths = deaths;
    }

    /** True while {@code player} is under Арц Хүж and {@code mobId} is a spirit: the hit is reduced by the ward's share. */
    public static boolean wardedAgainst(UUID player, String mobId) {
        Long until = WARD_UNTIL.get(player);
        return until != null && until >= System.currentTimeMillis() && Consumables.spirit(mobId);
    }

    /** The consumable a stack is, if it is one. */
    private Consumable consumable(ItemStack it) {
        if (it == null || it.getType().isAir()) return null;
        ItemInstance ii = services.items().read(it).orElse(null);
        if (ii == null) return null;
        ItemDefinition def = SuldContent.definitionFor(ii.definitionId());
        if (def == null || def.type() != ItemType.CONSUMABLE) return null;
        return Consumables.of(def.id()).orElse(null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Consumable c = consumable(e.getItem());
        if (c == null) return;
        // never the vanilla use (drink the flask, plant the reed, place on a block), in either hand
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        if (e.getHand() != EquipmentSlot.HAND) return;
        use(e.getPlayer(), c);
    }

    /** Defence in depth: a consumable is never eaten or drunk the vanilla way. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVanillaConsume(PlayerItemConsumeEvent e) {
        if (consumable(e.getItem()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) lastHurt.put(p.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastHurt.remove(e.getPlayer().getUniqueId());
        WARD_UNTIL.remove(e.getPlayer().getUniqueId()); // the incense burns out with the session
        // used cooldowns that have run out go; one still running stays, so leaving and coming back never resets it
        String prefix = e.getPlayer().getUniqueId() + ":";
        long now = System.currentTimeMillis();
        lastUse.entrySet().removeIf(en -> en.getKey().startsWith(prefix)
                && Consumables.of(en.getKey().substring(prefix.length())).map(c -> Consumables.cooldownLeftMs(c, en.getValue(), now) <= 0).orElse(true));
    }

    private void use(Player p, Consumable c) {
        UUID id = p.getUniqueId();
        if (services.isSoul.test(id) && !services.inDeathTrial.test(id)) {
            refuse(p, "Сүнс байхдаа хэрэглэх боломжгүй.");
            return;
        }
        long now = System.currentTimeMillis();
        String key = id + ":" + c.itemId();
        long left = Consumables.cooldownLeftMs(c, lastUse.get(key), now);
        if (left > 0) {
            refuse(p, "Дахин хэрэглэхэд " + (left + 999) / 1000 + " сек.");
            return;
        }
        ItemStack target = null;
        if (c.kind() == Consumables.Kind.REPAIR) {
            if (services.inCity(p.getLocation())) {
                refuse(p, "Хархорумд дархан өөрөө засна — билүү хотоос гадна хэрэглэгдэнэ.");
                return;
            }
            Long hurt = lastHurt.get(id);
            if (hurt != null && now - hurt < REPAIR_COMBAT_MS) {
                refuse(p, "Тулалдааны дунд засах боломжгүй — түр амсхий.");
                return;
            }
            target = repairTarget(p);
            if (target == null) {
                refuse(p, "Засах шаардлагатай зэвсэг, хуяг алга.");
                return;
            }
        }
        // the item off the hand and onto disk before the effect (a crash can lose the effect, never repeat it)
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (consumable(hand) != c) return; // the hand changed since the click
        hand.setAmount(hand.getAmount() - 1);
        p.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        PlayerDataSaves.now(p);
        lastUse.put(key, now);
        apply(p, c, target);
        services.audit().record(mn.suld.api.audit.AuditEvent.of(id.toString(), "item.consume", c.itemId(), c.kind().name()));
    }

    private void refuse(Player p, String why) {
        mn.suld.plugin.hud.HudService.notice(p, net.kyori.adventure.text.Component.text("✖ " + why, net.kyori.adventure.text.format.NamedTextColor.RED));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
    }

    private void apply(Player p, Consumable c, ItemStack target) {
        switch (c.kind()) {
            case RESOURCE -> {
                if (skills != null) {
                    var pool = skills.pool(p);
                    pool.gain(Consumables.resourceRestore(c, pool.max()));
                    skills.boostRegen(p, c.bonus(), c.durationSeconds());
                    services.hud().refresh(p);
                }
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.9f, 1.1f);
                p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1.2, 0), 10, 0.3, 0.4, 0.3, 0);
                p.sendMessage(Messages.success("Айраг: нөөц сэргэлээ, " + c.durationSeconds() + " сек нөөц хурдан нөхөгдөнө."));
            }
            case HEAL_OVER_TIME -> {
                for (PotionEffectType t : new PotionEffectType[] {PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.SLOWNESS}) {
                    p.removePotionEffect(t);
                }
                var max = p.getAttribute(Attribute.MAX_HEALTH);
                double perSecond = Consumables.healPerSecond(c, max == null ? 20 : max.getValue());
                new BukkitRunnable() {
                    int ticks;

                    @Override
                    public void run() {
                        if (!p.isOnline() || p.isDead() || ++ticks > c.durationSeconds()) {
                            cancel();
                            return;
                        }
                        var m = p.getAttribute(Attribute.MAX_HEALTH);
                        p.setHealth(Math.min(m == null ? 20 : m.getValue(), p.getHealth() + perSecond));
                        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2.0, 0), 1, 0.2, 0.1, 0.2, 0);
                    }
                }.runTaskTimer(plugin, 0L, 20L);
                p.playSound(p.getLocation(), Sound.BLOCK_GRASS_PLACE, 0.8f, 1.3f);
                p.sendMessage(Messages.success("Талын Эмийн Ургамал: шарх эдгэж, хор арилав."));
            }
            case WARD -> {
                WARD_UNTIL.put(p.getUniqueId(), System.currentTimeMillis() + c.durationSeconds() * 1000L); // refreshes, never stacks
                if (deaths != null) deaths.healWound(p, 10); // the juniper smoke eases a death wound by ten minutes of healing
                p.playSound(p.getLocation(), Sound.BLOCK_CAMPFIRE_CRACKLE, 1f, 1.0f);
                p.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, p.getLocation().add(0, 1.0, 0), 8, 0.3, 0.3, 0.3, 0.01);
                p.sendMessage(Messages.success("Арц Хүж: " + c.durationSeconds() / 60 + " минут сүнснүүдийн хохирол −"
                        + Math.round(c.amount() * 100) + "%."));
            }
            case REPAIR -> {
                if (target != null && target.getItemMeta() instanceof Damageable d) {
                    d.setDamage(Consumables.repairedDamage(c, d.getDamage(), target.getType().getMaxDurability()));
                    target.setItemMeta(d);
                    PlayerDataSaves.now(p); // the repaired durability too
                }
                p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.9f, 1.2f);
                p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1.2, 0), 12, 0.3, 0.3, 0.3, 0.05);
                p.sendMessage(Messages.success("Дарханы Билүү: бат бэх " + Math.round(c.amount() * 100) + "% засагдлаа."));
            }
        }
    }

    /**
     * The whetstone's target: the most worn of the weapons at hand (off hand, hotbar — the stone itself is in the main
     * hand) and the armour worn.
     */
    private ItemStack repairTarget(Player p) {
        ItemStack best = null;
        int most = 0;
        ItemStack held = p.getInventory().getItemInMainHand();
        java.util.List<ItemStack> candidates = new java.util.ArrayList<>();
        candidates.add(p.getInventory().getItemInOffHand());
        for (int slot = 0; slot < 9; slot++) candidates.add(p.getInventory().getItem(slot));
        candidates.addAll(java.util.Arrays.asList(p.getInventory().getArmorContents()));
        for (ItemStack it : candidates) {
            if (it != null && it != held && services.items().read(it).isEmpty()) continue; // SÜLD gear only
            if (it == null || it == held || it.getType().getMaxDurability() <= 0 || !(it.getItemMeta() instanceof Damageable d)) continue;
            if (d.isUnbreakable() || d.getDamage() <= most) continue;
            most = d.getDamage();
            best = it;
        }
        return best;
    }
}
