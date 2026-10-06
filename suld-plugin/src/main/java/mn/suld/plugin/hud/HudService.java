package mn.suld.plugin.hud;

import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.Progression;
import mn.suld.api.service.ProgressionService;
import mn.suld.plugin.content.SuldContent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom SÜLD HUD rendered as a per-player scoreboard sidebar. Fully hand-built
 * on the Bukkit scoreboard API — no BetterHud/TAB/etc. Updates only when the
 * displayed values actually change (per-player signature), to avoid packet spam.
 */
public final class HudService {

    private final ProgressionService progression;
    private final Map<UUID, String> lastSignature = new ConcurrentHashMap<>();

    public HudService(ProgressionService progression) {
        this.progression = progression;
    }

    public void update(Player player, PlayerProfile profile) {
        List<String> lines = buildLines(player, profile);
        String signature = String.join("\n", lines);
        if (signature.equals(lastSignature.get(player.getUniqueId()))) {
            return; // nothing changed
        }
        lastSignature.put(player.getUniqueId(), signature);

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective(
                "suld", Criteria.DUMMY,
                Component.text("ᠰ SÜLD", Messages.BRAND, TextDecoration.BOLD));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            obj.getScore(unique(lines.get(i), i)).setScore(score--);
        }
        player.setScoreboard(board);
    }

    public void clear(Player player) {
        lastSignature.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private List<String> buildLines(Player player, PlayerProfile profile) {
        Progression p = profile.progression();
        long toNext = progression.engine().expToNextLevel(p);
        String className = profile.playerClass().map(c -> c.displayName()).orElse("—");
        String resourceName = profile.playerClass().map(c -> c.resourceName()).orElse("—");
        int resourceMax = profile.playerClass().map(c -> c.resourceMax()).orElse(0);
        String quest = profile.questState().active()
                ? SuldContent.FIRST_HUNT.title() + " " + profile.questState().progress() + "/" + SuldContent.FIRST_HUNT.requiredCount()
                : (profile.questState().completed() ? "Дууссан" : "—");

        List<String> lines = new ArrayList<>();
        lines.add("§8" + "-".repeat(20));
        lines.add("§7Анги: §f" + className);
        lines.add("§7Түвшин: §a" + p.level());
        lines.add("§7EXP: §b" + p.expIntoLevel() + (toNext > 0 ? "§7/§b" + (p.expIntoLevel() + toNext) : " §7(дээд)"));
        lines.add("§7HP: §c" + (int) Math.ceil(player.getHealth()) + "§7/§c" + (int) player.getMaxHealth());
        lines.add("§7" + resourceName + ": §e" + resourceMax + "§7/§e" + resourceMax);
        lines.add("§7Эрэл: §f" + quest);
        lines.add("§7Зоос: §6" + profile.currency());
        lines.add("§8suld.mn");
        return lines;
    }

    /** Make each sidebar entry unique (scoreboard entries must differ). */
    @SuppressWarnings("deprecation")
    private static String unique(String line, int index) {
        ChatColor[] colors = ChatColor.values();
        String suffix = "" + ChatColor.RESET + colors[index % colors.length];
        // Cap to the scoreboard entry length limit.
        String base = line.length() > 40 ? line.substring(0, 40) : line;
        return base + suffix;
    }
}
