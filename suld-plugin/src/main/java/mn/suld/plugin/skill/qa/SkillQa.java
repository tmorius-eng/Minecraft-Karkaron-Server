package mn.suld.plugin.skill.qa;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.event.ExpGainedEvent;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.Progression;
import mn.suld.api.skill.Spell;
import mn.suld.api.skill.tree.Effect;
import mn.suld.api.skill.tree.KeystoneKind;
import mn.suld.api.skill.tree.SkillNode;
import mn.suld.api.skill.tree.SkillState;
import mn.suld.api.skill.tree.StatKey;
import mn.suld.api.skill.tree.TriggerEvent;
import mn.suld.api.skill.tree.Ultimate;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.combat.CombatListener;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.event.SuldDomainBukkitEvent;
import mn.suld.plugin.skill.SkillService;
import mn.suld.plugin.skill.SkillTreeService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The combat verification suite (/skillsadmin qa). For every effect of every node it builds a controlled situation on
 * a sky arena, measures the real game value before and after the node is learned (damage dealt to a dummy, attribute,
 * health, potion, absorption, distance reached, how often a chance fires...) and compares it with what the data file
 * promises. A check passes only when the measured change matches; executing a code path is never counted as proof.
 *
 * <p>Statistical checks (crit chance, dodge, proc chance, loot, durability) use enough trials that the tolerance is
 * four standard deviations: they cannot pass when the effect is absent.
 */
public final class SkillQa {

