package mn.suld.plugin.command;

import mn.suld.api.campaign.CampaignDefinition;
import mn.suld.api.campaign.CampaignGoal;
import mn.suld.api.campaign.CampaignRun;
import mn.suld.api.item.ItemInstance;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.Content;
import mn.suld.plugin.gui.Menus;
import mn.suld.plugin.ui.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /khuraldai} (GUI) · {@code vote <1-3|id>} · {@code status} · {@code deliver [amount]} ·
 * admin {@code open|close|force <campaign>|resolve|cancel|list}. The weekly vote and the week's campaign (docs/KHURALDAI.md).
 */
public final class KhuraldaiCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("vote", "status", "deliver");
    private static final List<String> ADMIN = List.of("open", "close", "force", "resolve", "cancel", "list");

    private final SuldServices services;
    private final Menus menus;

    public KhuraldaiCommand(SuldServices services, Menus menus) {
        this.services = services;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (ADMIN.contains(sub)) {
            if (!sender.hasPermission("suld.admin.campaign")) {
                sender.sendMessage(Messages.error("Эрх алга."));
                return true;
            }
            admin(sender, sub, args);
            return true;
        }
        if (!(sender instanceof Player p)) {
            status(sender);
            return true;
        }
        switch (sub) {
            case "" -> menus.khuraldai(p);
            case "vote", "санал" -> {
                if (args.length < 2) menus.khuraldai(p);
                else services.khuraldai().vote(p, args[1]);
            }
            case "status", "төлөв" -> status(p);
            case "deliver", "хүргэх" -> deliver(p, args.length > 1 ? args[1] : null);
            default -> p.sendMessage(Messages.info("/khuraldai [vote <1-3> | status | deliver [тоо]]"));
        }
        return true;
    }

    private void status(CommandSender s) {
        var k = services.khuraldai();
        Optional<CampaignRun> run = services.campaigns().active();
        s.sendMessage(Messages.accent("Их Хуралдай · " + services.weeklyCycle().cycleId(Instant.now())));
        if (run.isPresent()) {
            CampaignRun r = run.get();
            s.sendMessage(Messages.info("Аян: «" + r.definition().name() + "» " + r.total() + "/" + r.definition().goal().target() + " "
                    + r.definition().goal().unitWord() + ", " + r.ranking().size() + " оролцогч, " + (r.remainingSeconds(Instant.now()) / 3600) + " цаг үлдсэн"));
            if (s instanceof Player p) s.sendMessage(Messages.info("Таны хувь: " + r.unitsOf(p.getUniqueId()) + " · шат " + r.tierOf(p.getUniqueId())));
        } else {
            s.sendMessage(Messages.info("Энэ долоо хоногт аян явагдахгүй байна (эхлэх: " + k.settings().campaignStarts() + ")."));
        }
        if (k.open()) {
            s.sendMessage(Messages.info("Санал хураалт нээлттэй (" + k.settings().voteCloses() + " хүртэл):"));
            List<CampaignDefinition> ballot = k.ballot();
            for (int i = 0; i < ballot.size(); i++) {
                s.sendMessage(Messages.info("  " + (i + 1) + ". " + ballot.get(i).name() + " — " + k.votesFor(ballot.get(i).id()) + " санал"));
            }
        } else if (k.current() != null && k.current().closed()) {
            CampaignDefinition w = Content.pack().campaign(k.current().winner() == null ? "" : k.current().winner());
            s.sendMessage(Messages.info("Сүүлийн шийдвэр: «" + (w == null ? k.current().winner() : w.name()) + "» (" + k.current().voters() + " санал)."));
        } else {
            s.sendMessage(Messages.info("Дараагийн санал хураалт: " + k.settings().voteOpens() + "."));
        }
    }

    /** Hand the campaign the items it asks for, from the inventory: all matching stacks, or {@code amount}. */
    private void deliver(Player p, String amountText) {
        Optional<CampaignRun> run = services.campaigns().active();
        if (run.isEmpty() || run.get().definition().goal().type() != CampaignGoal.Type.DELIVER_ITEMS) {
            p.sendMessage(Messages.error("Энэ долоо хоногийн аян хүргэлт шаардахгүй."));
            return;
        }
        if (services.isSoul.test(p.getUniqueId())) {
            p.sendMessage(Messages.error("Сүнс хүргэлт хийхгүй."));
            return;
        }
        long want = Long.MAX_VALUE;
        if (amountText != null) {
            try {
                want = Math.max(1, Long.parseLong(amountText));
            } catch (NumberFormatException ex) {
                p.sendMessage(Messages.error("/khuraldai deliver [тоо]"));
                return;
            }
        }
        var items = services.items();
        ItemStack[] contents = p.getInventory().getStorageContents();
        long moved = 0;
        String movedId = null;
        for (int i = 0; i < contents.length && want > 0; i++) {
            ItemStack st = contents[i];
            if (st == null || st.isEmpty()) continue;
            Optional<ItemInstance> inst = items.read(st);
            if (inst.isEmpty() || inst.get().soulbound() || !run.get().definition().goal().targets().contains(inst.get().definitionId())) continue;
            int take = (int) Math.min(st.getAmount(), want);
            long counted = services.campaigns().deliver(p, inst.get().definitionId(), take);
            if (counted <= 0) break;
            st.setAmount(st.getAmount() - (int) counted);
            contents[i] = st.getAmount() <= 0 ? null : st;
            moved += counted;
            want -= counted;
            movedId = inst.get().definitionId();
        }
        if (moved == 0) {
            String names = String.join(", ", run.get().definition().goal().targets().stream()
                    .map(id -> { var d = mn.suld.plugin.content.SuldContent.definitionFor(id); return d == null ? id : d.displayName(); }).toList());
            p.sendMessage(Messages.error("Хүргэх зүйл алга. Аян хүсч байна: " + names + "."));
            return;
        }
        p.getInventory().setStorageContents(contents);
        mn.suld.plugin.item.PlayerDataSaves.now(p); // the items left the player before the units are flushed
        var d = mn.suld.plugin.content.SuldContent.definitionFor(movedId);
        p.sendMessage(Messages.success(moved + " " + (d == null ? movedId : d.displayName()) + " хүргэв. Таны хувь: " + run.get().unitsOf(p.getUniqueId())));
    }

    private void admin(CommandSender sender, String sub, String[] args) {
        String actor = sender.getName();
        switch (sub) {
            case "open" -> {
                services.khuraldai().adminOpen(actor);
                sender.sendMessage(Messages.success("Санал хураалтыг нээж байна."));
            }
            case "close" -> sender.sendMessage(services.khuraldai().adminClose(actor) ? Messages.success("Санал хураалтыг хааж байна.")
                    : Messages.error("Нээлттэй санал хураалт алга."));
            case "force" -> {
                if (args.length < 2) {
                    sender.sendMessage(Messages.error("/khuraldai force <campaign id>"));
                    return;
                }
                String id = args[1].startsWith("campaign.") ? args[1] : "campaign." + args[1];
                CampaignDefinition def = Content.pack().campaign(id);
                if (def == null) {
                    sender.sendMessage(Messages.error("Ийм аян алга: " + id + " (/khuraldai list)"));
                    return;
                }
                sender.sendMessage(services.campaigns().adminForce(def, actor) ? Messages.success(def.name() + " эхэлж байна.")
                        : Messages.error("Аян аль хэдийн явагдаж байна."));
            }
            case "resolve" -> sender.sendMessage(services.campaigns().adminResolve(actor) ? Messages.success("Аяныг дүгнэж байна.")
                    : Messages.error("Идэвхтэй аян алга."));
            case "cancel" -> sender.sendMessage(services.campaigns().adminCancel(actor) ? Messages.info("Аяныг зогсоож байна.")
                    : Messages.error("Идэвхтэй аян алга."));
            case "list" -> {
                for (CampaignDefinition d : Content.pack().campaigns()) {
                    sender.sendMessage(Messages.info(d.id() + " — " + d.name() + " (" + d.goal().type() + " " + d.goal().target() + ")"));
                }
            }
            default -> { }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(SUBS);
            if (sender.hasPermission("suld.admin.campaign")) out.addAll(ADMIN);
            return out.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("vote")) {
            List<String> out = new ArrayList<>();
            for (int i = 1; i <= services.khuraldai().ballot().size(); i++) out.add(String.valueOf(i));
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("force")) {
            return Content.pack().campaigns().stream().map(c -> c.id().substring("campaign.".length()))
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
