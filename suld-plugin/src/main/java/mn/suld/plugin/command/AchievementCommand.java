package mn.suld.plugin.command;

import mn.suld.api.achievement.Achievement;
import mn.suld.api.battlepass.CampaignPass;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.gui.AchievementMenu;
import mn.suld.plugin.ui.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /achievements [category]} and {@code /pass [claim]} (docs/ACHIEVEMENTS_AND_PASS.md): the pages, and every reached
 * reward of the season's pass claimed at once.
 */
public final class AchievementCommand implements TabExecutor {

    private final SuldServices services;
    private final AchievementMenu menu;
    private final boolean pass;

    public AchievementCommand(SuldServices services, AchievementMenu menu, boolean pass) {
        this.services = services;
        this.menu = menu;
        this.pass = pass;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Messages.error("Зөвхөн тоглогч."));
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (pass) {
            if (sub.equals("claim") || sub.equals("avah")) {
                List<CampaignPass.Reward> rs = services.achievements.claimable(p);
                if (rs.isEmpty()) p.sendMessage(Messages.info("Авах шагнал алга. /pass"));
                for (CampaignPass.Reward r : rs) services.achievements.claim(p, r, () -> { });
                return true;
            }
            menu.pass(p, -1);
            return true;
        }
        Achievement.Category cat = null;
        for (Achievement.Category c : Achievement.Category.values()) {
            if (c.name().equalsIgnoreCase(sub) || c.displayName().equalsIgnoreCase(sub)) cat = c;
        }
        menu.open(p, cat);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1) return out;
        if (pass) out.add("claim");
        else for (Achievement.Category c : Achievement.Category.values()) out.add(c.name().toLowerCase(Locale.ROOT));
        out.removeIf(s -> !s.startsWith(args[0].toLowerCase(Locale.ROOT)));
        return out;
    }
}