    public record Result(String id, String category, String what, String metric, double before, double after,
                         String expected, String verdict, String note) {
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final SkillTreeService st;
    private final SkillService skills;
    private volatile boolean busy;

    public SkillQa(Plugin plugin, SuldServices services, SkillTreeService st, SkillService skills) {
        this.plugin = plugin;
        this.services = services;
        this.st = st;
        this.skills = skills;
    }

    public boolean busy() {
        return busy;
    }

    public static final List<String> SUITES = List.of("stats", "mods", "procs", "wiring", "keystones", "ultimates", "cooldowns");

    public void run(CommandSender out, Player target, Set<String> suites) {
        if (busy) {
            out.sendMessage("QA already running");
            return;
        }
        PlayerProfile pr = services.profiles().cached(target.getUniqueId()).orElse(null);
        if (pr == null || pr.playerClass().isEmpty()) {
            out.sendMessage("QA needs a player with a class");
            return;
        }
        busy = true;
        new Run(out, target, pr, suites).go();
    }

    // ==================================================================================================== a run

    private final class Run implements Listener {
        final CommandSender out;
        final Player p;
        final PlayerProfile pr;
        final Set<String> suites;
        final QaKit k;
        final List<Result> results = new ArrayList<>();
        final List<Long> expSeen = new ArrayList<>();
        // saved state
        final PlayerClass origClass;
        final Progression origProgression;
        final long origCurrency;
        final mn.suld.api.quest.QuestState origQuest;
        final SkillState origState;
        final Location origLocation;
        final GameMode origMode;
        final double origHealth;
        final ItemStack origHand;

        static final int N_DUMMIES = 34; // 33 on layouts + 1 control parked away

        Run(CommandSender out, Player p, PlayerProfile pr, Set<String> suites) {
            this.out = out;
            this.p = p;
            this.pr = pr;
            this.suites = suites;
            this.only = suites.stream().filter(x -> x.startsWith("only=")).map(x -> x.substring(5)).findFirst().orElse(null);
            this.k = new QaKit(plugin, services, st, skills, p, pr, N_DUMMIES);
            this.origClass = pr.playerClass().orElseThrow();
            this.origProgression = pr.progression();
            this.origCurrency = pr.currency(); // real kills pay coins and count for the story chapter: put both back
            this.origQuest = pr.questState();
            this.origState = pr.skillState();
            this.origLocation = p.getLocation();
            this.origMode = p.getGameMode();
            this.origHealth = p.getHealth();
            this.origHand = p.getInventory().getItemInMainHand().clone();
        }

        /** Optional "only=baatar.m2" argument: run just the nodes whose "class.node" id contains this text. */
        final String only;

        boolean wanted(PlayerClass c, SkillNode n) {
            return only == null || (c.id() + "." + n.id()).contains(only);
        }

        boolean on(String suite) {
            return suites.contains(suite) || suites.contains("all");
        }

        @EventHandler
        public void onExp(SuldDomainBukkitEvent e) {
            if (e.payload() instanceof ExpGainedEvent ev && ev.player().equals(p.getUniqueId())) expSeen.add(ev.amount());
        }

        void go() {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            k.add(() -> {
                p.setGameMode(GameMode.SURVIVAL);
                k.buildArena();
                k.spawnDummies();
            });
            k.wait(5);
            k.add(() -> {
                k.configureDummies();
                pr.progression(new Progression(60, 0));
                for (int i = 0; i < N_DUMMIES; i++) k.park(i);
            });
            k.wait(2);
            if (on("stats")) stats();
            if (on("mods")) mods();
            if (on("procs")) procs();
            if (on("wiring")) wiring();
            if (on("keystones")) keystones();
            if (on("ultimates")) ultimates();
            if (on("cooldowns")) cooldowns();
            k.add(this::finish);
            k.start(ex -> {
                rec("harness.error", "harness", "a QA step threw", "exception", 0, 0, "no exception", "FAIL",
                        ex + java.util.Arrays.stream(ex.getStackTrace()).filter(f -> f.getClassName().contains("suld")).limit(3).map(f -> " @ " + f.getClassName().replaceAll(".*\\.", "") + "." + f.getMethodName() + ":" + f.getLineNumber()).collect(java.util.stream.Collectors.joining()));
            }, () -> { }, this::abort);
        }

        // -------------------------------------------------------------------------------------- recording

        void rec(String id, String cat, String what, String metric, double before, double after, String expected, String verdict, String note) {
            results.add(new Result(id, cat, what, metric, round(before), round(after), expected, verdict, note));
        }

        void check(String id, String cat, String what, String metric, double before, double after, String expected, boolean pass) {
            rec(id, cat, what, metric, before, after, expected, pass ? "PASS" : "FAIL", "");
        }

        void check(String id, String cat, String what, String metric, double before, double after, String expected, boolean pass, String note) {
            rec(id, cat, what, metric, before, after, expected, pass ? "PASS" : "FAIL", note);
        }

        void notMeasurable(String id, String cat, String what, String reason) {
            rec(id, cat, what, "-", 0, 0, "-", "NOT_MEASURABLE", reason);
        }

        double round(double v) {
            return Math.round(v * 10000.0) / 10000.0;
        }

        boolean near(double got, double expect, double tol) {
            return Math.abs(got - expect) <= tol;
        }

        boolean nearRel(double got, double expect, double rel) {
            return Math.abs(got - expect) <= Math.abs(expect) * rel + 1e-6;
        }

        String fmt(double v) {
            return String.format(Locale.ROOT, "%.4g", v);
        }

        double attr(Attribute a) {
            var inst = p.getAttribute(a);
            return inst == null ? 0 : inst.getValue();
        }

        double effectAmp(LivingEntity e, PotionEffectType t) {
            PotionEffect pe = e.getPotionEffect(t);
            return pe == null ? -1 : pe.getAmplifier();
        }

        double effectTicks(LivingEntity e, PotionEffectType t) {
            PotionEffect pe = e.getPotionEffect(t);
            return pe == null ? 0 : pe.getDuration();
        }

        double atk() {
            return CombatListener.attackOf(services, p);
        }

        /** Measures {@code m} without the node, then with it learned at its top rank; leaves the tree empty. */
        double[] beforeAfter(PlayerClass c, SkillNode n, Supplier<Double> m) {
            k.setClass(c);
            k.unlearn();
            double b = m.get();
            k.learn(n);
            double a = m.get();
            k.unlearn();
            return new double[]{b, a};
        }

        String idOf(PlayerClass c, SkillNode n, String what) {
            return c.id() + "." + n.id() + "." + what;
        }

        // -------------------------------------------------------------------------------------- finish

        /** The run was cut short (the player left): restore the profile and clean the arena without touching the player. */
        void abort() {
            plugin.getLogger().warning("[skill-qa] aborted: the player left");
            HandlerList.unregisterAll(this);
            k.clearArena();
            pr.forceClass(origClass);
            pr.progression(origProgression);
            pr.skillState(origState);
            pr.currency(origCurrency);
            pr.questState(origQuest);
            report();
            busy = false;
        }

        void finish() {
            HandlerList.unregisterAll(this);
            k.clearArena();
            pr.forceClass(origClass);
            pr.progression(origProgression);
            pr.skillState(origState);
            pr.currency(origCurrency);
            pr.questState(origQuest);
            st.attach(p);
            skills.rebuildPool(p);
            p.setGameMode(origMode);
            p.teleport(origLocation);
            p.setHealth(Math.min(origHealth, k.maxHealth()));
            for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
            p.setAbsorptionAmount(0);
            p.getInventory().setItemInMainHand(origHand);
            services.profiles().save(pr);
            report();
            busy = false;
        }

        void report() {
            int pass = 0, fail = 0, nm = 0;
            for (Result r : results) {
                switch (r.verdict()) {
                    case "PASS" -> pass++;
                    case "FAIL" -> fail++;
                    default -> nm++;
                }
            }
            StringBuilder json = new StringBuilder("{\n  \"generated\": \"" + Instant.now() + "\",\n  \"suites\": " + quote(String.join(",", suites))
                    + ",\n  \"pass\": " + pass + ", \"fail\": " + fail + ", \"notMeasurable\": " + nm + ",\n  \"results\": [\n");
            for (int i = 0; i < results.size(); i++) {
                Result r = results.get(i);
                json.append("    {\"id\": ").append(quote(r.id())).append(", \"category\": ").append(quote(r.category()))
                        .append(", \"what\": ").append(quote(r.what())).append(", \"metric\": ").append(quote(r.metric()))
                        .append(", \"before\": ").append(r.before()).append(", \"after\": ").append(r.after())
                        .append(", \"expected\": ").append(quote(r.expected())).append(", \"verdict\": ").append(quote(r.verdict()))
                        .append(", \"note\": ").append(quote(r.note())).append('}').append(i + 1 < results.size() ? ",\n" : "\n");
            }
            json.append("  ]\n}\n");
            try {
                Path dir = plugin.getDataFolder().toPath().resolve("qa");
                Files.createDirectories(dir);
                Path file = dir.resolve("skill-qa-" + Instant.now().toString().replace(':', '-') + ".json");
                Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
                plugin.getLogger().info("[skill-qa] report written to " + file);
            } catch (IOException e) {
                plugin.getLogger().warning("[skill-qa] cannot write the report: " + e.getMessage());
            }
            for (Result r : results) {
                if (r.verdict().equals("FAIL")) {
                    plugin.getLogger().warning("[skill-qa] FAIL " + r.id() + " — " + r.what() + ": " + r.metric() + " before=" + r.before()
                            + " after=" + r.after() + " expected " + r.expected() + (r.note().isEmpty() ? "" : " (" + r.note() + ")"));
                }
            }
            plugin.getLogger().info("[skill-qa] DONE pass=" + pass + " fail=" + fail + " notMeasurable=" + nm + " total=" + results.size());
            out.sendMessage("QA done: pass " + pass + ", fail " + fail + ", not measurable " + nm);
        }

        String quote(String s) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"";
        }

        // ======================================================================================== layouts

        void forward(double maxZ) {
            int n = N_DUMMIES - 1;
            for (int i = 0; i < n - 1; i++) k.place(i, 0, 0.5 + i * (maxZ - 0.5) / (n - 2));
            k.place(n - 1, 0, 0.4);
            k.place(n, -30, -30);
        }

        void twoSide(double center, double half) {
            int n = N_DUMMIES - 1;
            for (int i = 0; i < n; i++) k.place(i, 0, center - half + i * (2 * half) / (n - 1));
            k.place(n, -30, -30);
        }

        void lateral(double z, double maxX) {
            int n = N_DUMMIES - 1;
            for (int i = 0; i < n; i++) k.place(i, i * maxX / (n - 1), z);
            k.place(n, -30, -30);
        }

        /** One dummy at (0, z); everything else parked far away. */
        void single(double z) {
            k.place(0, 0, z);
            for (int i = 1; i < N_DUMMIES - 1; i++) k.park(i);
            k.place(N_DUMMIES - 1, -30, -30);
        }

        LivingEntity control() {
            return k.dummies.get(N_DUMMIES - 1);
        }

        // ======================================================================================== spell probes

        record Cfg(String layout, double a, double b, int ticks, boolean lateralReach, double anchor, boolean anchorFromPlayer) {
        }

        private java.util.Set<Spell> echoSpells;

        /** Spells some node gives an echo: their probes wait for the delayed repeat (12 ticks) so it is counted, not leaked into the next test. */
        Cfg cfgOf(Spell s) {
            if (echoSpells == null) {
                echoSpells = java.util.EnumSet.noneOf(Spell.class);
                for (PlayerClass c : PlayerClass.values()) {
                    for (SkillNode n : st.tree(c).nodes()) {
                        for (Effect e : n.effects()) if (e instanceof Effect.SpellMod m && m.key() == mn.suld.api.skill.tree.ModKey.ECHO_PCT) echoSpells.add(m.spell());
                    }
                }
            }
            Cfg base = baseCfg(s);
            return echoSpells.contains(s) ? new Cfg(base.layout(), base.a(), base.b(), base.ticks() + 14, base.lateralReach(), base.anchor(), base.anchorFromPlayer()) : base;
        }

        Cfg baseCfg(Spell s) {
            return switch (s) {
                case TENGER_TSAVCHILT -> new Cfg("forward", 8, 0, 6, false, 0, false);
                case DAINY_KHASHGIRAAN -> new Cfg("forward", 16, 0, 6, false, 0, false);
                case DOVTLOKH_USRELT -> new Cfg("forward", 16, 0, 60, false, 0, true);
                case CHONYN_NUD -> new Cfg("forward", 45, 0, 6, false, 0, false);
                case OLON_SUM -> new Cfg("volley", 3, 0, 40, false, 0, false);
                case UKHRAKH_USRELT -> new Cfg("forward", 10, 0, 6, false, 0, false);
                case TENGERIIN_SUM -> new Cfg("forward", 12, 0, 6, false, 0, false);
                case ONGONY_DUUDLAGA -> new Cfg("single", 8, 0, 60, false, 0, false);
                case KHENGERGIIN_DUU -> new Cfg("forward", 10, 0, 60, false, 0, false);
                case TENGERIIN_KHAALGA -> new Cfg("twoSide", 8, 10, 40, false, 8, false);
                case GALYN_DAVTALT -> new Cfg("forward", 7, 0, 6, false, 0, false);
                case KHAILSAN_TUMUR -> new Cfg("forward", 10, 0, 25, false, 0, false);
                case DARKHANY_DARANGUI -> new Cfg("twoSide", 8, 6, 30, false, 8, false);
                case KHURDAN_DOVTOLGOO -> new Cfg("lateral", 3, 4, 25, true, 0, false);
                case ZHADNY_SHIDELT -> new Cfg("forward", 12, 0, 6, false, 0, false);
                case KHULGIIN_DAIRALT -> new Cfg("lateral", 7, 5, 35, true, 0, false);
                default -> new Cfg("single", 6, 0, 6, false, 0, false);
            };
        }

        void applyLayout(Cfg c) {
            switch (c.layout()) {
                case "forward" -> forward(c.a());
                case "twoSide" -> twoSide(c.a(), c.b());
                case "lateral" -> lateral(c.a(), c.b());
                case "volley" -> single(c.a());
                default -> single(c.a());
            }
        }

        /** What one cast of a spell did, measured on the arena. */
        final class Probe {
            Spell spell;
            SkillService.CastResult cast;
            double hp0, hp, poolBefore, poolAfterCast, poolEnd, absorbAfterCast, speedAmpAfterCast = -1, speedTicksAfterCast;
            double total, perApplication, reach, maxFire, maxSlow, maxWeak, maxGlow;
            int hits, executions, riders;
            double markRatio = Double.NaN;
            int alive;
            String dbg = "";
        }

        /** Queues: reset, place dummies, cast, wait, measure; hands the probe to {@code done}. */
        void spellRun(Spell s, double hp0, boolean wantMark, Consumer<Probe> done) {
            Probe pb = new Probe();
            pb.spell = s;
            Cfg cfg = cfgOf(s);
            int[] ex0 = new int[2];
            k.add(() -> {
                k.resetPlayer();
                applyLayout(cfg);
                k.resetDummies();
                p.setHealth(Math.min(hp0, k.maxHealth()));
                pb.hp0 = p.getHealth();
                pb.poolBefore = skills.pool(p).value();
                ex0[0] = skills.executions.get();
                ex0[1] = skills.riderCalls.get();
                int alive = 0;
                for (LivingEntity dd : k.dummies) if (dd.isValid() && !dd.isDead()) alive++;
                pb.alive = alive;
                pb.dbg = "dummy0 " + k.dummies.get(0).getLocation().toVector() + " player " + p.getLocation().toVector() + " yaw " + p.getLocation().getYaw() + " pitch " + p.getLocation().getPitch();
                if (cfg.layout().equals("volley")) {
                    // arrows leave at eye height: look down at the dummy's chest so the volley really hits it
                    Location look = p.getLocation();
                    look.setPitch((float) Math.toDegrees(Math.atan2(1.2, cfg.a())));
                    p.teleport(look);
                    k.dummies.get(0).setCollidable(true); // a non-collidable entity cannot be hit by projectiles
                }
                pb.cast = skills.castDirect(p, s, true);
                pb.poolAfterCast = skills.pool(p).value();
                pb.absorbAfterCast = p.getAbsorptionAmount();
                if (cfg.layout().equals("volley")) {
                    // arrows vary (critical rolls): average over several volleys
                    for (int i = 0; i < 19; i++) {
                        skills.pool(p).gain(1e9);
                        skills.castDirect(p, s, true);
                    }
                }
                PotionEffect sp = p.getPotionEffect(PotionEffectType.SPEED);
                if (sp != null) {
                    pb.speedAmpAfterCast = sp.getAmplifier();
                    pb.speedTicksAfterCast = sp.getDuration();
                }
            });
            k.wait(cfg.ticks());
            k.add(() -> {
                pb.dbg += " end " + p.getLocation().toVector();
                if (cfg.layout().equals("volley")) k.dummies.get(0).setCollidable(false);
                pb.hp = p.getHealth();
                pb.poolEnd = skills.pool(p).value();
                pb.executions = skills.executions.get() - ex0[0];
                pb.riders = skills.riderCalls.get() - ex0[1];
                double anchorZ = cfg.anchorFromPlayer() ? p.getLocation().getZ() - k.origin.getZ() : cfg.anchor();
                LivingEntity marked = null;
                for (int i = 0; i < N_DUMMIES - 1; i++) {
                    LivingEntity d = k.dummies.get(i);
                    double def = k.deficit(d);
                    boolean touched = def > 1e-6 || effectTicks(d, PotionEffectType.SLOWNESS) > 0 || effectTicks(d, PotionEffectType.WEAKNESS) > 0;
                    if (touched) {
                        pb.hits++;
                        pb.total += def;
                        double dist = cfg.lateralReach() ? Math.abs(k.slot[i][0]) : Math.abs(k.slot[i][1] - anchorZ);
                        pb.reach = Math.max(pb.reach, dist);
                    }
                    pb.maxFire = Math.max(pb.maxFire, d.getFireTicks());
                    pb.maxSlow = Math.max(pb.maxSlow, effectTicks(d, PotionEffectType.SLOWNESS));
                    pb.maxWeak = Math.max(pb.maxWeak, effectTicks(d, PotionEffectType.WEAKNESS));
                    double glow = effectTicks(d, PotionEffectType.GLOWING);
                    pb.maxGlow = Math.max(pb.maxGlow, glow);
                    if (marked == null && (touched || glow > 0)) marked = d;
                    if (glow > 0 && !cfg.lateralReach()) {
                        double dist = Math.abs(k.slot[i][1] - anchorZ);
                        pb.reach = Math.max(pb.reach, dist);
                    }
                }
                if (pb.riders > 0 && pb.total > 0) pb.perApplication = pb.total / pb.riders;
                if (cfg.layout().equals("volley")) {
                    int arrowsHit = k.events.getOrDefault(k.dummies.get(0).getUniqueId(), 0);
                    pb.perApplication = arrowsHit > 0 ? k.deficit(k.dummies.get(0)) / arrowsHit : 0; // per arrow: riders only exist once a build has nodes
                }
                if (wantMark && marked != null) {
                    double mk = Double.MAX_VALUE, ct = Double.MAX_VALUE;
                    for (int i = 0; i < 6; i++) {
                        mk = Math.min(mk, k.hit(marked));
                        ct = Math.min(ct, k.hit(control()));
                    }
                    pb.markRatio = ct > 0 ? mk / ct : Double.NaN;
                }
                done.accept(pb);
            });
        }

        /** Spell probe before and after the node is learned; {@code eval} compares the two. */
        void spellCompare(PlayerClass c, SkillNode n, Spell s, double hp0, boolean wantMark, BiConsumer<Probe, Probe> eval) {
            Probe[] box = new Probe[1];
            k.add(() -> {
                k.setClass(c);
                k.unlearn();
            });
            spellRun(s, hp0, wantMark, b -> box[0] = b);
            k.add(() -> k.learn(n));
            spellRun(s, hp0, wantMark, a -> eval.accept(box[0], a));
            k.add(k::unlearn);
        }

        // ======================================================================================== STATS

        PlayerClass probeClass(Spell s) {
            return s.clazz();
        }

        Spell probeSpell(PlayerClass c) {
            return switch (c) {
                case BAATAR -> Spell.TENGER_TSAVCHILT;
                case MERGEN -> Spell.TENGERIIN_SUM;
                case BOO -> Spell.KHENGERGIIN_DUU;
                case DARKHAN -> Spell.GALYN_DAVTALT;
                case KHULEGCHIN -> Spell.ZHADNY_SHIDELT;
            };
        }

        void stats() {
            for (PlayerClass c : PlayerClass.values()) {
                for (SkillNode n : st.tree(c).nodes()) {
                    if (!wanted(c, n)) continue;
                    if (n.root()) continue;
                    boolean universal = n.tags().contains("universal");
                    if (universal && c != PlayerClass.BAATAR) continue;
                    for (Effect e : n.effects()) {
                        if (e instanceof Effect.Stat s) statNode(c, n, s);
                    }
                }
            }
        }

        void statNode(PlayerClass c, SkillNode n, Effect.Stat e) {
            int r = n.maxRank();
            double v = e.value() * r;
            String id = idOf(c, n, e.key().name());
            String what = e.key() + " " + (v >= 0 ? "+" : "") + EffectNum.n(v) + " (" + n.name() + " rank " + r + ")";
            switch (e.key()) {
                case HEALTH -> k.add(() -> {
                    double[] x = beforeAfter(c, n, () -> attr(Attribute.MAX_HEALTH));
                    check(id, "stat", what, "max health", x[0], x[1], "+" + EffectNum.n(v), near(x[1] - x[0], v, 0.01));
                });
                case MOVE_PCT -> k.add(() -> {
                    double[] x = beforeAfter(c, n, () -> attr(Attribute.MOVEMENT_SPEED));
                    check(id, "stat", what, "movement speed", x[0], x[1], "x" + EffectNum.n(1 + v / 100), near(x[1] / x[0] - 1, v / 100, 0.002));
                });
                case ARMOR -> k.add(() -> {
                    double[] x = beforeAfter(c, n, () -> attr(Attribute.ARMOR));
                    check(id, "stat", what, "armor", x[0], x[1], "+" + EffectNum.n(v), near(x[1] - x[0], v, 0.01));
                });
                case KB_RESIST -> k.add(() -> {
                    double[] x = beforeAfter(c, n, () -> attr(Attribute.KNOCKBACK_RESISTANCE));
                    check(id, "stat", what, "knockback resistance", x[0], x[1], "+" + EffectNum.n(Math.min(1, v / 100)), near(x[1] - x[0], Math.min(1, v / 100), 0.005));
                });
                case MINING_SPEED_PCT -> k.add(() -> {
                    var block = k.world.getBlockAt(k.origin.getBlockX() + 3, 199, k.origin.getBlockZ() + 3);
                    double[] x = beforeAfter(c, n, () -> (double) block.getBreakSpeed(p));
                    check(id, "stat", what, "break speed on stone (Block#getBreakSpeed)", x[0], x[1], "x" + EffectNum.n(1 + v / 100), nearRel(x[1] / x[0], 1 + v / 100, 0.01));
                });
                case ATTACK_PCT -> k.add(() -> {
                    single(2);
                    k.resetDummies();
                    double[] x = beforeAfter(c, n, () -> minHit(k.dummies.get(0), 25));
                    check(id, "stat", what, "damage of a normal hit", x[0], x[1], "x" + EffectNum.n(1 + v / 100), nearRel(x[1] / x[0], 1 + v / 100, 0.015));
                });
                case CRIT_CHANCE -> k.add(() -> {
                    single(2);
                    k.resetDummies();
                    int trials = 4000;
                    double[] x = beforeAfter(c, n, () -> critFrequency(k.dummies.get(0), trials));
                    double sigma = Math.sqrt(2 * 0.1 * 0.9 / trials);
                    check(id, "stat", what, "share of hits that crit (" + trials + " hits)", x[0], x[1], "+" + EffectNum.n(v / 100) + " +-" + fmt(4 * sigma),
                            near(x[1] - x[0], v / 100, Math.max(0.02, 4 * sigma)));
                });
                case CRIT_DAMAGE -> k.add(() -> {
                    single(2);
                    k.resetDummies();
                    double[] x = beforeAfter(c, n, () -> critMultiplier(k.dummies.get(0), 700));
                    check(id, "stat", what, "crit damage / normal damage", x[0], x[1], EffectNum.n(1.5 + v / 100), near(x[1] - x[0], v / 100, 0.02) && near(x[0], 1.5, 0.02));
                });
                case SPELL_DAMAGE -> {
                    Spell s = probeSpell(c);
                    // spell damage is built on the player's attack, so ATTACK_PCT on the same node scales it too
                    double atkSum = 0;
                    for (Effect other : n.effects()) if (other instanceof Effect.Stat o && o.key() == mn.suld.api.skill.tree.StatKey.ATTACK_PCT) atkSum += o.value() * r;
                    final double atkOnNode = atkSum;
                    double want = (1 + v / 100) * (1 + atkOnNode / 100);
                    spellCompare(c, n, s, 20, false, (b, a) -> {
                        boolean ok = b.perApplication > 0 && near(a.perApplication / b.perApplication, want, 0.02);
                        check(id, "stat", what, "damage per spell hit (" + s.displayName() + ")", b.perApplication, a.perApplication,
                                "x" + EffectNum.n(want) + (atkOnNode > 0 ? " (spell damage +" + EffectNum.n(v) + "% and attack +" + EffectNum.n(atkOnNode) + "% on this node)" : ""), ok,
                                ok ? "" : "hits before/after " + b.hits + "/" + a.hits);
                    });
                }
                case DAMAGE_REDUCTION -> k.add(() -> {
                    single(2);
                    LivingEntity src = k.dummies.get(0);
                    double[] x = beforeAfter(c, n, () -> {
                        k.resetPlayer();
                        return k.hurtPlayer(10, src);
                    });
                    double red = Math.min(75, v) / 100;
                    check(id, "stat", what, "health lost from a 10 damage hit", x[0], x[1], "x" + EffectNum.n(1 - red), near(x[1] / x[0], 1 - red, 0.01));
                });
                case DODGE_PCT -> k.add(() -> {
                    single(2);
                    LivingEntity src = k.dummies.get(0);
                    int trials = 3000;
                    double[] x = beforeAfter(c, n, () -> {
                        int dodged = 0;
                        for (int i = 0; i < trials; i++) {
                            k.resetPlayer();
                            if (k.hurtPlayer(1, src) < 1e-9) dodged++;
                        }
                        return dodged / (double) trials;
                    });
                    double pe = Math.min(40, v) / 100;
                    double sigma = Math.sqrt(pe * (1 - pe) / trials);
                    check(id, "stat", what, "share of hits dodged (" + trials + " hits)", x[0], x[1], EffectNum.n(pe) + " +-" + fmt(4 * sigma),
                            x[0] < 0.001 && near(x[1], pe, Math.max(0.01, 4 * sigma)));
                });
                case THORNS -> k.add(() -> {
                    single(2);
                    LivingEntity src = k.dummies.get(0);
                    double[] x = beforeAfter(c, n, () -> {
                        k.resetPlayer();
                        k.reset(src);
                        double taken = k.hurtPlayer(10, src);
                        return taken <= 0 ? 0 : k.deficit(src) / taken;
                    });
                    check(id, "stat", what, "damage returned / damage taken", x[0], x[1], EffectNum.n(v / 100), x[0] < 1e-6 && near(x[1], v / 100, 0.01));
                });
                case LIFESTEAL -> k.add(() -> {
                    single(2);
                    LivingEntity d = k.dummies.get(0);
                    double[] x = beforeAfter(c, n, () -> {
                        k.resetPlayer();
                        p.setHealth(1);
                        double dealt = k.hit(d);
                        return dealt <= 0 ? 0 : (p.getHealth() - 1) / dealt;
                    });
                    check(id, "stat", what, "health gained / damage dealt", x[0], x[1], EffectNum.n(v / 100), x[0] < 1e-6 && near(x[1], v / 100, 0.01));
                });
                case HEAL_POWER -> k.add(() -> {
                    double[] x = beforeAfter(c, n, () -> {
                        k.resetPlayer();
                        p.setHealth(1);
                        st.heal(p, 4);
                        return p.getHealth() - 1;
                    });
                    check(id, "stat", what, "health restored by a 4 point heal", x[0], x[1], "x" + EffectNum.n(1 + v / 100), near(x[1] / x[0], 1 + v / 100, 0.01));
                });
                case RESOURCE_MAX -> k.add(() -> {
                    double[] x = beforeAfter(c, n, () -> (double) skills.pool(p).max());
                    check(id, "stat", what, "resource capacity", x[0], x[1], "+" + EffectNum.n(v), near(x[1] - x[0], v, 0.01));
                });
                case RESOURCE_REGEN -> k.add(() -> {
                    double[] x = beforeAfter(c, n, () -> {
                        k.resetPlayer();
                        k.emptyPool();
                        for (int i = 0; i < 10; i++) skills.regenOne(p);
                        return skills.pool(p).fraction() * skills.pool(p).max() / 10.0;
                    });
                    check(id, "stat", what, "resource regained per second", x[0], x[1], "+" + EffectNum.n(v), near(x[1] - x[0], v, 0.05));
                });
                case COST_REDUCTION -> {
                    Spell s = probeSpell(c);
                    spellCompare(c, n, s, 20, false, (b, a) -> {
                        double base = b.poolBefore - b.poolAfterCast, now = a.poolBefore - a.poolAfterCast;
                        double exp = Math.max(1, Math.round(s.cost() * Math.max(0.2, 1 - v / 100)));
                        check(id, "stat", what, "resource spent by " + s.displayName(), base, now, EffectNum.n(exp), near(base, s.cost(), 0.01) && near(now, exp, 0.01));
                    });
                }
                case COOLDOWN_REDUCTION -> k.add(() -> {
                    Spell s = probeSpell(c);
                    double[] x = beforeAfter(c, n, () -> {
                        k.resetPlayer();
                        skills.castDirect(p, s, true);
                        return skills.cooldownLeft(p, s);
                    });
                    double cdr = Math.min(60, v) / 100;
                    check(id, "stat", what, "cooldown left right after casting " + s.displayName() + " (s)", x[0], x[1],
                            EffectNum.n(s.cooldownSeconds() * (1 - cdr)), near(x[0], s.cooldownSeconds(), 0.15) && near(x[1], s.cooldownSeconds() * (1 - cdr), 0.15));
                });
                case EXP_PCT -> expNode(c, n, id, what, v);
                case LOOT_PCT -> lootNode(c, n, id, what, v);
            }
            k.wait(1);
        }

        double minHit(LivingEntity d, int n) {
            double min = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                double dmg = k.hit(d);
                k.reset(d);
                if (dmg > 0) min = Math.min(min, dmg);
            }
            return min;
        }

