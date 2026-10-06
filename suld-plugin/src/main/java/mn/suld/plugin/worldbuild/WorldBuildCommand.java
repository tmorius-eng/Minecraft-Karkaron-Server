package mn.suld.plugin.worldbuild;

import mn.suld.plugin.ui.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * {@code /worldbuild} — operate the WorldBuilder (admin, {@code suld.admin.world}):
 * status, build, pause, resume, validate, dump, rollback confirm, approve, lock, unlock, tp &lt;point&gt;.
 */
public final class WorldBuildCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("status", "build", "pause", "resume", "validate", "repair", "dump",
            "rollback", "approve", "lock", "unlock", "tp", "edit");
    private final WorldBuildService service;

    public WorldBuildCommand(WorldBuildService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("suld.admin.world")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return true;
        }
        Consumer<String> say = s -> sender.sendMessage(Messages.info(s));
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> service.status().forEach(say);
            case "build" -> service.plan(say);
            case "pause" -> service.pause(say);
            case "resume" -> service.resume(say);
            case "validate" -> service.validateInWorld(say);
            case "repair" -> service.repair(say);
            case "dump" -> service.dump(say);
            case "rollback" -> {
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                    sender.sendMessage(Messages.error("Барилгыг бүхэлд нь буцаана. Баталгаажуулах: /worldbuild rollback confirm"));
                } else {
                    service.rollback(say);
                }
            }
            case "approve" -> service.setStatus(BuildState.Status.APPROVED, say);
            case "lock" -> service.setStatus(BuildState.Status.LOCKED, say);
            case "unlock" -> service.setStatus(BuildState.Status.BUILT, say);
            case "edit" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Messages.error("Зөвхөн тоглогч."));
                } else {
                    boolean on = service.toggleEditor(p.getUniqueId());
                    sender.sendMessage(on ? Messages.success("Барилгын горим: Хархорумыг засварлах боломжтой (дахин бичвэл унтарна).")
                            : Messages.info("Барилгын горим унтарлаа: хот дахин хамгаалагдсан."));
                }
            }
            case "tp" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Messages.error("Зөвхөн тоглогч."));
                } else if (!service.teleport(p, args.length > 1 ? args[1] : "spawn")) {
                    sender.sendMessage(Messages.error("Цэг олдсонгүй эсвэл барилга алга."));
                }
            }
            default -> sender.sendMessage(Messages.info("/worldbuild " + String.join("|", SUBS)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("suld.admin.world")) return List.of();
        if (args.length == 1) return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            return service.pointIds().stream().filter(s -> s.startsWith(args[1])).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rollback")) return List.of("confirm");
        return List.of();
    }
}
