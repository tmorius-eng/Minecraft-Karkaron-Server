package mn.suld.plugin.training;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.skill.Spell;
import mn.suld.api.skill.tree.Effect;
import mn.suld.api.skill.tree.EffectText;
import mn.suld.api.skill.tree.SkillNode;
import mn.suld.api.skill.tree.Ultimate;
import mn.suld.api.training.TrainingBuilds;
import mn.suld.api.training.TrainingStats;
import mn.suld.api.training.TrainingText;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.combat.CombatFeedback;
import mn.suld.plugin.combat.HitSource;
import mn.suld.plugin.mob.MobService;
import mn.suld.plugin.skill.SkillService;
import mn.suld.plugin.skill.SkillTreeService;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Pages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the training grounds tell a trainee (docs/TRAINING_GROUNDS.md), all of it theirs only: the numbers over the
 * Warden in the colour of their source (CombatFeedback), a line for every spell after it landed (what it does, its
 * factor, cost and wait, how many it hit for how much), a line the first time each passive fires (its rule, and how
 * often it has really fired here), the ultimate, and on the right the 10-second DPS, total, biggest hit, crit rate, the
 * share of each source and every passive's observed rate against its design. Main thread.
 */
final class TrainingCoach {

    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A"), CYAN = TextColor.fromHexString("#5FE3FF"),
            PINK = TextColor.fromHexString("#FF6BE8"), ORANGE = TextColor.fromHexString("#FF9A3C"), GREY = TextColor.fromHexString("#A4B0C8");

    private final Plugin plugin;
    private final SuldServices services;
    private final SkillService skills;
    private final SkillTreeService tree;
    private final TrainingService service;
    private BukkitTask ticker;
    private long ticks;

    /** A cast being watched: the hits its source names until it goes quiet. */
    private static final class Watch {
        final String name;
        final long at;
        long last;
        final Set<UUID> targets = new HashSet<>();
        double damage;

        Watch(String name, long at) {
            this.name = name;
            this.at = at;
        }
    }

    private final Map<UUID, Map<Spell, Watch>> casts = new HashMap<>();
    private final Map<UUID, Watch> ults = new HashMap<>();
    private final Map<UUID, Set<String>> explainedSpells = new HashMap<>(), explainedPassives = new HashMap<>();
    private final Map<UUID, Long> hints = new HashMap<>(), toasts = new HashMap<>(), grunts = new HashMap<>();
    /** Warden → the trainee who hit it last (burning has no attacker). */
    private final Map<UUID, UUID> lastHitter = new HashMap<>();

    TrainingCoach(Plugin plugin, SuldServices services, SkillService skills, SkillTreeService tree, TrainingService service) {
        this.plugin = plugin;
        this.services = services;
        this.skills = skills;
        this.tree = tree;
        this.service = service;
    }