        double critFrequency(LivingEntity d, int trials) {
            double[] dmg = new double[trials];
            double min = Double.MAX_VALUE;
            for (int i = 0; i < trials; i++) {
                dmg[i] = k.hit(d);
                k.reset(d);
                if (dmg[i] > 0) min = Math.min(min, dmg[i]);
            }
            int crits = 0;
            for (double x : dmg) if (x > min * 1.25) crits++;
            return crits / (double) trials;
        }

        double critMultiplier(LivingEntity d, int trials) {
            double min = Double.MAX_VALUE, max = 0;
            for (int i = 0; i < trials; i++) {
                double x = k.hit(d);
                k.reset(d);
                if (x > 0) {
                    min = Math.min(min, x);
                    max = Math.max(max, x);
                }
            }
            return max / min;
        }

        // ---- experience and loot (real kills)

        double expPerKill() {
            expSeen.clear();
            int kills = 6;
            for (int i = 0; i < kills; i++) {
                LivingEntity m = services.mobs().spawn(SuldContent.ORKHON_CHONO, k.origin.clone().add(20, 0, 20));
                m.setAI(false);
                m.setHealth(1);
                m.damage(5, p);
            }
            return expSeen.isEmpty() ? 0 : expSeen.stream().mapToLong(Long::longValue).sum() / (double) expSeen.size();
        }

        void expNode(PlayerClass c, SkillNode n, String id, String what, double v) {
            k.add(() -> {
                pr.progression(new Progression(30, 0)); // below the cap, so experience is really granted
                double[] x = beforeAfter(c, n, this::expPerKill);
                pr.progression(new Progression(60, 0));
                check(id, "stat", what, "EXP per kill", x[0], x[1], "x" + EffectNum.n(1 + v / 100), x[0] > 0 && near(x[1] / x[0], 1 + v / 100, 0.02));
            });
        }

