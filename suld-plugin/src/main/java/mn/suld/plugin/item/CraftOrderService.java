package mn.suld.plugin.item;

import mn.suld.api.audit.AuditEvent;
import mn.suld.api.craft.CraftOrder;
import mn.suld.api.craft.CraftOrderRules;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemTooltip;
import mn.suld.api.item.Recipe;
import mn.suld.api.loot.LootDrop;
import mn.suld.api.loot.LootTier;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.persistence.AtomicYaml;
import mn.suld.plugin.trade.Mailbox;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Дархан's order board (docs/CRAFTING.md): a player puts up a recipe and a fee; any other player who can make it
 * fills the order with their own materials and the recipe's coins, the item (made at the poster's level, held to the
 * recipe's band, bound to the poster when its rarity binds) goes to the poster, the fee to the crafter.
 * <p>
 * Money never doubles: the fee leaves the poster's profile and that profile is <em>stored</em> before the order is
 * written; the order is removed from {@code craft_orders.yml} (written at once) before anything is made or paid, so a
 * second click, a second crafter or a crash can never fill it twice. A withdrawn or expired order pays the fee back;
 * a poster who is offline gets it on their next join (the refund is kept in the same file). Main thread.
 */
public final class CraftOrderService implements Listener {

    private final Plugin plugin;
    private final SuldServices services;
    private final Mailbox mailbox;
    private final Path file;
    private final Map<String, CraftOrder> orders = new LinkedHashMap<>();
    /** Fees owed back to offline posters: paid on join. */
    private final Map<UUID, Long> refunds = new HashMap<>();
    /** Posters whose fee is being stored right now: one post at a time each (no racing two posts past MAX_OPEN). */
    private final java.util.Set<UUID> posting = new java.util.HashSet<>();

