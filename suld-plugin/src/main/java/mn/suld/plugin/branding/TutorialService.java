package mn.suld.plugin.branding;

import mn.suld.api.onboarding.TutorialProgress;
import mn.suld.api.onboarding.TutorialProgress.Step;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.ExpSource;
import mn.suld.api.region.Navigation;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.gui.Menus;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Presentation;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The guided first hour (docs/TUTORIAL.md): seven steps that teach the core loop (class, weapon, skill tree, lock-on,
 * the first hunt, the story, the first dungeon gate), shown in a boss bar with a compass arrow when there is
 * somewhere to go. It starts by itself on a player's first join only; {@code /tutorial restart} replays it and
 * {@code /tutorial skip} ends it. Each step pays its small reward once, ever (TutorialProgress keeps the paid
 * steps), so replays and relogs cannot farm it. Progress is kept in the player's data and survives restarts.
 * Checks run once a second for players in the tutorial only.
 */
public final class TutorialService implements Listener, TabExecutor {

    private final Plugin plugin;
    private final SuldServices services;
    private final Menus menus;
    private final NamespacedKey key;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Long> killBaseline = new HashMap<>();
    private final Set<UUID> openedSkills = new java.util.HashSet<>(), openedQuest = new java.util.HashSet<>();
    private Function<Player, Optional<? extends Entity>> lockOn = p -> Optional.empty();
    private Function<String, Optional<Location>> gate = id -> Optional.empty();

    public TutorialService(Plugin plugin, SuldServices services, Menus menus) {
        this.plugin = plugin;
        this.services = services;
        this.menus = menus;
        this.key = new NamespacedKey(plugin, "tutorial");
    }

