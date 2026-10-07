package mn.suld.plugin.item;

import mn.suld.api.audit.AuditEvent;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemCatalogLoader;
import mn.suld.api.item.ItemCodec;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemGenerator;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemTooltip;
import mn.suld.api.item.ItemValidator;
import mn.suld.api.loot.LootContext;
import mn.suld.api.loot.LootDrop;
import mn.suld.api.loot.LootEngine;
import mn.suld.api.loot.LootTable;
import mn.suld.api.loot.Rng;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.skill.tree.SkillTreeLoader;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The item engine on the server: owns the live {@link ItemCatalog} (bundled, or {@code plugins/SULD/items/} when an
 * admin overrides it), generates items, turns loot into stacks and keeps inventories honest — every SÜLD stack a
 * player has is validated, old ones migrated, forged ones and duplicate copies of one item taken away into
 * quarantine (a file per item for admin recovery) with an audit entry.
 */
public final class ItemService {

    private final Plugin plugin;
    private final SuldServices services;
    private final ItemFactory factory;
    private final Rng rng = Rng.threadLocal();
    private volatile ItemCatalog catalog;
    private volatile LootEngine engine;
    private volatile ItemValidator validator;
    private volatile List<SkillTreeLoader.Issue> issues = List.of();

    public ItemService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        this.factory = services.items();
        install(ItemCatalogLoader.load(ItemCatalogLoader.classpath()).catalog());
    }

    private void install(ItemCatalog c) {
        this.catalog = c;
        this.engine = new LootEngine(c);
        this.validator = new ItemValidator(c);
        SuldContent.items(c);
    }

    public ItemCatalog catalog() {
        return catalog;
    }

    public ItemGenerator generator() {
        return engine.generator();
    }

    public ItemValidator validator() {
        return validator;
    }

    public ItemFactory factory() {
        return factory;
    }

    public List<SkillTreeLoader.Issue> issues() {
        return issues;
    }

    // ------------------------------------------------------------------ catalog lifecycle

    private Path overrideDir() {
        return plugin.getDataFolder().toPath().resolve("items");
    }

    /** Check the data files without using them. */
    public List<SkillTreeLoader.Issue> validate() {
        return load().issues();
    }

    private ItemCatalogLoader.Result load() {
        Path dir = overrideDir();
        return Files.isRegularFile(dir.resolve("index.json")) ? ItemCatalogLoader.load(ItemCatalogLoader.directory(dir))
                : ItemCatalogLoader.load(ItemCatalogLoader.classpath());
    }

    /** Load (startup) or reload: a catalog with any problem is not used; the previous one stays. */
    public List<SkillTreeLoader.Issue> reload() {
        ItemCatalogLoader.Result r = load();
        issues = r.issues();
        if (r.ok()) {
            install(r.catalog());
            checkedDocs.clear();
            plugin.getLogger().info("[items] catalog: " + r.catalog().items().size() + " items, " + r.catalog().affixes().size()
                    + " affixes, " + r.catalog().sets().size() + " sets, " + r.catalog().lootTables().size() + " loot tables");
        } else {
            for (SkillTreeLoader.Issue i : r.issues()) plugin.getLogger().warning("[items] " + i);
            plugin.getLogger().warning("[items] the item data has " + r.issues().size() + " problem(s): keeping the previous catalog");
        }
        return r.issues();
    }

    // ------------------------------------------------------------------ making items

    /** A viewer for tooltips: the player's own requirements and worn set pieces. */
    public ItemTooltip.Viewer viewer(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        EquipmentService eq = services.equipment();
        Set<String> sets = eq == null ? Set.of() : eq.wornDefinitions(p);
        return new ItemTooltip.Viewer(p.getUniqueId(), pr == null ? null : pr.playerClass().orElse(null),
                pr == null ? 0 : pr.progression().level(), sets);
    }

    public ItemStack stack(ItemInstance i, Player viewer, int amount) {
        ItemStack s = factory.create(i, viewer == null ? ItemTooltip.Viewer.NOBODY : viewer(viewer));
        s.setAmount(Math.max(1, Math.min(amount, s.getMaxStackSize())));
        return s;
    }

    /** A new item of a definition; rarity null = its lowest. */
    public ItemInstance generate(ItemDefinition def, ItemRarity rarity, int level, UUID owner, String source) {
        return generator().generate(def, rarity == null ? def.rarity() : rarity, level, rng, source, owner);
    }

    /** Roll a loot table. */
    public List<LootDrop> roll(String tableId, LootContext ctx) {
        LootTable t = catalog.lootTable(tableId).orElse(null);
        return t == null ? List.of() : engine.roll(t, ctx, rng);
    }

    public List<LootDrop> roll(LootTable t, LootContext ctx) {
        return engine.roll(t, ctx, rng);
    }

    /** Give loot to a player (inventory first, the ground for the rest), announcing legendary and better drops. */
    public void give(Player p, List<LootDrop> drops, String reason) {
        for (LootDrop d : drops) {
            ItemStack s = stack(d.item(), p, d.amount());
            p.getInventory().addItem(s).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
            announce(p, d.item());
        }
    }

    public void announce(Player p, ItemInstance i) {
        if (!i.rarity().announced()) return;
        ItemDefinition def = catalog.item(i.definitionId()).orElse(null);
        if (def == null) return;
        Bukkit.broadcast(Component.text("✦ " + p.getName() + " — ", NamedTextColor.GRAY)
                .append(Component.text(mn.suld.api.item.ItemTooltip.name(catalog, def, i), ItemFactory.rarityColor(i.rarity()), TextDecoration.BOLD))
                .append(Component.text(" (" + i.rarity().displayName() + ")", ItemFactory.rarityColor(i.rarity()))));
    }

    // ------------------------------------------------------------------ keeping inventories honest

    /** Result of checking one stack. */
    public enum Verdict { NOT_SULD, GENUINE, MIGRATED, FORGED }

    public record Checked(Verdict verdict, ItemInstance item, List<String> problems) {
    }

    /**
     * Decoded and validated item documents by their exact text (the text of one item never changes without its
     * document changing), so equipment checks in combat do not parse JSON again. Bounded; cleared on reload.
     */
    private final Map<String, Checked> checkedDocs = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Checked> eldest) {
            return size() > 4096;
        }
    });

    public Checked check(ItemStack stack) {
        String doc = stack == null || !stack.hasItemMeta() ? null : stack.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, "item"), org.bukkit.persistence.PersistentDataType.STRING);
        if (doc != null) {
            Checked hit = checkedDocs.get(doc);
            if (hit != null) return hit;
            Checked c = checkUncached(stack);
            checkedDocs.put(doc, c);
            return c;
        }
        return checkUncached(stack);
    }

    private Checked checkUncached(ItemStack stack) {
        Optional<ItemFactory.Read> read = factory.inspect(stack);
        if (read.isEmpty()) return new Checked(Verdict.NOT_SULD, null, List.of());
        ItemInstance i = read.get().item();
        if (read.get().legacy()) i = migrate(i);
        List<String> problems = validator.problems(i);
        if (!problems.isEmpty()) return new Checked(Verdict.FORGED, i, problems);
        return new Checked(read.get().legacy() ? Verdict.MIGRATED : Verdict.GENUINE, i, List.of());
    }

    /**
     * An item made by the first item system: stats the definition no longer has (materials used to carry some) are
     * dropped, values above today's best roll are lowered to it; the identity stays.
     */
    ItemInstance migrate(ItemInstance i) {
        ItemDefinition def = catalog.item(i.definitionId()).orElse(null);
        if (def == null) return i;
        Map<mn.suld.api.item.ItemStat, Double> stats = new java.util.EnumMap<>(mn.suld.api.item.ItemStat.class);
        i.stats().forEach((k, v) -> {
            if (def.stats().containsKey(k)) stats.put(k, Math.min(v, ItemGenerator.round(def.maxStat(k, i.rarity(), i.itemLevel()))));
        });
        ItemRarity r = def.canRoll(i.rarity()) ? i.rarity() : def.rarity();
        boolean soulbound = i.soulbound() || def.bindingAt(r) == mn.suld.api.item.Binding.SOULBOUND;
        return new ItemInstance(i.definitionId(), i.uuid(), r, Math.min(ItemDefinition.MAX_LEVEL, i.itemLevel()), stats, List.of(),
                soulbound, i.boundTo(), i.upgradeLevel(), i.provenance(), ItemInstance.SCHEMA_VERSION);
    }

    /**
     * Check every stack in an inventory: migrate old items in place, take forged ones and any second copy of the same
     * item (same identity) into quarantine. {@code seen} carries identities across inventories of one owner.
     * Returns how many stacks were taken.
     */
    public int sweep(Player owner, Inventory inv, String where, Set<UUID> seen) {
        int taken = 0;
        ItemTooltip.Viewer viewer = viewer(owner);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack s = inv.getItem(slot);
            if (s == null || s.isEmpty()) continue;
            if (services.relics() != null && services.relics().items().isRelic(s)) continue; // the relic system validates its own copies
            Checked c = check(s);
            switch (c.verdict()) {
                case NOT_SULD -> {
                }
                case FORGED -> {
                    quarantine(owner, s, where + "#" + slot, String.join("; ", c.problems()));
                    inv.setItem(slot, null);
                    taken++;
                }
                case MIGRATED, GENUINE -> {
                    ItemDefinition def = catalog.require(c.item().definitionId());
                    if (!def.stackable() && !seen.add(c.item().uuid())) {
                        quarantine(owner, s, where + "#" + slot, "second copy of item " + c.item().uuid());
                        inv.setItem(slot, null);
                        taken++;
                    } else if (c.verdict() == Verdict.MIGRATED) {
                        ItemStack fresh = factory.create(c.item(), viewer);
                        fresh.setAmount(s.getAmount());
                        copyDamage(s, fresh);
                        inv.setItem(slot, fresh);
                    }
                }
            }
        }
        if (taken > 0) owner.sendMessage(Messages.error(taken + " хуурамч / давхардсан эд зүйлийг хурааж авлаа (" + where + "). Админд хандана уу."));
        return taken;
    }

    private static void copyDamage(ItemStack from, ItemStack to) {
        if (from.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable a && to.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable b) {
            b.setDamage(Math.min(a.getDamage(), b.hasMaxDamage() ? b.getMaxDamage() - 1 : a.getDamage()));
            to.setItemMeta(b);
        }
    }

    /** Join: the player's inventory, armour, off hand and ender chest, plus the stored accessories. */
    public void sweepPlayer(Player p) {
        Set<UUID> seen = new HashSet<>();
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr != null) {
            for (ItemInstance a : pr.equipment().slots().values()) seen.add(a.uuid());
        }
        sweep(p, p.getInventory(), "inventory", seen);
        sweep(p, p.getEnderChest(), "enderchest", seen);
    }

    /**
     * Duplicate guard across everyone online: the same item identity in two players' hands is a duplicate; the copy
     * of the player who held it longest (the earlier-seen holder) is kept, the other is quarantined.
     */
    private final Map<UUID, UUID> holderOf = new HashMap<>();

    public int sweepOnline() {
        Map<UUID, UUID> now = new HashMap<>();
        int taken = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Inventory inv = p.getInventory();
            for (int slot = 0; slot < inv.getSize(); slot++) {
                ItemStack s = inv.getItem(slot);
                if (s == null || s.isEmpty() || !s.hasItemMeta()) continue;
                ItemInstance i = factory.read(s).orElse(null);
                if (i == null || catalog.item(i.definitionId()).map(ItemDefinition::stackable).orElse(true)) continue;
                UUID other = now.putIfAbsent(i.uuid(), p.getUniqueId());
                if (other == null || other.equals(p.getUniqueId())) continue;
                // two online players hold the same item: keep the copy of whoever held it before
                UUID keeper = p.getUniqueId().equals(holderOf.get(i.uuid())) ? p.getUniqueId() : other;
                Player loser = Bukkit.getPlayer(keeper.equals(p.getUniqueId()) ? other : p.getUniqueId());
                if (loser != null && removeCopy(loser, i.uuid(), "duplicate of an item held by " + Bukkit.getOfflinePlayer(keeper).getName())) taken++;
                now.put(i.uuid(), keeper);
            }
        }
        holderOf.clear();
        holderOf.putAll(now);
        return taken;
    }

    private boolean removeCopy(Player p, UUID itemId, String why) {
        Inventory inv = p.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack s = inv.getItem(slot);
            ItemInstance i = s == null ? null : factory.read(s).orElse(null);
            if (i != null && i.uuid().equals(itemId)) {
                quarantine(p, s, "inventory#" + slot, why);
                inv.setItem(slot, null);
                p.sendMessage(Messages.error("Давхардсан эд зүйлийг хурааж авлаа. Админд хандана уу."));
                return true;
            }
        }
        return false;
    }

    /** Write the stack's stored document to plugins/SULD/quarantine/ and log it; an admin can give it back. */
    public void quarantine(Player owner, ItemStack s, String where, String why) {
        String doc = s.hasItemMeta() ? s.getItemMeta().getPersistentDataContainer()
                .getOrDefault(new org.bukkit.NamespacedKey(plugin, "item"), org.bukkit.persistence.PersistentDataType.STRING, "") : "";
        ItemInstance i = factory.read(s).orElse(null);
        String id = i == null ? "unknown" : i.definitionId() + "-" + i.uuid();
        try {
            Path dir = plugin.getDataFolder().toPath().resolve("quarantine");
            Files.createDirectories(dir);
            String name = Instant.now().toString().replace(':', '-') + "-" + owner.getUniqueId() + "-" + id.replaceAll("[^A-Za-z0-9._-]", "_") + ".json";
            String body = "{\"owner\":\"" + owner.getUniqueId() + "\",\"name\":\"" + owner.getName() + "\",\"where\":\"" + where
                    + "\",\"why\":" + mn.suld.api.json.Json.write(why) + ",\"amount\":" + s.getAmount() + ",\"material\":\"" + s.getType().name()
                    + "\",\"item\":" + mn.suld.api.json.Json.write(doc) + "}\n";
            Files.writeString(dir.resolve(name), body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("[items] could not write quarantine file: " + e.getMessage());
        }
        services.audit().record(AuditEvent.of(owner.getUniqueId().toString(), "item.quarantine", id, where + ": " + why));
        plugin.getLogger().warning("[items] quarantined " + id + " from " + owner.getName() + " (" + where + "): " + why);
    }

    /** Admin recovery: a quarantined document back as an item (validated again, the identity kept). */
    public Optional<ItemInstance> recover(String document) {
        return ItemCodec.decode(document).filter(validator::genuine);
    }

    /** Every quarantine file, newest first. */
    public List<Path> quarantineFiles() {
        Path dir = plugin.getDataFolder().toPath().resolve("quarantine");
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (var s = Files.list(dir)) {
            s.filter(f -> f.toString().endsWith(".json")).sorted(java.util.Comparator.reverseOrder()).forEach(out::add);
        } catch (IOException ignored) {
            // nothing to list
        }
        return out;
    }
}
