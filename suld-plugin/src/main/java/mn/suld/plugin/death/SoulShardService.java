package mn.suld.plugin.death;

import mn.suld.api.audit.AuditEvent;
import mn.suld.api.death.SoulShard;
import mn.suld.api.death.Wound;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * «Сүнсний Хэлтэрхий» as an item (api {@link SoulShard}): an echo shard that a won «Тэнгэрийн сорил» leaves in the
 * winner's bag. A right-click heals its tier's active minutes of the death wound at once. Only its winner may use it;
 * it fades after {@link SoulShard#LIFETIME_DAYS} days (a faded shard crumbles when it is used). It is kept on death
 * like class gear, never revives anything and has no price. The spent shard reaches the player file before the wound
 * is stored, so a crash between the two can only lose the healing, never repeat it.
 */
public final class SoulShardService implements Listener {

    /** Fixed namespace, so the death rules can recognise a shard without the plugin instance. */
    private static final NamespacedKey KEY = new NamespacedKey("suld", "soul_shard");
    private static final TextColor SOUL = TextColor.fromHexString("#9FD8FF");

    private final Plugin plugin;
    private final SuldServices services;
    private final DeathService deaths;
    private final DateTimeFormatter when;

    public SoulShardService(Plugin plugin, SuldServices services, DeathService deaths) {
        this.plugin = plugin;
        this.services = services;
        this.deaths = deaths;
        this.when = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(services.zone());
    }

    /** True for a Сүнсний Хэлтэрхий (whatever its state). */
    public static boolean isShard(ItemStack it) {
        return it != null && it.getType() == Material.ECHO_SHARD && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.STRING);
    }

    /** The shard an item carries, if it is one and its data reads. */
    public static Optional<SoulShard> read(ItemStack it) {
        if (!isShard(it)) return Optional.empty();
        String[] v = it.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING).split(";");
        try {
            return Optional.of(new SoulShard(SoulShard.Tier.valueOf(v[0]), UUID.fromString(v[1]), Long.parseLong(v[2])));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** The item of {@code shard}, named for {@code ownerName}. */
    public ItemStack item(SoulShard shard, String ownerName) {
        ItemStack it = new ItemStack(Material.ECHO_SHARD);
        ItemMeta m = it.getItemMeta();
        m.itemName(Component.text("Сүнсний Хэлтэрхий", SOUL, TextDecoration.BOLD));
        m.lore(List.of(
                line("Тэнгэрийн сорилын шагнал", NamedTextColor.GRAY),
                line("Баруун товч: шархыг " + shard.tier().healMinutes() + " идэвхтэй минутаар эдгээнэ", NamedTextColor.WHITE),
                line("Зөвхөн " + ownerName + " · " + when.format(Instant.ofEpochMilli(shard.expiresAt())) + " хүртэл", NamedTextColor.GRAY),
                line("Үхэхэд унахгүй", NamedTextColor.GRAY)));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(KEY, PersistentDataType.STRING,
                shard.tier().name() + ";" + shard.boundTo() + ";" + shard.expiresAt());
        it.setItemMeta(m);
        return it;
    }

    private static Component line(String text, NamedTextColor colour) {
        return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
    }

    /** A won trial's shard into the bag (a full bag: at the feet, where only its winner can make use of it). */
    public void give(Player p, SoulShard.Tier tier) {
        SoulShard shard = SoulShard.won(tier, p.getUniqueId(), System.currentTimeMillis());
        for (ItemStack rest : p.getInventory().addItem(item(shard, p.getName())).values()) {
            p.getWorld().dropItem(p.getLocation(), rest);
        }
        p.sendMessage(Component.text("✦ Сүнсний Хэлтэрхий олдлоо: ", SOUL, TextDecoration.BOLD)
                .append(Component.text("баруун товч дарж шархаа " + tier.healMinutes() + " идэвхтэй минутаар эдгээ ("
                        + SoulShard.LIFETIME_DAYS + " хоног хадгалагдана).", NamedTextColor.WHITE)));
        services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of("soul_shard_won", p.getUniqueId(),
                Map.<String, Object>of("tier", tier.name())));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isShard(e.getItem())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (deaths.isSoul(id)) {
            p.sendMessage(Messages.error("Сүнс хэлтэрхий хэрэглэхгүй: эхлээд амил."));
            return;
        }
        EquipmentSlot hand = e.getHand() == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        ItemStack held = p.getInventory().getItem(hand);
        SoulShard shard = read(held).orElse(null);
        if (shard == null) return;
        long now = System.currentTimeMillis();
        if (shard.expired(now)) {
            p.getInventory().setItem(hand, null);
            p.sendMessage(Messages.info("Хэлтэрхий бүдгэрч алга болов (" + SoulShard.LIFETIME_DAYS + " хоног өнгөрсөн)."));
            return;
        }
        if (!shard.boundTo().equals(id)) {
            p.sendMessage(Messages.error("Энэ хэлтэрхий өөр баатрын сорилоос гарсан: зөвхөн тэр хэрэглэнэ."));
            return;
        }
        Wound before = deaths.wound(id);
        if (!shard.usable(id, now, before)) {
            p.sendMessage(Messages.info("Танд шарх алга: хэлтэрхийгээ дараагийн шархад хадгал."));
            return;
        }
        held.setAmount(held.getAmount() - 1);
        p.getInventory().setItem(hand, held.getAmount() <= 0 ? null : held);
        mn.suld.plugin.item.PlayerDataSaves.now(p); // the spent shard on disk first (PERS order)
        Wound after = deaths.healWound(p, shard.tier().healMinutes());
        var s = services.config().death();
        p.sendMessage(Component.text("✦ Сүнсний Хэлтэрхий: ", SOUL, TextDecoration.BOLD)
                .append(Component.text(after.stacks() == 0 ? "шарх бүрэн эдгэлээ." : "шарх " + shard.tier().healMinutes()
                        + " минутаар эдгэв — одоо −" + after.percent(s) + " %.", NamedTextColor.WHITE)));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        p.getWorld().spawnParticle(Particle.SCULK_SOUL, p.getLocation().add(0, 1, 0), 24, 0.4, 0.6, 0.4, 0.02);
        services.audit().record(AuditEvent.of(id.toString(), "death.shard_use", id.toString(),
                shard.tier() + " wound " + before.stacks() + "/" + Math.round(before.healMinutes()) + " -> "
                        + after.stacks() + "/" + Math.round(after.healMinutes())));
        services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of("soul_shard_use", id,
                Map.<String, Object>of("tier", shard.tier().name(), "stacks_before", before.stacks(), "stacks_after", after.stacks())));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) services.profiles().cached(id).ifPresent(pr -> services.hud().update(p, pr));
        });
    }
}
