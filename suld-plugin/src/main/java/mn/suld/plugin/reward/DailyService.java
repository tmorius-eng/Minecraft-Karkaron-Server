package mn.suld.plugin.reward;

import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.ExpSource;
import mn.suld.api.reward.DailyReward;
import mn.suld.api.style.PlayerStyle;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.gui.Menu;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Presentation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /daily}: the daily login reward ({@link DailyReward}) — a seven-day streak board GUI, one claim per calendar
 * day in {@code daily.timezone} (default Asia/Ulaanbaatar), and a reminder on join while today's reward is waiting.
 * Pays coins and EXP only.
 */
public final class DailyService implements Listener, TabExecutor {

    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");
    private static final TextColor GREEN = TextColor.fromHexString("#7CE07C");

    private final Plugin plugin;
    private final SuldServices services;
    private final ZoneId zone;

    public DailyService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        ZoneId z;
        try {
            z = ZoneId.of(plugin.getConfig().getString("daily.timezone", "Asia/Ulaanbaatar"));
        } catch (DateTimeException ex) {
            plugin.getLogger().warning("daily.timezone is not a valid time zone; using Asia/Ulaanbaatar");
            z = ZoneId.of("Asia/Ulaanbaatar");
        }
        this.zone = z;
    }

    private long today() {
        return LocalDate.now(zone).toEpochDay();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Messages.error("Зөвхөн тоглогч ашиглана."));
            return true;
        }
        open(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        return List.of();
    }

    /** The streak board: claimed days, today's chest (click to claim) and the days ahead. */
    public void open(Player p) {
        PlayerStyle style = services.styles().cached(p.getUniqueId()).orElse(null);
        PlayerProfile profile = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (style == null || profile == null) {
            p.sendMessage(Messages.error("Профайл ачаалагдаагүй байна. Түр хүлээнэ үү."));
            return;
        }
        long today = today();
        boolean ready = DailyReward.claimable(style.dailyDay(), today);
        int day = DailyReward.nextDay(style.dailyDay(), style.dailyStreak(), today);
        int level = profile.progression().level();
        Menu m = new Menu(3, "Өдрийн шагнал · " + (ready ? "бэлэн!" : "маргааш"), null);
        for (int d = 1; d <= DailyReward.CYCLE; d++) {
            boolean done = d < day || (!ready && d == day);
            boolean now = ready && d == day;
            TextColor color = done ? GREEN : now ? GOLD : NamedTextColor.GRAY;
            List<Component> info = new ArrayList<>();
            info.add(Menu.kv("Зоос:", DailyReward.coins(d) + " ₮", GOLD));
            info.add(Menu.kv("EXP:", String.valueOf(DailyReward.exp(d, level)), GREEN));
            Material icon = done ? Material.LIME_STAINED_GLASS_PANE : now ? Material.CHEST
                    : d == DailyReward.CYCLE ? Material.ENDER_CHEST : Material.GRAY_STAINED_GLASS_PANE;
            ItemStack it = Menu.item(icon, Component.text(d + "-р өдөр" + (d == DailyReward.CYCLE ? " · Их шагнал" : ""), color, TextDecoration.BOLD),
                    Menu.lore(color, List.of(Component.text(done ? "Авсан ✔" : now ? "Өнөөдрийн шагнал!" : "Өдөр бүр орж ир.",
                            NamedTextColor.WHITE, TextDecoration.BOLD)), info, now ? "Дарж авах" : null));
            if (now) Menu.glow(it);
            m.set(9 + d, it, now ? (pl, click) -> claim(pl) : null);
        }
        m.set(22, Menu.item(Material.CLOCK, Menu.title("Дараалал: " + (ready ? Math.max(0, day - 1) : day) + "/" + DailyReward.CYCLE, GOLD),
                List.of(Component.text("Өдөр алгасвал 1-р өдрөөс эхэлнэ.", NamedTextColor.WHITE, TextDecoration.BOLD))), null);
        m.open(p);
    }

    private void claim(Player p) {
        PlayerStyle style = services.styles().cached(p.getUniqueId()).orElse(null);
        PlayerProfile profile = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (style == null || profile == null) return;
        long today = today();
        int level = profile.progression().level();
        DailyReward.Claim c = DailyReward.claim(style.dailyDay(), style.dailyStreak(), today, level);
        if (!c.allowed() || !style.claimDaily(today, c.day())) {
            p.sendMessage(Messages.info("Өнөөдрийн шагналаа авсан байна. Маргааш дахин ир!"));
            p.closeInventory();
            return;
        }
        profile.addCurrency(c.coins());
        var gained = services.progression().grantExp(profile, c.exp(), ExpSource.OTHER);
        services.profiles().save(profile);
        p.closeInventory();
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
        p.sendMessage(Messages.success("Өдрийн шагнал (" + c.day() + "/" + DailyReward.CYCLE + "-р өдөр): +"
                + c.coins() + " ₮, +" + c.exp() + " EXP"));
        if (gained.leveledUp()) Presentation.levelUp(p, level, gained.after().level());
        else Presentation.banner(p, "ӨДРИЙН ШАГНАЛ", c.day() + "-р өдөр · +" + c.coins() + " ₮", NamedTextColor.GOLD);
        services.hud().update(p, profile);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            PlayerStyle style = services.styles().cached(p.getUniqueId()).orElse(null);
            PlayerProfile profile = services.profiles().cached(p.getUniqueId()).orElse(null);
            if (style == null || profile == null || !profile.hasSelectedClass()) return;
            if (!DailyReward.claimable(style.dailyDay(), today())) return;
            p.sendMessage(Component.text("✦ ", GOLD).append(Component.text("Өдрийн шагнал хүлээж байна! ", NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text("[/daily]", NamedTextColor.AQUA, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/daily"))));
        }, 100L);
    }
}