    void start() {
        CombatFeedback.observer = this::onHit;
        skills.castListener = this::onCast;
        tree.ultimateListener = this::onUltimate;
        tree.procListener = this::onProc;
        if (services.hud() != null) services.hud().sidebar = this::sidebar;
        if (ticker != null) ticker.cancel();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    void stop() {
        if (ticker != null) ticker.cancel();
        ticker = null;
        CombatFeedback.observer = null;
        skills.castListener = (p, s) -> { };
        tree.ultimateListener = (p, u) -> { };
        tree.procListener = (p, n, proc, fired) -> { };
        if (services.hud() != null) services.hud().sidebar = p -> null;
    }

    // ------------------------------------------------------------------------------------------------ a session

    /** Training starts (or the class / build changes): the first words, a clean slate. */
    void begin(Player p, TrainingService.Session s) {
        UUID id = p.getUniqueId();
        casts.remove(id);
        ults.remove(id);
        explainedSpells.remove(id);
        explainedPassives.remove(id);
        TrainingBuilds.Build b = TrainingBuilds.of(tree.tree(s.clazz), s.variant);
        TextColor cc = TrainingYard.COLOUR.get(s.clazz);
        p.sendMessage(Component.text("— Дасгал: ", GOLD, TextDecoration.BOLD).append(Component.text(s.clazz.displayName(), cc, TextDecoration.BOLD))
                .append(Component.text(" · 60-р түвшин · Build " + b.letter() + " —", GOLD, TextDecoration.BOLD)));
        if (b.keystone() != null) {
            p.sendMessage(Component.text("◆ Keystone: ", GOLD).append(Component.text(line(b.keystone(), EffectText.Kind.KEYSTONE), NamedTextColor.WHITE)));
        }
        if (b.ultimate() != null) {
            p.sendMessage(Component.text("★ Их чадвар (F): ", ORANGE).append(Component.text(line(b.ultimate(), EffectText.Kind.ULTIMATE), NamedTextColor.WHITE)));
        }
        StringBuilder spells = new StringBuilder();
        for (Spell sp : Spell.of(s.clazz)) spells.append(spells.length() == 0 ? "" : " · ").append(sp.keys()).append(' ').append(sp.displayName());
        p.sendMessage(Component.text("✦ Ид шид: ", CYAN).append(Component.text(spells.toString(), NamedTextColor.WHITE)));
        StringBuilder ps = new StringBuilder();
        for (SkillNode n : TrainingBuilds.passives(b.allocation())) ps.append(ps.length() == 0 ? "" : ", ").append(n.name());
        p.sendMessage(Component.text("⚡ Идэвхгүй: ", PINK).append(Component.text(ps.toString(), NamedTextColor.WHITE)));
        p.sendMessage(Component.text("Warden-д дайр: тоо бүр эх үүсвэрийнхээ өнгөтэй, баруун талд DPS, хувь, давтамж. "
                + "Цэс: 8-р нүдний ном · Гарах: /dasgal leave эсвэл хаалгаар гар.", GREY));
    }

    void end(Player p) {
        UUID id = p.getUniqueId();
        casts.remove(id);
        ults.remove(id);
        explainedSpells.remove(id);
        explainedPassives.remove(id);
        toasts.remove(id);
        lastHitter.values().removeIf(id::equals);
    }

    void reset(Player p) {
        TrainingService.Session s = service.session(p.getUniqueId());
        if (s == null) {
            p.sendMessage(Messages.info("Та дасгал хийгээгүй байна. Эхлэх: /dasgal"));
            return;
        }
        s.stats.reset();
        p.sendMessage(Messages.success("Тоо тэглэгдлээ: шинээр тоолж эхэллээ."));
        if (services.hud() != null) services.hud().update(p, s.stand);
    }

    /** Someone not training hit a Warden: how to start (at most every five seconds). */
    void hint(Player p) {
        long now = System.currentTimeMillis();
        Long last = hints.get(p.getUniqueId());
        if (last != null && now - last < 5000) return;
        hints.put(p.getUniqueId(), now);
        p.sendMessage(Messages.info("Дасгал эхлүүлэх: ангийн тавцан дээр дар, эсвэл /dasgal."));
    }

    private static String line(SkillNode n, EffectText.Kind kind) {
        for (EffectText.Line l : EffectText.lines(n)) if (l.kind() == kind) return l.text();
        return n.name();
    }

    // ------------------------------------------------------------------------------------------------ what happened

    /** Every hit a player lands on a SÜLD mob (CombatFeedback): a trainee's hit on a Warden is counted. */
    private void onHit(Player attacker, LivingEntity victim, double damage, boolean crit, EntityDamageByEntityEvent e) {
        if (!TrainingYard.dummy(victim)) return;
        TrainingService.Session s = service.session(attacker.getUniqueId());
        if (s == null) return;
        long now = System.currentTimeMillis();
        TrainingStats.Source src = HitSource.current();
        if (src == null) {
            if (e.getDamager() instanceof AbstractArrow arrow) {
                src = arrow.getScoreboardTags().contains("suld_volley") ? HitSource.spell(Spell.OLON_SUM) : TrainingStats.Source.of(TrainingStats.Kind.ARROW);
            } else if (e.getDamager() instanceof Player) {
                src = TrainingStats.Source.of(TrainingStats.Kind.BASIC);
            } else {
                src = TrainingStats.Source.of(TrainingStats.Kind.OTHER);
            }
        }
        s.stats.hit(now, src, damage, crit);
        lastHitter.put(victim.getUniqueId(), attacker.getUniqueId());
        UUID id = attacker.getUniqueId();
        if (src.kind() == TrainingStats.Kind.SPELL) {
            Map<Spell, Watch> m = casts.get(id);
            if (m != null) for (Watch w : m.values()) if (w.name.equals(src.name())) add(w, victim, damage, now);
        } else if (src.kind() == TrainingStats.Kind.ULTIMATE) {
            Watch w = ults.get(id);
            if (w != null) add(w, victim, damage, now);
        }
        // the Warden answers a big hit with a grunt, at most once a second
        Long g = grunts.get(id);
        if ((crit || damage > 300) && (g == null || now - g > 1000)) {
            grunts.put(id, now);
            attacker.playSound(victim.getLocation(), Sound.ENTITY_WARDEN_HURT, 0.5f, 0.8f + (float) Math.random() * 0.3f);
        }
    }

    private static void add(Watch w, LivingEntity victim, double damage, long now) {
        w.targets.add(victim.getUniqueId());
        w.damage += damage;
        w.last = now;
    }

    /** A Warden burning (no attacker): the trainee who hit it last is credited, and sees the tick. */
    void unattributed(LivingEntity dummy, EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FIRE_TICK && e.getCause() != EntityDamageEvent.DamageCause.FIRE) return;
        UUID who = lastHitter.get(dummy.getUniqueId());
        Player p = who == null ? null : Bukkit.getPlayer(who);
        TrainingService.Session s = p == null ? null : service.session(who);
        if (s == null) return;
        double dmg = MobService.trueDamage(e);
        s.stats.hit(System.currentTimeMillis(), TrainingStats.Source.of(TrainingStats.Kind.BURN), dmg, false);
        CombatFeedback cf = CombatFeedback.instance();
        if (cf != null) cf.burn(p, dummy, dmg);
    }