    public void hooks(Function<Player, Optional<? extends Entity>> lockOn, Function<String, Optional<Location>> gate) {
        this.lockOn = lockOn;
        this.gate = gate;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    public TutorialProgress progress(Player p) {
        return TutorialProgress.decode(p.getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    private void save(Player p, TutorialProgress t) {
        p.getPersistentDataContainer().set(key, PersistentDataType.STRING, t.encode());
    }

    // ------------------------------------------------------------------ lifecycle

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        TutorialProgress t = progress(p);
        if (t.state() == TutorialProgress.State.NOT_STARTED) {
            // first join only: begin once the welcome has been seen
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline() || progress(p).state() != TutorialProgress.State.NOT_STARTED) return;
                begin(p, progress(p).start());
                Presentation.banner(p, "ИХ МОНГОЛД ТАВТАЙ МОРИЛ", "Заавар эхэллээ — дээрх самбарыг дага", NamedTextColor.GOLD);
            }, 120L);
        } else if (t.state() == TutorialProgress.State.IN_PROGRESS) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) begin(p, progress(p));
            }, 60L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        BossBar b = bars.remove(id);
        if (b != null) e.getPlayer().hideBossBar(b);
        killBaseline.remove(id);
        openedSkills.remove(id);
        openedQuest.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String c = e.getMessage().toLowerCase(Locale.ROOT);
        UUID id = e.getPlayer().getUniqueId();
        if (c.startsWith("/skills") || c.startsWith("/skill ") || c.equals("/skill") || c.startsWith("/skilltree")) openedSkills.add(id);
        if (c.startsWith("/quest") || c.equals("/q") || c.startsWith("/q ")) openedQuest.add(id);
    }

    /** The skill tree or quest screen was opened from a menu or an NPC (not through a command). */
    public void opened(Player p, String screen) {
        if ("skills".equals(screen)) openedSkills.add(p.getUniqueId());
        if ("quest".equals(screen)) openedQuest.add(p.getUniqueId());
    }

    private void begin(Player p, TutorialProgress t) {
        save(p, t);
        killBaseline.put(p.getUniqueId(), services.session(p.getUniqueId()).mobsDefeated);
        show(p, t);
    }

    private void end(Player p) {
        BossBar b = bars.remove(p.getUniqueId());
        if (b != null) p.hideBossBar(b);
    }

    // ------------------------------------------------------------------ steps

    private boolean done(Player p, Step s) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return false;
        return switch (s) {
            case CHOOSE_CLASS -> pr.playerClass().isPresent();
            case HOLD_WEAPON -> services.items().read(p.getInventory().getItemInMainHand())
                    .map(i -> i.definitionId().startsWith("weapon.class.")).orElse(false);
            case OPEN_SKILLS -> openedSkills.contains(p.getUniqueId());
            case LOCK_ON -> lockOn.apply(p).isPresent();
            case FIRST_KILL -> services.session(p.getUniqueId()).mobsDefeated > killBaseline.getOrDefault(p.getUniqueId(), Long.MAX_VALUE);
            case OPEN_QUEST -> openedQuest.contains(p.getUniqueId());
            case FIND_GATE -> gate.apply(SuldContent.KHASAR_DEN.id())
                    .map(g -> g.getWorld().equals(p.getWorld()) && g.distanceSquared(p.getLocation()) <= 24 * 24).orElse(false);
        };
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!bars.containsKey(p.getUniqueId())) continue; // only players in the tutorial
            TutorialProgress t = progress(p);
            Step s = t.current();
            if (s == null) {
                end(p);
                continue;
            }
            if (done(p, s)) {
                complete(p, t, s);
            } else {
                show(p, t);
            }
        }
    }

    private void complete(Player p, TutorialProgress t, Step s) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr != null && t.unpaid(t.step()) && (s.exp() > 0 || s.coins() > 0)) {
            services.progression().grantExp(pr, s.exp(), ExpSource.QUEST);
            pr.addCurrency(s.coins());
            services.profiles().save(pr);
            p.sendMessage(Messages.success("Заавар: «" + s.title() + "» ✔  +" + s.exp() + " EXP, +" + s.coins() + " ₮"));
        } else {
            p.sendMessage(Messages.success("Заавар: «" + s.title() + "» ✔"));
        }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
        TutorialProgress next = t.advance();
        if (next.state() == TutorialProgress.State.COMPLETED) {
            if (pr != null && t.finishUnpaid()) {
                services.progression().grantExp(pr, TutorialProgress.FINISH_EXP, ExpSource.QUEST);
                pr.addCurrency(TutorialProgress.FINISH_COINS);
                services.profiles().save(pr);
                p.sendMessage(Messages.success("Заавар дууслаа! +" + TutorialProgress.FINISH_EXP + " EXP, +" + TutorialProgress.FINISH_COINS + " ₮"));
            }
            save(p, next);
            Presentation.banner(p, "ЗААВАР ДУУСЛАА", "Одоо Хасарын Агуйд ор — /dungeon enter khasar_den", NamedTextColor.GREEN);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            end(p);
            return;
        }
        save(p, next);
        if (next.current() == Step.FIRST_KILL) killBaseline.put(p.getUniqueId(), services.session(p.getUniqueId()).mobsDefeated);
        show(p, next);
    }

    private void show(Player p, TutorialProgress t) {
        Step s = t.current();
        if (s == null) return;
        BossBar b = bars.computeIfAbsent(p.getUniqueId(), k -> {
            BossBar nb = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
            p.showBossBar(nb);
            return nb;
        });
        Component title = Component.text("Заавар " + (t.step() + 1) + "/" + TutorialProgress.STEPS.size() + " · ", NamedTextColor.GOLD)
                .append(Component.text(s.title(), NamedTextColor.WHITE, TextDecoration.BOLD));
        if (s == Step.FIND_GATE) {
            Location g = gate.apply(SuldContent.KHASAR_DEN.id()).orElse(null);
            if (g != null && g.getWorld().equals(p.getWorld())) {
                double dx = g.getX() - p.getLocation().getX(), dz = g.getZ() - p.getLocation().getZ();
                title = title.append(Component.text("  " + Navigation.arrow(Navigation.bearing(dx, dz), Navigation.facing(p.getLocation().getYaw()))
                        + " " + (int) Math.hypot(dx, dz) + "м", NamedTextColor.YELLOW));
            }
        }
        if (!title.equals(b.name())) b.name(title);
        b.progress((float) t.step() / TutorialProgress.STEPS.size());
        // the hint, every 10 s on the HUD notice line
        if ((Bukkit.getCurrentTick() / 20) % 10 == 0 && services.hud() != null) {
            services.hud().toast(p, Component.text(s.hint(), NamedTextColor.GRAY), 4000);
        }
    }

    // ------------------------------------------------------------------ /tutorial

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Messages.error("Зөвхөн тоглогч."));
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        TutorialProgress t = progress(p);
        switch (sub) {
            case "restart" -> {
                begin(p, t.start());
                p.sendMessage(Messages.info("Заавар эхнээс нь. Шагнал зөвхөн анх удаа олгогдоно."));
            }
            case "skip" -> {
                save(p, new TutorialProgress(TutorialProgress.State.COMPLETED, TutorialProgress.STEPS.size(), t.rewarded()));
                end(p);
                p.sendMessage(Messages.info("Заавар алгасагдлаа. Дахин: /tutorial restart"));
            }
            default -> {
                menus.tutorial(p);
                Step s = t.current();
                p.sendMessage(Messages.info(switch (t.state()) {
                    case NOT_STARTED -> "Заавар эхлээгүй. /tutorial restart";
                    case IN_PROGRESS -> "Заавар " + (t.step() + 1) + "/" + TutorialProgress.STEPS.size() + ": " + s.title() + " — " + s.hint();
                    case COMPLETED -> "Заавар дууссан ✔ · дахин: /tutorial restart · алгасах: /tutorial skip";
                }));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 ? List.of("restart", "skip").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of();
    }
}
