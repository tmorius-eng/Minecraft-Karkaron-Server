package mn.suld.plugin.branding;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Rotating chat tips ({@code tips:} in config.yml): every {@code interval-minutes} the next line of {@code messages}
 * (MiniMessage; empty = the built-in SÜLD tips) goes to everyone online. {@code interval-minutes: 0} turns it off.
 */
public final class TipsService {

    private static final List<String> DEFAULT_TIPS = List.of(
            "<white><bold>Өдөр бүр <aqua><click:run_command:'/daily'>/daily</click></aqua> — 7 хоногийн дараалсан шагнал!</bold></white>",
            "<white><bold>«Сүлдний Зам» эрэл: <aqua><click:run_command:'/quest'>/quest</click></aqua> — 18 бүлэг, 4 агуй.</bold></white>",
            "<white><bold>Ангийн зэвсгээ барьж 3 товшилтоор ид шид: <aqua><click:run_command:'/skills'>/skills</click></aqua></bold></white>",
            "<white><bold>Шинэ нутаг нээх бүрт EXP — Говь, Хангай, Алтайг судал!</bold></white>",
            "<white><bold>Үхвэл юмныхаа хагасыг алдана. Үнэт зүйлээ хотод хадгал!</bold></white>",
            "<white><bold>Бүлэг байгуулж агуйд яв: <aqua>/party invite</aqua> <gray>→</gray> <aqua>/dungeon list</aqua></bold></white>",
            "<white><bold>Цолоо ахиул: <aqua><click:run_command:'/rankup'>/rankup</click></aqua> · Түвшний шагнал: <aqua><click:run_command:'/lvlup'>/lvlup</click></aqua></bold></white>",
            "<white><bold>Гоёл, таг, өнгө: <aqua><click:run_command:'/cosmetics'>/cosmetics</click></aqua> · Дэлгүүр: <aqua><click:run_command:'/buy'>/buy</click></aqua></bold></white>");

    private final Plugin plugin;
    private final List<Component> tips = new ArrayList<>();
    private int next;

    public TipsService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int minutes = plugin.getConfig().getInt("tips.interval-minutes", 6);
        if (minutes <= 0) return;
        List<String> lines = plugin.getConfig().getStringList("tips.messages");
        if (lines.isEmpty()) lines = DEFAULT_TIPS;
        MiniMessage mm = MiniMessage.miniMessage();
        Component prefix = Component.text("✦ Зөвлөгөө › ", TextColor.fromHexString("#FFD24A"), TextDecoration.BOLD);
        for (String line : lines) {
            try {
                tips.add(prefix.append(mm.deserialize(line)));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("tips.messages: skipping a line that is not valid MiniMessage: " + ex.getMessage());
            }
        }
        if (tips.isEmpty()) return;
        long period = minutes * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::broadcast, period, period);
    }

    private void broadcast() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        Component tip = tips.get(next++ % tips.size());
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(tip);
    }
}
