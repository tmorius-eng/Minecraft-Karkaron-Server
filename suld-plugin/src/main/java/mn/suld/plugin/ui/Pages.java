package mn.suld.plugin.ui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Text pages in the «Командын Өргөө» look (docs/UI_HUB.md): a native dialog with the SÜLD header, HD body lines
 * ({@code suld:hd}) and themed buttons (the pack retextures the widget sprites). Replaces the chat walls of
 * {@code /help <page>}, {@code /rules}, {@code /commands} and the link commands. Everything runs on the main thread.
 */
public final class Pages {

    private static final TextColor GOLD = TextColor.fromHexString("#ECBE4C");
    private static final TextColor CYAN = TextColor.fromHexString("#56E2EC");
    private static final TextColor GREY = TextColor.fromHexString("#A4B0C8");
    private static final int WIDTH = 320;
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();

    private static Plugin plugin;

    private Pages() {
    }

    /** Once, at enable: the plugin that owns the main-thread hop. */
    public static void install(Plugin p) {
        plugin = p;
    }

    /** One dialog button: label, tooltip (nullable) and what it does; a null action only closes. */
    public record Button(Component label, Component tip, Consumer<Player> action) {
    }

    /** A button that runs {@code command} (without the slash or with) after closing the page. */
    public static Button command(String command, String what) {
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        return new Button(StyleFormat.hd(Component.text("/" + cmd, CYAN)), what == null ? null : StyleFormat.hd(Component.text(what, GREY)),
                pl -> pl.performCommand(cmd));
    }

    public static Button action(String label, String tip, Consumer<Player> action) {
        return new Button(StyleFormat.hd(Component.text(label, GOLD)), tip == null ? null : StyleFormat.hd(Component.text(tip, GREY)), action);
    }

    /** A body line in the page's grey HD text. */
    public static Component line(String text) {
        return StyleFormat.hd(Component.text(text, NamedTextColor.WHITE));
    }

    public static Component dim(String text) {
        return StyleFormat.hd(Component.text(text, GREY));
    }

    /** The header every page carries: «SÜLD ◆ TITLE». */
    public static Component header(String title) {
        return StyleFormat.hd(Component.text("SÜLD", GOLD, TextDecoration.BOLD)
                .append(Component.text("  ◆  ", CYAN).decoration(TextDecoration.BOLD, false))
                .append(Component.text(title, GOLD, TextDecoration.BOLD)));
    }

    /**
     * A text page: {@code lines} one under the other, {@code buttons} in up to three columns, «« Буцах» when
     * {@code back} is given, «✕ Хаах» always.
     */
    public static void text(Player p, String title, List<Component> lines, List<Button> buttons, Consumer<Player> back) {
        List<DialogBody> body = new ArrayList<>();
        for (Component l : lines) body.add(DialogBody.plainMessage(l, WIDTH));
        List<ActionButton> actions = new ArrayList<>();
        for (Button b : buttons) actions.add(button(b));
        if (back != null) actions.add(button(new Button(StyleFormat.hd(Component.text("« Буцах", GOLD)), null, back)));
        ActionButton close = ActionButton.create(StyleFormat.hd(Component.text("✕ Хаах", TextColor.fromHexString("#FF6B6B"))), null, 120, null);
        DialogBase base = DialogBase.builder(header(title))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .build();
        Dialog d = Dialog.create(f -> f.empty().base(base).type(actions.isEmpty() ? DialogType.notice(close)
                : DialogType.multiAction(actions).columns(Math.min(3, Math.max(1, actions.size()))).exitAction(close).build()));
        p.showDialog(d);
    }

    /** A link page: the address shown, one «Нээх» button that opens it in the browser. */
    public static void link(Player p, String title, String url) {
        List<DialogBody> body = List.of(DialogBody.plainMessage(StyleFormat.hd(Component.text(url, CYAN, TextDecoration.UNDERLINED)), WIDTH));
        ActionButton open = ActionButton.create(StyleFormat.hd(Component.text("Нээх ↗", GOLD)), StyleFormat.hd(Component.text(url, GREY)), 150,
                DialogAction.staticAction(ClickEvent.openUrl(url)));
        ActionButton close = ActionButton.create(StyleFormat.hd(Component.text("✕ Хаах", TextColor.fromHexString("#FF6B6B"))), null, 120, null);
        DialogBase base = DialogBase.builder(header(title)).canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE).body(body).build();
        p.showDialog(Dialog.create(f -> f.empty().base(base).type(DialogType.multiAction(List.of(open)).columns(1).exitAction(close).build())));
    }

    private static ActionButton button(Button b) {
        if (b.action() == null) return ActionButton.create(b.label(), b.tip(), 150, null);
        Consumer<Player> action = b.action();
        return ActionButton.create(b.label(), b.tip(), 150, DialogAction.customClick((view, who) -> onMain(() -> {
            if (who instanceof Player pl && pl.isOnline()) action.accept(pl);
        }), ONCE));
    }

    private static void onMain(Runnable r) {
        if (plugin == null || Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }
}
