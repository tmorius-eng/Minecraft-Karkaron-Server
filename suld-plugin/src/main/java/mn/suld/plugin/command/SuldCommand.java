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
            case "spawnmob" -> spawnMob(sender, args);
            case "auth" -> authStatus(sender);
            case "exp" -> giveExp(sender, args);
            case "quest" -> setQuest(sender, args);
            case "coins" -> giveCoins(sender, args);
            case "content" -> content(sender, args);
            case "endgame" -> endgame(sender, args);
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

    /** Admin: {@code /suld spawnmob [player] [mobId] [count]} — SÜLD mobs at a player (console too; QA of mob rewards). */
    private void spawnMob(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        Player at = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
        if (at == null) {
            sender.sendMessage(Messages.error("/suld spawnmob <тоглогч> [mob id] [тоо]"));
            return;
        }
        mn.suld.api.mob.MobDefinition def = args.length > 2 ? mn.suld.plugin.content.SuldContent.mobFor(args[2])
                : mn.suld.plugin.content.SuldContent.GOVIIN_CHONO;
        if (def == null) {
            sender.sendMessage(Messages.error("Мангас олдсонгүй: " + args[2]));
            return;
        }
        int n = 1;
        if (args.length > 3) {
            try {
                n = Math.max(1, Math.min(50, Integer.parseInt(args[3])));
            } catch (NumberFormatException ignored) {
                // keep 1
            }
        }
        for (int i = 0; i < n; i++) services.mobs().spawn(def, at.getLocation().add((i % 5) - 2, 0, (i / 5) - 2));
        sender.sendMessage(Messages.success(def.displayName() + " ×" + n + " дуудлаа."));
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
            sender.sendMessage(Messages.info("/suld content validate|export — тоглоомын агуулгын файлууд (моб, бүс, dungeon, эрэл)"));
            sender.sendMessage(Messages.info("/suld endgame <тоглогч> rank|points|palace <тоо> — Тэнгэрийн Зэрэг (QA)"));
            sender.sendMessage(Messages.info("/suld auth · /suld spawnmob"));
        }
        sender.sendMessage(Messages.info("Бүх команд: /commands"));
    }

    /**
     * {@code /suld content validate}: check the server's content files (plugins/SULD/content) without using them;
     * {@code /suld content export}: write the bundled files there to start editing (an existing file is kept).
     * Edited content takes effect on the next restart (docs/CONTENT_DATA.md).
     */
    private void content(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        java.nio.file.Path dir = plugin.getDataFolder().toPath().resolve("content");
        String what = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "validate";
        if (what.equals("export")) {
            int wrote = 0;
            try {
                java.nio.file.Files.createDirectories(dir);
                for (String f : mn.suld.plugin.content.ContentLoader.FILES) {
                    java.nio.file.Path out = dir.resolve(f);
                    if (java.nio.file.Files.exists(out)) continue;
                    java.nio.file.Files.writeString(out, mn.suld.plugin.content.ContentLoader.classpath().read(f), java.nio.charset.StandardCharsets.UTF_8);
                    wrote++;
                }
            } catch (java.io.IOException e) {
                sender.sendMessage(Messages.error("Бичиж чадсангүй: " + e.getMessage()));
                return;
            }
            sender.sendMessage(Messages.success(wrote + " файл бичлээ: plugins/SULD/content/ (байсан файлыг хөндөөгүй). Засаад /suld content validate, дараа нь серверээ дахин асаа."));
            return;
        }
        java.util.List<String> overridden = new java.util.ArrayList<>();
        var catalog = mn.suld.plugin.content.SuldContent.items();
        var r = mn.suld.plugin.content.ContentLoader.load(mn.suld.plugin.content.ContentLoader.serverOrBundled(dir, overridden),
                id -> catalog.lootTable(id).isPresent(), id -> catalog.item(id).isPresent(), overridden);
        String files = overridden.isEmpty() ? "серверийн файл алга — jar доторхыг шалгалаа" : String.join(", ", overridden);
        if (r.ok()) {
            sender.sendMessage(Messages.success("Агуулга зөв (" + files + "): " + r.pack().mobs().size() + " моб, " + r.pack().regions().size() + " бүс, "
                    + r.pack().areas().size() + " газар, " + r.pack().dungeons().size() + " dungeon, " + r.pack().chapters().size() + " бүлэг. Дахин асаахад хэрэгжинэ."));
        } else {
            sender.sendMessage(Messages.error("Агуулгад " + r.issues().size() + " алдаа (" + files + ") — засах хүртэл jar доторх агуулга ажиллана:"));
            r.issues().stream().limit(15).forEach(i -> sender.sendMessage(Messages.info(" • " + i)));
            if (r.issues().size() > 15) sender.sendMessage(Messages.info(" … дахиад " + (r.issues().size() - 15) + " (консолд бүгд)"));
            r.issues().forEach(i -> plugin.getLogger().warning("[content] " + i));
        }
    }

    /** Staff/QA: set a player's Тэнгэрийн Зэрэг rank, Тэнгэрийн оноо or Тэнгэрийн Ордон clears. Audited. */
    private void endgame(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        org.bukkit.entity.Player t = args.length > 1 ? org.bukkit.Bukkit.getPlayerExact(args[1]) : null;
        mn.suld.api.profile.PlayerProfile pr = t == null ? null : services.profiles().cached(t.getUniqueId()).orElse(null);
        long n;
        try {
            n = args.length > 3 ? Long.parseLong(args[3]) : -1;
        } catch (NumberFormatException e) {
            n = -1;
        }
        if (pr == null || n < 0) {
            sender.sendMessage(Messages.error("/suld endgame <онлайн тоглогч> rank|points|palace <тоо>"));
            return;
        }
        var e = pr.endgame();
        switch (args[2].toLowerCase(java.util.Locale.ROOT)) {
            case "rank" -> e = e.withAscension((int) Math.min(mn.suld.api.balance.Ascension.MAX_RANK, n));
            case "points" -> e = e.withPoints(n);
            case "palace" -> e = new mn.suld.api.profile.Endgame(e.ascension(), e.tengeriPoints(), e.restedExp(), e.curveVersion(), (int) Math.min(1000, n));
            default -> {
                sender.sendMessage(Messages.error("rank|points|palace"));
                return;
            }
        }
        pr.endgame(e);
        services.profiles().save(pr);
        services.audit().record(mn.suld.api.audit.AuditEvent.of(sender instanceof org.bukkit.entity.Player p ? p.getUniqueId().toString() : "console",
                "admin.endgame", t.getUniqueId().toString(), args[2] + "=" + n));
        sender.sendMessage(Messages.success(t.getName() + ": " + e.toJson()));
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