    private void onCast(Player p, Spell s) {
        if (service.session(p.getUniqueId()) == null) return;
        long now = System.currentTimeMillis();
        casts.computeIfAbsent(p.getUniqueId(), k -> new EnumMap<>(Spell.class)).put(s, new Watch(s.displayName(), now));
    }

    private void onUltimate(Player p, Ultimate u) {
        TrainingService.Session s = service.session(p.getUniqueId());
        if (s == null) return;
        p.sendMessage(Component.text("★ ", ORANGE).append(Component.text(TrainingText.ultimate(u, tree.ultimateCooldownSeconds(p) > 0
                ? tree.ultimateCooldownSeconds(p) : Ultimate.COOLDOWN_SECONDS), ORANGE)));
        ults.put(p.getUniqueId(), new Watch(u.displayName(), System.currentTimeMillis()));
    }

    private void onProc(Player p, String name, Effect.Proc proc, boolean fired) {
        TrainingService.Session s = service.session(p.getUniqueId());
        if (s == null || name == null) return;
        s.stats.passiveRolled(name, proc.chance(), fired);
        if (!fired) return;
        if (explainedPassives.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(name)) {
            TrainingStats.PassiveRate rate = null;
            for (TrainingStats.PassiveRate r : s.stats.passives()) if (r.name().equals(name)) rate = r;
            p.sendMessage(Component.text("⚡ ", PINK).append(Component.text(TrainingText.passive(name, proc, rate), PINK)));
            return;
        }
        long now = System.currentTimeMillis();
        Long t = toasts.get(p.getUniqueId());
        if (t != null && now - t < 700) return;
        toasts.put(p.getUniqueId(), now);
        if (services.hud() != null) services.hud().toast(p, Component.text("⚡ " + name, PINK), 900);
    }

    // ------------------------------------------------------------------------------------------------ every 5 ticks

