package mn.suld.plugin.item;

import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemStat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds Bukkit {@link ItemStack}s from SÜLD {@link ItemInstance}s (and reads
 * them back), writing identity/stats into the item's PersistentDataContainer so
 * the server stays authoritative over item identity (anti-dup / anti-spoof).
 * Also renders the premium MMORPG tooltip, styled by rarity.
 */
public final class ItemFactory {

    private final NamespacedKey keyId;
    private final NamespacedKey keyUuid;
    private final NamespacedKey keyRarity;
    private final NamespacedKey keyLevel;
    private final NamespacedKey keyStats;
    private final NamespacedKey keySoulbound;
    private final NamespacedKey keyUpgrade;

    public ItemFactory(Plugin plugin) {
        this.keyId = new NamespacedKey(plugin, "item_id");
        this.keyUuid = new NamespacedKey(plugin, "item_uuid");
        this.keyRarity = new NamespacedKey(plugin, "rarity");
        this.keyLevel = new NamespacedKey(plugin, "item_level");
        this.keyStats = new NamespacedKey(plugin, "stats");
        this.keySoulbound = new NamespacedKey(plugin, "soulbound");
        this.keyUpgrade = new NamespacedKey(plugin, "upgrade");
    }

    public ItemStack create(ItemInstance instance, ItemDefinition def) {
        Material material = Optional.ofNullable(
                        Material.matchMaterial(def.baseMaterial().replace("minecraft:", "")))
                .orElse(Material.PAPER);
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();

        TextColor color = rarityColor(instance.rarity());
        meta.displayName(Component.text(def.displayName() + (instance.upgradeLevel() > 0 ? " +" + instance.upgradeLevel() : ""), color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, instance.rarity().ordinal() >= ItemRarity.LEGENDARY.ordinal()));

        List<Component> lore = new ArrayList<>();
        lore.add(line(instance.rarity().displayName() + " · Зэрэг " + instance.itemLevel(), color));
        lore.add(Component.empty());
        for (Map.Entry<ItemStat, Double> e : instance.stats().entrySet()) {
            String val = e.getKey().percent()
                    ? "+" + Math.round(e.getValue() * 100) + "%"
                    : "+" + trim(e.getValue());
            lore.add(line("  " + val + " " + e.getKey().label(), NamedTextColor.WHITE));
        }
        if (instance.soulbound()) {
            lore.add(line("✦ Сүнсэнд холбоотой (Soulbound)", NamedTextColor.GOLD));
        }
        if (instance.rarity().isServerUnique()) {
            lore.add(line("✦ ДЭЛХИЙД ГАНЦ · 1 / 1", NamedTextColor.AQUA));
        }
        lore.add(Component.empty());
        lore.add(line("SÜLD", NamedTextColor.GRAY));
        meta.lore(lore);

        if (def.customModelData() > 0) {
            meta.setCustomModelData(def.customModelData());
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyId, PersistentDataType.STRING, instance.definitionId());
        pdc.set(keyUuid, PersistentDataType.STRING, instance.uuid().toString());
        pdc.set(keyRarity, PersistentDataType.STRING, instance.rarity().id());
        pdc.set(keyLevel, PersistentDataType.INTEGER, instance.itemLevel());
        pdc.set(keySoulbound, PersistentDataType.INTEGER, instance.soulbound() ? 1 : 0);
        if (instance.upgradeLevel() > 0) pdc.set(keyUpgrade, PersistentDataType.INTEGER, instance.upgradeLevel());
        pdc.set(keyStats, PersistentDataType.STRING, encodeStats(instance.stats()));

        stack.setItemMeta(meta);
        return stack;
    }

    /** Read a SÜLD item back from a stack, if it is one. */
    public Optional<ItemInstance> read(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(keyId, PersistentDataType.STRING);
        String uuid = pdc.get(keyUuid, PersistentDataType.STRING);
        if (id == null || uuid == null) {
            return Optional.empty();
        }
        ItemRarity rarity = ItemRarity.byId(pdc.getOrDefault(keyRarity, PersistentDataType.STRING, "common"))
                .orElse(ItemRarity.COMMON);
        int level = pdc.getOrDefault(keyLevel, PersistentDataType.INTEGER, 1);
        boolean soulbound = pdc.getOrDefault(keySoulbound, PersistentDataType.INTEGER, 0) == 1;
        Map<ItemStat, Double> stats = decodeStats(pdc.getOrDefault(keyStats, PersistentDataType.STRING, ""));
        int upgrade = pdc.getOrDefault(keyUpgrade, PersistentDataType.INTEGER, 0);
        return Optional.of(new ItemInstance(id, UUID.fromString(uuid), rarity, level, stats, soulbound, upgrade, "stack"));
    }

    /**
     * Bring SÜLD items made before their skin existed up to date: set the definition's current
     * custom model data (the resource-pack skin). Returns how many stacks changed.
     */
    public int refreshModels(ItemStack[] stacks) {
        int changed = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || !stack.hasItemMeta()) continue;
            ItemMeta meta = stack.getItemMeta();
            String id = meta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
            if (id == null) continue;
            ItemDefinition def = mn.suld.plugin.content.SuldContent.definitionFor(id);
            if (def == null || def.customModelData() <= 0) continue;
            if (meta.hasCustomModelData() && meta.getCustomModelData() == def.customModelData()) continue;
            meta.setCustomModelData(def.customModelData());
            stack.setItemMeta(meta);
            changed++;
        }
        return changed;
    }

    public boolean isSuldItem(ItemStack stack) {
        return read(stack).isPresent();
    }

    private static Component line(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static String encodeStats(Map<ItemStat, Double> stats) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<ItemStat, Double> e : stats.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey().id()).append(':').append(e.getValue());
        }
        return sb.toString();
    }

    private static Map<ItemStat, Double> decodeStats(String encoded) {
        Map<ItemStat, Double> out = new EnumMap<>(ItemStat.class);
        if (encoded == null || encoded.isEmpty()) {
            return out;
        }
        for (String part : encoded.split(";")) {
            String[] kv = part.split(":");
            if (kv.length == 2) {
                ItemStat.byId(kv[0]).ifPresent(stat -> {
                    try {
                        out.put(stat, Double.parseDouble(kv[1]));
                    } catch (NumberFormatException ignored) {
                        // skip malformed stat
                    }
                });
            }
        }
        return out;
    }

    public static TextColor rarityColor(ItemRarity rarity) {
        TextColor c = TextColor.fromHexString(rarity.colorHex());
        return c == null ? NamedTextColor.WHITE : c;
    }
}
