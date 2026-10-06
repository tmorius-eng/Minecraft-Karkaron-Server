package mn.suld.plugin.gui;

import mn.suld.api.leaderboard.Leaderboard;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * {@code /top [level|coins]}: the server leaderboards as a podium GUI. Stored rows are refreshed from the database
 * every minute (off the main thread); online players' live numbers are merged in when the menu opens.
 */
public final class LeaderboardService implements TabExecutor {

    private static final int SIZE = 10;
    private static final int[] SLOTS = {4, 12, 14, 19, 20, 21, 22, 23, 24, 25};
    private static final TextColor[] PODIUM = {TextColor.fromHexString("#FFD24A"), TextColor.fromHexString("#D9E2EC"),
            TextColor.fromHexString("#E0965A")};

    private final Plugin plugin;
    private final SuldServices services;
    private final Map<Leaderboard, List<Leaderboard.Entry>> stored = new EnumMap<>(Leaderboard.class);

    public LeaderboardService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        for (Leaderboard b : Leaderboard.values()) stored.put(b, List.of());
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 40L, 20L * 60);
    }

    private void refresh() {
        for (Leaderboard b : Leaderboard.values()) {
            services.profileRepository().top(b, SIZE + 20).whenComplete((rows, err) -> {
                if (err != null) {
                    plugin.getLogger().log(Level.WARNING, "Leaderboard " + b + " refresh failed", err);
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> stored.put(b, rows));
            });
        }
    }

    public List<Leaderboard.Entry> current(Leaderboard b) {
        List<Leaderboard.Entry> rows = new ArrayList<>(stored.get(b));
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
            if (pr == null || !pr.hasSelectedClass()) continue;
            rows.add(new Leaderboard.Entry(pr.playerId(), pr.name(), pr.progression().level(), pr.progression().expIntoLevel(), pr.currency()));
        }
        return b.rank(rows, SIZE);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        Leaderboard b = a.length > 0 && a[0].toLowerCase(Locale.ROOT).startsWith("c") ? Leaderboard.COINS : Leaderboard.LEVEL;
        if (s instanceof Player p) {
            open(p, b);
            return true;
        }
        s.sendMessage(Messages.accent("Шилдэг · " + b.displayName()));
        int i = 1;
        for (Leaderboard.Entry e : current(b)) s.sendMessage(Messages.info(i++ + ". " + e.name() + " — " + value(b, e)));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        return a.length == 1 ? List.of("level", "coins") : List.of();
    }

    private static String value(Leaderboard b, Leaderboard.Entry e) {
        return switch (b) {
            case LEVEL -> "Түвшин " + e.level();
            case COINS -> String.format(Locale.ROOT, "%,d ₮", e.coins());
        };
    }

    public void open(Player viewer, Leaderboard b) {
        List<Leaderboard.Entry> top = current(b);
        Menu m = new Menu(5, "Шилдэг · " + b.displayName(), null);
        for (int i = 0; i < SLOTS.length; i++) {
            if (i >= top.size()) {
                m.set(SLOTS[i], Menu.item(Material.GRAY_STAINED_GLASS_PANE, Component.text((i + 1) + ". —", NamedTextColor.GRAY, TextDecoration.BOLD), List.of()), null);
                continue;
            }
            Leaderboard.Entry e = top.get(i);
            TextColor color = i < PODIUM.length ? PODIUM[i] : NamedTextColor.WHITE;
            ItemStack head = Menu.item(Material.PLAYER_HEAD, Component.text((i + 1) + ". " + e.name(), color, TextDecoration.BOLD), List.of(
                    Menu.kv("Түвшин:", String.valueOf(e.level()), TextColor.fromHexString("#7CE07C")),
                    Menu.kv("Зоос:", String.format(Locale.ROOT, "%,d ₮", e.coins()), TextColor.fromHexString("#FFD24A"))));
            if (head.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(Bukkit.getOfflinePlayer(e.player()));
                head.setItemMeta(sm);
            }
            if (i < 3) Menu.glow(head);
            m.set(SLOTS[i], head, null);
        }
        for (Leaderboard other : Leaderboard.values()) {
            boolean active = other == b;
            int slot = other == Leaderboard.LEVEL ? 39 : 41;
            ItemStack tab = Menu.item(other == Leaderboard.LEVEL ? Material.EXPERIENCE_BOTTLE : Material.GOLD_INGOT,
                    Menu.title(other.displayName(), active ? TextColor.fromHexString("#FFD24A") : NamedTextColor.WHITE),
                    List.of(Component.text(active ? "Одоо харж байна" : "Дарж харах", NamedTextColor.WHITE, TextDecoration.BOLD)));
            if (active) Menu.glow(tab);
            m.set(slot, tab, active ? null : (pl, click) -> open(pl, other));
        }
        m.open(viewer);
    }
}
