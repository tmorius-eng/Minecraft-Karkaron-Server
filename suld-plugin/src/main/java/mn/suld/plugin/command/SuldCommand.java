package mn.suld.plugin.command;

import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.Progression;
import mn.suld.api.progression.ProgressionEngine;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /suld info} — server/plugin status. {@code /suld profile} — the
 * caller's character summary (class, level, XP bar).
 */
public final class SuldCommand implements CommandExecutor {

    private final Plugin plugin;
    private final SuldServices services;

    public SuldCommand(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "info" -> info(sender);
            case "profile" -> profile(sender);
            case "spawnmob" -> spawnMob(sender);
            case "auth" -> authStatus(sender);
            case "exp" -> giveExp(sender, args);
            case "quest" -> setQuest(sender, args);
            case "coins" -> giveCoins(sender, args);
            case "guide" -> {
                if (!sender.hasPermission("suld.admin")) {
                    sender.sendMessage(Messages.error("Эрх алга."));
                } else {
                    var gb = ((mn.suld.plugin.SuldPlugin) plugin).guide();
                    sender.sendMessage(Messages.success("Заавар самбар: " + (gb == null ? 0 : gb.rebuild()) + " ширхэг шинэчиллээ."));
                }
            }
            default -> help(sender);
        }
        return true;
    }

    private void spawnMob(CommandSender sender) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Зөвхөн тоглогч ашиглана."));
            return;
        }
        services.mobs().spawn(mn.suld.plugin.content.SuldContent.GOVIIN_CHONO, player.getLocation());
        sender.sendMessage(Messages.success("Говийн Чоно дуудлаа."));
    }

    /** Admin/console: {@code /suld exp <player> <amount>} — grant EXP (levels up, upgrades class weapons). */
    private void giveExp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Messages.info("/suld exp <тоглогч> <EXP>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            amount = -1;
        }
        PlayerProfile profile = target == null ? null : services.profiles().cached(target.getUniqueId()).orElse(null);
        if (profile == null || amount <= 0 || amount > 100_000_000) {
            sender.sendMessage(Messages.error("Онлайн тоглогч ба 1..100000000 EXP."));
            return;
        }
        var result = services.progression().grantExp(profile, amount, mn.suld.api.progression.ExpSource.ADMIN);
        services.hud().update(target, profile);
        sender.sendMessage(Messages.success(target.getName() + ": +" + amount + " EXP → түвшин " + profile.progression().level()
                + (result.leveledUp() ? " (+" + result.levelsGained() + ")" : "")));
    }

    /** Admin/console: {@code /suld coins <player> <±amount>} — add or remove SÜLD coins (audited). */
    private void giveCoins(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        Player target = args.length < 3 ? null : Bukkit.getPlayerExact(args[1]);
        PlayerProfile profile = target == null ? null : services.profiles().cached(target.getUniqueId()).orElse(null);
        long amount;
        try {
            amount = args.length < 3 ? 0 : Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            amount = 0;
        }
        if (profile == null || amount == 0 || Math.abs(amount) > 100_000_000) {
            sender.sendMessage(Messages.info("/suld coins <онлайн тоглогч> <±1..100000000>"));
            return;
        }
        long now = profile.addCurrency(amount);
        services.profiles().save(profile);
        services.hud().update(target, profile);
        plugin.getLogger().info("[audit] coins " + (amount > 0 ? "+" : "") + amount + " target=" + target.getName() + " by=" + sender.getName());
        sender.sendMessage(Messages.success(target.getName() + ": " + (amount > 0 ? "+" : "") + amount + " ₮ → " + now + " ₮"));
    }

    /** Admin/console: {@code /suld quest <player> <chapter 1..N | reset>} — move a player in the storyline. */
    private void setQuest(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        var chain = services.quests().chain();
        Player target = args.length < 3 ? null : Bukkit.getPlayerExact(args[1]);
        PlayerProfile profile = target == null ? null : services.profiles().cached(target.getUniqueId()).orElse(null);
        if (profile == null) {
            sender.sendMessage(Messages.info("/suld quest <онлайн тоглогч> <1.." + chain.size() + " | reset>"));
            return;
        }
        if (args[2].equalsIgnoreCase("reset")) {
            profile.questState(mn.suld.api.quest.QuestState.NONE);
        } else {
            int n;
            try {
                n = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                n = -1;
            }
            if (n < 1 || n > chain.size()) {
                sender.sendMessage(Messages.error("Бүлэг 1.." + chain.size()));
                return;
            }
            profile.questState(chain.chapters().get(n - 1).initialState());
        }
        services.quests().ensure(target, profile);
        services.profiles().save(profile);
        sender.sendMessage(Messages.success(target.getName() + ": эрэл → " + profile.questState().questId()));
    }

    /** Admin/console: authentication mode and per-player session/profile state. */
    private void authStatus(CommandSender sender) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        services.auth().statusLines().forEach(line -> sender.sendMessage(Messages.info(line)));
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Messages.accent("SULD — Монгол Hardcore MMORPG"));
        sender.sendMessage(Messages.info("/suld info — серверийн мэдээлэл"));
        sender.sendMessage(Messages.info("/suld profile — таны дүрийн мэдээлэл"));
        if (sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.info("/suld exp <тоглогч> <EXP> — EXP олгох"));
            sender.sendMessage(Messages.info("/suld coins <тоглогч> <±тоо> — зоос нэмэх/хасах"));
            sender.sendMessage(Messages.info("/suld quest <тоглогч> <1..18|reset> — эрлийн бүлэг"));
            sender.sendMessage(Messages.info("/suld guide — заавар самбаруудыг шинэчлэх"));
            sender.sendMessage(Messages.info("/suld auth · /suld spawnmob"));
        }
        sender.sendMessage(Messages.info("Бүх команд: /commands"));
    }

    private void info(CommandSender sender) {
        sender.sendMessage(Messages.accent("SULD v" + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(Messages.info("Онлайн: " + Bukkit.getOnlinePlayers().size()
                + "/" + Bukkit.getMaxPlayers()));
        sender.sendMessage(Messages.info("Хадгалалт: " + services.config().database().type()));
        sender.sendMessage(Messages.info("Дээд түвшин: " + services.config().progression().maxLevel()));
    }

    private void profile(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Энэ командыг зөвхөн тоглогч ашиглана."));
            return;
        }
        PlayerProfile profile = services.profiles().cached(player.getUniqueId()).orElse(null);
        if (profile == null) {
            sender.sendMessage(Messages.error("Профайл ачааллагдаагүй байна. Түр хүлээнэ үү."));
            return;
        }
        ProgressionEngine engine = services.progression().engine();
        Progression progression = profile.progression();

        sender.sendMessage(Messages.accent("Таны дүр"));
        sender.sendMessage(Messages.info("Анги: "
                + profile.playerClass().map(c -> c.displayName()).orElse("сонгоогүй")));
        sender.sendMessage(Messages.info("Түвшин: " + progression.level()
                + "  (дараагийн түвшин хүртэл " + engine.expToNextLevel(progression) + " EXP)"));
        sender.sendMessage(Messages.PREFIX.append(bar(engine.progressFraction(progression))));
    }

    /** A 20-segment XP progress bar as an Adventure component. */
    private Component bar(double fraction) {
        int total = 20;
        int filled = (int) Math.round(fraction * total);
        Component filledPart = Component.text("█".repeat(Math.max(0, filled)), Messages.BRAND);
        Component emptyPart = Component.text("█".repeat(Math.max(0, total - filled)), NamedTextColor.GRAY);
        return filledPart.append(emptyPart)
                .append(Component.text(" " + Math.round(fraction * 100) + "%", NamedTextColor.WHITE));
    }
}