        /** Queues {@code kills} real boss kills in batches (so no tick is long) and stores the item total in {@code out[idx]}. */
        void queueLoot(int kills, double[] out, int idx) {
            int batch = 20;
            k.add(() -> out[idx] = 0);
            for (int done = 0; done < kills; done += batch) {
                int count = Math.min(batch, kills - done);
                k.add(() -> {
                    for (int i = 0; i < count; i++) {
                        LivingEntity m = services.mobs().spawn(SuldContent.KHASAR, k.origin.clone().add(25, 0, 25));
                        m.setAI(false);
                        m.setHealth(1);
                        m.damage(5, p);
                    }
                    for (var e : k.world.getNearbyEntities(k.origin.clone().add(25, 0, 25), 8, 8, 8)) {
                        if (e instanceof Item it) {
                            out[idx] += it.getItemStack().getAmount();
                            it.remove();
                        }
                    }
                });
                k.wait(1);
            }
        }

        void lootNode(PlayerClass c, SkillNode n, String id, String what, double v) {
            int kills = 300; // the table always drops at least one item per roll
            double[] items = new double[2];
            k.add(() -> {
                k.setClass(c);
                k.unlearn();
            });
            queueLoot(kills, items, 0);
            k.add(() -> k.learn(n));
            queueLoot(kills, items, 1);
            k.add(() -> {
                k.unlearn();
                double extraRolls = items[1] - items[0];
                double pe = v / 100;
                // the table can drop more than one item per roll (bonus entries), so an extra roll is worth the table's own mean
                double perRoll = items[0] / kills;
                double expectedExtra = items[0] * pe;
                double sigma = Math.sqrt(kills * pe * (1 - pe)) * perRoll + 0.05 * items[0] * pe + Math.sqrt(items[0]) * pe;
                check(id, "stat", what, "items from " + kills + " kills", items[0], items[1],
                        "extra rolls: " + EffectNum.n(expectedExtra) + " +-" + fmt(4 * sigma) + " (base table gives " + EffectNum.n(perRoll) + " per kill)",
                        items[0] >= kills && near(extraRolls, expectedExtra, 4 * sigma));
            });
        }

        // ======================================================================================== MODS

        void mods() {
            for (PlayerClass c : PlayerClass.values()) {
                for (SkillNode n : st.tree(c).nodes()) {
                    if (!wanted(c, n)) continue;
                    if (n.root()) continue;
                    for (Effect e : n.effects()) {
                        if (e instanceof Effect.SpellMod m) modNode(c, n, m);
                    }
                }
            }
        }

        void modNode(PlayerClass c, SkillNode n, Effect.SpellMod m) {
            Spell s = m.spell();
            double v = m.value() * n.maxRank();
            String id = idOf(c, n, s.name() + "." + m.key());
            String what = s.displayName() + ": " + m.key() + " " + (v >= 0 ? "+" : "") + EffectNum.n(v) + " (" + n.name() + ")";
            Cfg cfg = cfgOf(s);
            switch (m.key()) {
                case DAMAGE_PCT -> {
                    if (s == Spell.SUNSNII_ZALBIRAL) {
                        spellCompare(c, n, s, 1, false, (b, a) -> {
                            double hb = b.hp - b.hp0, ha = a.hp - a.hp0;
                            check(id, "mod", what, "health restored by the heal", hb, ha, "x" + EffectNum.n(1 + v / 100), hb > 0 && near(ha / hb, 1 + v / 100, 0.02));
                        });
                    } else {
                        boolean arrows = s == Spell.OLON_SUM;
                        spellCompare(c, n, s, 20, false, (b, a) -> {
                            double tol = arrows ? 0.15 : 0.02;
                            boolean ok = b.perApplication > 0 && near(a.perApplication / b.perApplication, 1 + v / 100, tol);
                            check(id, "mod", what, "damage per hit", b.perApplication, a.perApplication, "x" + EffectNum.n(1 + v / 100) + (arrows ? " (arrows, +-15%)" : ""), ok,
                                    ok ? "" : "hits " + b.hits + "/" + a.hits + " cast " + b.cast + "/" + a.cast + " total " + EffectNum.n(b.total) + "/" + EffectNum.n(a.total) + " riders " + b.riders + "/" + a.riders + " alive " + b.alive + " " + b.dbg);
                        });
                    }
                }
                case COST_PCT -> spellCompare(c, n, s, 20, false, (b, a) -> {
                    double base = b.poolBefore - b.poolAfterCast, now = a.poolBefore - a.poolAfterCast;
                    double exp = Math.max(1, Math.round(s.cost() * Math.max(0.2, 1 + v / 100)));
                    check(id, "mod", what, "resource spent", base, now, EffectNum.n(exp), near(base, s.cost(), 0.01) && near(now, exp, 0.01));
                });
                case RADIUS_PCT -> {
                    if (s == Spell.SUNSNII_ZALBIRAL) {
                        notMeasurable(id, "mod", what, "the heal radius only matters for other players; a second player is needed (manual)");
                    } else {
                        spellCompare(c, n, s, 20, false, (b, a) -> {
                            double step = cfg.layout().equals("lateral") ? cfg.b() / 32 : cfg.layout().equals("twoSide") ? 2 * cfg.b() / 32 : (cfg.a() - 0.5) / 32;
                            // spells that move while they hit (the dash, the leap) sample the area coarsely: wider tolerance
                            double slack = (s == Spell.KHURDAN_DOVTOLGOO || s == Spell.DOVTLOKH_USRELT) ? 1.2 : 0.3;
                            double tol = 2.2 * step + slack;
                            double exp = b.reach * (1 + v / 100);
                            boolean ok = b.reach > 0 && near(a.reach, exp, tol) && a.reach > b.reach;
                            check(id, "mod", what, "farthest enemy hit (blocks from the origin of the effect)", b.reach, a.reach, EffectNum.n(exp) + " +-" + fmt(tol), ok,
                                    ok ? "" : "hits " + b.hits + "/" + a.hits + " cast " + b.cast + "/" + a.cast + " pool " + EffectNum.n(b.poolBefore) + "->" + EffectNum.n(b.poolAfterCast) + " alive " + b.alive + " " + b.dbg);
                        });
                    }
                }
                case BURN -> spellCompare(c, n, s, 20, false, (b, a) -> {
                    double lost = cfg.ticks() + 8;
                    boolean own = b.maxFire > 0; // the spell sets its own fire: the node's seconds come on top of it
                    check(id, "mod", what, "longest fire on an enemy (ticks)", b.maxFire, a.maxFire,
                            own ? "+" + EffectNum.n(v * 20) + " over the spell's own fire" : ">= " + EffectNum.n(v * 20 - lost) + " and before 0",
                            own ? near(a.maxFire - b.maxFire, v * 20, 4) : b.maxFire <= 0 && a.maxFire >= v * 20 - lost && a.maxFire <= v * 20 + 1);
                });
                case SLOW -> spellCompare(c, n, s, 20, false, (b, a) -> {
                    double lost = cfg.ticks() + 8;
                    check(id, "mod", what, "longest slowness on an enemy (ticks; the spell may slow a little by itself)", b.maxSlow, a.maxSlow, ">= " + EffectNum.n(v * 20 - lost) + " and longer than before",
                            a.maxSlow >= v * 20 - lost && a.maxSlow <= v * 20 + 1 && a.maxSlow > b.maxSlow);
                });
                case WEAKEN -> spellCompare(c, n, s, 20, false, (b, a) -> {
                    double lost = cfg.ticks() + 8;
                    check(id, "mod", what, "longest weakness on an enemy (ticks)", b.maxWeak, a.maxWeak, ">= " + EffectNum.n(v * 20 - lost) + " and before 0",
                            b.maxWeak <= 0 && a.maxWeak >= v * 20 - lost && a.maxWeak <= v * 20 + 1);
                });
                case VULN -> spellCompare(c, n, s, 20, true, (b, a) -> {
                    double base = Double.isNaN(b.markRatio) ? 1 : b.markRatio;
                    check(id, "mod", what, "damage to a touched enemy / damage to an untouched one", base, a.markRatio, "x" + EffectNum.n(1 + v / 100) + " (before ~1)",
                            near(base, 1, 0.02) && !Double.isNaN(a.markRatio) && near(a.markRatio, 1 + v / 100, 0.03));
                });
                case HEAL_ON_HIT -> spellCompare(c, n, s, 1, false, (b, a) -> {
                    double gainB = b.hp - b.hp0, gainA = a.hp - a.hp0;
                    double exp = v * a.riders;
                    check(id, "mod", what, "health gained over the cast (" + a.riders + " enemies touched)", gainB, gainA, EffectNum.n(exp) + " (" + EffectNum.n(v) + " per enemy)",
                            near(gainB, 0, 0.01) && a.riders > 0 && near(gainA, Math.min(exp, k.maxHealth() - 1), 0.1));
                });
                case REFUND -> spellCompare(c, n, s, 20, false, (b, a) -> {
                    double netB = b.poolEnd - b.poolAfterCast, netA = a.poolEnd - a.poolAfterCast;
                    double cost = a.poolBefore - a.poolAfterCast;
                    double exp = Math.min(v * a.riders, cost - netB); // the pool cannot rise above its maximum
                    check(id, "mod", what, "resource regained during the cast beyond natural regeneration (" + a.riders + " hits)", netB, netA, "+" + EffectNum.n(exp) + " over the baseline",
                            a.riders > 0 && near(netA - netB, exp, 0.6));
                });
                case SHIELD -> spellCompare(c, n, s, 20, false, (b, a) -> {
                    // the shield is added on top of whatever absorption the spell already grants
                    check(id, "mod", what, "absorption right after the cast", b.absorbAfterCast, a.absorbAfterCast, "+" + EffectNum.n(v), near(a.absorbAfterCast - b.absorbAfterCast, v, 0.01));
                });
                case HASTE -> spellCompare(c, n, s, 20, false, (b, a) -> {
                    double baseAmp = b.speedAmpAfterCast, baseTicks = b.speedTicksAfterCast;
                    boolean ok = a.speedAmpAfterCast >= Math.max(baseAmp, 1) && a.speedTicksAfterCast >= Math.max(baseTicks, v * 20) - 2;
                    check(id, "mod", what, "speed effect after the cast (amplifier, ticks)", baseAmp * 1000 + baseTicks, a.speedAmpAfterCast * 1000 + a.speedTicksAfterCast,
                            "amplifier >= max(base,1), ticks >= max(base," + EffectNum.n(v * 20) + ") -- never weaker than without the node", ok,
                            "base amp " + baseAmp + "/" + baseTicks + "t, with node amp " + a.speedAmpAfterCast + "/" + a.speedTicksAfterCast + "t");
                });
                case ECHO_PCT -> echoNode(c, n, s, id, what, v);
                case KNOCKUP, PULL -> riderVelocity(c, n, s, m, id, what, v);
            }
            k.wait(1);
        }

        void echoNode(PlayerClass c, SkillNode n, Spell s, String id, String what, double v) {
            int casts = 150;
            int[] box = new int[2];
            k.add(() -> {
                k.setClass(c);
                k.unlearn();
                single(2);
                k.resetDummies();
                box[0] = skills.executions.get();
                for (int i = 0; i < casts; i++) {
                    k.resetPlayer();
                    skills.castDirect(p, s, true);
                }
            });
            k.wait(30); // let delayed echoes land
            k.add(() -> {
                box[0] = skills.executions.get() - box[0];
                k.learn(n);
                single(2);
                k.resetDummies();
                box[1] = skills.executions.get();
                for (int i = 0; i < casts; i++) {
                    k.resetPlayer();
                    skills.castDirect(p, s, true);
                }
            });
            k.wait(30);
            k.add(() -> {
                int after = skills.executions.get() - box[1];
                double pe = v / 100;
                double sigma = Math.sqrt(casts * pe * (1 - pe));
                check(id, "mod", what, "spell executions for " + casts + " casts", box[0], after,
                        EffectNum.n(casts + casts * pe) + " +-" + fmt(4 * sigma), box[0] == casts && near(after - casts, casts * pe, 4 * sigma));
                k.unlearn();
            });
        }

