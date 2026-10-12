package mn.suld.plugin.item;

import mn.suld.api.audit.AuditEvent;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemTooltip;
import mn.suld.api.item.Recipe;
import mn.suld.api.loot.LootDrop;
import mn.suld.api.loot.LootTier;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * The Дархан's work (docs/CRAFTING.md): one recipe, checked and paid in one main-thread step. Used by
 * {@code /item craft} and the smith's recipe window, so both follow the same rules: the crafter's level reaches the
 * recipe, the item's level is held to the recipe's band, coins and every material are there before anything is
 * taken, and the bag has room.
 */
public final class CraftService {

    private final SuldServices services;

    public CraftService(SuldServices services) {
        this.services = services;
    }

    private ItemService items() {
        return services.itemService();
    }

    private ItemCatalog catalog() {
        return items().catalog();
    }

    /** How many of a material (by definition id) the player carries in the bag. */
    public int count(Player p, String defId) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            ItemInstance i = s == null ? null : items().factory().read(s).orElse(null);
            if (i != null && i.definitionId().equals(defId)) n += s.getAmount();
        }
        return n;
    }

    private void take(Player p, String defId, int amount) {
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int k = 0; k < inv.length && amount > 0; k++) {
            ItemInstance i = inv[k] == null ? null : items().factory().read(inv[k]).orElse(null);
            if (i == null || !i.definitionId().equals(defId)) continue;
            int use = Math.min(amount, inv[k].getAmount());
            amount -= use;
            if (use == inv[k].getAmount()) inv[k] = null;
            else inv[k].setAmount(inv[k].getAmount() - use);
        }
        p.getInventory().setStorageContents(inv);
    }

    public String describe(Map<String, Integer> mats) {
        StringBuilder sb = new StringBuilder();
        mats.forEach((id, n) -> sb.append(sb.length() == 0 ? "" : ", ").append(catalog().item(id).map(ItemDefinition::displayName).orElse(id)).append(" ×").append(n));
        return sb.toString();
    }

    /** Why the player cannot craft {@code r} for themselves now, or null when they can. */
    public Component refusal(Player p, Recipe r) {
        return refusalFor(p, r, true);
    }

    /**
     * Why the player cannot craft {@code r} now, or null. {@code ownBag}: the item lands in their bag, so it needs a
     * free slot (a crafting order sends it to the poster instead).
     */
    public Component refusalFor(Player p, Recipe r, boolean ownBag) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null || !pr.hasSelectedClass()) return Messages.error("Эхлээд ангиа сонго.");
        if (services.isSoul.test(p.getUniqueId())) return Messages.error("Сүнс байхдаа урлаж болохгүй.");
        if (pr.progression().level() < r.levelReq()) return Messages.error("Түвшин " + r.levelReq() + " хэрэгтэй.");
        if (pr.currency() < r.coins()) return Messages.error("Зоос хүрэлцэхгүй (" + pr.currency() + "/" + r.coins() + " зоос).");
        for (Map.Entry<String, Integer> m : r.materials().entrySet()) {
            if (count(p, m.getKey()) < m.getValue()) return Messages.error("Материал дутуу: " + describe(Map.of(m.getKey(), m.getValue())));
        }
        if (ownBag && p.getInventory().firstEmpty() < 0) return Messages.error("Цүнх дүүрэн байна.");
        return null;
    }

    /** Take the recipe's materials and coins from {@code p} (checked by {@link #refusalFor} just before). */
    public void pay(Player p, Recipe r) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElseThrow();
        r.materials().forEach((id, n) -> take(p, id, n));
        pr.addCurrency(-r.coins());
    }

    /** Craft {@code r}: the made item, or null (the player was told why). Main thread. */
    public ItemInstance craft(Player p, Recipe r) {
        Component no = refusal(p, r);
        if (no != null) {
            p.sendMessage(no);
            return null;
        }
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElseThrow();
        pay(p, r);
        ItemDefinition def = catalog().require(r.resultId());
        ItemRarity rarity = catalog().band(LootTier.CRAFT).pick(mn.suld.api.loot.Rng.threadLocal(), r.minRarity(), r.maxRarity());
        ItemInstance made = items().generate(def, rarity, r.itemLevel(pr.progression().level()), p.getUniqueId(), "craft:" + r.id());
        items().give(p, List.of(new LootDrop(made, 1)), "craft");
        services.profiles().save(pr);
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "item.craft", def.id() + "-" + made.uuid(), r.id()));
        p.sendMessage(Messages.success("Урлалаа: " + ItemTooltip.name(catalog(), def, made) + " (" + rarity.displayName() + ", Зэрэг " + made.itemLevel() + ")"));
        services.profiles().cached(p.getUniqueId()).ifPresent(x -> services.hud().update(p, x));
        return made;
    }
}
