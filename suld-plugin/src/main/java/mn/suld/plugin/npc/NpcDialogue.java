package mn.suld.plugin.npc;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import mn.suld.api.npc.NpcAction;
import mn.suld.api.npc.NpcDefinition;
import mn.suld.api.npc.NpcLine;
import mn.suld.api.npc.NpcTopic;
import mn.suld.api.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Conversations with Kharkhorum's people (docs/NPC_DIALOGUE.md, content/npcs.json): a native dialog with the speaker's
 * words, one button for what they do (shop, quest, travel, smith, class) and «💬» topics for what they know. Every
 * paragraph shows its history label: a «түүхэн баримт» line is VERIFIED content, SÜLD's own story is told as the
 * speaker's belief (FICTION) or as "in the spirit of" (INSPIRED). Shift + right-click skips the talk.
 */
public final class NpcDialogue {

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build();

    private final Plugin plugin;

    public NpcDialogue(Plugin plugin) {
        this.plugin = plugin;
    }

    /** True when there is a conversation for this NPC (a greeting at least; otherwise the action runs at once). */
    public boolean has(NpcDefinition who) {
        return who != null && who.talks();
    }

    /** A button another service adds to a conversation (a side quest to take or hand in): its label and its click. */
    public record Extra(Component label, java.util.function.Consumer<Player> click) {
    }

    /** Shows the greeting with the NPC's action button and the topics. */
    public void open(Player p, NpcDefinition who, String npcId, PlayerProfile pr, String actionLabel, Runnable action) {
        open(p, who, npcId, pr, actionLabel, action, List.of());
    }

    /** As above, with {@code extras} between the action button and the topics. */
    public void open(Player p, NpcDefinition who, String npcId, PlayerProfile pr, String actionLabel, Runnable action, List<Extra> extras) {
        if (who == null || !who.talks()) {
            action.run();
            return;
        }
        String greet = who.greetings().get(ThreadLocalRandom.current().nextInt(who.greetings().size()));
        if (pr != null && pr.playerClass().isPresent() && who.action() == NpcAction.CLASS) {
            greet = "Чи " + pr.playerClass().get().displayName() + "-ийн замыг сонгосон. Тэнгэр чамайг харж байна.";
        }
        Material icon = Material.matchMaterial(who.icon());
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.item(new ItemStack(icon == null ? Material.PAPER : icon)).description(DialogBody.plainMessage(
                Component.text("«" + greet + "»", NamedTextColor.WHITE), 260)).showTooltip(false).build());
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(ActionButton.create(Component.text(actionLabel, NamedTextColor.GREEN, TextDecoration.BOLD), null, 150,
                DialogAction.customClick((view, a) -> onMain(() -> {
                    if (a instanceof Player pl) {
                        pl.closeDialog();
                        action.run();
                    }
                }), ONCE)));
        for (Extra x : extras) buttons.add(button(x));
        for (NpcTopic t : who.topics()) {
            buttons.add(ActionButton.create(Component.text("💬 " + t.title(), NamedTextColor.GOLD), null, 150,
                    DialogAction.customClick((view, a) -> onMain(() -> {
                        if (a instanceof Player pl) topic(pl, who, npcId, pr, t, actionLabel, action, extras);
                    }), ONCE)));
        }
        show(p, who, body, buttons);
    }

    /** A page of the NPC's own: the paragraphs and the buttons (a side quest's story, «Авах», «« Буцах»). */
    public void page(Player p, NpcDefinition who, List<Component> paragraphs, List<Extra> buttons) {
        List<DialogBody> body = new ArrayList<>();
        for (Component c : paragraphs) body.add(DialogBody.plainMessage(c, 300));
        List<ActionButton> out = new ArrayList<>();
        for (Extra x : buttons) out.add(button(x));
        show(p, who, body, out);
    }

    private ActionButton button(Extra x) {
        return ActionButton.create(x.label(), null, 150, DialogAction.customClick((view, a) -> onMain(() -> {
            if (a instanceof Player pl) x.click().accept(pl);
        }), ONCE));
    }

    private void topic(Player p, NpcDefinition who, String npcId, PlayerProfile pr, NpcTopic t, String actionLabel, Runnable action, List<Extra> extras) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(t.title(), NamedTextColor.GOLD, TextDecoration.BOLD), 300));
        for (NpcLine para : t.paragraphs()) {
            body.add(DialogBody.plainMessage(Component.text(para.text(), NamedTextColor.GRAY)
                    .append(Component.text("  — " + label(para.history()), NamedTextColor.DARK_GRAY)), 300));
        }
        List<ActionButton> buttons = List.of(
                ActionButton.create(Component.text(actionLabel, NamedTextColor.GREEN, TextDecoration.BOLD), null, 150,
                        DialogAction.customClick((view, a) -> onMain(() -> {
                            if (a instanceof Player pl) {
                                pl.closeDialog();
                                action.run();
                            }
                        }), ONCE)),
                ActionButton.create(Component.text("« Буцах"), null, 150,
                        DialogAction.customClick((view, a) -> onMain(() -> {
                            if (a instanceof Player pl) open(pl, who, npcId, pr, actionLabel, action, extras);
                        }), ONCE)));
        show(p, who, body, buttons);
    }

    /** The label a paragraph carries in the talk (docs/research/history/README.md): fact, in the spirit of, SÜLD's tale. */
    public static String label(String history) {
        return switch (history) {
            case "VERIFIED" -> "түүхэн баримт";
            case "INSPIRED" -> "түүхийг сэдэвлэсэн";
            default -> "SÜLD-ийн домог";
        };
    }

    private void show(Player p, NpcDefinition who, List<DialogBody> body, List<ActionButton> buttons) {
        Dialog d = Dialog.create(f -> f.empty()
                .base(DialogBase.builder(Component.text(who.name(), NamedTextColor.GOLD, TextDecoration.BOLD))
                        .canCloseWithEscape(true).pause(false).afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(body).build())
                .type(DialogType.multiAction(buttons).columns(2)
                        .exitAction(ActionButton.create(Component.text("Баяртай"), null, 100, null)).build()));
        p.showDialog(d);
    }

    private void onMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run(); else Bukkit.getScheduler().runTask(plugin, r);
    }
}