        void riderVelocity(PlayerClass c, SkillNode n, Spell s, Effect.SpellMod m, String id, String what, double v) {
            k.add(() -> {
                k.setClass(c);
                single(4);
                double[] x = new double[2];
                for (int phase = 0; phase < 2; phase++) {
                    if (phase == 0) k.unlearn();
                    else k.learn(n);
                    k.resetPlayer();
                    LivingEntity d = k.dummies.get(0);
                    k.reset(d);
                    d.setVelocity(new Vector());
                    skills.riders(p, d, s);
                    Vector vel = d.getVelocity();
                    if (m.key() == mn.suld.api.skill.tree.ModKey.KNOCKUP) x[phase] = vel.getY();
                    else {
                        Vector toPlayer = p.getLocation().toVector().subtract(d.getLocation().toVector()).setY(0).normalize();
                        x[phase] = vel.clone().setY(0).dot(toPlayer);
                    }
                }
                double exp = m.key() == mn.suld.api.skill.tree.ModKey.KNOCKUP ? 0.25 + 0.1 * v : 0.5 * v;
                check(id, "mod", what + " [applied through the spell's rider function on a still dummy]",
                        m.key() == mn.suld.api.skill.tree.ModKey.KNOCKUP ? "upward velocity" : "velocity toward the caster", x[0], x[1], EffectNum.n(exp) + " (before 0)",
                        near(x[0], 0, 0.02) && near(x[1], exp, 0.06));
                k.unlearn();
            });
        }

        // ======================================================================================== PROCS

        void procs() {
            for (PlayerClass c : PlayerClass.values()) {
                for (SkillNode n : st.tree(c).nodes()) {
                    if (!wanted(c, n)) continue;
                    if (n.root()) continue;
                    for (Effect e : n.effects()) {
                        if (e instanceof Effect.Proc proc) procNode(c, n, proc);
                    }
                }
            }
        }

