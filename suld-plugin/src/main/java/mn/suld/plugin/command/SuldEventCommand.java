package mn.suld.plugin.command;

import mn.suld.api.worldevent.WorldEventDefinition;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.worldevent.WorldEventService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Admin: {@code /suldevent start [wolf_raid] | stop | status}. Status is open to everyone. */
public final class SuldEventCommand implements CommandExecutor, TabCompleter {

    private final WorldEventService events;

    public SuldEventCommand(WorldEventService events) {
        this.events = events;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("status") && !sender.hasPermission("suld.admin.event")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return true;
        }
        switch (sub) {
            case "start" -> {
                WorldEventDefinition def = SuldContent.worldEventFor(args.length > 1 ? args[1] : "wolf_raid");
                if (def == null) {
                    sender.sendMessage(Messages.error("Ийм үйл явдал алга. (wolf_raid)"));
                    return true;
                }
                Component error = events.start(def);
                sender.sendMessage(error != null ? error : Messages.success(def.displayName() + " эхэллээ."));
            }
            case "stop" -> sender.sendMessage(events.stop()
                    ? Messages.info("Үйл явдлыг зогсоолоо.") : Messages.error("Идэвхтэй үйл явдал алга."));
            default -> sender.sendMessage(events.active()
                    .map(r -> Messages.accent(r.definition().displayName() + ": " + r.kills() + "/"
                            + r.definition().targetKills() + ", " + r.remainingSeconds(Instant.now()) + "с үлдсэн, "
                            + r.contributions().size() + " оролцогч"))
                    .orElse(Messages.info("Одоогоор үйл явдал алга.")));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("start", "stop", "status").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return args.length == 2 && args[0].equalsIgnoreCase("start") ? List.of("wolf_raid") : List.of();
    }
}
