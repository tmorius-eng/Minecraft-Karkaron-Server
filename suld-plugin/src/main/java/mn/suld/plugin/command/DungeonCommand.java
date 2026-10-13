package mn.suld.plugin.command;

import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.dungeon.DungeonRun;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /dungeon} opens the dungeon window ({@link mn.suld.plugin.gui.DungeonMenu}); {@code enter [id]|status|leave|abort};
 * {@code heroic [id] [key]} the heroic keys (docs/HEROIC_DUNGEONS.md): the window, or a run at a key (the highest open
 * one by default).
 */
public final class DungeonCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("list", "enter", "heroic", "status", "leave", "abort");

    private final SuldServices services;
    private final mn.suld.plugin.gui.DungeonMenu menu;
    private final mn.suld.plugin.gui.HeroicMenu heroicMenu;

    public DungeonCommand(SuldServices services, mn.suld.plugin.gui.DungeonMenu menu, mn.suld.plugin.gui.HeroicMenu heroicMenu) {
        this.services = services;
        this.menu = menu;
        this.heroicMenu = heroicMenu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length >= 3 && args[0].equalsIgnoreCase("grant")) { // /dungeon grant <player> <dungeon|all>: support
            if (!sender.hasPermission("suld.admin.world")) {
                sender.sendMessage(Messages.error("Эрх алга."));
                return true;
            }
            Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Messages.error("Тоглогч онлайн биш."));
                return true;
            }
            int n = 0;
            for (DungeonDefinition d : mn.suld.plugin.content.DungeonContent.ALL) {
                if (args[2].equalsIgnoreCase("all") || shortId(d).equalsIgnoreCase(args[2]) || d.id().equalsIgnoreCase(args[2])) {
                    services.dungeons().grantClear(target, d.id());
                    n++;
                }
            }
            sender.sendMessage(n == 0 ? Messages.error("Ийм агуй алга.") : Messages.success(target.getName() + ": " + n + " агуй давсанд тооцлоо."));
            services.audit().record(mn.suld.api.audit.AuditEvent.of(sender.getName(), "dungeon.grant", target.getName(), args[2]));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Зөвхөн тоглогч ашиглана."));
            return true;
        }
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list", "menu" -> menu.open(player);
            case "enter" -> enter(player, args.length > 1 ? args[1] : "khasar_den");
            case "heroic" -> {
                if (args.length < 2) heroicMenu.open(player);
                else heroic(player, args[1], args.length > 2 ? args[2] : null, args.length > 3 ? args[3] : null);
            }
            case "status" -> status(player);
            case "leave" -> services.parties().leave(player, true);
            case "abort" -> {
                boolean admin = player.hasPermission("suld.admin");
                if (!services.dungeons().abort(player.getUniqueId(), admin)) {
                    player.sendMessage(Messages.error("Зогсоох агуй алга (эсвэл та ахлагч биш)."));
                }
            }
            default -> menu.open(player);
        }
        return true;
    }

    private void enter(Player player, String rawId) {
        String id = rawId.startsWith("dungeon.") ? rawId : "dungeon." + rawId;
        DungeonDefinition def = SuldContent.dungeonFor(id);
        if (def == null) {
            player.sendMessage(Messages.error("Ийм агуй олдсонгүй — /dungeon цонхноос сонго."));
            return;
        }
        Component error = services.dungeons().start(player, def);
        if (error != null) {
            player.sendMessage(error);
        }
    }

    private void heroic(Player player, String rawId, String rawKey, String rawAffixes) {
        String id = rawId.startsWith("dungeon.") ? rawId : "dungeon." + rawId;
        DungeonDefinition def = SuldContent.dungeonFor(id);
        if (def == null) {
            player.sendMessage(Messages.error("Ийм агуй олдсонгүй — /dungeon heroic цонхноос сонго."));
            return;
        }
        int key;
        if (rawKey == null) {
            key = services.heroic.key(player.getUniqueId(), def.id()).open();
        } else {
            try {
                key = Integer.parseInt(rawKey.startsWith("+") ? rawKey.substring(1) : rawKey);
            } catch (NumberFormatException e) {
                player.sendMessage(Messages.error("Түлхүүр бол тоо: /dungeon heroic " + shortId(def) + " <1–20>"));
                return;
            }
        }
        if (rawAffixes != null) { // staff QA: this run's affixes instead of the week's (ZUD,GAN,CHONYN_OMOG,TENGERIIN_NIRGELT or NONE)
            if (!player.hasPermission("suld.admin")) {
                player.sendMessage(Messages.error("Эрх алга."));
                return;
            }
            List<mn.suld.api.dungeon.heroic.SteppeAffix> list = new java.util.ArrayList<>();
            for (String a : rawAffixes.toUpperCase(Locale.ROOT).split(",")) {
                if (a.isBlank() || a.equals("NONE")) continue;
                try {
                    list.add(mn.suld.api.dungeon.heroic.SteppeAffix.valueOf(a.trim()));
                } catch (IllegalArgumentException e) {
                    player.sendMessage(Messages.error("Нөхцөл: ZUD, GAN, CHONYN_OMOG, TENGERIIN_NIRGELT, NONE"));
                    return;
                }
            }
            services.heroic.force(player.getUniqueId(), list);
            services.audit().record(mn.suld.api.audit.AuditEvent.of(player.getUniqueId().toString(), "dungeon.heroic_affixes", def.id(), rawAffixes));
        }
        Component error = services.dungeons().start(player, def, key);
        if (error != null) {
            services.heroic.force(player.getUniqueId(), null); // a refused start keeps nothing for later
            player.sendMessage(error);
        }
    }

    private static String shortId(DungeonDefinition d) {
        return d.id().startsWith("dungeon.") ? d.id().substring("dungeon.".length()) : d.id();
    }

    private void status(Player player) {
        Optional<DungeonRun> run = services.dungeons().runFor(player.getUniqueId());
        if (run.isEmpty()) {
            player.sendMessage(Messages.info("Та агуйд байхгүй байна."));
            return;
        }
        DungeonRun r = run.get();
        mn.suld.plugin.ui.Pages.text(player, "АГУЙ · " + r.dungeonId(), java.util.List.of(
                mn.suld.plugin.ui.Pages.line("Төлөв: " + services.dungeons().statusLine(player.getUniqueId()).orElse("—")),
                mn.suld.plugin.ui.Pages.dim("Өнгөрсөн: " + r.elapsedSeconds() + " с")),
                java.util.List.of(mn.suld.plugin.ui.Pages.command("dungeon leave", "Аянаас гарах")), null);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("enter") || args[0].equalsIgnoreCase("heroic"))) {
            return mn.suld.plugin.content.DungeonContent.ALL.stream().map(DungeonCommand::shortId)
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