        /** One firing of the passive spell; returns the measured effect (or NaN when it did not run). */
        double procTrial(Effect.Proc e, LivingEntity target, LivingEntity neighbour, LivingEntity inside, LivingEntity outside) {
            k.resetPlayer();
            k.reset(target);
            k.reset(neighbour);
            k.reset(inside);
            k.reset(outside);
            switch (e.kind()) {
                case HEAL -> p.setHealth(1);
                case RESOURCE -> k.emptyPool();
                case CLEANSE -> p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 400, 0));
                default -> {
                }
            }
            double hp0 = p.getHealth();
            int before = st.activations.getOrDefault(e.event(), new java.util.concurrent.atomic.AtomicInteger()).get();
            st.qaResetTimers(p);
            st.adminFireAt(p, e.event(), target);
            int ran = st.activations.getOrDefault(e.event(), new java.util.concurrent.atomic.AtomicInteger()).get() - before;
            if (ran == 0) return Double.NaN;
            return switch (e.kind()) {
                case HEAL -> p.getHealth() - hp0;
                case SHIELD -> p.getAbsorptionAmount();
                case SPEED -> effectAmp(p, PotionEffectType.SPEED) + 1;
                case STRENGTH -> effectAmp(p, PotionEffectType.STRENGTH) + 1;
                case RESIST -> effectAmp(p, PotionEffectType.RESISTANCE) + 1;
                case RESOURCE -> skills.pool(p).value();
                case AOE -> k.deficit(inside) + (k.deficit(outside) > 0 ? 1e6 : 0);
                case BONUS, SMITE -> k.deficit(target);
                case CHAIN -> k.deficit(neighbour);
                case IGNITE -> target.getFireTicks();
                case CLEANSE -> p.hasPotionEffect(PotionEffectType.POISON) ? -1 : 1;
                case SLOW_AREA -> effectTicks(inside, PotionEffectType.SLOWNESS);
                case SHOVE -> inside.getVelocity().length();
            };
        }

        void procNode(PlayerClass c, SkillNode n, Effect.Proc e) {
            String id = idOf(c, n, e.event() + "." + e.kind());
            String what = n.name() + ": " + e.event() + " " + EffectNum.n(e.chance()) + "% -> " + e.kind() + "(" + EffectNum.n(e.a()) + "," + EffectNum.n(e.b()) + ") cd " + EffectNum.n(e.cooldown()) + "s";
            k.add(() -> {
                k.setClass(c);
                k.learn(n);
                // target 2 ahead, neighbour 3 from the target, one enemy inside the AoE radius, one outside it
                double radius = e.kind() == mn.suld.api.skill.tree.ProcKind.AOE ? e.b() : 4;
                k.place(0, 0, 2);
                k.place(1, 0, 5);
                boolean aoe = e.kind() == mn.suld.api.skill.tree.ProcKind.AOE;
                k.place(2, 0, aoe ? Math.max(1.0, radius - 1.0) : 40); // only the AoE test wants enemies near the player
                k.place(3, 0, aoe ? radius + 3.5 : 48);
                for (int i = 4; i < N_DUMMIES; i++) k.park(i);
                LivingEntity target = k.dummies.get(0), neighbour = k.dummies.get(1), inside = k.dummies.get(2), outside = k.dummies.get(3);
                int trials = e.chance() >= 100 ? 12 : 300;
                int ran = 0;
                double sum = 0;
                for (int i = 0; i < trials; i++) {
                    double v = procTrial(e, target, neighbour, inside, outside);
                    if (!Double.isNaN(v)) {
                        ran++;
                        sum += v;
                    }
                }
                double freq = ran / (double) trials;
                double pe = e.chance() / 100;
                double sigma = Math.sqrt(pe * (1 - pe) / trials);
                boolean chanceOk = near(freq, pe, e.chance() >= 100 ? 0 : Math.max(0.02, 4 * sigma));
                check(id + ".chance", "proc", what, "share of triggers that fire (" + trials + " triggers)", 0, freq, EffectNum.n(pe) + " +-" + fmt(4 * sigma), chanceOk);
                double mean = ran == 0 ? Double.NaN : sum / ran;
                double a = atk();
                double expected;
                String metric;
                boolean ok;
                switch (e.kind()) {
                    case HEAL -> { expected = e.a(); metric = "health restored"; ok = near(mean, expected, 0.1); }
                    case SHIELD -> { expected = e.a(); metric = "absorption"; ok = near(mean, expected, 0.1); }
                    case SPEED, STRENGTH, RESIST -> { expected = e.a(); metric = "effect level"; ok = near(mean, expected, 0.01); }
                    case RESOURCE -> { expected = Math.floor(e.a()); metric = "resource after (from empty)"; ok = near(mean, expected, 1.01); }
                    case AOE -> { expected = a * e.a(); metric = "damage to an enemy inside the radius (outside must be 0)"; ok = nearRel(mean, expected, 0.03); }
                    case BONUS, SMITE -> { expected = a * e.a(); metric = "damage to the target"; ok = nearRel(mean, expected, 0.03); }
                    case CHAIN -> { expected = a * e.a(); metric = "damage to the neighbour of the target"; ok = nearRel(mean, expected, 0.03); }
                    case IGNITE -> { expected = e.a() * 20; metric = "fire ticks on the target"; ok = mean >= expected - 3 && mean <= expected; }
                    case CLEANSE -> { expected = 1; metric = "poison removed (1 = yes)"; ok = near(mean, 1, 0.001); }
                    default -> { expected = 0; metric = "?"; ok = false; }
                }
                check(id + ".effect", "proc", what, metric, 0, mean, EffectNum.n(expected), ran > 0 && ok);
                // the cooldown holds back an immediate second firing (chance-100 passives only; others are random)
                if (e.chance() >= 100 && e.cooldown() > 0) {
                    k.resetPlayer();
                    st.qaResetTimers(p);
                    int before = st.activations.getOrDefault(e.event(), new java.util.concurrent.atomic.AtomicInteger()).get();
                    st.adminFireAtNoReset(p, e.event(), target);
                    st.adminFireAtNoReset(p, e.event(), target);
                    int fired = st.activations.getOrDefault(e.event(), new java.util.concurrent.atomic.AtomicInteger()).get() - before;
                    check(id + ".cooldown", "proc", what, "firings of two back-to-back triggers (cooldown " + EffectNum.n(e.cooldown()) + " s)", 0, fired, "1", fired == 1);
                }
                k.unlearn();
            });
            if (e.chance() >= 100 && e.cooldown() > 0 && e.cooldown() <= 4) {
                // the passive becomes ready again after its cooldown: measured in real time
                int[] box = new int[1];
                k.add(() -> {
                    k.setClass(c);
                    k.learn(n);
                    k.resetPlayer();
                    st.qaResetTimers(p);
                    box[0] = st.activations.getOrDefault(e.event(), new java.util.concurrent.atomic.AtomicInteger()).get();
                    st.adminFireAtNoReset(p, e.event(), k.dummies.get(0));
                });
                k.wait((int) (e.cooldown() * 20) + 10);
                k.add(() -> {
                    int mid = st.activations.getOrDefault(e.event(), new java.util.concurrent.atomic.AtomicInteger()).get();
                    st.adminFireAtNoReset(p, e.event(), k.dummies.get(0));
                    int end = st.activations.getOrDefault(e.event(), new java.util.concurrent.atomic.AtomicInteger()).get();
                    check(id + ".ready-again", "proc", what, "fires again after " + EffectNum.n(e.cooldown()) + " s + 0.5 s", mid - box[0], end - box[0], "2 firings in total", end - box[0] == 2);
                    k.unlearn();
                });
            }
            k.wait(1);
        }

        // ======================================================================================== WIRING (real events)

        void wiring() {
            k.add(() -> {
                single(2);
                k.resetDummies();
            });
            // HIT: Дарханы Дөлт Цохилт ignites on 20% of real hits
            wire(PlayerClass.DARKHAN, "r3", TriggerEvent.HIT, "real melee hits (EntityDamageByEntityEvent from the player)", () -> {
                int fired = 0;
                for (int i = 0; i < 60; i++) {
                    st.qaResetTimers(p);
                    k.reset(k.dummies.get(0));
                    k.hit(k.dummies.get(0));
                    fired += k.dummies.get(0).getFireTicks() > 0 ? 1 : 0;
                }
                return fired;
            }, v -> v >= 1);
            // CRIT: Баатрын Цус Бялдар heals 3 only on crits
            wire(PlayerClass.BAATAR, "r4", TriggerEvent.CRIT, "real critical hits only", () -> {
                int critHealed = 0, normalHealed = 0, crits = 0;
                double min = Double.MAX_VALUE;
                double[] dmg = new double[400];
                double[] heal = new double[400];
                for (int i = 0; i < 400; i++) {
                    st.qaResetTimers(p);
                    k.reset(k.dummies.get(0));
                    p.setHealth(1);
                    dmg[i] = k.hit(k.dummies.get(0));
                    heal[i] = p.getHealth() - 1;
                    if (dmg[i] > 0) min = Math.min(min, dmg[i]);
                }
                for (int i = 0; i < 400; i++) {
                    boolean crit = dmg[i] > min * 1.25;
                    if (crit) crits++;
                    if (crit && heal[i] >= 3) critHealed++;
                    if (!crit && heal[i] > 0.01) normalHealed++;
                }
                return crits >= 3 && critHealed == crits && normalHealed == 0 ? 1 : 0;
            }, v -> v == 1);
            // KILL: Баатрын Асгарсан Цус heals 5 on a real kill
            wire(PlayerClass.BAATAR, "l5b", TriggerEvent.KILL, "a real kill of a hostile mob", () -> {
                p.setHealth(1);
                LivingEntity m = services.mobs().spawn(SuldContent.ORKHON_CHONO, k.origin.clone().add(15, 0, 15));
                m.setAI(false);
                m.setHealth(1);
                m.damage(5, p);
                return p.getHealth() - 1 >= 5 ? 1 : 0;
            }, v -> v == 1);
            // DAMAGED: Бөөгийн Хамгаалагч Онгон shields on 25% of hits taken
            wire(PlayerClass.BOO, "l3", TriggerEvent.DAMAGED, "real hits taken from a mob", () -> {
                int shielded = 0;
                for (int i = 0; i < 40; i++) {
                    k.resetPlayer();
                    st.qaResetTimers(p);
                    k.hurtPlayer(1, k.dummies.get(0));
                    shielded += p.getAbsorptionAmount() > 0 ? 1 : 0;
                }
                return shielded;
            }, v -> v >= 1);
            // LOW_HP: Баатрын Эр Зориг shields when a hit leaves 35% or less
            wire(PlayerClass.BAATAR, "l4", TriggerEvent.LOW_HP, "a real hit that leaves 35% health or less (and none at full health)", () -> {
                k.resetPlayer();
                st.qaResetTimers(p);
                k.hurtPlayer(1, k.dummies.get(0));
                boolean atFull = p.getAbsorptionAmount() > 0;
                k.resetPlayer();
                st.qaResetTimers(p);
                p.setHealth(4);
                k.hurtPlayer(1, k.dummies.get(0));
                boolean low = p.getAbsorptionAmount() > 0;
                return !atFull && low ? 1 : 0;
            }, v -> v == 1);
            // SNEAK: Мэргэний Салхи Шиг speeds up on a real sneak event
            wire(PlayerClass.MERGEN, "l7", TriggerEvent.SNEAK, "a real PlayerToggleSneakEvent", () -> {
                k.resetPlayer();
                st.qaResetTimers(p);
                Bukkit.getPluginManager().callEvent(new PlayerToggleSneakEvent(p, true));
                return effectAmp(p, PotionEffectType.SPEED) >= 1 ? 1 : 0;
            }, v -> v == 1);
            // CAST: Мэргэний Салхин Харвалт gives resource on 50% of casts
            wire(PlayerClass.MERGEN, "r5", TriggerEvent.CAST, "real spell casts", () -> {
                int refunded = 0;
                for (int i = 0; i < 40; i++) {
                    k.resetPlayer();
                    st.qaResetTimers(p);
                    k.emptyPool();
                    skills.pool(p).gain(Spell.CHONYN_NUD.cost());
                    skills.castDirect(p, Spell.CHONYN_NUD, true);
                    refunded += skills.pool(p).value() >= 15 ? 1 : 0;
                }
                return refunded;
            }, v -> v >= 5);
            // SPELL_HIT: Бөөгийн Сүнсний Гинж chains on 30% of spell hits (the drum hits over 30 ticks, so wait for it)
            int[] spellHitBefore = new int[1];
            k.add(() -> {
                k.setClass(PlayerClass.BOO);
                k.learn(st.tree(PlayerClass.BOO).node("r3"));
                spellHitBefore[0] = st.activations.getOrDefault(TriggerEvent.SPELL_HIT, new java.util.concurrent.atomic.AtomicInteger()).get();
                for (int i = 0; i < 20; i++) {
                    k.resetPlayer();
                    st.qaResetTimers(p);
                    forward(8);
                    k.resetDummies();
                    skills.castDirect(p, Spell.KHENGERGIIN_DUU, true);
                }
            });
            k.wait(50);
            k.add(() -> {
                int fired = st.activations.getOrDefault(TriggerEvent.SPELL_HIT, new java.util.concurrent.atomic.AtomicInteger()).get() - spellHitBefore[0];
                check("wiring." + TriggerEvent.SPELL_HIT, "wiring", st.tree(PlayerClass.BOO).node("r3").name() + " reacts to a real damaging spell hitting an enemy",
                        "passive activations after casting the drum (spell hits over 30 ticks)", 0, fired, ">= 1 (30% chance per spell hit)", fired >= 1);
                k.unlearn();
            });
        }

        void wire(PlayerClass c, String nodeId, TriggerEvent ev, String how, Supplier<Integer> trial, java.util.function.IntPredicate ok) {
            k.add(() -> {
                k.setClass(c);
                SkillNode n = st.tree(c).node(nodeId);
                k.learn(n);
                k.resetPlayer();
                single(2);
                k.resetDummies();
                int before = st.activations.getOrDefault(ev, new java.util.concurrent.atomic.AtomicInteger()).get();
                int measured = trial.get();
                int fired = st.activations.getOrDefault(ev, new java.util.concurrent.atomic.AtomicInteger()).get() - before;
                check("wiring." + ev, "wiring", n.name() + " reacts to " + how, "observed effect count / passive activations", measured, fired,
                        "the effect is observed and the passive ran", ok.test(measured) && fired >= 1);
                k.unlearn();
            });
            k.wait(1);
        }

        // ======================================================================================== KEYSTONES

        /** Mean damage of {@code n} arrows placed against the lone dummy (they land on the next tick); queues its own steps. */
        void arrowSample(boolean volley, int n, Consumer<Double> done) {
            List<Arrow> arrows = new ArrayList<>();
            k.add(() -> {
                LivingEntity target = k.dummies.get(0);
                k.reset(target);
                target.setCollidable(true); // a non-collidable entity cannot be hit by projectiles
                arrows.clear();
                // real arrows shot by the player at the dummy's chest (the dummy stands 3 blocks ahead)
                Location look = p.getLocation();
                double dz = target.getLocation().getZ() - p.getLocation().getZ();
                look.setPitch((float) Math.toDegrees(Math.atan2(1.2, dz)));
                p.teleport(look);
                Vector dir = p.getEyeLocation().getDirection().multiply(2.6);
                for (int i = 0; i < n; i++) {
                    Arrow a = p.launchProjectile(Arrow.class, dir.clone());
                    a.setCritical(false);
                    a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                    if (volley) a.addScoreboardTag("suld_volley");
                    arrows.add(a);
                }
            });
            k.wait(6);
            k.add(() -> {
                int landed = k.events.getOrDefault(k.dummies.get(0).getUniqueId(), 0);
                for (Arrow a : arrows) a.remove();
                k.dummies.get(0).setCollidable(false);
                double total = k.deficit(k.dummies.get(0));
                done.accept(landed > 0 ? total / landed : 0.0);
            });
        }

        void keystones() {
            for (PlayerClass c : PlayerClass.values()) {
                for (SkillNode n : st.tree(c).nodes()) {
                    if (!wanted(c, n)) continue;
                    for (Effect e : n.effects()) if (e instanceof Effect.Keystone ks) keystone(c, n, ks.kind());
                }
            }
        }

        void keystone(PlayerClass c, SkillNode n, KeystoneKind kind) {
            String id = c.id() + "." + n.id() + "." + kind;
            String what = kind.displayName() + " — " + kind.description();
            switch (kind) {
                case MUNKH_TESVER -> k.add(() -> {
                    single(2);
                    LivingEntity src = k.dummies.get(0);
                    double[] low = beforeAfter(c, n, () -> {
                        k.resetPlayer();
                        p.setHealth(k.maxHealth() * 0.25);
                        return k.hurtPlayer(2, src);
                    });
                    double[] high = beforeAfter(c, n, () -> {
                        k.resetPlayer();
                        return k.hurtPlayer(2, src);
                    });
                    double[] heal = beforeAfter(c, n, () -> {
                        k.resetPlayer();
                        p.setHealth(5);
                        p.heal(4, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.CUSTOM);
                        return p.getHealth() - 5;
                    });
                    check(id + ".resist-low", "keystone", what, "health lost from a 2 damage hit at 25% health", low[0], low[1], "x0.65", near(low[1] / low[0], 0.65, 0.01));
                    check(id + ".no-resist-high", "keystone", what, "health lost from a 2 damage hit at full health", high[0], high[1], "x1.0 (no change above 30%)", near(high[1] / high[0], 1.0, 0.01));
                    check(id + ".healing", "keystone", what, "health restored by a 4 point heal", heal[0], heal[1], "x0.5", near(heal[1] / heal[0], 0.5, 0.01));
                });
                case TENGERTEI_KHOLBOGDOKH -> k.add(() -> {
                    double[] hp = beforeAfter(c, n, () -> attr(Attribute.MAX_HEALTH));
                    double[] pool = beforeAfter(c, n, () -> (double) skills.pool(p).max());
                    double[] regen = beforeAfter(c, n, () -> {
                        k.resetPlayer();
                        k.emptyPool();
                        for (int i = 0; i < 10; i++) skills.regenOne(p);
                        return skills.pool(p).fraction() * skills.pool(p).max() / 10.0;
                    });
                    check(id + ".max-health", "keystone", what, "max health", hp[0], hp[1], "x0.75", near(hp[1] / hp[0], 0.75, 0.005));
                    check(id + ".resource-max", "keystone", what, "resource capacity", pool[0], pool[1], "+40", near(pool[1] - pool[0], 40, 0.01));
                    check(id + ".resource-regen", "keystone", what, "resource per second", regen[0], regen[1], "+3", near(regen[1] - regen[0], 3, 0.05));
                });
                case ALTAN_DOSH -> altan(c, n, id, what);
                case TALYN_SALKHI -> k.add(() -> {
                    k.setClass(c);
                    double[] walk = beforeAfter(c, n, () -> attr(Attribute.MOVEMENT_SPEED));
                    check(id + ".walking", "keystone", what, "walking speed", walk[0], walk[1], "-10% (x0.9)", near(walk[1] / walk[0] - 1, -0.10, 0.003));
                    double[] horse = new double[2];
                    k.unlearn();
                    k.resetPlayer();
                    Horse h = k.world.spawn(k.origin.clone().add(0, 0, -4), Horse.class);
                    h.setAI(false);
                    h.setTamed(true);
                    h.getInventory().setSaddle(new ItemStack(Material.SADDLE));
                    var sp = h.getAttribute(Attribute.MOVEMENT_SPEED);
                    if (sp != null) sp.setBaseValue(0.2); // horses roll a random speed: measure the same animal before and after
                    h.addPassenger(p);
                    horse[0] = sp == null ? 0 : sp.getValue();
                    h.eject();
                    k.learn(n);
                    h.addPassenger(p);
                    horse[1] = sp == null ? 0 : sp.getValue();
                    h.eject();
                    h.remove();
                    check(id + ".mounted", "keystone", what, "horse speed attribute with the player on it", horse[0], horse[1], "x1.6", horse[0] > 0 && near(horse[1] / horse[0], 1.6, 0.01));
                });
                case NEG_SUMNII_KHUVI -> arrowKeystone(c, n, id, what);
            }
            k.wait(1);
        }

        void altan(PlayerClass c, SkillNode n, String id, String what) {
            k.add(() -> {
                k.setClass(c);
                ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
                p.getInventory().setItemInMainHand(sword);
                int trials = 400;
                double[] loss = new double[2];
                for (int phase = 0; phase < 2; phase++) {
                    if (phase == 0) k.unlearn();
                    else k.learn(n);
                    ItemStack it = new ItemStack(Material.DIAMOND_SWORD);
                    p.getInventory().setItemInMainHand(it);
                    for (int i = 0; i < trials; i++) p.damageItemStack(EquipmentSlot.HAND, 1);
                    var meta = (org.bukkit.inventory.meta.Damageable) p.getInventory().getItemInMainHand().getItemMeta();
                    loss[phase] = meta == null ? 0 : meta.getDamage();
                }
                k.unlearn();
                double sigma = Math.sqrt(trials * 0.4 * 0.6);
                check(id + ".durability", "keystone", what, "durability lost over " + trials + " item damage events", loss[0], loss[1], "x0.4 = " + EffectNum.n(0.4 * trials) + " +-" + fmt(4 * sigma),
                        near(loss[0], trials, 0.5) && near(loss[1], 0.4 * trials, 4 * sigma));
            });
            Spell s = probeSpellFor(PlayerClass.DARKHAN);
            spellCompare(c, n, s, 20, false, (b, a) -> check(id + ".spell-damage", "keystone", what, "damage per spell hit (" + s.displayName() + ")", b.perApplication, a.perApplication,
                    "x1.15", b.perApplication > 0 && near(a.perApplication / b.perApplication, 1.15, 0.02)));
            spellCompare(c, n, s, 20, false, (b, a) -> check(id + ".spell-burn", "keystone", what, "longest fire on an enemy after " + s.displayName() + " (ticks)", b.maxFire, a.maxFire,
                    "+60 ticks (3 s) over the spell's own fire", near(a.maxFire - b.maxFire, 60, 4)));
        }

        Spell probeSpellFor(PlayerClass c) {
            return probeSpell(c);
        }

        /** Arrow damage through the real arrow-hit pipeline (CombatListener), with and without the keystone. */
        void arrowKeystone(PlayerClass c, SkillNode n, String id, String what) {
            double[][] mean = new double[2][2]; // [phase][normal, volley]
            for (int phase = 0; phase < 2; phase++) {
                final int ph = phase;
                k.add(() -> {
                    k.setClass(c);
                    if (ph == 0) k.unlearn();
                    else k.learn(n);
                    k.resetPlayer();
                    single(3);
                    k.resetDummies();
                });
                arrowSample(false, 30, m -> mean[ph][0] = m);
                arrowSample(true, 30, m -> mean[ph][1] = m);
            }
            k.add(() -> {
                k.unlearn();
                check(id + ".single-target", "keystone", what, "damage per normal arrow (mean of 30)", mean[0][0], mean[1][0], "x1.4 (+-0.25: arrow crit rolls)",
                        mean[0][0] > 0 && near(mean[1][0] / mean[0][0], 1.4, 0.25));
                check(id + ".volley", "keystone", what, "damage per Олон Сум arrow (mean of 30)", mean[0][1], mean[1][1], "x0.7 (+-0.2: arrow crit rolls)",
                        mean[0][1] > 0 && near(mean[1][1] / mean[0][1], 0.7, 0.2));
            });
        }

        // ======================================================================================== ULTIMATES

        void ultimates() {
            for (PlayerClass c : PlayerClass.values()) {
                for (SkillNode n : st.tree(c).nodes()) {
                    if (!wanted(c, n)) continue;
                    for (Effect e : n.effects()) if (e instanceof Effect.UnlockUltimate u) ultimate(c, n, u.ultimate());
                }
            }
        }

        /** Casts the ultimate on a fresh arena and runs {@code measure} after {@code wait} ticks. */
        void ultRun(PlayerClass c, SkillNode n, Runnable layout, int wait, Runnable afterCast, Runnable measure) {
            k.add(() -> {
                k.setClass(c);
                k.learn(n);
                k.resetPlayer();
                layout.run();
                k.resetDummies();
                boolean cast = st.qaCastUltimate(p);
                if (!cast) rec("ultimate.cast." + n.id(), "ultimate", n.name(), "cast", 0, 0, "cast", "FAIL", "the ultimate did not cast");
                afterCast.run();
            });
            k.wait(wait);
            k.add(() -> {
                measure.run();
                k.unlearn();
            });
        }

        void ultimate(PlayerClass c, SkillNode n, Ultimate u) {
            String id = c.id() + "." + n.id() + "." + u;
            String what = u.displayName() + " — " + u.description();
            Runnable none = () -> { };
            double[] snap = new double[8];
            switch (u) {
                case CHINGISIIN_UUR -> ultRun(c, n, () -> single(2), 4, none, () -> {
                    check(id + ".strength", "ultimate", what, "Strength amplifier (0 = I)", -1, effectAmp(p, PotionEffectType.STRENGTH), "1 (II)", effectAmp(p, PotionEffectType.STRENGTH) == 1);
                    check(id + ".resistance", "ultimate", what, "Resistance amplifier", -1, effectAmp(p, PotionEffectType.RESISTANCE), "0 (I)", effectAmp(p, PotionEffectType.RESISTANCE) == 0);
                    check(id + ".regeneration", "ultimate", what, "Regeneration amplifier", -1, effectAmp(p, PotionEffectType.REGENERATION), "1 (II)", effectAmp(p, PotionEffectType.REGENERATION) == 1);
                    check(id + ".duration", "ultimate", what, "ticks left", 0, effectTicks(p, PotionEffectType.STRENGTH), "~240", near(effectTicks(p, PotionEffectType.STRENGTH), 240, 12));
                });
                case BUKHNII_NURAL -> ultRun(c, n, () -> forward(12), 4, () -> {
                    LivingEntity near = k.dummies.get(3);
                    snap[0] = near.getVelocity().getY();
                }, () -> {
                    double expected = atk() * 6 * st.spellDamageMultiplier(p);
                    double inside = 0, outside = 0;
                    int hitIn = 0, hitOut = 0;
                    for (int i = 0; i < N_DUMMIES - 1; i++) {
                        double d = k.deficit(k.dummies.get(i));
                        if (k.slot[i][1] <= 7.5) {
                            if (d > 0) hitIn++;
                            inside = Math.max(inside, d);
                        } else if (k.slot[i][1] >= 8.6) {
                            if (d > 0) hitOut++;
                            outside = Math.max(outside, d);
                        }
                    }
                    check(id + ".damage", "ultimate", what, "damage to an enemy inside 8 blocks (6 x ATK)", 0, inside, EffectNum.n(expected), nearRel(inside, expected, 0.03));
                    check(id + ".radius", "ultimate", what, "enemies hit beyond 8 blocks", hitOut, hitIn, "0 outside, >0 inside", hitOut == 0 && hitIn > 0);
                    check(id + ".knockup", "ultimate", what, "upward velocity right after the cast", 0, snap[0], "0.9", near(snap[0], 0.9, 0.05));
                });
                case UKHEL_UNDER -> ultRun(c, n, () -> single(2), 4, () -> snap[0] = p.getAbsorptionAmount(), () -> {
                    check(id + ".resistance", "ultimate", what, "Resistance amplifier", -1, effectAmp(p, PotionEffectType.RESISTANCE), "3 (IV)", effectAmp(p, PotionEffectType.RESISTANCE) == 3);
                    check(id + ".absorption", "ultimate", what, "absorption", 0, snap[0], "20", near(snap[0], 20, 0.01));
                });
                case SUM_BORON -> ultRun(c, n, () -> twoSide(10, 8), 130, none, () -> {
                    double expected = atk() * 0.9 * st.spellDamageMultiplier(p);
                    double minHit = Double.MAX_VALUE, total = 0;
                    int touchedNear = 0, touchedFar = 0;
                    for (int i = 0; i < N_DUMMIES - 1; i++) {
                        double d = k.deficit(k.dummies.get(i));
                        if (d > 0) {
                            minHit = Math.min(minHit, d);
                            total += d;
                            if (Math.abs(k.slot[i][1] - 10) <= 7.0) touchedNear++;
                            else touchedFar++;
                        }
                    }
                    check(id + ".damage", "ultimate", what, "smallest damage a dummy took (one arrow = 0.9 x ATK)", 0, minHit, EffectNum.n(expected), minHit < Double.MAX_VALUE && nearRel(minHit, expected, 0.03));
                    check(id + ".area", "ultimate", what, "dummies damaged in the rain area / outside it", touchedFar, touchedNear, ">0 within 7 blocks of the target (5 spread + 1.8 hit radius), 0 beyond", touchedNear > 0 && touchedFar == 0,
                            "total damage " + EffectNum.n(total) + ", " + EffectNum.n(total / expected) + " arrow hits");
                });
                case KHETIIN_KHARVAACH -> {
                    double[] mean = new double[2];
                    ultRun(c, n, () -> single(3), 4, () -> snap[0] = effectAmp(p, PotionEffectType.SPEED), () -> {
                        check(id + ".speed", "ultimate", what, "Speed amplifier", -1, snap[0], "1 (II)", snap[0] == 1);
                        Integer left = CombatListener.EMPOWERED_ARROWS.get(p.getUniqueId());
                        check(id + ".empowered", "ultimate", what, "empowered arrows queued", 0, left == null ? 0 : left, "999", left != null && left == 999);
                        CombatListener.EMPOWERED_ARROWS.remove(p.getUniqueId());
                    });
                    k.add(() -> {
                        k.setClass(c);
                        k.learn(n);
                        k.resetPlayer();
                        single(3);
                        k.resetDummies();
                    });
                    arrowSample(false, 30, m -> mean[0] = m);
                    k.add(() -> {
                        k.resetPlayer();
                        st.qaCastUltimate(p);
                    });
                    arrowSample(false, 30, m -> mean[1] = m);
                    k.add(() -> {
                        CombatListener.EMPOWERED_ARROWS.remove(p.getUniqueId());
                        k.unlearn();
                        check(id + ".arrow", "ultimate", what, "damage per arrow (mean of 30) without / with the ultimate", mean[0], mean[1], "x1.5 (+-0.3: arrow crit rolls)",
                                mean[0] > 0 && near(mean[1] / mean[0], 1.5, 0.3));
                    });
                }
                case UKHLIIN_TEMDEG -> ultRun(c, n, () -> {
                    forward(20);
                    k.place(N_DUMMIES - 2, 0, 30);
                }, 4, none, () -> {
                    LivingEntity marked = k.dummies.get(5), far = k.dummies.get(N_DUMMIES - 2);
                    double mk = Double.MAX_VALUE, ct = Double.MAX_VALUE;
                    for (int i = 0; i < 6; i++) {
                        mk = Math.min(mk, k.hit(marked));
                        ct = Math.min(ct, k.hit(far));
                    }
                    boolean glow = effectTicks(marked, PotionEffectType.GLOWING) > 0 && effectTicks(far, PotionEffectType.GLOWING) <= 0;
                    check(id + ".mark", "ultimate", what, "damage to a marked enemy / one beyond 24 blocks", ct, mk, "x1.4", ct > 0 && near(mk / ct, 1.4, 0.03));
                    check(id + ".glow", "ultimate", what, "glowing in range (1) / out of range (0)", 0, glow ? 1 : 0, "yes", glow);
                });
                case OVGODIIN_ZALBIRAL -> {
                    k.add(() -> {
                        k.setClass(c);
                        k.learn(n);
                        k.resetPlayer();
                        single(2);
                        k.resetDummies();
                        p.setHealth(1);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 600, 0));
                        p.setFireTicks(200);
                        snap[0] = p.getHealth();
                        st.qaCastUltimate(p);
                    });
                    k.wait(4);
                    k.add(() -> {
                        check(id + ".heal", "ultimate", what, "health", snap[0], p.getHealth(), "full (" + EffectNum.n(k.maxHealth()) + ")", near(p.getHealth(), k.maxHealth(), 0.01));
                        check(id + ".cleanse", "ultimate", what, "poison and fire removed (1 = yes)", 0, !p.hasPotionEffect(PotionEffectType.POISON) && p.getFireTicks() <= 0 ? 1 : 0, "yes",
                                !p.hasPotionEffect(PotionEffectType.POISON) && p.getFireTicks() <= 0);
                        k.unlearn();
                    });
                }
                case TENGERIIN_SHIITGEL -> ultRun(c, n, () -> forward(18), 70, none, () -> {
                    double expected = atk() * 5 * st.spellDamageMultiplier(p);
                    int hit = 0;
                    double dmg = 0;
                    for (int i = 0; i < N_DUMMIES - 1; i++) {
                        double d = k.deficit(k.dummies.get(i));
                        if (d > 0) {
                            hit++;
                            dmg = Math.max(dmg, d);
                        }
                    }
                    check(id + ".count", "ultimate", what, "enemies struck (the 8 nearest)", 0, hit, "8", hit == 8);
                    check(id + ".damage", "ultimate", what, "damage per strike (5 x ATK)", 0, dmg, EffectNum.n(expected), nearRel(dmg, expected, 0.03));
                });
                case SUNSNII_KHUL -> ultRun(c, n, () -> single(2), 4, none, () -> {
                    check(id + ".speed", "ultimate", what, "Speed amplifier", -1, effectAmp(p, PotionEffectType.SPEED), "2 (III)", effectAmp(p, PotionEffectType.SPEED) == 2);
                    check(id + ".resistance", "ultimate", what, "Resistance amplifier", -1, effectAmp(p, PotionEffectType.RESISTANCE), "1 (II)", effectAmp(p, PotionEffectType.RESISTANCE) == 1);
                    check(id + ".regeneration", "ultimate", what, "Regeneration amplifier", -1, effectAmp(p, PotionEffectType.REGENERATION), "2 (III)", effectAmp(p, PotionEffectType.REGENERATION) == 2);
                });
                case KHAILSAN_DALAI -> ultRun(c, n, () -> forward(12), 125, none, () -> {
                    double expected = atk() * 0.8 * st.spellDamageMultiplier(p);
                    double min = Double.MAX_VALUE;
                    int inside = 0, outside = 0;
                    for (int i = 0; i < N_DUMMIES - 1; i++) {
                        double d = k.deficit(k.dummies.get(i));
                        if (d > 0) {
                            min = Math.min(min, d);
                            if (k.slot[i][1] <= 7.2) inside++;
                            else outside++;
                        }
                    }
                    check(id + ".damage", "ultimate", what, "smallest damage a dummy took (one pulse = 0.8 x ATK; burning adds a little)", 0, min, EffectNum.n(expected) + " (+ fire ticks)", min < Double.MAX_VALUE && min >= expected * 0.97);
                    check(id + ".radius", "ultimate", what, "dummies hurt beyond 7 blocks / inside", outside, inside, "0 / >0", outside == 0 && inside > 0);
                });
                case BAMBAIN_KHEREM -> {
                    ultRun(c, n, () -> single(2), 2, () -> {
                        snap[0] = p.getAbsorptionAmount();
                        snap[1] = effectAmp(p, PotionEffectType.RESISTANCE);
                    }, () -> {
                        check(id + ".resistance", "ultimate", what, "Resistance amplifier", -1, snap[1], "2 (III)", snap[1] == 2);
                        check(id + ".absorption", "ultimate", what, "absorption", 0, snap[0], "16", near(snap[0], 16, 0.01));
                    });
                    k.add(() -> {
                        k.setClass(c);
                        k.learn(n);
                        k.resetPlayer();
                        single(2);
                        k.resetDummies();
                        st.qaCastUltimate(p);
                        LivingEntity src = k.dummies.get(0);
                        k.reset(src);
                        p.setAbsorptionAmount(0);
                        double taken = k.hurtPlayer(10, src);
                        check(id + ".thorns", "ultimate", what, "damage returned to the attacker / damage taken", 0, taken > 0 ? k.deficit(src) / taken : 0, "0.30",
                                taken > 0 && near(k.deficit(src) / taken, 0.30, 0.02));
                        k.unlearn();
                    });
                }
                case MYANGAN_ALKH -> ultRun(c, n, () -> forward(12), 60, none, () -> {
                    double each = atk() * 3 * st.spellDamageMultiplier(p);
                    double total = 0, min = Double.MAX_VALUE, worstFraction = 0;
                    for (int i = 0; i < N_DUMMIES - 1; i++) {
                        double d = k.deficit(k.dummies.get(i));
                        total += d;
                        if (d > 0) {
                            min = Math.min(min, d);
                            worstFraction = Math.max(worstFraction, Math.abs(d / each - Math.rint(d / each)));
                        }
                    }
                    double hits = total / each;
                    check(id + ".anvils", "ultimate", what, "anvil hits on dummies (total damage / (3 x ATK))", 0, hits, ">= 10 (10 anvils, each hits every enemy within 2.5)", hits >= 10 - 0.05 && Math.abs(hits - Math.rint(hits)) < 0.05);
                    // anvils land on random enemies and the dummies stand close together, so one dummy often takes several
                    check(id + ".damage", "ultimate", what, "damage each dummy took, in anvils (3 x ATK = " + EffectNum.n(each) + "); worst distance from a whole number", 0, worstFraction,
                            "every dummy took a whole number (>= 1) of anvils, +-0.03", min < Double.MAX_VALUE && min >= each * 0.97 && worstFraction <= 0.03);
                });
                case SHUURGA_DAVKHILT -> ultRun(c, n, () -> forward(24), 60, none, () -> {
                    double each = atk() * 2 * st.spellDamageMultiplier(p);
                    double min = Double.MAX_VALUE;
                    int hit = 0;
                    for (int i = 0; i < N_DUMMIES - 1; i++) {
                        double d = k.deficit(k.dummies.get(i));
                        if (d > 0) {
                            hit++;
                            min = Math.min(min, d);
                        }
                    }
                    double travelled = p.getLocation().getZ() - k.origin.getZ();
                    check(id + ".dash", "ultimate", what, "distance dashed forward (blocks)", 0, travelled, "> 6 (three dashes)", travelled > 6);
                    check(id + ".damage", "ultimate", what, "smallest damage on a dummy (one dash hit = 2 x ATK)", 0, min, EffectNum.n(each), hit > 0 && nearRel(min, each, 0.03));
                });
                case SALKHINY_GEGEEN -> ultRun(c, n, () -> single(2), 4, none, () -> {
                    check(id + ".speed", "ultimate", what, "Speed amplifier", -1, effectAmp(p, PotionEffectType.SPEED), "3 (IV)", effectAmp(p, PotionEffectType.SPEED) == 3);
                    check(id + ".strength", "ultimate", what, "Strength amplifier", -1, effectAmp(p, PotionEffectType.STRENGTH), "0 (I)", effectAmp(p, PotionEffectType.STRENGTH) == 0);
                    check(id + ".jump", "ultimate", what, "Jump Boost amplifier", -1, effectAmp(p, PotionEffectType.JUMP_BOOST), "1 (II)", effectAmp(p, PotionEffectType.JUMP_BOOST) == 1);
                });
                case MYANGAN_MORI -> ultRun(c, n, () -> {
                    int[] idx = {0};
                    double[][] pos = {{0, 5}, {0, -5}, {5, 0}, {-5, 0}, {3.5, 3.5}, {-3.5, 3.5}, {3.5, -3.5}, {-3.5, -3.5}};
                    for (double[] q : pos) k.place(idx[0]++, q[0], q[1]);
                    for (int i = idx[0]; i < N_DUMMIES - 1; i++) k.park(i);
                    k.place(N_DUMMIES - 1, -30, -30);
                }, 30, none, () -> {
                    double each = atk() * 2.5 * st.spellDamageMultiplier(p);
                    int hit = 0;
                    double worst = 0;
                    for (int i = 0; i < 8; i++) {
                        double d = k.deficit(k.dummies.get(i));
                        if (d > 0) hit++;
                        worst = Math.max(worst, Math.abs(d - each));
                    }
                    check(id + ".directions", "ultimate", what, "dummies hit (one in each of 8 directions)", 0, hit, "8", hit == 8);
                    check(id + ".damage", "ultimate", what, "damage per dummy (2.5 x ATK)", 0, each - worst, EffectNum.n(each), hit == 8 && worst <= each * 0.03);
                });
            }
            // cooldown, for every ultimate
            k.add(() -> {
                k.setClass(c);
                k.learn(n);
                k.resetPlayer();
                for (int i = 0; i < N_DUMMIES; i++) k.park(i); // this real cast must not reach the next test's dummies
                boolean first = st.qaCastUltimate(p);
                int left = st.ultimateCooldownSeconds(p);
                boolean second = st.qaCastUltimate(p);
                check(id + ".cooldown", "ultimate", what, "cooldown left after casting (s); second cast allowed?", 0, left, "45 and the second cast refused", first && left >= 44 && left <= 45 && !second);
            });
            k.wait(140); // delayed parts of that cast (strikes, anvils, horses, the 6 s molten sea) finish before the next ultimate is measured
            k.add(k::unlearn);
        }

        // ======================================================================================== COOLDOWNS (spells and ultimate)

        void cooldowns() {
            for (PlayerClass c : PlayerClass.values()) {
                for (Spell s : Spell.of(c)) {
                    k.add(() -> {
                        k.setClass(c);
                        k.unlearn();
                        k.resetPlayer();
                        single(2);
                        skills.castDirect(p, s, true);
                        double left = skills.cooldownLeft(p, s);
                        SkillService.CastResult again = skills.castDirect(p, s, false);
                        check("cooldown." + s.name(), "cooldown", s.displayName() + " (slot " + s.slot() + ")", "seconds left right after the cast; recast refused?", 0, left,
                                EffectNum.n(s.cooldownSeconds()) + " and the recast refused", near(left, s.cooldownSeconds(), 0.15) && again != SkillService.CastResult.CAST);
                    });
                    k.wait(1);
                }
            }
            // real time: a spell is ready again after its cooldown (Тэнгэрийн Цавчилт 1.5 s)
            double[] box = new double[2];
            k.add(() -> {
                k.setClass(PlayerClass.BAATAR);
                k.unlearn();
                k.resetPlayer();
                skills.castDirect(p, Spell.TENGER_TSAVCHILT, true);
                box[0] = skills.castDirect(p, Spell.TENGER_TSAVCHILT, false) != SkillService.CastResult.CAST ? 1 : 0;
            });
            k.wait(12);
            k.add(() -> {
                box[1] = skills.cooldownLeft(p, Spell.TENGER_TSAVCHILT);
            });
            k.wait(24);
            k.add(() -> check("cooldown.ready-again", "cooldown", "Тэнгэрийн Цавчилт: refused at once, still counting down at 0.6 s, ready after 1.8 s", "cooldown left at 0.6 s; recast after 1.8 s allowed (1)", box[1],
                    skills.castDirect(p, Spell.TENGER_TSAVCHILT, false) == SkillService.CastResult.CAST ? 1 : 0, "~0.9 s left and recast allowed",
                    box[0] == 1 && near(box[1], 0.9, 0.2) && skills.cooldownLeft(p, Spell.TENGER_TSAVCHILT) > 1.0));
        }
    }
}
