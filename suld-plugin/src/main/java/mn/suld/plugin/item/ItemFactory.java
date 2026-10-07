package mn.suld.plugin.item;

import com.google.common.collect.ImmutableMultimap;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemCodec;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemTooltip;
import mn.suld.api.item.ItemType;
import mn.suld.plugin.content.SuldContent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The one codec between SÜLD items and Bukkit {@link ItemStack}s. The item's identity, rarity, rolls and binding are a
 * versioned JSON document in the stack's PersistentDataContainer ({@code suld:item}); the name and lore are rendered
 * from it and never read back. Stacks from before the item engine (separate keys) are read and migrated.
 *
 * <p>Equipment shows no vanilla attribute lines; armour carries no vanilla armour points (SÜLD armour is the ARMOR
 * stat, applied through the player's stat pipeline). Wearing items use the vanilla durability bar with the
 * definition's maximum; an item at its last point is broken: it gives nothing until it is repaired, and never
 * disappears.
 */
public final class ItemFactory {

    private final NamespacedKey keyItem;
    private final NamespacedKey keyId;
    private final NamespacedKey keyUuid;
    private final NamespacedKey keyRarity;
    private final NamespacedKey keyLevel;
    private final NamespacedKey keyStats;
    private final NamespacedKey keySoulbound;
    private final NamespacedKey keyUpgrade;

    public ItemFactory(Plugin plugin) {
        this.keyItem = new NamespacedKey(plugin, "item");
        this.keyId = new NamespacedKey(plugin, "item_id");
        this.keyUuid = new NamespacedKey(plugin, "item_uuid");
        this.keyRarity = new NamespacedKey(plugin, "rarity");
        this.keyLevel = new NamespacedKey(plugin, "item_level");
        this.keyStats = new NamespacedKey(plugin, "stats");
        this.keySoulbound = new NamespacedKey(plugin, "soulbound");
        this.keyUpgrade = new NamespacedKey(plugin, "upgrade");
    }

    private static ItemCatalog catalog() {
        return SuldContent.items();
    }

    /** What a stack is: the item, whether it came from the old format, and what the validator says. */
    public record Read(ItemInstance item, boolean legacy) {
    }

    // ------------------------------------------------------------------ writing

    public ItemStack create(ItemInstance instance) {
        return create(instance, ItemTooltip.Viewer.NOBODY);
    }

    /** Kept for callers that already hold the definition. */
    public ItemStack create(ItemInstance instance, ItemDefinition ignored) {
        return create(instance);
    }

    /** A new stack for the item, its tooltip rendered for {@code viewer} (requirements in red when not met). */
    public ItemStack create(ItemInstance instance, ItemTooltip.Viewer viewer) {
        ItemDefinition def = catalog().item(instance.definitionId()).orElse(null);
        if (def == null) throw new IllegalArgumentException("unknown item " + instance.definitionId());
        Material material = Optional.ofNullable(Material.matchMaterial(def.material().replace("minecraft:", ""))).orElse(Material.PAPER);
        ItemStack stack = new ItemStack(material);
        ItemInstance stored = def.stackable() ? stackForm(instance) : instance;
        ItemMeta meta = stack.getItemMeta();
        if (def.model() > 0) meta.setCustomModelData(def.model());
        if (def.stackable()) meta.setMaxStackSize(def.maxStack());
        else meta.setMaxStackSize(1);
        int max = def.maxDurability(instance.rarity());
        if (meta instanceof Damageable d) {
            if (max > 0) {
                d.setMaxDamage(max + 1); // the extra point is never used: at damage == max the item is broken, not destroyed
                d.setUnbreakable(false);
            } else if (def.type().equippable() && material.getMaxDurability() > 0) {
                d.setUnbreakable(true); // items that never wear (class weapons, relics, jewellery on a tool material)
            }
        }
        if (def.type().category() == ItemType.Category.ARMOR || def.type() == ItemType.SHIELD) {
            meta.setAttributeModifiers(ImmutableMultimap.of()); // SÜLD armour comes from the ARMOR stat, not the material
        }
        if (def.equippable()) meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (instance.rarity().atLeast(ItemRarity.LEGENDARY)) meta.setEnchantmentGlintOverride(true);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyItem, PersistentDataType.STRING, ItemCodec.encode(stored));
        // the plain id/uuid keys stay: the relic validator and older readers look for them
        pdc.set(keyId, PersistentDataType.STRING, stored.definitionId());
        pdc.set(keyUuid, PersistentDataType.STRING, stored.uuid().toString());
        for (NamespacedKey legacy : List.of(keyRarity, keyLevel, keyStats, keySoulbound, keyUpgrade)) pdc.remove(legacy);
        render(meta, def, stored, viewer, max, max);
        stack.setItemMeta(meta);
        equipmentAsset(stack, def);
        return stack;
    }

    /**
     * Class armour is worn with its own look: the {@code minecraft:equippable} component points at the pack's
     * equipment asset {@code suld:<class>_t<tier>} (resourcepack/assets/suld/equipment, tools/pack/gen_armor.py) instead
     * of the vanilla material's layer.
     */
    private static void equipmentAsset(ItemStack stack, ItemDefinition def) {
        mn.suld.api.classgear.ArmorRules.parse(def.id()).ifPresent(a -> {
            org.bukkit.inventory.EquipmentSlot slot = switch (a.piece()) {
                case HELMET -> org.bukkit.inventory.EquipmentSlot.HEAD;
                case CHESTPLATE -> org.bukkit.inventory.EquipmentSlot.CHEST;
                case LEGGINGS -> org.bukkit.inventory.EquipmentSlot.LEGS;
                case BOOTS -> org.bukkit.inventory.EquipmentSlot.FEET;
            };
            String sound = switch (a.tier()) {
                case T1 -> "item.armor.equip_leather";
                case T2 -> "item.armor.equip_chain";
                case T3, T4 -> "item.armor.equip_iron";
                case T5 -> "item.armor.equip_diamond";
                case T6 -> "item.armor.equip_netherite";
            };
            stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE,
                    io.papermc.paper.datacomponent.item.Equippable.equippable(slot)
                            .assetId(net.kyori.adventure.key.Key.key("suld", mn.suld.api.classgear.ArmorRules.assetId(a.clazz(), a.tier())))
                            .equipSound(net.kyori.adventure.key.Key.key("minecraft", sound))
                            .build());
        });
    }

    /**
     * Stackable items (materials) have no individual identity: every copy of the same material and rarity is the same
     * stored document, so the game stacks them.
     */
    public static ItemInstance stackForm(ItemInstance i) {
        UUID stable = UUID.nameUUIDFromBytes(("suld:" + i.definitionId() + ":" + i.rarity().id()).getBytes(StandardCharsets.UTF_8));
        return new ItemInstance(i.definitionId(), stable, i.rarity(), 1, i.stats(), List.of(), i.soulbound(), null, 0, "stack", ItemInstance.SCHEMA_VERSION);
    }

    /** Put a changed item (bound, upgraded) back into the same stack, keeping its durability. */
    public void rewrite(ItemStack stack, ItemInstance changed, ItemTooltip.Viewer viewer) {
        ItemDefinition def = catalog().item(changed.definitionId()).orElse(null);
        if (def == null || !stack.hasItemMeta()) return;
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(keyItem, PersistentDataType.STRING, ItemCodec.encode(changed));
        meta.getPersistentDataContainer().set(keyUuid, PersistentDataType.STRING, changed.uuid().toString());
        int max = def.maxDurability(changed.rarity());
        int left = max;
        if (meta instanceof Damageable d && max > 0) {
            if (!d.hasMaxDamage() || d.getMaxDamage() != max + 1) d.setMaxDamage(max + 1);
            left = Math.max(0, max - d.getDamage());
        }
        render(meta, def, changed, viewer, left, max);
        stack.setItemMeta(meta);
    }

    /** Re-render name and lore for a viewer (requirements, set progress) without touching the stored item. */
    public void rerender(ItemStack stack, ItemTooltip.Viewer viewer) {
        read(stack).ifPresent(i -> rewrite(stack, i, viewer));
    }

    private static void render(ItemMeta meta, ItemDefinition def, ItemInstance i, ItemTooltip.Viewer viewer, int left, int max) {
        List<ItemTooltip.Line> lines = ItemTooltip.lines(catalog(), def, i, viewer, left, max, null);
        TextColor color = rarityColor(i.rarity());
        meta.displayName(Component.text(lines.get(0).text(), color).decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, i.rarity().atLeast(ItemRarity.LEGENDARY)));
        List<Component> lore = new ArrayList<>();
        for (int k = 1; k < lines.size(); k++) lore.add(component(lines.get(k), color));
        lore.add(Component.text("SÜLD", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
    }

    /** Colour of a tooltip line (also used by the item GUIs for comparison lines). */
    public static Component component(ItemTooltip.Line l, TextColor rarity) {
        TextColor c = switch (l.style()) {
            case NAME, RARITY -> rarity;
            case TYPE, INFO -> NamedTextColor.GRAY;
            case OK -> NamedTextColor.GREEN;
            case FAIL, DOWN -> NamedTextColor.RED;
            case STAT -> NamedTextColor.WHITE;
            case AFFIX -> NamedTextColor.AQUA;
            case UNIQUE -> NamedTextColor.GOLD;
            case SET -> NamedTextColor.YELLOW;
            case SET_ACTIVE -> NamedTextColor.GREEN;
            case SET_INACTIVE -> NamedTextColor.DARK_GRAY;
            case BINDING -> NamedTextColor.LIGHT_PURPLE;
            case FLAVOR -> NamedTextColor.DARK_AQUA;
            case UP -> NamedTextColor.GREEN;
            case SAME -> NamedTextColor.GRAY;
        };
        return Component.text(l.text(), c).decoration(TextDecoration.ITALIC, l.style() == ItemTooltip.Style.FLAVOR);
    }

    // ------------------------------------------------------------------ reading

    /** The SÜLD item in a stack (new or migrated old format), if it carries one. Not yet validated. */
    public Optional<ItemInstance> read(ItemStack stack) {
        return inspect(stack).map(Read::item);
    }

    public Optional<Read> inspect(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) return Optional.empty();
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String doc = pdc.get(keyItem, PersistentDataType.STRING);
        if (doc != null) {
            // a document the server cannot read is reported as an unreadable item, never treated as "not SÜLD"
            return Optional.of(ItemCodec.decode(doc).map(i -> new Read(i, false))
                    .orElse(new Read(unreadable(pdc), false)));
        }
        String id = pdc.get(keyId, PersistentDataType.STRING);
        String uuid = pdc.get(keyUuid, PersistentDataType.STRING);
        if (id == null || uuid == null) return Optional.empty();
        if (pdc.has(new NamespacedKey("suld", "relic_key"), PersistentDataType.STRING) && !pdc.has(keyRarity, PersistentDataType.STRING)) {
            // relic copies from before the item engine carried only id + uuid
            return ItemCodec.legacy(id, uuid, "unique", 1, "", true, 0).map(i -> new Read(i, true));
        }
        return ItemCodec.legacy(id, uuid, pdc.getOrDefault(keyRarity, PersistentDataType.STRING, "common"),
                pdc.getOrDefault(keyLevel, PersistentDataType.INTEGER, 1), pdc.getOrDefault(keyStats, PersistentDataType.STRING, ""),
                pdc.getOrDefault(keySoulbound, PersistentDataType.INTEGER, 0) == 1, pdc.getOrDefault(keyUpgrade, PersistentDataType.INTEGER, 0))
                .map(i -> new Read(i, true));
    }

    /** A placeholder the validator always refuses (schema 0), so an unreadable document is quarantined, not ignored. */
    private ItemInstance unreadable(PersistentDataContainer pdc) {
        String id = pdc.getOrDefault(keyId, PersistentDataType.STRING, "unreadable.item");
        UUID u;
        try {
            u = UUID.fromString(pdc.getOrDefault(keyUuid, PersistentDataType.STRING, ""));
        } catch (IllegalArgumentException e) {
            u = new UUID(0, 0);
        }
        return new ItemInstance(id, u, ItemRarity.COMMON, 1, java.util.Map.of(), List.of(), false, null, 0, "unreadable", 0);
    }

    public boolean isSuldItem(ItemStack stack) {
        return inspect(stack).isPresent();
    }

    // ------------------------------------------------------------------ durability

    /** Durability left (0 = broken) and the maximum; max 0 = the item never wears. */
    public int[] durability(ItemStack stack, ItemInstance i) {
        ItemDefinition def = catalog().item(i.definitionId()).orElse(null);
        int max = def == null ? 0 : def.maxDurability(i.rarity());
        if (max <= 0 || !(stack.getItemMeta() instanceof Damageable d)) return new int[]{0, 0};
        return new int[]{Math.max(0, max - d.getDamage()), max};
    }

    public boolean broken(ItemStack stack, ItemInstance i) {
        int[] d = durability(stack, i);
        return d[1] > 0 && d[0] <= 0;
    }

    /**
     * Bring SÜLD items made before their skin existed up to date: set the definition's current
     * custom model data (the resource-pack skin). Returns how many stacks changed.
     */
    public int refreshModels(ItemStack[] stacks) {
        int changed = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || !stack.hasItemMeta()) continue;
            ItemInstance i = read(stack).orElse(null);
            if (i == null) continue;
            ItemDefinition def = catalog().item(i.definitionId()).orElse(null);
            if (def == null || def.model() <= 0) continue;
            ItemMeta meta = stack.getItemMeta();
            if (meta.hasCustomModelData() && meta.getCustomModelData() == def.model()) continue;
            meta.setCustomModelData(def.model());
            stack.setItemMeta(meta);
            changed++;
        }
        return changed;
    }

    public static TextColor rarityColor(ItemRarity rarity) {
        TextColor c = TextColor.fromHexString(rarity.colorHex());
        return c == null ? NamedTextColor.WHITE : c;
    }
}
