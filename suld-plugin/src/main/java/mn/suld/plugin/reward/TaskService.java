package mn.suld.plugin.reward;

import mn.suld.api.mob.MobDefinition;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.ExpSource;
import mn.suld.api.region.RegionDefinition;
import mn.suld.api.reward.DailyTasks;
import mn.suld.api.style.PlayerStyle;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.content.WorldContent;
import mn.suld.plugin.gui.Menu;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Presentation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /tasks}: three daily hunting tasks ({@link DailyTasks}) picked from the regions a player's level can handle.
 * Kills of the right SÜLD mob advance them; a finished task pays coins and EXP at once. Progress is stored on the
 * style row for the current day ({@code daily.timezone}).
 */
public final class TaskService implements Listener, TabExecutor {

    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");
    private static final TextColor GREEN = TextColor.fromHexString("#7CE07C");

    private final SuldServices services;
    private final ZoneId zone;
    private final List<DailyTasks.Prey> pool = new ArrayList<>();

    public TaskService(Plugin plugin, SuldServices services) {
        this.services = services;
        ZoneId z;
        try {
            z = ZoneId.of(plugin.getConfig().getString("daily.timezone", "Asia/Ulaanbaatar"));
        } catch (DateTimeException ex) {
            z = ZoneId.of("Asia/Ulaanbaatar");
        }
        this.zone = z;
        for (RegionDefinition r : WorldContent.REGIONS) {
            if (r.safeZone()) continue;
            for (String id : r.mobIds()) {
                MobDefinition m = SuldContent.mobFor(id);
                if (m != null) pool.add(new DailyTasks.Prey(m.id(), m.displayName(), r.displayName(), m.level(), m.scaledExp(), r.minLevel()));
            }
        }
    }

    private long today() {
        return LocalDate.now(zone).toEpochDay();
    }

    private List<DailyTasks.Task> tasks(Player p, PlayerProfile pr) {
        return DailyTasks.tasks(p.getUniqueId(), today(), pr.progression().level(), pool);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();
        Player killer = mob.getKiller();
        if (killer == null || !services.mobs().isSuldMob(mob)) return;
        String mobId = services.mobs().mobId(mob).orElse(null);
        PlayerProfile pr = services.profiles().cached(killer.getUniqueId()).orElse(null);
        PlayerStyle st = services.styles().cached(killer.getUniqueId()).orElse(null);
        if (mobId == null || pr == null || st == null || !pr.hasSelectedClass()) return;
        long day = today();
        List<DailyTasks.Task> tasks = tasks(killer, pr);
        int[] progress = DailyTasks.progress(st.taskProgress(day));
        int done = DailyTasks.kill(tasks, progress, mobId);
        if (done == -2) return;
        st.taskProgress(day, DailyTasks.format(progress));
        if (done >= 0) {
            DailyTasks.Task t = tasks.get(done);
            int from = pr.progression().level();
            pr.addCurrency(t.coins());
            var gained = services.progression().grantExp(pr, t.exp(), ExpSource.OTHER);
            killer.sendMessage(Messages.success("Өдрийн даалгавар биелэв: " + t.prey().mobName() + " ×" + t.count()
                    + "  (+" + t.coins() + " ₮, +" + t.exp() + " EXP)"));
            killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
            if (gained.leveledUp()) Presentation.levelUp(killer, from, gained.after().level());
            services.hud().update(killer, pr);
        } else {
            for (int i = 0; i < tasks.size(); i++) {
                if (tasks.get(i).prey().mobId().equals(mobId) && progress[i] < tasks.get(i).count()) {
                    killer.sendActionBar(Component.text("Даалгавар · " + tasks.get(i).prey().mobName() + " " + progress[i] + "/" + tasks.get(i).count(),
                            GOLD, TextDecoration.BOLD));
                    break;
                }
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Messages.error("Зөвхөн тоглогч ашиглана."));
            return true;
        }
        open(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        return List.of();
    }

    public void open(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        PlayerStyle st = services.styles().cached(p.getUniqueId()).orElse(null);
        if (pr == null || st == null || !pr.hasSelectedClass()) {
            p.sendMessage(Messages.error("Эхлээд ангиа сонго: /class"));
            return;
        }
        List<DailyTasks.Task> tasks = tasks(p, pr);
        int[] progress = DailyTasks.progress(st.taskProgress(today()));
        int finished = 0;
        for (int i = 0; i < tasks.size(); i++) if (progress[i] >= tasks.get(i).count()) finished++;
        Menu m = new Menu(3, "Өдрийн даалгавар · " + finished + "/" + tasks.size(), null);
        int[] slots = {11, 13, 15};
        for (int i = 0; i < tasks.size(); i++) {
            DailyTasks.Task t = tasks.get(i);
            boolean done = progress[i] >= t.count();
            TextColor c = done ? GREEN : GOLD;
            int filled = (int) Math.round(10.0 * Math.min(progress[i], t.count()) / t.count());
            ItemStack it = Menu.item(done ? Material.LIME_DYE : Material.IRON_SWORD,
                    Component.text(t.prey().mobName() + " ×" + t.count(), c, TextDecoration.BOLD),
                    Menu.lore(c, List.of(
                                    Component.text(t.prey().regionName() + " нутагт ан хий.", NamedTextColor.WHITE, TextDecoration.BOLD),
                                    Component.text("▰".repeat(filled), c).append(Component.text("▱".repeat(10 - filled), NamedTextColor.DARK_GRAY))
                                            .append(Component.text("  " + Math.min(progress[i], t.count()) + "/" + t.count(), NamedTextColor.WHITE, TextDecoration.BOLD))),
                            List.of(Menu.kv("Шагнал:", t.coins() + " ₮ · " + t.exp() + " EXP", GREEN)), done ? "Биелсэн ✔" : "Идэвхтэй"));
            if (done) Menu.glow(it);
            m.set(slots[i], it, null);
        }
        m.set(22, Menu.item(Material.CLOCK, Menu.title("Шинэ даалгавар маргааш", NamedTextColor.WHITE),
                List.of(Component.text("Түвшин ахих тусам өндөр нутгийн даалгавар.", NamedTextColor.WHITE, TextDecoration.BOLD))), null);
        m.open(p);
    }
}
