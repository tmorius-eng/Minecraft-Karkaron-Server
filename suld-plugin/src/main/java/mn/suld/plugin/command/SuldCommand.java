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
            case "flush" -> flush(sender);
            case "auth" -> authStatus(sender);
            case "exp" -> giveExp(sender, args);
            case "quest" -> setQuest(sender, args);
            case "coins" -> giveCoins(sender, args);
            case "classreset" -> classReset(sender, args);
            case "classchange" -> classChange(sender, args);
            case "classseal" -> classSeal(sender, args);
            case "content" -> content(sender, args);
            case "endgame" -> endgame(sender, args);
            case "mastery" -> mastery(sender, args);
            case "profession" -> profession(sender, args);
            case "achievement" -> achievement(sender, args);
            case "payment" -> payment(sender, args);
            case "gather" -> gather(sender, args);
            case "heroic" -> heroic(sender, args);
            case "badge" -> badge(sender, args);
            case "props" -> {
                if (!sender.hasPermission("suld.admin")) {
                    sender.sendMessage(Messages.error("Эрх алга."));
                } else {
                    var ps = ((mn.suld.plugin.SuldPlugin) plugin).props();
                    if (ps != null && args.length >= 3 && args[1].equalsIgnoreCase("tp") && sender instanceof Player pl) {
                        org.bukkit.Location at = ps.where(args[2].toLowerCase(java.util.Locale.ROOT));
                        if (at == null) {
                            sender.sendMessage(Messages.error("Тийм prop алга: " + String.join(", ", ps.ids())));
                        } else {
                            pl.teleportAsync(at);
                            sender.sendMessage(Messages.success(args[2] + " руу явлаа."));
                        }
                    } else if (ps != null) {
                        for (String line : ps.describe(sender instanceof Player pl ? pl.getLocation() : null)) sender.sendMessage(Messages.info(line));
                    }
                }
            }
            case "map" -> {
                if (!sender.hasPermission("suld.admin")) {
                    sender.sendMessage(Messages.error("Эрх алга."));
                } else if (services.map == null) {
                    sender.sendMessage(Messages.error("Газрын зураг унтраастай (config map.enabled)."));
                } else {
                    services.map.command(sender, args);
                }
            }
            case "setrank" -> setRank(sender, args);
            case "grim" -> {
                if (!sender.hasPermission("suld.admin")) {
                    sender.sendMessage(Messages.error("Эрх алга."));
                } else {
                    var g = ((mn.suld.plugin.SuldPlugin) plugin).grim();
                    if (g != null && args.length > 1 && args[1].equalsIgnoreCase("reset")) {
                        g.reset();
                        sender.sendMessage(Messages.success("Grim тоолуур тэглэгдлээ."));
                    } else if (g != null) {
                        for (String line : g.describe()) sender.sendMessage(Messages.info(line));
                    }
                }
            }
            case "guide", "holograms", "hologram" -> holograms(sender, args);
            default -> help(sender);
        }
        return true;
    }

    /** Admin: {@code /suld spawnmob [player] [mobId] [count]} — SÜLD mobs at a player (console too; QA of mob rewards). */
    /**
     * Admin/console: {@code /suld flush}. Writes every cached profile and every dirty style and clan now, then writes
     * {@code plugins/SULD/flush.done} ("ok <epochMs> …" or "error <epochMs> …") once all writes completed, so
     * deploy/backup.sh can take the database dump only after SÜLD's in-memory state is on disk (audit OPS-001).
     * The writes are the normal async saves: nothing blocks the main thread.
     */
    private void flush(CommandSender sender) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        long started = System.currentTimeMillis();
        java.util.List<java.util.concurrent.CompletableFuture<?>> writes = new java.util.ArrayList<>();
        int profiles = 0;
        for (mn.suld.api.profile.PlayerProfile pr : services.profiles().cachedProfiles()) {
            writes.add(services.profiles().save(pr));
            profiles++;
        }
        writes.add(services.styles().stop());
        writes.add(services.clans().shutdown());
        int n = profiles;
        java.nio.file.Path marker = plugin.getDataFolder().toPath().resolve("flush.done");
        java.util.concurrent.CompletableFuture.allOf(writes.toArray(new java.util.concurrent.CompletableFuture[0]))
                .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .whenComplete((v, err) -> {
                    String line = (err == null ? "ok " : "error ") + started + " profiles=" + n + " ms=" + (System.currentTimeMillis() - started)
                            + (err == null ? "" : " " + err);
                    try {
                        java.nio.file.Files.writeString(marker, line + "\n");
                    } catch (java.io.IOException io) {
                        plugin.getLogger().severe("flush: cannot write " + marker + ": " + io.getMessage());
                    }
                    plugin.getLogger().info("[SULD] flush " + line);
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(err == null
                            ? Messages.success("Хадгаллаа: " + n + " профайл, стиль, овог (" + (System.currentTimeMillis() - started) + " ms)")
                            : Messages.error("Хадгалалт бүтсэнгүй: " + err.getMessage())));
                });
    }

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
        if (training(sender, target)) return;
        PlayerProfile profile = target == null ? null : services.profiles().cached(target.getUniqueId()).orElse(null);
        if (profile == null || amount <= 0 || amount > 100_000_000) {
            sender.sendMessage(Messages.error("Онлайн тоглогч ба 1..100000000 EXP."));
            return;
        }
        var result = services.progression().grantExp(profile, amount, mn.suld.api.progression.ExpSource.ADMIN);
        services.profiles().save(profile);
        services.hud().update(target, profile);
        sender.sendMessage(Messages.success(target.getName() + ": +" + amount + " EXP → түвшин " + profile.progression().level()
                + (result.leveledUp() ? " (+" + result.levelsGained() + ")" : "")));
    }

    /** Admin/console: {@code /suld coins <player> <±amount>} — add or remove SÜLD coins (audited). */
    /** Staff support: clear a player's class-change cooldown (docs/CLASS_CHANGE.md). */
    private void classReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        Player target = args.length < 2 ? null : Bukkit.getPlayerExact(args[1]);
        if (training(sender, target)) return;
        PlayerProfile profile = target == null ? null : services.profiles().cached(target.getUniqueId()).orElse(null);
        if (profile == null) {
            sender.sendMessage(Messages.info("/suld classreset <онлайн тоглогч>"));
            return;
        }
        profile.clearClassChangeCooldown();
        if (services.classTrial != null) services.classTrial.clearCooldown(target.getUniqueId());
        services.profiles().save(profile);
        services.audit().record(mn.suld.api.audit.AuditEvent.of(sender.getName(), "class.admin.reset", target.getUniqueId().toString(), "cooldown cleared"));
        sender.sendMessage(Messages.success(target.getName() + ": анги солих хүлээлт арилсан."));
    }

    /** Staff QA: {@code /suld classchange <player> <class>} — the player's real class change, every rule and price. */
    private void classChange(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        Player target = args.length < 3 ? null : Bukkit.getPlayerExact(args[1]);
        mn.suld.api.clazz.PlayerClass to = args.length < 3 ? null : mn.suld.api.clazz.PlayerClass.byId(args[2]).orElse(null);
        if (target == null || to == null || services.classChange == null) {
            sender.sendMessage(Messages.info("/suld classchange <онлайн тоглогч> <baatar|mergen|boo|darkhan|khulegchin>"));
            return;
        }
        var v = services.classChange.change(target, to);
        sender.sendMessage(v.allowed() ? Messages.success(target.getName() + ": " + to.displayName()) : Messages.error(v.refusal().text));
    }

    /** Give class change seals («Хувь Төөргийн Тамга»). */
    private void classSeal(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        Player target = args.length < 2 ? null : Bukkit.getPlayerExact(args[1]);
        int n;
        try {
            n = args.length < 3 ? 1 : Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            n = 0;
        }
        if (target == null || n < 1 || n > 64) {
            sender.sendMessage(Messages.info("/suld classseal <онлайн тоглогч> [1..64]"));
            return;
        }
        target.getInventory().addItem(mn.suld.plugin.clazz.ClassSeal.create(n)).values()
                .forEach(x -> target.getWorld().dropItemNaturally(target.getLocation(), x));
        services.audit().record(mn.suld.api.audit.AuditEvent.of(sender.getName(), "class.admin.seal", target.getUniqueId().toString(), "amount=" + n));
        sender.sendMessage(Messages.success(target.getName() + ": " + n + " Хувь Төөргийн Тамга."));
    }

    private void giveCoins(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        Player target = args.length < 3 ? null : Bukkit.getPlayerExact(args[1]);
        if (training(sender, target)) return;
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
        sender.sendMessage(Messages.success(target.getName() + ": " + (amount > 0 ? "+" : "") + amount + " зоос → " + now + " зоос"));
    }

    /**
     * Admin/console: {@code /suld badge <player> <id|none>} — give (or take) a staff/supporter badge. Kept in the
     * player's data, so it survives restarts; a LuckPerms permission {@code suld.badge.<id>} on a group still wins
     * (that is the way for offline players and whole groups).
     */
    private void badge(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        Player target = args.length < 3 ? null : Bukkit.getPlayerExact(args[1]);
        String id = args.length < 3 ? "" : args[2].toLowerCase(java.util.Locale.ROOT);
        mn.suld.api.style.StaffBadge badge = null;
        for (mn.suld.api.style.StaffBadge b : mn.suld.api.style.StaffBadge.values()) if (b.id().equals(id)) badge = b;
        if (target == null || (badge == null && !id.equals("none"))) {
            sender.sendMessage(Messages.info("/suld badge <онлайн тоглогч> <owner|admin|developer|mod|helper|streamer|sponsor|none>"));
            return;
        }
        var pdc = target.getPersistentDataContainer();
        if (badge == null) pdc.remove(mn.suld.plugin.ui.StyleFormat.BADGE_KEY);
        else pdc.set(mn.suld.plugin.ui.StyleFormat.BADGE_KEY, org.bukkit.persistence.PersistentDataType.STRING, badge.id());
        services.styles().badgeChanged(target);
        plugin.getLogger().info("[audit] badge " + id + " target=" + target.getName() + " by=" + sender.getName());
        sender.sendMessage(Messages.success(target.getName() + ": " + (badge == null ? "тэмдэг хасагдлаа" : badge.displayName() + " тэмдэг олгогдлоо")));
        if (badge != null) target.sendMessage(Messages.success("Танд «" + badge.displayName() + "» тэмдэг олгогдлоо!"));
    }

    /** Admin/console: {@code /suld setrank <player> <rank>} — set a rank directly (events, staff, tests). */
    private void setRank(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        Player target = args.length < 3 ? null : Bukkit.getPlayerExact(args[1]);
        mn.suld.api.style.Rank rank = null;
        if (args.length >= 3) {
            for (mn.suld.api.style.Rank r : mn.suld.api.style.Rank.values()) if (r.id().equalsIgnoreCase(args[2])) rank = r;
        }
        if (target == null || rank == null) {
            sender.sendMessage(Messages.info("/suld setrank <онлайн тоглогч> <ard|tsereg|aravt|zuut|myangat|tumen|noyon|khaan>"));
            return;
        }
        services.styles().setRank(target, rank);
        plugin.getLogger().info("[audit] setrank " + rank.id() + " target=" + target.getName() + " by=" + sender.getName());
        sender.sendMessage(Messages.success(target.getName() + ": цол → " + rank.displayName()));
    }

    /** Admin/console: {@code /suld quest <player> <chapter 1..N | reset>} — move a player in the storyline. */
    private void setQuest(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        var chain = services.quests().chain();
        Player target = args.length < 3 ? null : Bukkit.getPlayerExact(args[1]);
        if (training(sender, target)) return;
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
            sender.sendMessage(Messages.info("/suld holograms reload|list|rebuild — hologram-ууд (content/holograms.json)"));
            sender.sendMessage(Messages.info("/suld content validate|export — тоглоомын агуулгын файлууд (моб, бүс, dungeon, эрэл)"));
            sender.sendMessage(Messages.info("/suld endgame <тоглогч> rank|points|palace <тоо> — Тэнгэрийн Зэрэг (QA)"));
            sender.sendMessage(Messages.info("/suld badge <тоглогч> <owner|admin|developer|mod|helper|streamer|sponsor|none> — ажилтан/дэмжигчийн тэмдэг"));
            sender.sendMessage(Messages.info("/suld setrank <тоглогч> <ard|tsereg|aravt|zuut|myangat|tumen|noyon|khaan> — цол"));
            sender.sendMessage(Messages.info("/suld props [tp <id>] — 3D дурсгалууд хаана байгаа / тийш очих"));
            sender.sendMessage(Messages.info("/suld grim [reset] — GrimAC: SÜLD хөдөлгөөнд уучилсан flag-ууд"));
            sender.sendMessage(Messages.info("/suld map status|scan [full|stop]|export <zoom> [x z r]|pois|flush|refresh [r] — газрын зураг"));
            sender.sendMessage(Messages.info("/suld auth · /suld spawnmob"));
        }
        sender.sendMessage(Messages.info("Бүх команд: /commands"));
    }

    /**
     * {@code /suld holograms reload}: read content/holograms.json again (the server's copy, else the bundled one) and
     * place every board anew, at once; {@code list}: each board and where it stands; {@code rebuild} (and the old
     * {@code /suld guide}): place them again without reading the file.
     */
    private void holograms(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        var h = ((mn.suld.plugin.SuldPlugin) plugin).holograms();
        if (h == null) {
            sender.sendMessage(Messages.error("Hologram ажиллахгүй байна."));
            return;
        }
        String what = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : (args[0].equalsIgnoreCase("guide") ? "rebuild" : "list");
        switch (what) {
            case "reload" -> {
                var issues = h.reload();
                if (issues.isEmpty()) {
                    sender.sendMessage(Messages.success("content/holograms.json уншлаа: " + mn.suld.plugin.hologram.Holograms.set().boards().size() + " самбар шинээр байрлаа."));
                } else {
                    sender.sendMessage(Messages.error("holograms.json-д " + issues.size() + " алдаа — jar доторх hologram-ууд ажиллана:"));
                    issues.stream().limit(15).forEach(i -> sender.sendMessage(Messages.info(" • " + i)));
                }
            }
            case "rebuild" -> sender.sendMessage(Messages.success("Hologram: " + h.rebuild() + " самбар шинэчиллээ."));
            default -> {
                sender.sendMessage(Messages.accent("Hologram самбарууд (content/holograms.json):"));
                h.describe().forEach(l -> sender.sendMessage(Messages.info(" • " + l)));
                sender.sendMessage(Messages.info("Засах: /suld content export → plugins/SULD/content/holograms.json → /suld holograms reload"));
            }
        }
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
                java.nio.file.Path holo = dir.resolve(mn.suld.api.hologram.HologramLoader.FILE);
                if (!java.nio.file.Files.exists(holo)) {
                    java.nio.file.Files.writeString(holo, mn.suld.plugin.hologram.Holograms.bundledText(), java.nio.charset.StandardCharsets.UTF_8);
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
        try {
            var h = mn.suld.plugin.hologram.Holograms.read(dir);
            if (h.ok()) {
                sender.sendMessage(Messages.success("holograms.json зөв: " + h.set().boards().size() + " самбар, " + h.set().labels().size() + " шошго. Хэрэгжүүлэх: /suld holograms reload"));
            } else {
                sender.sendMessage(Messages.error("holograms.json-д " + h.issues().size() + " алдаа:"));
                h.issues().stream().limit(15).forEach(i -> sender.sendMessage(Messages.info(" • " + i)));
            }
        } catch (java.io.IOException e) {
            sender.sendMessage(Messages.error("holograms.json уншигдсангүй: " + e.getMessage()));
        }
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

    /**
     * True (and the sender told why) when {@code target} trains: an admin change would land on the training stand-in,
     * which is never written and is dropped when they leave (docs/TRAINING_GROUNDS.md).
     */
    private boolean training(CommandSender sender, Player target) {
        if (target == null || services.training == null || !services.training.training(target.getUniqueId())) return false;
        sender.sendMessage(Messages.error(target.getName() + " дасгалын талбайд байна (/dasgal) — гарсны дараа өөрчилнө үү."));
        return true;
    }

    /** Staff/QA: set a player's Тэнгэрийн Зэрэг rank, Тэнгэрийн оноо or Тэнгэрийн Ордон clears. Audited. */
    private void endgame(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        org.bukkit.entity.Player t = args.length > 1 ? org.bukkit.Bukkit.getPlayerExact(args[1]) : null;
        if (training(sender, t)) return;
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
            case "rank" -> e = e.withAscension((int) Math.min(mn.suld.api.balance.AscensionHonor.LAST, n));
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

    /** Staff/QA: {@code /suld mastery <player> <SCIMITAR|COMPOSITE_BOW|SHAMANIC_STAFF|HAMMER|LANCE> <level>}. Audited. */
    private void mastery(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
        if (training(sender, t)) return;
        mn.suld.api.mastery.WeaponMastery.Track track = null;
        int level = -1;
        try {
            if (args.length > 3) {
                track = mn.suld.api.mastery.WeaponMastery.Track.valueOf(args[2].toUpperCase(java.util.Locale.ROOT));
                level = Integer.parseInt(args[3]);
            }
        } catch (IllegalArgumentException e) {
            track = null;
        }
        if (t == null || track == null || level < 1 || level > mn.suld.api.mastery.WeaponMastery.MAX_LEVEL || services.weaponMastery == null) {
            sender.sendMessage(Messages.error("/suld mastery <онлайн тоглогч> SCIMITAR|COMPOSITE_BOW|SHAMANIC_STAFF|HAMMER|LANCE <1-50>"));
            return;
        }
        services.weaponMastery.set(t.getUniqueId(), track, level);
        services.audit().record(mn.suld.api.audit.AuditEvent.of(sender instanceof Player p ? p.getUniqueId().toString() : "console",
                "admin.weapon_mastery", t.getUniqueId().toString(), track.name() + "=" + level));
        sender.sendMessage(Messages.success(t.getName() + ": " + track.displayName() + " " + level));
    }

    /** Staff/QA: {@code /suld profession <player> <craft> <1-50>} sets a craft's level. Audited. */
    /**
     * Staff/QA: {@code /suld achievement <player>} (the line the bots read: unlocked, season, points, tier, heroic),
     * {@code … deed <DEED> <subject|-> <amount>} (recorded as the game would), {@code … points <n>}, {@code … heroic},
     * {@code … reset}. Audited (admin.achievement).
     */
    /**
     * {@code /suld payment <player>|reconcile} (docs/PAYMENT_ARCHITECTURE.md §Staff): whether the gateway is on, a player's
     * last intents, or a reconcile now. Nothing here can mark a purchase paid: only the provider's signed event does.
     */
    private void payment(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        var pay = services.payments;
        if (args.length < 2) {
            sender.sendMessage(Messages.error("/suld payment <тоглогч|uuid> | reconcile"));
            return;
        }
        String actor = sender instanceof Player p ? p.getUniqueId().toString() : "console";
        java.util.function.Consumer<String> out = line -> {
            sender.sendMessage(Messages.info(line));
            if (!(sender instanceof Player)) return; // the console already printed it
            Bukkit.getLogger().info("[SULD] " + line);
        };
        if (args[1].equalsIgnoreCase("reconcile")) {
            services.audit().record(mn.suld.api.audit.AuditEvent.of(actor, "admin.payment", "reconcile", "reconcile"));
            pay.reconcileNow(out);
            return;
        }
        java.util.UUID id;
        String name = args[1];
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            id = online.getUniqueId();
        } else {
            try {
                id = java.util.UUID.fromString(name);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(Messages.error("Онлайн тоглогч эсвэл UUID: " + name));
                return;
            }
        }
        services.audit().record(mn.suld.api.audit.AuditEvent.of(actor, "admin.payment", id.toString(), "view"));
        pay.describe(id, name, out);
    }

    private void achievement(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        var ach = services.achievements;
        Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
        if (t == null || ach == null) {
            sender.sendMessage(Messages.error("/suld achievement <онлайн тоглогч> [deed <DEED> <subject|-> <тоо> | points <тоо> | heroic | reset]"));
            return;
        }
        java.util.UUID id = t.getUniqueId();
        String sub = args.length > 2 ? args[2].toLowerCase(java.util.Locale.ROOT) : "";
        switch (sub) {
            case "" -> {
                long points = ach.points(id);
                sender.sendMessage(Messages.info("deeds " + t.getName() + (ach.loaded(id) ? "" : " (loading)") + ": unlocked=" + ach.unlocked(id).size()
                        + " [" + String.join(",", new java.util.TreeSet<>(ach.unlocked(id))) + "] season=" + ach.season().id() + " points=" + points
                        + " tier=" + mn.suld.api.battlepass.CampaignPass.tier(points) + " heroic=" + ach.heroic(id)
                        + " claimable=" + ach.claimable(t).size()));
                return;
            }
            case "deed" -> {
                try {
                    var deed = mn.suld.api.achievement.Deed.valueOf(args[3].toUpperCase(java.util.Locale.ROOT));
                    String subject = args[4].equals("-") ? "" : args[4];
                    long amount = Long.parseLong(args[5]);
                    ach.staffRecord(t, deed, subject, amount);
                    sender.sendMessage(Messages.success("deed " + deed + " " + subject + " " + amount + " → " + t.getName()));
                } catch (RuntimeException e) {
                    sender.sendMessage(Messages.error("/suld achievement <тоглогч> deed <DEED> <subject|-> <тоо>"));
                    return;
                }
            }
            case "points" -> {
                try {
                    ach.staffPoints(t, Long.parseLong(args[3]));
                    sender.sendMessage(Messages.success("points +" + args[3] + " → " + t.getName()));
                } catch (RuntimeException e) {
                    sender.sendMessage(Messages.error("/suld achievement <тоглогч> points <тоо>"));
                    return;
                }
            }
            case "heroic" -> ach.openHeroic(id, "staff", line -> sender.sendMessage(Messages.info("heroic " + t.getName() + ": " + line)));
            case "reset" -> {
                ach.reset(t);
                sender.sendMessage(Messages.success("deeds of " + t.getName() + " reset"));
            }
            default -> {
                sender.sendMessage(Messages.error("/suld achievement <тоглогч> [deed … | points … | heroic | reset]"));
                return;
            }
        }
        services.audit().record(mn.suld.api.audit.AuditEvent.of(sender.getName(), "admin.achievement", id.toString(), String.join(" ", args)));
    }

    private void profession(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
        if (t != null && args.length == 2 && services.professions != null) {
            // the four crafts of a player: level and points toward the next (the bots read this line)
            StringBuilder b = new StringBuilder("crafts " + t.getName() + (services.professions.loaded(t.getUniqueId()) ? "" : " (loading)") + ":");
            for (mn.suld.api.mastery.Profession c : mn.suld.api.mastery.Profession.values()) {
                mn.suld.api.mastery.Profession.State st = services.professions.state(t.getUniqueId(), c);
                b.append(' ').append(c.name()).append('=').append(st.level()).append('/').append(st.points());
            }
            sender.sendMessage(Messages.info(b.toString()));
            return;
        }
        if (training(sender, t)) return;
        mn.suld.api.mastery.Profession craft = null;
        int level = -1;
        try {
            if (args.length > 3) {
                craft = mn.suld.api.mastery.Profession.valueOf(args[2].toUpperCase(java.util.Locale.ROOT));
                level = Integer.parseInt(args[3]);
            }
        } catch (IllegalArgumentException e) {
            craft = null;
        }
        if (t == null || craft == null || level < 1 || level > mn.suld.api.mastery.Profession.MAX_LEVEL || services.professions == null) {
            sender.sendMessage(Messages.error("/suld profession <онлайн тоглогч> TOMRIIN_DARKHAN|EMIIN_BARIACH|ADUUCHIN|ESGIICH <1-50>"));
            return;
        }
        services.professions.set(t.getUniqueId(), craft, level);
        services.audit().record(mn.suld.api.audit.AuditEvent.of(sender instanceof Player p ? p.getUniqueId().toString() : "console",
                "admin.profession", t.getUniqueId().toString(), craft.name() + "=" + level));
        sender.sendMessage(Messages.success(t.getName() + ": " + craft.displayName() + " " + level));
    }

    /**
     * Staff/QA: {@code /suld gather list <player> [n]} prints the gathering places nearest a player (id, kind, tier, where,
     * state, distance); {@code /suld gather tp <player> <id>} takes the player beside one; {@code /suld gather respawn}
     * brings every spent node and animal back.
     */
    private void gather(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        var ps = services.professions;
        String sub = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
        Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player p ? p : null;
        switch (sub) {
            case "list" -> {
                if (t == null) {
                    sender.sendMessage(Messages.error("/suld gather list <онлайн тоглогч> [тоо]"));
                    return;
                }
                int n = 12;
                try {
                    if (args.length > 3) n = Math.max(1, Math.min(500, Integer.parseInt(args[3])));
                } catch (NumberFormatException ignored) {
                    // the default
                }
                for (String line : ps.describe(t, n)) sender.sendMessage(Messages.info(line));
            }
            case "tp" -> {
                org.bukkit.Location at = t == null || args.length < 4 ? null : ps.where(args[3]);
                if (at == null) {
                    sender.sendMessage(Messages.error("/suld gather tp <онлайн тоглогч> <node id>"));
                    return;
                }
                t.teleportAsync(at.clone().add(1.5, 0.2, 0));
                sender.sendMessage(Messages.success(t.getName() + " → " + args[3]));
            }
            case "respawn" -> {
                ps.respawnAll();
                sender.sendMessage(Messages.success("Бүх цэг, мал сэргэлээ."));
            }
            default -> sender.sendMessage(Messages.error("/suld gather list|tp|respawn"));
        }
    }

    /** Staff/QA: {@code /suld heroic <player> <dungeon> <open> <best>} raises a player's heroic keys (never lowers). Audited. */
    private void heroic(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return;
        }
        Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
        if (training(sender, t)) return;
        mn.suld.api.dungeon.DungeonDefinition def = args.length > 2
                ? mn.suld.plugin.content.SuldContent.dungeonFor(args[2].startsWith("dungeon.") ? args[2] : "dungeon." + args[2]) : null;
        int open = -1, best = -1;
        try {
            if (args.length > 4) {
                open = Integer.parseInt(args[3]);
                best = Integer.parseInt(args[4]);
            }
        } catch (NumberFormatException ignored) {
            // the usage line below
        }
        if (t == null || def == null || open < 1 || best < 0 || services.heroic == null) {
            sender.sendMessage(Messages.error("/suld heroic <онлайн тоглогч> <агуй> <нээлттэй 1-20> <шилдэг 0-20>"));
            return;
        }
        services.heroic.grant(t.getUniqueId(), def.id(), open, best);
        services.audit().record(mn.suld.api.audit.AuditEvent.of(sender instanceof Player p ? p.getUniqueId().toString() : "console",
                "admin.heroic_keys", t.getUniqueId().toString(), def.id() + " open=" + open + " best=" + best));
        sender.sendMessage(Messages.success(t.getName() + ": «" + def.displayName() + "» +" + open + " нээлттэй, шилдэг +" + best));
    }

    private void info(CommandSender sender) {
        String v = plugin.getPluginMeta().getVersion();
        int plus = v.indexOf('+');
        sender.sendMessage(Messages.accent("SULD v" + (plus < 0 ? v : v.substring(0, plus))));
        if (plus >= 0) sender.sendMessage(Messages.info("Build: " + v.substring(plus + 1)
                + " (github.com/tmorius-eng/Minecraft-Karkaron-Server/commit/" + v.substring(plus + 1).replace("-dirty", "") + ")"));
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
