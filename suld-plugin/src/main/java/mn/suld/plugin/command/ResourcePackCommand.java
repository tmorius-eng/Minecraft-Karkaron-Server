package mn.suld.plugin.command;

import mn.suld.api.config.SuldConfig;
import mn.suld.api.config.SuldConfigFactory;
import mn.suld.plugin.config.BukkitConfigView;
import mn.suld.plugin.resourcepack.ResourcePackService;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /suldpack [status|reload|force <player>]} — custom resource-pack
 * control. No external pack plugin.
 */
public final class ResourcePackCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ResourcePackService packs;

    public ResourcePackCommand(JavaPlugin plugin, ResourcePackService packs) {
        this.plugin = plugin;
        this.packs = packs;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "resend" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "status" -> {
                sender.sendMessage(Messages.info("Дүрс багц идэвхтэй: " + packs.enabled()));
                if (sender instanceof Player p) {
                    sender.sendMessage(Messages.info("Таны төлөв: " + packs.statusOf(p.getUniqueId())));
                }
            }
            case "reload" -> {
                if (!sender.hasPermission("suld.admin.pack")) {
                    sender.sendMessage(Messages.error("Эрх алга."));
                    return true;
                }
                plugin.reloadConfig();
                SuldConfig cfg = SuldConfigFactory.load(new BukkitConfigView(plugin.getConfig()));
                packs.updateSettings(cfg.resourcePack());
                sender.sendMessage(Messages.success("Дүрс багцны тохиргоо дахин ачааллаа."));
            }
            case "force" -> {
                if (!sender.hasPermission("suld.admin.pack")) {
                    sender.sendMessage(Messages.error("Эрх алга."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Messages.info("Хэрэглээ: /suldpack force <тоглогч>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Messages.error("Тоглогч олдсонгүй: " + args[1]));
                    return true;
                }
                packs.send(target);
                sender.sendMessage(Messages.success(target.getName() + "-д дүрс багц илгээлээ."));
            }
            case "resend" -> {
                if (sender instanceof Player p) {
                    packs.send(p);
                } else {
                    sender.sendMessage(Messages.info("Хэрэглээ: /suldpack <status|reload|force>"));
                }
            }
            default -> sender.sendMessage(Messages.info("Хэрэглээ: /suldpack <status|reload|force>"));
        }
        return true;
    }
}