    public CraftOrderService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        this.mailbox = services.mailbox(plugin); // SULD-F12: the one mailbox TradeService also writes (one lock, one file)
        this.file = plugin.getDataFolder().toPath().resolve("craft_orders.yml");
        load();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 20L * 60, 20L * 600); // every ten minutes
    }

    public List<CraftOrder> open() {
        List<CraftOrder> out = new ArrayList<>(orders.values());
        out.sort(Comparator.comparingLong(CraftOrder::createdAt));
        return out;
    }

    public Recipe recipe(CraftOrder o) {
        return services.itemService().catalog().recipe(o.recipeId()).orElse(null);
    }

    // ------------------------------------------------------------------ post

    /** Put up an order; the player is told the outcome. */
    public void post(Player p, Recipe r, long fee) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null || !pr.hasSelectedClass() || services.isSoul.test(p.getUniqueId())) {
            p.sendMessage(Messages.error("Сүнс, анги сонгоогүй үед захиалга өгөхгүй."));
            return;
        }
        if (posting.contains(p.getUniqueId())) {
            p.sendMessage(Messages.error("Өмнөх захиалга хадгалагдаж байна — түр хүлээ."));
            return;
        }
        CraftOrderRules.Post v = CraftOrderRules.post(p.getUniqueId(), pr.progression().level(), pr.currency(), fee, r.levelReq(), orders.values());
        switch (v) {
            case FEE_RANGE -> p.sendMessage(Messages.error("Хөлс " + CraftOrderRules.MIN_FEE + "–" + CraftOrderRules.MAX_FEE + " зоос байна."));
            case LEVEL -> p.sendMessage(Messages.error("Энэ жорын эд зүйлийг өмсөхөд түвшин " + r.levelReq() + " хэрэгтэй."));
            case TOO_MANY -> p.sendMessage(Messages.error("Нэг дор " + CraftOrderRules.MAX_OPEN + "-аас олон захиалга өгөхгүй."));
            case COINS -> p.sendMessage(Messages.error("Зоос хүрэлцэхгүй (" + pr.currency() + "/" + fee + ")."));
            case OK -> {
                posting.add(p.getUniqueId());
                pr.addCurrency(-fee);
                services.profiles().save(pr).whenComplete((v2, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    posting.remove(p.getUniqueId());
                    if (err != null) { // not stored: the fee comes back, no order
                        pr.addCurrency(fee);
                        if (p.isOnline()) p.sendMessage(Messages.error("Захиалгыг хадгалж чадсангүй — зоос буцлаа."));
                        return;
                    }
                    CraftOrder o = new CraftOrder(UUID.randomUUID().toString().substring(0, 8), p.getUniqueId(), p.getName(), r.id(), fee,
                            pr.progression().level(), System.currentTimeMillis());
                    orders.put(o.id(), o);
                    if (!save()) { // the order did not reach the disk: undo it, the fee comes back
                        orders.remove(o.id());
                        payBack(o.poster(), fee);
                        if (p.isOnline()) p.sendMessage(Messages.error("Захиалгыг хадгалж чадсангүй — зоос буцлаа."));
                        return;
                    }
                    services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "craft.order_post", o.id(), r.id() + " fee " + fee));
                    String name = name(r);
                    if (p.isOnline()) p.sendMessage(Messages.success("Захиалга өглөө: «" + name + "» · хөлс " + fee + " зоос (/order)."));
                    Bukkit.broadcast(Messages.info("Дархан хэрэгтэй: " + p.getName() + " «" + name + "» захиаллаа, хөлс " + fee + " зоос — /order"));
                }));
            }
        }
    }

    // ------------------------------------------------------------------ withdraw / expire

    /** The poster takes their order back; the fee is paid back. */
    public boolean withdraw(Player p, String id) {
        CraftOrder o = orders.get(id);
        if (o == null || !o.poster().equals(p.getUniqueId())) return false;
        orders.remove(id);
        if (!save()) {
            orders.put(id, o);
            p.sendMessage(Messages.error("Хадгалж чадсангүй — дахин оролдоно уу."));
            return true;
        }
        payBack(o.poster(), o.fee());
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "craft.order_withdraw", o.id(), o.recipeId()));
        p.sendMessage(Messages.info("Захиалгыг буцаалаа."));
        return true;
    }

    private void expire() {
        long now = System.currentTimeMillis();
        List<CraftOrder> gone = new ArrayList<>();
        for (CraftOrder o : orders.values()) if (CraftOrderRules.expired(o, now) || recipe(o) == null) gone.add(o);
        if (gone.isEmpty()) return;
        for (CraftOrder o : gone) orders.remove(o.id());
        for (CraftOrder o : gone) refunds.merge(o.poster(), o.fee(), Long::sum); // owed first, so the save carries them
        if (!save()) {
            for (CraftOrder o : gone) {
                orders.put(o.id(), o);
                refunds.merge(o.poster(), -o.fee(), Long::sum);
            }
            return;
        }
        for (CraftOrder o : gone) {
            Player p = Bukkit.getPlayer(o.poster());
            if (p != null) settleRefund(p);
            plugin.getLogger().info("[craft] order " + o.id() + " (" + o.recipeId() + ") expired; " + o.fee() + " coins back to " + o.posterName());
        }
    }

    /** Coins back to a poster: at once when they are online and loaded, else kept for their next join. */
    private void payBack(UUID poster, long fee) {
        refunds.merge(poster, fee, Long::sum);
        if (!save()) plugin.getLogger().severe("[craft] refund of " + fee + " to " + poster + " not stored; it is kept in memory");
        Player p = Bukkit.getPlayer(poster);
        if (p != null) settleRefund(p);
    }

    /** Pay what is owed back to {@code p}; the owed entry goes only once the profile holding the coins is stored. */
    private void settleRefund(Player p) {
        Long owed = refunds.get(p.getUniqueId());
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (owed == null || owed <= 0 || pr == null) return;
        if (pr.ephemeral()) { // in the training yard: the refund stays owed on the board and is paid when they leave
            if (services.training != null) services.training.defer(p.getUniqueId(), "Дарханы захиалгын буцаалт", this::settleRefund);
            return;
        }
        refunds.remove(p.getUniqueId());
        if (!save()) {
            refunds.put(p.getUniqueId(), owed);
            return;
        }
        pr.addCurrency(owed);
        services.profiles().save(pr).whenComplete((v, err) -> {
            if (err != null) Bukkit.getScheduler().runTask(plugin, () -> { // put it back: paid on the next join
                pr.addCurrency(-owed);
                refunds.merge(p.getUniqueId(), owed, Long::sum);
                save();
            });
        });
        p.sendMessage(Messages.info("Дарханы захиалгын " + owed + " зоос буцаж ирлээ."));
        services.profiles().cached(p.getUniqueId()).ifPresent(x -> services.hud().update(p, x));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) settleRefund(p);
        }, 60L);
    }

    // ------------------------------------------------------------------ fill

    /** SULD-F05: a filled order goes into the poster's hands only when it cannot fall on the ground (alive, a free slot). */
    static boolean toHand(boolean online, boolean dead, int firstEmpty) {
        return online && !dead && firstEmpty >= 0;
    }

    /** {@code crafter} fills order {@code id}: their materials and the recipe's coins, the item to the poster, the fee to them. */
    public void fill(Player crafter, String id) {
        CraftOrder o = orders.get(id);
        Recipe r = o == null ? null : recipe(o);
        PlayerProfile cp = services.profiles().cached(crafter.getUniqueId()).orElse(null);
        if (cp == null) return;
        CraftOrderRules.Fill v = CraftOrderRules.fill(crafter.getUniqueId(), cp.progression().level(), r == null ? null : o, r == null ? 0 : r.levelReq());
        switch (v) {
            case GONE -> crafter.sendMessage(Messages.error("Энэ захиалга аль хэдийн биелсэн эсвэл буцаагдсан."));
            case OWN -> crafter.sendMessage(Messages.error("Өөрийн захиалгыг биелүүлэхгүй."));
            case LEVEL -> crafter.sendMessage(Messages.error("Түвшин " + r.levelReq() + " хэрэгтэй."));
            case OK -> {
                // the crafter's side: the same checks as crafting for oneself, but the item goes to the poster
                var no = services.crafting().refusalFor(crafter, r, false);
                if (no != null) {
                    crafter.sendMessage(no);
                    return;
                }
                orders.remove(id);
                if (!save()) { // not removed on disk: nothing happens
                    orders.put(id, o);
                    crafter.sendMessage(Messages.error("Хадгалж чадсангүй — дахин оролдоно уу."));
                    return;
                }
                services.crafting().pay(crafter, r);
                cp.addCurrency(o.fee());
                services.profiles().save(cp);
                PlayerDataSaves.now(crafter); // the spent materials reach the player file at once
                Player poster = Bukkit.getPlayer(o.poster());
                // a poster in the training yard reads as level 60 there: the level they posted at stands for the real one,
                // and the item waits in the mailbox (their bag there is the kit) until training ends
                boolean training = poster != null && services.training != null && services.training.training(o.poster());
                if (training) poster = null;
                int level = poster != null ? services.profiles().cached(o.poster()).map(x -> x.progression().level()).orElse(o.posterLevel()) : o.posterLevel();
                var catalog = services.itemService().catalog();
                ItemDefinition def = catalog.require(r.resultId());
                ItemRarity rarity = catalog.band(LootTier.CRAFT).pick(mn.suld.api.loot.Rng.threadLocal(), r.minRarity(), r.maxRarity());
                // IT-03: made for the poster — an ON_PICKUP piece binds to them whether it reaches their hands or the mailbox
                ItemInstance made = services.itemService().bindForReceiver(
                        services.itemService().generate(def, rarity, r.itemLevel(level), o.poster(), "craft:" + r.id() + ":order:" + o.id()), o.poster());
                String itemName = ItemTooltip.name(catalog, def, made);
                if (poster != null && toHand(poster.isOnline(), poster.isDead(), poster.getInventory().firstEmpty())) {
                    services.itemService().give(poster, List.of(new LootDrop(made, 1)), "craft order");
                    PlayerDataSaves.now(poster);
                    poster.sendMessage(Messages.success("Таны захиалга биеллээ: " + crafter.getName() + " " + itemName + " урлав."));
                } else if (poster != null) {
                    // SULD-F05: a dead poster or a full bag would drop a bound item on the ground: the mailbox keeps it
                    mailbox.keep(o.poster(), List.of(services.itemService().stack(made, poster, 1)), "craft order " + o.id());
                    poster.sendMessage(Messages.success("Таны захиалга биеллээ: " + crafter.getName() + " " + itemName
                            + " урлав. " + (poster.isDead() ? "Та нас барсан байсан" : "Цүнх дүүрэн байсан")
                            + " тул шуудангийн хайрцагт аюулгүй хадгалагдлаа — дахин нэвтрэхэд ирнэ."));
                } else {
                    mailbox.keep(o.poster(), List.of(services.itemService().stack(made, null, 1)), "craft order " + o.id());
                    if (training) services.training.defer(o.poster(), "Дарханы захиалга биеллээ: " + crafter.getName() + " " + itemName + " урлав", q -> {
                        int n = mailbox.deliver(q);
                        if (n > 0) q.sendMessage(Messages.success("Таны захиалгын эд зүйл ирлээ: " + itemName + "."));
                    });
                }
                services.audit().record(AuditEvent.of(crafter.getUniqueId().toString(), "craft.order_fill", o.id(),
                        r.id() + " for " + o.posterName() + " fee " + o.fee() + " item " + made.uuid()));
                crafter.sendMessage(Messages.success("Захиалга биелүүллээ: " + itemName + " → " + o.posterName() + " · +" + o.fee() + " зоос"));
                services.profiles().cached(crafter.getUniqueId()).ifPresent(x -> services.hud().update(crafter, x));
            }
        }
    }

    // ------------------------------------------------------------------ file

    private void load() {
        YamlConfiguration y = AtomicYaml.read(file);
        ConfigurationSection os = y.getConfigurationSection("orders");
        if (os != null) {
            for (String id : os.getKeys(false)) {
                ConfigurationSection s = os.getConfigurationSection(id);
                if (s == null) continue;
                try {
                    orders.put(id, new CraftOrder(id, UUID.fromString(s.getString("poster")), s.getString("name"), s.getString("recipe"),
                            s.getLong("fee"), s.getInt("level", 1), s.getLong("at")));
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("[craft] order " + id + " unreadable: " + ex.getMessage());
                }
            }
        }
        ConfigurationSection rs = y.getConfigurationSection("refunds");
        if (rs != null) for (String k : rs.getKeys(false)) {
            try {
                refunds.put(UUID.fromString(k), rs.getLong(k));
            } catch (IllegalArgumentException ignored) {
                // a malformed key cannot be paid to anyone
            }
        }
    }

    /** Write the board at once; false when the disk refused (the caller undoes its change). */
    private boolean save() {
        YamlConfiguration y = new YamlConfiguration();
        for (CraftOrder o : orders.values()) {
            String k = "orders." + o.id();
            y.set(k + ".poster", o.poster().toString());
            y.set(k + ".name", o.posterName());
            y.set(k + ".recipe", o.recipeId());
            y.set(k + ".fee", o.fee());
            y.set(k + ".level", o.posterLevel());
            y.set(k + ".at", o.createdAt());
        }
        refunds.forEach((id, n) -> {
            if (n > 0) y.set("refunds." + id, n);
        });
        try {
            AtomicYaml.writeNow(file, y);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("[craft] craft_orders.yml not written: " + e.getMessage());
            return false;
        }
    }

    private String name(Recipe r) {
        return services.itemService().catalog().item(r.resultId()).map(ItemDefinition::displayName).orElse(r.resultId());
    }
}
