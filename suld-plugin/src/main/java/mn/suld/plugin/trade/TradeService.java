package mn.suld.plugin.trade;

import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.gui.Menu;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /trade <player>}: a two-sided trade window. Offered items and coins leave the inventory into the trade (so
 * nothing can be offered twice); every click in the window is handled by hand (nothing vanilla moves items in or
 * out); both players must confirm, any change clears both confirmations, and the swap happens only when both
 * inventories have room. Closing the window, leaving, dying or teleporting cancels the trade and returns everything to
 * its owner. Relics and the menu item cannot be traded.
 */
public final class TradeService implements Listener, TabExecutor {

    private static final int[] OWN = slots(0);
    private static final int[] THEIRS = slots(5);
    private static final int CONFIRM = 45, COINS = 46, THEIR_COINS = 52, THEIR_CONFIRM = 53;
    private static final long REQUEST_MS = 60_000;
    private static final double MAX_DISTANCE = 24;
    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");

    private static int[] slots(int col) {
        int[] s = new int[20];
        for (int r = 0, i = 0; r < 5; r++) for (int c = 0; c < 4; c++) s[i++] = r * 9 + col + c;
        return s;
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final NamespacedKey menuKey;
    /** target → (requester → expiry). */
    private final Map<UUID, Map<UUID, Long>> requests = new HashMap<>();
    private final Map<UUID, Trade> trades = new HashMap<>();
    /** Offers of players who died mid-trade: handed back after respawn (the death itself never touches them). */
    private final Map<UUID, List<ItemStack>> afterRespawn = new HashMap<>();
    /** The offer of a player whose death event is in progress (between LOWEST and MONITOR). */
    private final Map<UUID, List<ItemStack>> dyingOffer = new HashMap<>();

    public TradeService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        this.menuKey = new NamespacedKey(plugin, "menu_item");
    }

    // ------------------------------------------------------------------ model

    private final class Side {
        final UUID player;
        final ItemStack[] items = new ItemStack[OWN.length];
        long coins;
        boolean confirmed;
        Inventory view;

        Side(UUID player) {
            this.player = player;
        }
    }

    private final class Trade {
        final Side a, b;
        boolean finished;

        Trade(UUID a, UUID b) {
            this.a = new Side(a);
            this.b = new Side(b);
        }

        Side side(UUID p) {
            return a.player.equals(p) ? a : b;
        }

        Side other(UUID p) {
            return a.player.equals(p) ? b : a;
        }
    }

    private static final class View implements InventoryHolder {
        final UUID owner;
        Inventory inventory;

