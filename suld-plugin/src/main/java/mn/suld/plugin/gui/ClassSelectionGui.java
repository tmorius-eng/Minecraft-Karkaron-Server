package mn.suld.plugin.gui;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.event.ClassSelectedEvent;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.hud.HudService;
import mn.suld.plugin.item.ItemFactory;
import mn.suld.plugin.quest.QuestService;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * First-login class selection GUI. Shows each class's role, difficulty, stats,
 * unique resource, starter skills, and lore. A first-time player cannot escape
 * the menu without choosing (it reopens on close) — custom Bukkit inventory GUI,
 * no menu plugin.
 */
public final class ClassSelectionGui implements Listener {

    private static final int[] SLOTS = {10, 11, 12, 13, 14};
    private static final Map<PlayerClass, Material> ICONS = Map.of(
            PlayerClass.BAATAR, Material.IRON_SWORD,
            PlayerClass.MERGEN, Material.BOW,
            PlayerClass.BOO, Material.BLAZE_ROD,
            PlayerClass.DARKHAN, Material.NETHERITE_INGOT,
            PlayerClass.KHULEGCHIN, Material.SADDLE);
    private static final Map<PlayerClass, String> STARTER_SKILL = Map.of(
            PlayerClass.BAATAR, "Тэнгэрийн Цавчилт",
            PlayerClass.MERGEN, "Чонын Нүд",
            PlayerClass.BOO, "Сүнсний Залбирал",
            PlayerClass.DARKHAN, "Галын Давталт",
            PlayerClass.KHULEGCHIN, "Хурдан Довтолгоо");

    private final Plugin plugin;
    private final SuldServices services;
    private final HudService hud;
    private final QuestService quests;
    private final ItemFactory items;
    private final java.util.Set<UUID> mustChoose = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> reopening = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public ClassSelectionGui(Plugin plugin, SuldServices services, HudService hud,
                             QuestService quests, ItemFactory items) {
        this.plugin = plugin;
        this.services = services;
        this.hud = hud;
        this.quests = quests;
        this.items = items;
    }

    public void open(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof ClassSelectionHolder) {
            return; // already showing: re-opening would close it and trigger another reopen (loop)
        }
        ClassSelectionHolder holder = new ClassSelectionHolder();
        Inventory inv = org.bukkit.Bukkit.createInventory(holder, 27,
                Component.text("Анги Сонгох — SÜLD", Messages.BRAND));
        holder.setInventory(inv);
        PlayerClass[] classes = PlayerClass.values();
        for (int i = 0; i < classes.length && i < SLOTS.length; i++) {
            inv.setItem(SLOTS[i], icon(classes[i]));
        }
        mustChoose.add(player.getUniqueId());
        player.openInventory(inv);
    }

    private ItemStack icon(PlayerClass clazz) {
        ItemStack stack = new ItemStack(ICONS.getOrDefault(clazz, Material.PAPER));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(clazz.displayName(), Messages.BRAND, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(line("Үүрэг: " + role(clazz), NamedTextColor.WHITE));
        lore.add(line("Хүндрэл: " + "★".repeat(clazz.difficulty()) + "☆".repeat(Math.max(0, 5 - clazz.difficulty())),
                NamedTextColor.YELLOW));
        lore.add(line("Нөөц: " + clazz.resourceName(), NamedTextColor.AQUA));
        lore.add(line("HP " + (int) clazz.baseHealth() + "  ·  ATK " + (int) clazz.baseAttack(), NamedTextColor.WHITE));
        lore.add(line("Эхлэл ур: " + STARTER_SKILL.getOrDefault(clazz, "—"), NamedTextColor.GREEN));
        lore.add(Component.empty());
        lore.add(line(lore(clazz), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(line("» Сонгохын тулд дар «", NamedTextColor.GOLD));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ClassSelectionHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        PlayerClass[] classes = PlayerClass.values();
        for (int i = 0; i < SLOTS.length && i < classes.length; i++) {
            if (slot == SLOTS[i]) {
                select(player, classes[i]);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ClassSelectionHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        // First-time players must pick: reopen next tick if THEY closed it without choosing. A close caused
        // by another inventory opening (or a plugin) is not reopened, and at most one reopen is pending.
        if (mustChoose.contains(player.getUniqueId()) && event.getReason() == InventoryCloseEvent.Reason.PLAYER
                && reopening.add(player.getUniqueId())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                reopening.remove(player.getUniqueId());
                if (player.isOnline() && mustChoose.contains(player.getUniqueId())) {
                    open(player);
                }
            });
        }
    }

    private void select(Player player, PlayerClass clazz) {
        PlayerProfile profile = services.profiles().cached(player.getUniqueId()).orElse(null);
        if (profile == null) {
            player.sendMessage(Messages.error("Профайл ачааллагдаагүй байна."));
            return;
        }
        if (!profile.selectClass(clazz)) {
            player.sendMessage(Messages.info("Та аль хэдийн анги сонгосон байна."));
            return;
        }
        mustChoose.remove(player.getUniqueId());

        // Starter equipment.
        // the class weapon (tier follows the level: upgraded in place at 10 / 25 / 45)
        player.getInventory().addItem(services.classWeapons().starter(clazz, profile.progression().level()));

        services.events().dispatch(new ClassSelectedEvent(player.getUniqueId(), clazz));
        services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.CLASS_SELECTED, player.getUniqueId(),
                Map.of("class", clazz.id())));

        quests.startFirstQuestIfNeeded(profile);
        services.profiles().save(profile);

        player.closeInventory();
        player.sendMessage(Messages.accent("Та " + clazz.displayName() + " ангийг сонголоо!"));
        player.sendMessage(Messages.info("Эхний эрэл: " + SuldContent.FIRST_HUNT.title()));
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        hud.update(player, profile);
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static String role(PlayerClass clazz) {
        return switch (clazz) {
            case BAATAR -> "Хамгаалагч / Берсеркер";
            case MERGEN -> "Харваач / Алуурчин";
            case BOO -> "Бөө / Дэмжлэг";
            case DARKHAN -> "Дархан / Зэвсэгчин";
            case KHULEGCHIN -> "Морьтон / Довтолгоо";
        };
    }

    private static String lore(PlayerClass clazz) {
        return switch (clazz) {
            case BAATAR -> "\"Тэнгэрийн дор ганц ч алхам ухрахгүй.\"";
            case MERGEN -> "\"Нэг сум — нэг амь.\"";
            case BOO -> "\"Сүнснүүд түүний дуудлагад хариулна.\"";
            case DARKHAN -> "\"Галд давтагдсан нь мөнх.\"";
            case KHULEGCHIN -> "\"Салхи түүний хойноос гүйцэхгүй.\"";
        };
    }
}
