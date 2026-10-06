package mn.suld.plugin.relic;

import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.relic.RelicDefinition;
import mn.suld.api.relic.RelicRecord;
import mn.suld.api.relic.RelicValidator;
import mn.suld.plugin.item.ItemFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds and recognises the physical copy of a relic. The copy is only a representation of
 * the database record: it carries the relic key, the minted item UUID and the current
 * generation (record version), which {@link RelicValidator} checks against the database.
 */
public final class RelicItems {

    private final ItemFactory items;
    private final NamespacedKey keyRelic;
    private final NamespacedKey keyGeneration;
    private final NamespacedKey keyItemUuid;

    public RelicItems(Plugin plugin, ItemFactory items) {
        this.items = items;
        this.keyRelic = new NamespacedKey(plugin, "relic_key");
        this.keyGeneration = new NamespacedKey(plugin, "relic_gen");
        this.keyItemUuid = new NamespacedKey(plugin, "item_uuid");
    }

    public ItemStack create(RelicDefinition def, RelicRecord record) {
        ItemDefinition idef = new ItemDefinition(def.key(), def.displayName(), def.baseMaterial(), ItemRarity.UNIQUE,
                def.customModelData(), Map.of(), Map.of(), true);
        ItemInstance inst = new ItemInstance(def.key(), record.itemUuid(), ItemRarity.UNIQUE, 1, Map.of(), true, 0, "relic");
        ItemStack stack = items.create(inst, idef);
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = new ArrayList<>(Optional.ofNullable(meta.lore()).orElse(List.of()));
        int insertAt = Math.max(0, lore.size() - 2);
        List<Component> extra = List.of(
                line(def.lore(), NamedTextColor.GRAY, true),
                Component.empty(),
                line("✦ Эзэмшигч: +" + Math.round(def.expBonus() * 100) + "% EXP", NamedTextColor.AQUA, false),
                line("✦ Эзэмшигч гэрэлтэнэ — бүх хүн таныг харна", NamedTextColor.LIGHT_PURPLE, false),
                line("✦ Тоглогч таныг албал сүлдийг булаана", NamedTextColor.RED, false),
                line("✦ Хаяж, хадгалж, шилжүүлэх боломжгүй", NamedTextColor.DARK_GRAY, false));
        lore.addAll(insertAt, extra);
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyRelic, PersistentDataType.STRING, def.key());
        pdc.set(keyGeneration, PersistentDataType.LONG, record.version());
        stack.setItemMeta(meta);
        return stack;
    }

    /** The relic identity carried by a stack at {@code slot}, if it is a relic copy (genuine or not). */
    public Optional<RelicValidator.Found> read(ItemStack stack, int slot) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String key = pdc.get(keyRelic, PersistentDataType.STRING);
        if (key == null) {
            return Optional.empty();
        }
        UUID itemUuid;
        try {
            itemUuid = UUID.fromString(pdc.getOrDefault(keyItemUuid, PersistentDataType.STRING, ""));
        } catch (IllegalArgumentException ex) {
            itemUuid = new UUID(0, 0); // malformed => counterfeit
        }
        long gen = pdc.getOrDefault(keyGeneration, PersistentDataType.LONG, -1L);
        return Optional.of(new RelicValidator.Found(slot, key, itemUuid, gen, stack.getAmount()));
    }

    public boolean isRelic(ItemStack stack) {
        return read(stack, -1).isPresent();
    }

    private static Component line(String text, NamedTextColor color, boolean italic) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, italic);
    }
}