        View(UUID owner) {
            this.owner = owner;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    // ------------------------------------------------------------------ command

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Messages.error("Зөвхөн тоглогч ашиглана."));
            return true;
        }
        if (a.length == 0) {
            p.sendMessage(Messages.info("/trade <тоглогч> — арилжаа санал болгох · /trade accept <тоглогч> · /trade deny"));
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("accept") && a.length > 1) {
            accept(p, a[1]);
        } else if (sub.equals("deny")) {
            requests.remove(p.getUniqueId());
            p.sendMessage(Messages.info("Арилжааны саналуудыг татгалзлаа."));
        } else {
            request(p, a[0]);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (a.length == 1) {
            List<String> out = new ArrayList<>(List.of("accept", "deny"));
            for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(s)) out.add(o.getName());
            return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    private String problem(Player p, Player o) {
        if (o == null || !o.isOnline()) return "Тоглогч онлайн биш байна.";
        if (o.equals(p)) return "Өөртэйгөө арилжаа хийх боломжгүй.";
        if (!p.getWorld().equals(o.getWorld()) || p.getLocation().distance(o.getLocation()) > MAX_DISTANCE) {
            return o.getName() + " хэт хол байна (" + (int) MAX_DISTANCE + " блок дотор ойрт).";
        }
        if (trades.containsKey(p.getUniqueId()) || trades.containsKey(o.getUniqueId())) return "Аль нэг нь арилжаа хийж байна.";
        if (services.dungeons().isInAnyRun(p.getUniqueId()) || services.dungeons().isInAnyRun(o.getUniqueId())) {
            return "Агуйд арилжаа хийхгүй.";
        }
        if (p.isDead() || o.isDead()) return "Одоо арилжаа хийх боломжгүй.";
        if (services.isSoul.test(p.getUniqueId()) || services.isSoul.test(o.getUniqueId())) return "Сүнс арилжаа хийх боломжгүй.";
        return null;
    }

    private void request(Player p, String targetName) {
        Player o = Bukkit.getPlayerExact(targetName);
        String why = problem(p, o);
        if (why != null) {
            p.sendMessage(Messages.error(why));
            return;
        }
        requests.computeIfAbsent(o.getUniqueId(), k -> new HashMap<>()).put(p.getUniqueId(), System.currentTimeMillis() + REQUEST_MS);
        p.sendMessage(Messages.success(o.getName() + "-д арилжааны санал илгээлээ."));
        o.sendMessage(Component.text("⇄ " + p.getName() + " арилжаа санал болгож байна. ", GOLD, TextDecoration.BOLD)
                .append(Component.text("[Зөвшөөрөх]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/trade accept " + p.getName())))
                .append(Component.text(" "))
                .append(Component.text("[Татгалзах]", NamedTextColor.RED, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/trade deny"))));
        o.playSound(o.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
    }

    private void accept(Player p, String requesterName) {
        Player o = Bukkit.getPlayerExact(requesterName);
        Map<UUID, Long> mine = requests.get(p.getUniqueId());
        Long until = o == null || mine == null ? null : mine.get(o.getUniqueId());
        if (until == null || until < System.currentTimeMillis()) {
            p.sendMessage(Messages.error("Хүчинтэй арилжааны санал алга."));
            return;
        }
        mine.remove(o.getUniqueId());
        String why = problem(p, o);
        if (why != null) {
            p.sendMessage(Messages.error(why));
            return;
        }
        Trade t = new Trade(o.getUniqueId(), p.getUniqueId());
        trades.put(o.getUniqueId(), t);
        trades.put(p.getUniqueId(), t);
        open(o, t);
        open(p, t);
    }

    // ------------------------------------------------------------------ views

    private void open(Player p, Trade t) {
        View holder = new View(p.getUniqueId());
        Player other = Bukkit.getPlayer(t.other(p.getUniqueId()).player);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Арилжаа ⇄ " + (other == null ? "?" : other.getName()),
                TextColor.fromHexString("#3A2A10"), TextDecoration.BOLD));
        holder.inventory = inv;
        t.side(p.getUniqueId()).view = inv;
        render(t);
        p.openInventory(inv);
    }

    private void render(Trade t) {
        for (Side me : List.of(t.a, t.b)) {
            if (me.view == null) continue;
            Side them = t.other(me.player);
            Inventory inv = me.view;
            ItemStack bar = Menu.item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
            for (int r = 0; r < 6; r++) inv.setItem(r * 9 + 4, bar);
            for (int i = 47; i <= 51; i++) inv.setItem(i, bar);
            for (int i = 0; i < OWN.length; i++) {
                inv.setItem(OWN[i], me.items[i]);
                inv.setItem(THEIRS[i], them.items[i]);
            }
            inv.setItem(CONFIRM, Menu.item(me.confirmed ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                    Menu.title(me.confirmed ? "Зөвшөөрсөн ✔" : "Зөвшөөрөх", me.confirmed ? NamedTextColor.GREEN : NamedTextColor.RED),
                    List.of(Component.text("Хоёулаа зөвшөөрвөл солилцоно.", NamedTextColor.WHITE, TextDecoration.BOLD))));
            inv.setItem(COINS, Menu.item(Material.GOLD_NUGGET, Menu.title("Таны зоос: " + me.coins + " ₮", GOLD), List.of(
                    Component.text("Зүүн: +10 · Баруун: −10", NamedTextColor.WHITE, TextDecoration.BOLD),
                    Component.text("Shift: ×100", NamedTextColor.WHITE, TextDecoration.BOLD))));
            inv.setItem(THEIR_COINS, Menu.item(Material.GOLD_INGOT, Menu.title("Тэдний зоос: " + them.coins + " ₮", GOLD), List.of()));
            inv.setItem(THEIR_CONFIRM, Menu.item(them.confirmed ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                    Menu.title(them.confirmed ? "Тэд зөвшөөрсөн ✔" : "Тэд хүлээж байна", them.confirmed ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                    List.of()));
        }
    }

    private boolean tradable(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        if (services.relics().items().isRelic(it) || holdsRelic(it) || services.soulbound().holdsBound(it)) return false;
        var inst = services.items().read(it).orElse(null);
        if (inst != null) {
            if (mn.suld.plugin.item.SoulboundGuard.soulbound(inst) || inst.bound()) return false; // soulbound and bound gear stays with its owner
            var def = mn.suld.plugin.content.SuldContent.definitionFor(inst.definitionId());
            if (def == null || !def.tradable()) return false;
            var checked = services.itemService().check(it);
            if (checked.verdict() == mn.suld.plugin.item.ItemService.Verdict.FORGED) return false;
        }
        return !(it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(menuKey, PersistentDataType.BYTE));
    }

    /** A relic hidden inside a shulker box or a bundle. */
    private boolean holdsRelic(ItemStack it) {
        if (!it.hasItemMeta()) return false;
        var meta = it.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.BlockStateMeta bsm && bsm.getBlockState() instanceof org.bukkit.block.ShulkerBox box) {
            for (ItemStack in : box.getInventory().getContents()) if (in != null && services.relics().items().isRelic(in)) return true;
        }
        if (meta instanceof org.bukkit.inventory.meta.BundleMeta bundle) {
            for (ItemStack in : bundle.getItems()) if (services.relics().items().isRelic(in)) return true;
        }
        return false;
    }

    private void changed(Trade t) {
        t.a.confirmed = false;
        t.b.confirmed = false;
        render(t);
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof View view) || !(e.getWhoClicked() instanceof Player p)) return;
        Trade t = trades.get(p.getUniqueId());
        e.setCancelled(true); // nothing vanilla happens in a trade window
        if (t == null || t.finished || !view.owner.equals(p.getUniqueId())) return;
        Side me = t.side(p.getUniqueId());
        boolean top = e.getClickedInventory() != null && e.getClickedInventory().equals(e.getView().getTopInventory());
        if (!top) {
            // own inventory: a shift-click offers the stack; plain clicks are refused (keeps the cursor empty)
            if (e.isShiftClick() && e.getClickedInventory() != null) {
                ItemStack it = e.getCurrentItem();
                if (!tradable(it)) {
                    if (it != null && !it.getType().isAir()) p.sendMessage(Messages.error("Энэ зүйлийг арилжих боломжгүй."));
                    return;
                }
                for (int i = 0; i < me.items.length; i++) {
                    if (me.items[i] == null) {
                        me.items[i] = it.clone();
                        e.getClickedInventory().setItem(e.getSlot(), null);
                        changed(t);
                        return;
                    }
                }
                p.sendMessage(Messages.error("Арилжааны талбар дүүрсэн."));
            }
            return;
        }
        int slot = e.getRawSlot();
        if (slot == CONFIRM) {
            me.confirmed = !me.confirmed;
            render(t);
            if (t.a.confirmed && t.b.confirmed) complete(t);
            return;
        }
        if (slot == COINS) {
            long step = e.isShiftClick() ? 1000 : 10;
            long next = me.coins + (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT ? -step : step);
            long have = services.profiles().cached(p.getUniqueId()).map(PlayerProfile::currency).orElse(0L);
            me.coins = Math.max(0, Math.min(have, next));
            changed(t);
            return;
        }
        for (int i = 0; i < OWN.length; i++) {
            if (OWN[i] == slot && me.items[i] != null) {
                giveBack(p, me.items[i]);
                me.items[i] = null;
                changed(t);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof View) e.setCancelled(true);
    }

    @EventHandler
    public void onAction(InventoryClickEvent e) {
        // belt and braces: never let a collect-to-cursor or hotbar swap reach a trade window
        if (e.getView().getTopInventory().getHolder() instanceof View
                && (e.getAction() == InventoryAction.COLLECT_TO_CURSOR || e.getAction() == InventoryAction.HOTBAR_SWAP)) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ finish

    private static boolean fits(Player p, List<ItemStack> incoming) {
        Inventory sim = Bukkit.createInventory(null, 36);
        ItemStack[] storage = p.getInventory().getStorageContents();
        for (int i = 0; i < storage.length && i < 36; i++) sim.setItem(i, storage[i] == null ? null : storage[i].clone());
        for (ItemStack it : incoming) if (!sim.addItem(it.clone()).isEmpty()) return false;
        return true;
    }

    private static List<ItemStack> offered(Side s) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack it : s.items) if (it != null) out.add(it);
        return out;
    }

