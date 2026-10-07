package mn.suld.plugin.item;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.event.LevelUpEvent;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemStat;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.event.SuldDomainBukkitEvent;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The five class weapons, each in four tiers that follow the player's level: the weapon a player owns is upgraded
 * in place (same item, same UUID) when they reach level 10, 25 and 45 — a new 3D model, a new name, higher stats,
 * and from tier 2 on visual effects while held and on hit (class-coloured; tier 4 shines with the celestial blue).
 * Models: tools/pack/gen_weapons.py (custom_model_data 871000 + classIndex*10 + tier).
 */
public final class ClassWeapons implements Listener {

    public static final int[] TIER_LEVEL = {0, 1, 10, 25, 45};
    private static final String PREFIX = "weapon.class.";
    private static final java.util.Set<String> LEGACY_STARTERS = java.util.Set.of("weapon.surgamj_ild", "weapon.surgamj_num");

    private record Line(PlayerClass clazz, int index, String base, String[] names, Color color, double crit) {
    }

    private static final Map<PlayerClass, Line> LINES = new EnumMap<>(PlayerClass.class);

    static {
        add(new Line(PlayerClass.BAATAR, 0, "minecraft:iron_sword",
                new String[]{"Сургамжийн Илд", "Цэргийн Ган Илд", "Баатрын Алтан Илд", "Тэнгэрийн Хөх Илд"}, Color.fromRGB(255, 90, 70), 0.05));
        add(new Line(PlayerClass.MERGEN, 1, "minecraft:bow",
                new String[]{"Сургамжийн Нум", "Эвэр Нум", "Мэргэний Алтан Нум", "Тэнгэрийн Хөх Нум"}, Color.fromRGB(110, 220, 110), 0.10));
        add(new Line(PlayerClass.BOO, 2, "minecraft:blaze_rod",
                new String[]{"Бөөгийн Таяг", "Онгоны Таяг", "Сүнсний Алтан Таяг", "Тэнгэрийн Хөх Таяг"}, Color.fromRGB(170, 110, 255), 0.04));
        add(new Line(PlayerClass.DARKHAN, 3, "minecraft:iron_axe",
                new String[]{"Дархны Алх", "Ган Алх", "Галын Алтан Алх", "Тэнгэрийн Хөх Алх"}, Color.fromRGB(255, 150, 50), 0.04));
        add(new Line(PlayerClass.KHULEGCHIN, 4, "minecraft:iron_sword",
                new String[]{"Хүлэгчийн Жад", "Цэргийн Жад", "Хааны Алтан Жад", "Тэнгэрийн Хөх Жад"}, Color.fromRGB(90, 170, 255), 0.07));
    }

    private static void add(Line l) {
        LINES.put(l.clazz(), l);
    }

    private static final double[] ATTACK = {0, 5, 9, 14, 21};
    private static final ItemRarity[] RARITY = {null, ItemRarity.COMMON, ItemRarity.RARE, ItemRarity.EPIC, ItemRarity.LEGENDARY};

    public static String id(PlayerClass c, int tier) {
        return PREFIX + c.id() + "." + tier;
    }

    public static int tierFor(int level) {
        int t = 1;
        for (int i = 1; i < TIER_LEVEL.length; i++) if (level >= TIER_LEVEL[i]) t = i;
        return t;
    }

    /** The catalog definition of a class weapon tier (items/weapons.json). */
    public static ItemDefinition definition(PlayerClass c, int tier) {
        return mn.suld.plugin.content.SuldContent.items().require(id(c, tier));
    }

