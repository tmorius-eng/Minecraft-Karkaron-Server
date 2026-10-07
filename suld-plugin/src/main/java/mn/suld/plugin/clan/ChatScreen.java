package mn.suld.plugin.clan;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import mn.suld.api.chat.ChatChannel;
import mn.suld.api.chat.ChatHistory;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The SÜLD chat screen ({@code /chat}, docs/CHAT.md): a native dialog with one tab per channel (Бүгд shows everything the
 * player could hear), the last lines of that tab with each speaker's head, a text field and «Илгээх». Sending sets the
 * sticky channel to the tab and reopens the screen, so it works like a chat app; Esc closes it. Also the chat commands:
 * {@code /ch <channel>} (sticky), {@code /g /l /pc /tr <text>} (one line).
 */
public final class ChatScreen implements TabExecutor {

    private static final int LINES = 12;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();

    private final Plugin plugin;
    private final ChatChannels channels;

    public ChatScreen(Plugin plugin, ChatChannels channels) {
        this.plugin = plugin;
        this.channels = channels;
    }

    /** Opens the screen on {@code tab} (main thread). */
    public void open(Player p, ChatChannel tab) {
        List<ChatHistory.Line> lines = channels.history().view(tab, p.getUniqueId(), LINES);
        List<DialogBody> body = new ArrayList<>();
        Component log = Component.empty();
        if (lines.isEmpty()) {
            log = Component.text("Энэ сувагт одоохондоо бичлэг алга.", NamedTextColor.GRAY, TextDecoration.ITALIC);
        } else {
            boolean first = true;
            for (ChatHistory.Line l : lines) {
                if (!first) log = log.append(Component.newline());
                first = false;
                log = log.append(Component.text(TIME.format(Instant.ofEpochMilli(l.at())) + " ", NamedTextColor.DARK_GRAY))
                        .append(Component.object(ObjectContents.playerHead(l.sender()))).append(Component.text(" "))
                        .append(Component.text("[" + l.channel().tag() + "] ", TextColor.color(l.channel().rgb())))
                        .append(Component.text(l.senderName(), NamedTextColor.WHITE))
                        .append(Component.text(" » ", NamedTextColor.GRAY))
                        .append(Component.text(l.text(), NamedTextColor.WHITE));
            }
        }
        body.add(DialogBody.plainMessage(log, 360));
        ChatChannel sticky = channels.channel(p.getUniqueId());
        ChatChannel speakIn = tab == ChatChannel.ALL || !channels.canUse(p, tab) ? (channels.canUse(p, sticky) ? sticky : ChatChannel.GLOBAL) : tab;
        body.add(DialogBody.plainMessage(Component.text("Бичих суваг: ", NamedTextColor.GRAY)
                .append(Component.text(speakIn.label(), TextColor.color(speakIn.rgb()), TextDecoration.BOLD))
                .append(Component.text("  ·  !текст = Нийт", NamedTextColor.DARK_GRAY)), 360));

        List<ActionButton> buttons = new ArrayList<>();
        for (ChatChannel c : ChatChannel.values()) {
            boolean on = c == tab;
            Component label = on ? Component.text("▶ " + c.label(), TextColor.color(c.rgb()), TextDecoration.BOLD)
                    : Component.text(c.label(), NamedTextColor.GRAY);
            Component tip = Component.text(c == ChatChannel.ALL ? "Таны сонсож чадах бүх суваг" : tipOf(c));
            buttons.add(ActionButton.create(label, tip, 60, DialogAction.customClick((view, who) -> onMain(() -> {
                if (who instanceof Player pl) open(pl, c); // a tab only shows; «Илгээх» decides where a line goes
            }), ONCE)));
        }
        buttons.add(ActionButton.create(Component.text("Илгээх ✉", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text(speakIn.label() + " суваг руу илгээнэ"), 120,
                DialogAction.customClick((view, who) -> {
                    String text = view.getText("msg");
                    onMain(() -> {
                        if (!(who instanceof Player pl)) return;
                        if (text != null && !text.isBlank()) {
                            channels.setChannel(pl, speakIn); // where one writes from the screen becomes the channel
                            channels.say(pl, speakIn, text.strip());
                        }
                        if (pl.isOnline()) open(pl, tab);
                    });
                }, ONCE)));
        buttons.add(ActionButton.create(Component.text("Шинэчлэх ⟳", NamedTextColor.AQUA), Component.text("Шинэ бичлэгүүдийг харах"), 120,
                DialogAction.customClick((view, who) -> onMain(() -> {
                    if (who instanceof Player pl) open(pl, tab);
                }), ONCE)));

        Dialog d = Dialog.create(f -> f.empty()
                .base(DialogBase.builder(Component.text("СҮЛД · Чат", NamedTextColor.GOLD, TextDecoration.BOLD))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(body)
                        .inputs(List.of(DialogInput.text("msg", Component.text("Мессеж", NamedTextColor.GRAY))
                                .width(360).maxLength(256).labelVisible(false).build()))
                        .build())
                .type(DialogType.multiAction(buttons)
                        .columns(ChatChannel.values().length)
                        .exitAction(ActionButton.create(Component.text("Хаах"), null, 100, null))
                        .build()));
        p.showDialog(d);
    }

    private static String tipOf(ChatChannel c) {
        return switch (c) {
            case GLOBAL -> "Бүх тоглогч";
            case LOCAL -> ChatChannel.LOCAL_RADIUS + " блокийн доторх тоглогчид";
            case PARTY -> "Таны бүлгийн гишүүд";
            case CLAN -> "Таны овгийн гишүүд";
            case TRADE -> "Худалдааны зар — бүх тоглогч";
            default -> "";
        };
    }

    private void onMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run(); else Bukkit.getScheduler().runTask(plugin, r);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Messages.error("Зөвхөн тоглогч."));
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        String text = String.join(" ", args);
        switch (name) {
            case "chat" -> open(p, args.length > 0 ? ChatChannel.parse(args[0]).orElse(ChatChannel.ALL) : ChatChannel.ALL);
            case "ch" -> {
                if (args.length == 0) {
                    open(p, channels.channel(p.getUniqueId()));
                    return true;
                }
                ChatChannel c = ChatChannel.parse(args[0]).orElse(null);
                if (c == null) {
                    p.sendMessage(Messages.error("Суваг: нийт, ойр, бүлэг, овог, худалдаа (g, l, p, c, t)"));
                    return true;
                }
                if (!channels.setChannel(p, c)) {
                    p.sendMessage(Messages.error(c == ChatChannel.PARTY ? "Та бүлэгт байхгүй." : "Та овоггүй."));
                    return true;
                }
                p.sendMessage(Messages.success("Чатын суваг: " + c.label() + (args.length > 1 ? "" : " — !текст гэвэл Нийт рүү.")));
                if (args.length > 1) channels.say(p, c, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            }
            case "g" -> sayOrHelp(p, ChatChannel.GLOBAL, text);
            case "l" -> sayOrHelp(p, ChatChannel.LOCAL, text);
            case "pc" -> sayOrHelp(p, ChatChannel.PARTY, text);
            case "tr" -> sayOrHelp(p, ChatChannel.TRADE, text);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void sayOrHelp(Player p, ChatChannel c, String text) {
        if (text.isBlank()) {
            if (channels.setChannel(p, c)) p.sendMessage(Messages.success("Чатын суваг: " + c.label())); // "/l" alone switches
            else p.sendMessage(Messages.error(c == ChatChannel.PARTY ? "Та бүлэгт байхгүй." : "Та овоггүй."));
        } else {
            channels.say(p, c, text);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        String n = command.getName().toLowerCase(Locale.ROOT);
        if ((n.equals("ch") || n.equals("chat")) && args.length == 1) {
            List<String> out = new ArrayList<>();
            for (ChatChannel c : ChatChannel.values()) {
                if (n.equals("ch") && !c.speakable()) continue;
                String s = c.label().toLowerCase(Locale.ROOT);
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
            return out;
        }
        return List.of();
    }
}