    private void complete(Trade t) {
        Player pa = Bukkit.getPlayer(t.a.player), pb = Bukkit.getPlayer(t.b.player);
        if (pa == null || pb == null) {
            cancel(t, "Тоглогч гарсан.");
            return;
        }
        PlayerProfile ra = services.profiles().cached(pa.getUniqueId()).orElse(null);
        PlayerProfile rb = services.profiles().cached(pb.getUniqueId()).orElse(null);
        if (ra == null || rb == null || ra.currency() < t.a.coins || rb.currency() < t.b.coins) {
            t.a.confirmed = t.b.confirmed = false;
            render(t);
            pa.sendMessage(Messages.error("Зоос хүрэлцэхгүй байна."));
            pb.sendMessage(Messages.error("Зоос хүрэлцэхгүй байна."));
            return;
        }
        if (!fits(pa, offered(t.b)) || !fits(pb, offered(t.a))) {
            t.a.confirmed = t.b.confirmed = false;
            render(t);
            pa.sendMessage(Messages.error("Цүнхэнд зай хүрэлцэхгүй байна."));
            pb.sendMessage(Messages.error("Цүнхэнд зай хүрэлцэхгүй байна."));
            return;
        }
        t.finished = true;
        trades.remove(t.a.player);
        trades.remove(t.b.player);
        if (services.activity != null) {
            services.activity.signal(pa.getUniqueId(), mn.suld.api.activity.ActivitySignal.TRADE);
            services.activity.signal(pb.getUniqueId(), mn.suld.api.activity.ActivitySignal.TRADE);
        }
        for (ItemStack it : offered(t.b)) giveBack(pa, it);
        for (ItemStack it : offered(t.a)) giveBack(pb, it);
        ra.addCurrency(t.b.coins - t.a.coins);
        rb.addCurrency(t.a.coins - t.b.coins);
        services.profiles().save(ra);
        services.profiles().save(rb);
        // the coins are in SQL now: write the inventories too, so a crash before the autosave cannot undo one side only
        pa.saveData();
        pb.saveData();
        plugin.getLogger().info("[audit] trade " + pa.getName() + " (" + offered(t.a).size() + " stacks, " + t.a.coins + " coins) <-> "
                + pb.getName() + " (" + offered(t.b).size() + " stacks, " + t.b.coins + " coins)");
        pa.closeInventory();
        pb.closeInventory();
        for (Player p : List.of(pa, pb)) {
            p.sendMessage(Messages.success("Арилжаа амжилттай!"));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
            services.profiles().cached(p.getUniqueId()).ifPresent(pr -> services.hud().update(p, pr));
        }
    }