    /** Resolve {@code weapon.class.<class>.<tier>} ids for the item registry. */
    public static ItemDefinition byId(String id) {
        if (id == null || !id.startsWith(PREFIX)) return null;
        String[] p = id.substring(PREFIX.length()).split("\\.");
        if (p.length != 2) return null;
        for (PlayerClass c : PlayerClass.values()) {
            if (c.id().equals(p[0])) {
                try {
                    int t = Integer.parseInt(p[1]);
                    return t >= 1 && t <= 4 ? definition(c, t) : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private record Held(PlayerClass clazz, int tier) {
    }

    private static Optional<Held> parse(String id) {
        ItemDefinition d = byId(id);
        if (d == null) return Optional.empty();
        String[] p = id.substring(PREFIX.length()).split("\\.");
        for (PlayerClass c : PlayerClass.values()) if (c.id().equals(p[0])) return Optional.of(new Held(c, Integer.parseInt(p[1])));
        return Optional.empty();
    }

    // ------------------------------------------------------------------ runtime

    private final Plugin plugin;
    private final SuldServices services;

    public ClassWeapons(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::auras, 20L, 6L);
    }

    /** The starter weapon for a freshly chosen class (tier by current level, normally 1). */
    public ItemStack starter(PlayerClass c, int level) {
        return starter(c, level, null);
    }

    /** The starter weapon, soulbound to {@code owner}. */
    public ItemStack starter(PlayerClass c, int level, Player owner) {
        ItemDefinition d = definition(c, tierFor(level));
        ItemInstance i = services.itemService().generate(d, d.rarity(), level, owner == null ? null : owner.getUniqueId(), "starter");
        return services.itemService().stack(i, owner, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDomain(SuldDomainBukkitEvent e) {
        if (e.payload() instanceof LevelUpEvent lu) {
            Player p = Bukkit.getPlayer(lu.player());
            if (p != null) upgrade(p, lu.toLevel(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        services.profiles().cached(p.getUniqueId()).ifPresent(pr -> upgrade(p, pr.progression().level(), false));
    }

    /** Bring every class weapon in the inventory up to the tier of {@code level}. */
    public void upgrade(Player p, int level, boolean announce) {
        int want = tierFor(level);
        ItemStack[] inv = p.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < inv.length; i++) {
            ItemStack it = inv[i];
            ItemInstance ii = it == null ? null : services.items().read(it).orElse(null);
            if (ii == null) continue;
            Held h = parse(ii.definitionId()).orElse(null);
            if (h == null && LEGACY_STARTERS.contains(ii.definitionId())) {
                // the old starter saber/bow becomes this player's class weapon
                PlayerClass c = services.profiles().cached(p.getUniqueId()).flatMap(PlayerProfile::playerClass).orElse(null);
                if (c != null) h = new Held(c, 0);
            }
            if (h == null || h.tier() >= want) continue;
            ItemDefinition next = definition(h.clazz(), want);
            // the same item (identity, binding) in its next form: new name, model, stats and affixes
            ItemInstance rolled = services.itemService().generator().generate(next, next.rarity(), level, mn.suld.api.loot.Rng.threadLocal(),
                    "upgrade", p.getUniqueId(), ii.uuid());
            ItemStack up = services.itemService().stack(rolled, p, 1);
            p.getInventory().setItem(i, up);
            changed = true;
            if (announce) {
                Line l = LINES.get(h.clazz());
                TextColor tc = TextColor.color(l.color().asRGB());
                p.showTitle(Title.title(Component.text("ЗЭВСЭГ ХҮЧИРХЭГЖЛЭЭ", tc, TextDecoration.BOLD),
                        Component.text(next.displayName(), TextColor.fromHexString("#FFD24A"), TextDecoration.BOLD),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(600))));
                p.sendMessage(Messages.success("Таны зэвсэг шинэчлэгдлээ: " + next.displayName() + " (ATK +" + (int) ATTACK[want] + ")"));
                p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                burst(p.getLocation().add(0, 1, 0), l.color(), want, 40);
            }
        }
        if (changed) p.updateInventory();
    }

    // ------------------------------------------------------------------ effects

    private Optional<Held> held(Player p) {
        ItemInstance ii = services.items().read(p.getInventory().getItemInMainHand()).orElse(null);
        return ii == null ? Optional.empty() : parse(ii.definitionId());
    }

    /** Held-weapon aura: tier 2 sparks, tier 3 class-coloured dust and runes, tier 4 celestial light. */
    private void auras() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Held h = held(p).orElse(null);
            if (h == null || h.tier() < 2 || p.isInvisible()) continue;
            Location hand = handLocation(p);
            Line l = LINES.get(h.clazz());
            switch (h.tier()) {
                case 2 -> {
                    if (Math.random() < 0.35) p.getWorld().spawnParticle(Particle.CRIT, hand, 1, 0.08, 0.08, 0.08, 0.01);
                }
                case 3 -> {
                    p.getWorld().spawnParticle(Particle.DUST, hand, 2, 0.12, 0.18, 0.12, 0, new Particle.DustOptions(l.color(), 0.8f));
                    if (Math.random() < 0.4) p.getWorld().spawnParticle(Particle.ENCHANT, hand, 2, 0.2, 0.2, 0.2, 0.3);
                }
                default -> {
                    p.getWorld().spawnParticle(Particle.DUST, hand, 3, 0.14, 0.22, 0.14, 0,
                            new Particle.DustOptions(Color.fromRGB(150, 235, 255), 1.0f));
                    if (Math.random() < 0.5) p.getWorld().spawnParticle(Particle.END_ROD, hand, 1, 0.1, 0.15, 0.1, 0.005);
                    if (Math.random() < 0.25) p.getWorld().spawnParticle(Particle.DUST, hand, 2, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(l.color(), 1.1f));
                }
            }
        }
    }

    private static Location handLocation(Player p) {
        Location eye = p.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection().setY(0).normalize();
        org.bukkit.util.Vector right = new org.bukkit.util.Vector(-dir.getZ(), 0, dir.getX());
        return eye.add(dir.multiply(0.45)).add(right.multiply(0.38)).add(0, -0.55, 0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity target)) return;
        Held h = held(p).orElse(null);
        if (h == null || h.tier() < 2) return;
        Location at = target.getLocation().add(0, target.getHeight() * 0.6, 0);
        burst(at, LINES.get(h.clazz()).color(), h.tier(), 6 + h.tier() * 4);
        if (h.tier() >= 4) p.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7f, 1.6f);
    }

    private static void burst(Location at, Color color, int tier, int count) {
        at.getWorld().spawnParticle(Particle.DUST, at, count, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(color, 1.2f));
        if (tier >= 3) at.getWorld().spawnParticle(Particle.CRIT, at, count / 2, 0.3, 0.3, 0.3, 0.2);
        if (tier >= 4) {
            at.getWorld().spawnParticle(Particle.END_ROD, at, count / 2, 0.25, 0.25, 0.25, 0.05);
            at.getWorld().spawnParticle(Particle.DUST, at, count, 0.35, 0.35, 0.35, 0,
                    new Particle.DustOptions(Color.fromRGB(150, 235, 255), 1.3f));
        }
    }

    public static List<ItemDefinition> all() {
        List<ItemDefinition> out = new ArrayList<>();
        for (PlayerClass c : PlayerClass.values()) for (int t = 1; t <= 4; t++) out.add(definition(c, t));
        return out;
    }
}