    private void tick() {
        ticks++;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, TrainingService.Session> e : service.sessions().entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            TrainingService.Session s = e.getValue();
            Map<Spell, Watch> m = casts.get(e.getKey());
            if (m != null) {
                List<Spell> done = new ArrayList<>();
                for (Map.Entry<Spell, Watch> c : m.entrySet()) {
                    Watch w = c.getValue();
                    boolean quiet = w.last > 0 ? now - w.last > 800 : now - w.at > 1600;
                    if (quiet || now - w.at > 6000) done.add(c.getKey());
                }
                for (Spell sp : done) report(p, sp, m.remove(sp));
            }
            Watch u = ults.get(e.getKey());
            if (u != null && (u.last > 0 ? now - u.last > 1500 : now - u.at > 6000) || u != null && now - u.at > 10_000) {
                ults.remove(e.getKey());
                if (u.damage > 0) {
                    p.sendMessage(Component.text("★ " + u.name + ": " + u.targets.size() + " бай, " + TrainingText.number(u.damage) + " хохирол", ORANGE));
                }
            }
            if (ticks % 4 == 0) {
                s.bar.name(Component.text("ДАЙЧДЫН ДАСГАЛЫН ТАЛБАЙ", GOLD, TextDecoration.BOLD)
                        .append(Component.text(" · ", NamedTextColor.GRAY))
                        .append(Component.text(s.clazz.displayName().toUpperCase(java.util.Locale.ROOT) + " 60", TrainingYard.COLOUR.get(s.clazz), TextDecoration.BOLD))
                        .append(Component.text(" · Build " + (s.variant == 0 ? "A" : "B") + " · DPS " + TrainingText.number(s.stats.dps(now))
                                + " · гарах: /dasgal leave", NamedTextColor.GRAY)));
                if (services.hud() != null) services.hud().update(p, s.stand);
            }
        }
    }

    /** The line after a cast: in full the first time a spell is cast in a session, short after that. */
    private void report(Player p, Spell s, Watch w) {
        boolean first = explainedSpells.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(s.name());
        if (!first) {
            if (s.damageMultiplier() > 0) {
                p.sendMessage(Component.text("✦ " + s.displayName() + " — " + (w.targets.isEmpty() ? "бай оносонгүй"
                        : w.targets.size() + " бай, " + TrainingText.number(w.damage) + " хохирол"), CYAN));
            }
            return;
        }
        int level = TrainingBuilds.LEVEL;
        double power = s.damageMultiplier() > 0 ? skills.powerOf(p, s) : 0;
        double cd = SkillService.cooldownSeconds(tree.build(p), s, level);
        String text = TrainingText.spell(s, s.rankAt(level), power, skills.costFor(p, s), TrainingService.resource(s.clazz()), cd,
                w.targets.size(), w.damage);
        p.sendMessage(Component.text("✦ ", GOLD).append(Component.text(text, CYAN)));
    }

    // ------------------------------------------------------------------------------------------------ the right side

    /** The sidebar of a trainee (15 lines at most), or null for everyone else. */
    List<Component> sidebar(Player p) {
        TrainingService.Session s = service.session(p.getUniqueId());
        if (s == null) return null;
        long now = System.currentTimeMillis();
        TrainingStats st = s.stats;
        List<Component> out = new ArrayList<>();
        out.add(Component.text("ДАСГАЛ · ", GOLD, TextDecoration.BOLD).append(Component.text(s.clazz.displayName().toUpperCase(java.util.Locale.ROOT)
                + " 60", TrainingYard.COLOUR.get(s.clazz), TextDecoration.BOLD)).append(Component.text(" · " + (s.variant == 0 ? "A" : "B"), GOLD, TextDecoration.BOLD)));
        out.add(Component.text("T6 зэвсэг · хуяг T6 ✦" + mn.suld.api.classgear.ArmorRules.MAX_ENHANCE + " · Зэрэг III", GREY));
        out.add(Component.empty());
        out.add(row("DPS (10 сек)", TrainingText.number(st.dps(now)), NamedTextColor.YELLOW));
        out.add(row("Нийт", TrainingText.number(st.total()), NamedTextColor.YELLOW));
        out.add(row("Хамгийн их", (st.maxSource() == null ? "—" : TrainingText.number(st.maxHit())), GOLD));
        out.add(row("Цохилт", st.hits() + " · crit " + Math.round(st.critRate()) + "%", NamedTextColor.YELLOW));
        out.add(Component.text("Эх үүсвэр", CYAN, TextDecoration.BOLD));
        List<TrainingStats.Share> shares = st.shares();
        for (int i = 0; i < Math.min(3, shares.size()); i++) out.add(Component.text("  " + TrainingText.share(shares.get(i)), colour(shares.get(i).source())));
        if (shares.isEmpty()) out.add(Component.text("  Warden-д дайр", GREY));
        out.add(Component.text("Идэвхгүй", PINK, TextDecoration.BOLD));
        List<TrainingStats.PassiveRate> rates = new ArrayList<>(st.passives());
        rates.removeIf(r -> r.fired() == 0);
        rates.sort((a, b) -> Integer.compare(b.fired(), a.fired()));
        for (int i = 0; i < Math.min(3, rates.size()); i++) out.add(Component.text("  " + TrainingText.rate(rates.get(i)), PINK));
        if (rates.isEmpty()) out.add(Component.text("  одоохондоо ороогүй", GREY));
        return out.size() > 15 ? out.subList(0, 15) : out;
    }

    private static Component row(String key, String value, TextColor colour) {
        return Component.text(key + ": ", NamedTextColor.WHITE).append(Component.text(value, colour));
    }

    private static TextColor colour(TrainingStats.Source s) {
        return switch (s.kind()) {
            case SPELL -> CYAN;
            case PASSIVE -> PINK;
            case ULTIMATE -> ORANGE;
            case BURN -> TextColor.fromHexString("#FFB347");
            default -> NamedTextColor.WHITE;
        };
    }

    /** {@code /dasgal stats}: every source and every passive, with the numbers in full. */
    void statsPage(Player p) {
        TrainingService.Session s = service.session(p.getUniqueId());
        if (s == null) {
            p.sendMessage(Messages.info("Та дасгал хийгээгүй байна. Эхлэх: /dasgal"));
            return;
        }
        long now = System.currentTimeMillis();
        TrainingStats st = s.stats;
        List<Component> lines = new ArrayList<>();
        lines.add(Pages.line(s.clazz.displayName() + " · Build " + (s.variant == 0 ? "A" : "B") + " · DPS (10 сек) " + TrainingText.number(st.dps(now))
                + " · нийт " + TrainingText.number(st.total()) + " · цохилт " + st.hits() + " · crit " + Math.round(st.critRate()) + "%"));
        if (st.maxSource() != null) lines.add(Pages.dim("Хамгийн их цохилт: " + TrainingText.number(st.maxHit()) + " (" + st.maxSource().name() + ")"));
        lines.add(Pages.line("Эх үүсвэр:"));
        for (TrainingStats.Share sh : st.shares()) {
            lines.add(Pages.dim("  " + sh.source().name() + " — " + TrainingText.number(sh.damage()) + " (" + Math.round(sh.percent()) + "%, " + sh.hits() + " цохилт)"));
        }
        lines.add(Pages.line("Идэвхгүй чадвар (энд орсон / боломж · загварын магадлал):"));
        for (TrainingStats.PassiveRate r : st.passives()) {
            lines.add(Pages.dim("  " + r.name() + " — " + r.fired() + "/" + r.rolls() + " (" + Math.round(r.observed()) + "%) · " + EffectText.num(r.designChance()) + "%"));
        }
        lines.add(Pages.dim("Боломж = өдөөгч болсон ба чадвар хүлээлтгүй байсан удаа. Тоо нь 60-р түвшний мангас дээрх бодит хохирол."));
        Pages.text(p, "Дасгалын тоо", lines, List.of(Pages.action("Тоо тэглэх", null, this::reset)), service::menu);
    }
}