    private void giveBack(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    private void cancel(Trade t, String reason) {
        cancel(t, reason, null);
    }

    private void cancel(Trade t, String reason, UUID dying) {
        if (t.finished) return;
        t.finished = true;
        trades.remove(t.a.player);
        trades.remove(t.b.player);
        for (Side s : List.of(t.a, t.b)) {
            Player p = Bukkit.getPlayer(s.player);
            List<ItemStack> back = offered(s);
            if (s.player.equals(dying)) {
                dyingOffer.put(s.player, back); // onDeath puts these through the death rules like the inventory
                back = List.of();
            }
            if (p != null) {
                for (ItemStack it : back) giveBack(p, it);
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof View) p.closeInventory();
                p.sendMessage(Messages.info("Арилжаа цуцлагдлаа: " + reason));
            } else if (!back.isEmpty()) {
                plugin.getLogger().warning("[trade] " + back.size() + " stacks could not be returned to offline " + s.player);
            }
        }
    }

    private void cancelFor(UUID player, String reason) {
        Trade t = trades.get(player);
        if (t != null) cancel(t, reason);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof View view)) return;
        Trade t = trades.get(view.owner);
        if (t != null && !t.finished) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!t.finished) cancel(t, "цонх хаагдсан");
            });
        }
    }

    /** Quitting returns the offer while the player is still online. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        cancelFor(e.getPlayer().getUniqueId(), e.getPlayer().getName() + " гарлаа");
        List<ItemStack> pending = afterRespawn.remove(e.getPlayer().getUniqueId());
        if (pending != null) for (ItemStack it : pending) giveBack(e.getPlayer(), it); // left while dead: back into the inventory
        requests.remove(e.getPlayer().getUniqueId());
    }

    /**
     * Dying mid-trade: the offer is part of what the player carried, so it joins the drop list before the hardcore
     * death rules (DeathService, HIGHEST) pick the lost stacks; offering valuables never shields them from a death.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        Trade t = trades.get(id);
        if (t != null) cancel(t, "нас барсан", id);
        List<ItemStack> offer = dyingOffer.get(id);
        if (offer != null) e.getDrops().addAll(offer);
    }

    /**
     * After the death rules: a kept offer stack is in getItemsToKeep, but Paper only keeps stacks that are in the
     * inventory, so it is handed back on respawn instead; a lost one stays in the drops. With keepInventory on,
     * the whole offer comes back.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathAfterRules(PlayerDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        List<ItemStack> offer = dyingOffer.remove(id);
        if (offer == null) return;
        List<ItemStack> back = new ArrayList<>();
        for (ItemStack it : offer) {
            boolean dropped = !e.getKeepInventory() && !e.isCancelled() && e.getDrops().stream().anyMatch(d -> d == it);
            if (!dropped) {
                e.getDrops().removeIf(d -> d == it);
                e.getItemsToKeep().removeIf(d -> d == it);
                back.add(it);
            }
        }
        if (!back.isEmpty()) afterRespawn.computeIfAbsent(id, k -> new ArrayList<>()).addAll(back);
    }

    @EventHandler
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent e) {
        List<ItemStack> back = afterRespawn.remove(e.getPlayer().getUniqueId());
        if (back == null) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (ItemStack it : back) {
                if (p.isOnline()) giveBack(p, it);
                else afterRespawn.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(it);
            }
        });
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        cancelFor(e.getPlayer().getUniqueId(), "зөөгдсөн");
    }

    public void shutdown() {
        for (Trade t : List.copyOf(trades.values())) cancel(t, "сервер унтарч байна");
        for (Map.Entry<UUID, List<ItemStack>> en : List.copyOf(afterRespawn.entrySet())) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) {
                plugin.getLogger().warning("[trade] " + en.getValue().size() + " stacks of " + en.getKey() + " were mid-trade when they died and could not be returned");
                continue;
            }
            for (ItemStack it : en.getValue()) giveBack(p, it);
        }
        afterRespawn.clear();
    }
}
