package mn.suld.sim;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.ItemRarity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

/**
 * The progression simulation: runs the scenario matrix for the live rules and the proposed balance, checks the
 * compliance criteria and writes {@code audit/progression-balance.json} (key {@code simulation}) and the generated
 * tables of {@code docs/PROGRESSION_SIMULATION.md}. Deterministic: same seed, same output.
 *
 * <pre>
 *   ./gradlew :suld-plugin:simulate            full matrix
 *   ./gradlew :suld-plugin:simulate -Pquick    1 run per class and cell, shorter horizons
 * </pre>
 */
public final class Sim {

    static final long SEED = 20261007L;

    final boolean quick;
    final int perClass;
    final Rules live = new LiveRules();
    final ProposedRules proposed;
    final Map<String, Object> out = new LinkedHashMap<>();
    final Map<String, String> tables = new LinkedHashMap<>();
    final List<Map<String, Object>> compliance = new ArrayList<>();

    Sim(boolean quick, ProposedRules proposed) {
        this.quick = quick;
        this.perClass = quick ? 1 : 4;
        this.proposed = proposed;
    }

    public static void main(String[] args) throws IOException {
        boolean quick = Arrays.asList(args).contains("--quick");
        String outJson = arg(args, "--out", "audit/progression-balance.json");
        String doc = arg(args, "--doc", "docs/PROGRESSION_SIMULATION.md");
        if (Arrays.asList(args).contains("--tune")) {
            tune();
            return;
        }
        long t0 = System.nanoTime();
        Sim s = new Sim(quick, new ProposedRules());
        s.runAll();
        s.out.put("runtimeSeconds", Math.round((System.nanoTime() - t0) / 1e9));
        s.write(Path.of(outJson), Path.of(doc));
        System.out.println("simulation done in " + s.out.get("runtimeSeconds") + " s; compliance:");
        for (Map<String, Object> c : s.compliance) System.out.println("  " + c.get("id") + " " + c.get("result") + "  " + c.get("value") + "  — " + c.get("criterion"));
    }

    static String arg(String[] a, String k, String d) {
        for (int i = 0; i + 1 < a.length; i++) if (a[i].equals(k)) return a[i + 1];
        return d;
    }

    // ============================================================================================ running cells

    record Cell(String name, Rules rules, Profile profile, int days, int perClass) {
    }

    List<Engine.Result> run(Cell c) {
        PlayerClass[] classes = PlayerClass.values();
        int n = classes.length * c.perClass();
        Engine.Result[] res = new Engine.Result[n];
        IntStream.range(0, n).parallel().forEach(i -> {
            PlayerClass pc = classes[i % classes.length];
            long seed = SEED * 31 + (long) c.name().hashCode() * 1_000_003L + i;
            res[i] = Engine.run(c.rules(), c.profile(), pc, seed, c.days());
        });
        return Arrays.asList(res);
    }

    static double pct(List<Double> v, double q) {
        List<Double> s = new ArrayList<>();
        for (Double d : v) if (d != null && !d.isNaN()) s.add(d);
        if (s.isEmpty()) return Double.NaN;
        s.sort(Double::compare);
        double idx = q * (s.size() - 1);
        int lo = (int) Math.floor(idx), hi = (int) Math.ceil(idx);
        return s.get(lo) + (s.get(hi) - s.get(lo)) * (idx - lo);
    }

    /** Fraction of runs where the value is defined (e.g. reached level 60). */
    static double reached(List<Double> v) {
        long ok = v.stream().filter(d -> d != null && !d.isNaN()).count();
        return v.isEmpty() ? 0 : (double) ok / v.size();
    }

    static List<Double> col(List<Engine.Result> rs, ToDoubleFunction<Engine.Result> f) {
        List<Double> out = new ArrayList<>();
        for (Engine.Result r : rs) out.add(f.applyAsDouble(r));
        return out;
    }

    static double snap(Engine.Result r, int day, String key) {
        for (Map<String, Double> s : r.snaps()) if (s.get("day") == day) return s.getOrDefault(key, Double.NaN);
        return Double.NaN;
    }

    static double hoursTo(Engine.Result r, int level) {
        Double h = r.player().hoursAtLevel.get(level);
        return h == null ? Double.NaN : h;
    }

    static double dayTo(Engine.Result r, int level) {
        List<Integer> dl = r.player().dayLevel;
        for (int i = 0; i < dl.size(); i++) if (dl.get(i) >= level) return i + 1;
        return Double.NaN;
    }

    static Map<String, Object> stat(List<Double> v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("p10", round(pct(v, 0.1)));
        m.put("p50", round(pct(v, 0.5)));
        m.put("p90", round(pct(v, 0.9)));
        m.put("reached", round(reached(v)));
        return m;
    }

    static Object round(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return null;
        return Math.round(d * 100) / 100.0;
    }

    static String f0(double d) {
        return Double.isNaN(d) ? "—" : String.valueOf(Math.round(d));
    }

    static String f1(double d) {
        return Double.isNaN(d) ? "—" : String.format(java.util.Locale.ROOT, "%.1f", d);
    }

    static String rar(double ordinal) {
        if (Double.isNaN(ordinal)) return "—";
        return ItemRarity.values()[(int) Math.round(ordinal)].id();
    }

    // =================================================================================================== matrix

    static final int[] DAYS = {7, 14, 30, 60, 90, 180};
    static final String[] ARCH = {"casual", "active", "hardcore"};

    static Profile arch(String a) {
        return switch (a) {
            case "casual" -> Profile.casual();
            case "active" -> Profile.active();
            default -> Profile.hardcore();
        };
    }

    void runAll() {
        out.put("seed", SEED);
        out.put("mode", quick ? "quick" : "full");
        out.put("runsPerCell", perClass * 5);
        out.put("curve", curveTable());
        Map<String, Object> rulesOut = new LinkedHashMap<>();
        Map<String, Map<String, List<Engine.Result>>> main = new LinkedHashMap<>();
        for (Rules r : List.of(live, proposed)) {
            Map<String, List<Engine.Result>> byArch = new LinkedHashMap<>();
            for (String a : ARCH) {
                int days = quick ? (a.equals("hardcore") ? 60 : 90) : 180;
                byArch.put(a, run(new Cell(r.name() + "." + a, r, arch(a), days, perClass)));
                System.out.println("  ran " + r.name() + " " + a);
            }
            main.put(r.name(), byArch);
            rulesOut.put(r.name(), archetypeReport(r, byArch));
        }
        out.put("archetypes", rulesOut);
        tables.put("player-table", playerTable(main));
        tables.put("seven-day", sevenDayTable(main.get("proposed")));
        tables.put("time-to", timeToTable(main));
        tables.put("classes", classTable(main));
        tables.put("level20", level20Table(main));
        tables.put("economy", economyTable(main));
        tables.put("armor", armorTable(main.get("proposed")));
        styles();
        exploits();
        calibration();
        sensitivity();
        deathLocks();
        party();
        complianceChecks(main);
        out.put("compliance", compliance);
        tables.put("compliance", complianceTable());
    }

    Map<String, Object> curveTable() {
        Map<String, Object> m = new LinkedHashMap<>();
        StringBuilder t = new StringBuilder("| Level | Live EXP to next | Live cumulative | Proposed EXP to next | Proposed cumulative |\n|---|---|---|---|---|\n");
        long cl = 0, cp = 0;
        List<Object> rows = new ArrayList<>();
        for (int lv = 1; lv <= 60; lv++) {
            long nl = lv < 60 ? live.curve().expForLevel(lv) : 0, np = lv < 60 ? proposed.curve().expForLevel(lv) : 0;
            if (lv == 1 || lv % 5 == 0 || lv == 59) {
                t.append("| ").append(lv).append(" | ").append(nl).append(" | ").append(cl).append(" | ").append(np).append(" | ").append(cp).append(" |\n");
            }
            rows.add(List.of(lv, nl, cl, np, cp));
            cl += nl;
            cp += np;
        }
        m.put("liveTotalTo60", cl);
        m.put("proposedTotalTo60", cp);
        m.put("proposedFormula", "round(" + proposed.curveBase() + " * L^" + ProposedRules.CURVE_EXP + ")");
        m.put("rows", rows);
        tables.put("curve", t.toString());
        return m;
    }

    static final String[] METRICS = {"level", "levelFraction", "activeHours", "totalExp", "skillPoints", "gearScore", "gearPower",
            "gearPowerPar", "highestRarity", "normalRarity", "dungeonsOpen", "dungeonsClearable", "dungeonsCleared", "dungeonsTotal",
            "bossesDefeated", "storyPct", "regions", "masteryTotal", "coins", "coinsEarned", "coinsSpent", "crafting", "ascension",
            "armorLevel", "armorTier", "mythicTier", "heroicCleared", "deaths", "lockedHours", "endgamePct", "maxGearPct"};

    Map<String, Object> archetypeReport(Rules r, Map<String, List<Engine.Result>> byArch) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, List<Engine.Result>> e : byArch.entrySet()) {
            Map<String, Object> a = new LinkedHashMap<>();
            List<Engine.Result> rs = e.getValue();
            for (int d : Engine.CHECKPOINTS) {
                if (Double.isNaN(snap(rs.get(0), d, "level"))) continue;
                Map<String, Object> dm = new LinkedHashMap<>();
                for (String k : METRICS) dm.put(k, stat(col(rs, x -> snap(x, d, k))));
                a.put("day" + d, dm);
            }
            Map<String, Object> tt = new LinkedHashMap<>();
            for (int lv : new int[]{10, 20, 30, 40, 50, 60}) {
                tt.put("hoursToLevel" + lv, stat(col(rs, x -> hoursTo(x, lv))));
                tt.put("daysToLevel" + lv, stat(col(rs, x -> dayTo(x, lv))));
            }
            tt.put("hoursToFirstLegendary", stat(col(rs, x -> x.player().firstRarity[ItemRarity.LEGENDARY.ordinal()])));
            tt.put("hoursToFirstAncient", stat(col(rs, x -> x.player().firstRarity[ItemRarity.ANCIENT.ordinal()])));
            tt.put("hoursToFirstMythic", stat(col(rs, x -> x.player().firstRarity[ItemRarity.MYTHIC.ordinal()])));
            tt.put("hoursToFirstEndgameDungeon", stat(col(rs, x -> x.player().firstEndgameDungeon)));
            for (int asc : new int[]{1, 3, 5, 10}) tt.put("hoursToAscension" + asc, stat(col(rs, x -> x.player().hoursAtAscension[asc])));
            a.put("timeTo", tt);
            Map<String, Object> src = new LinkedHashMap<>();
            Map<String, Double> sum = new LinkedHashMap<>();
            for (Engine.Result x : rs) x.player().expBySource.forEach((k, v) -> sum.merge(k, v, Double::sum));
            double total = sum.values().stream().mapToDouble(Double::doubleValue).sum();
            sum.forEach((k, v) -> src.put(k, round(v / Math.max(1, total))));
            a.put("expShareBySource", src);
            m.put(e.getKey(), a);
        }
        return m;
    }

    // ===================================================================================================== tables

    String playerTable(Map<String, Map<String, List<Engine.Result>>> main) {
        StringBuilder t = new StringBuilder();
        for (String rn : List.of("proposed", "live")) {
            t.append("\n**").append(rn.equals("live") ? "Live rules (the game today)" : "Proposed rules").append("** — p50 of ")
                    .append(perClass * 5).append(" runs per row (all five classes), mid calibration\n\n");
            t.append("| Player | Day | Level | Gear power (×par) · rarity | Dungeons cleared / open (+ heroic) | Bosses | Story | Regions | Mastery | Ascension | Armour lvl/tier | Endgame % |\n");
            t.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
            for (String a : ARCH) {
                List<Engine.Result> rs = main.get(rn).get(a);
                for (int d : DAYS) {
                    if (Double.isNaN(snap(rs.get(0), d, "level"))) continue;
                    double lv = pct(col(rs, x -> snap(x, d, "levelFraction")), 0.5);
                    t.append("| ").append(a).append(' ').append(a.equals("casual") ? "2h" : a.equals("active") ? "5h" : "10h").append(" | ")
                            .append(d).append(" | ").append(f1(lv)).append(" | ")
                            .append(f0(pct(col(rs, x -> snap(x, d, "gearPower")), 0.5))).append(" (").append(f1(pct(col(rs, x -> snap(x, d, "gearPowerPar")), 0.5)))
                            .append(") · ").append(rar(pct(col(rs, x -> snap(x, d, "normalRarity")), 0.5))).append('/').append(rar(pct(col(rs, x -> snap(x, d, "highestRarity")), 0.5)))
                            .append(" | ").append(f0(pct(col(rs, x -> snap(x, d, "dungeonsCleared")), 0.5))).append(" / ").append(f0(pct(col(rs, x -> snap(x, d, "dungeonsOpen")), 0.5)))
                            .append(" of ").append(f0(snap(rs.get(0), d, "dungeonsTotal")))
                            .append(pct(col(rs, x -> snap(x, d, "heroicCleared")), 0.5) > 0 ? " (+" + f0(pct(col(rs, x -> snap(x, d, "heroicCleared")), 0.5)) + " H)" : "")
                            .append(" | ").append(f0(pct(col(rs, x -> snap(x, d, "bossesDefeated")), 0.5)))
                            .append(" | ").append(f0(pct(col(rs, x -> snap(x, d, "storyPct")), 0.5))).append(" %")
                            .append(" | ").append(f0(pct(col(rs, x -> snap(x, d, "regions")), 0.5))).append('/').append(f0(snap(rs.get(0), d, "regionsTotal")))
                            .append(" | ").append(f0(pct(col(rs, x -> snap(x, d, "masteryTotal")), 0.5)))
                            .append(" | ").append(f0(pct(col(rs, x -> snap(x, d, "ascension")), 0.5)))
                            .append(" | ").append(rn.equals("live") ? "—" : f0(pct(col(rs, x -> snap(x, d, "armorLevel")), 0.5)) + " / T" + f0(pct(col(rs, x -> snap(x, d, "armorTier")), 0.5)))
                            .append(" | ").append(rn.equals("live") ? "— (none exists)" : f0(pct(col(rs, x -> snap(x, d, "endgamePct")), 0.5)))
                            .append(" |\n");
                }
            }
        }
        return t.toString();
    }

    String sevenDayTable(Map<String, List<Engine.Result>> byArch) {
        StringBuilder t = new StringBuilder("| Player | Day | Level (p10–p90) | Gear power | Highest rarity | Dungeons cleared | Story | Regions | Armour tier | Milestones that day |\n|---|---|---|---|---|---|---|---|---|---|\n");
        for (String a : ARCH) {
            List<Engine.Result> rs = byArch.get(a);
            for (int d = 1; d <= 7; d++) {
                final int day = d;
                t.append("| ").append(a).append(" | ").append(d).append(" | ")
                        .append(f1(pct(col(rs, x -> snap(x, day, "levelFraction")), 0.5))).append(" (")
                        .append(f1(pct(col(rs, x -> snap(x, day, "levelFraction")), 0.1))).append("–")
                        .append(f1(pct(col(rs, x -> snap(x, day, "levelFraction")), 0.9))).append(") | ")
                        .append(f0(pct(col(rs, x -> snap(x, day, "gearPower")), 0.5))).append(" | ")
                        .append(rar(pct(col(rs, x -> snap(x, day, "highestRarity")), 0.5))).append(" | ")
                        .append(f0(pct(col(rs, x -> snap(x, day, "dungeonsCleared")), 0.5))).append(" | ")
                        .append(f0(pct(col(rs, x -> snap(x, day, "storyPct")), 0.5))).append(" % | ")
                        .append(f0(pct(col(rs, x -> snap(x, day, "regions")), 0.5))).append(" | T")
                        .append(f0(pct(col(rs, x -> snap(x, day, "armorTier")), 0.5))).append(" | ")
                        .append(f0(pct(col(rs, x -> x.player().dayMilestones.size() >= day ? x.player().dayMilestones.get(day - 1) : Double.NaN), 0.5)))
                        .append(" |\n");
            }
        }
        return t.toString();
    }

    String timeToTable(Map<String, Map<String, List<Engine.Result>>> main) {
        StringBuilder t = new StringBuilder("| Rules | Player | L10 | L20 | L30 | L40 | L50 | L60 (h · day) | Reached 60 | 1st legendary | 1st ancient | 1st mythic | Asc I | Asc V | Asc X |\n|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (String rn : List.of("live", "proposed")) {
            for (String a : ARCH) {
                List<Engine.Result> rs = main.get(rn).get(a);
                t.append("| ").append(rn).append(" | ").append(a);
                for (int lv : new int[]{10, 20, 30, 40, 50}) t.append(" | ").append(f1(pct(col(rs, x -> hoursTo(x, lv)), 0.5))).append(" h");
                t.append(" | ").append(f1(pct(col(rs, x -> hoursTo(x, 60)), 0.5))).append(" h · d").append(f0(pct(col(rs, x -> dayTo(x, 60)), 0.5)));
                t.append(" | ").append(f0(100 * reached(col(rs, x -> hoursTo(x, 60))))).append(" %");
                t.append(" | ").append(f1(pct(col(rs, x -> x.player().firstRarity[ItemRarity.LEGENDARY.ordinal()]), 0.5))).append(" h");
                t.append(" | ").append(f1(pct(col(rs, x -> x.player().firstRarity[ItemRarity.ANCIENT.ordinal()]), 0.5))).append(" h");
                t.append(" | ").append(f1(pct(col(rs, x -> x.player().firstRarity[ItemRarity.MYTHIC.ordinal()]), 0.5))).append(" h");
                for (int asc : new int[]{1, 5, 10}) t.append(" | ").append(f0(pct(col(rs, x -> x.player().hoursAtAscension[asc]), 0.5))).append(" h");
                t.append(" |\n");
            }
        }
        t.append("\nHours are active play hours (p50). \"—\" = not reached within the simulated horizon by half of the runs.\n");
        return t.toString();
    }

    String classTable(Map<String, Map<String, List<Engine.Result>>> main) {
        StringBuilder t = new StringBuilder("| Rules | Class | Hours to L30 | Hours to L60 | Day-7 level (hardcore) | Deaths per 100 h | DPS at 60 | HP at 60 |\n|---|---|---|---|---|---|---|---|\n");
        for (String rn : List.of("live", "proposed")) {
            List<Engine.Result> rs = main.get(rn).get("hardcore");
            for (PlayerClass c : PlayerClass.values()) {
                List<Engine.Result> cr = rs.stream().filter(x -> x.clazz() == c).toList();
                Engine.Result last = cr.get(0);
                int lastDay = (int) Math.round(last.snaps().get(last.snaps().size() - 1).get("day"));
                t.append("| ").append(rn).append(" | ").append(c.displayName()).append(" | ")
                        .append(f1(pct(col(cr, x -> hoursTo(x, 30)), 0.5))).append(" | ")
                        .append(f1(pct(col(cr, x -> hoursTo(x, 60)), 0.5))).append(" | ")
                        .append(f1(pct(col(cr, x -> snap(x, 7, "levelFraction")), 0.5))).append(" | ")
                        .append(f1(pct(col(cr, x -> 100.0 * x.player().deaths / Math.max(1, x.player().activeHours())), 0.5))).append(" | ")
                        .append(f0(pct(col(cr, x -> snap(x, lastDay, "dps")), 0.5))).append(" | ")
                        .append(f0(pct(col(cr, x -> snap(x, lastDay, "hp")), 0.5))).append(" |\n");
            }
        }
        return t.toString();
    }

    String level20Table(Map<String, Map<String, List<Engine.Result>>> main) {
        StringBuilder t = new StringBuilder("| Rules | Player | Dungeons open at L20 | Realistically clearable | Ladder size | Share open |\n|---|---|---|---|---|---|\n");
        Map<String, Object> m = new LinkedHashMap<>();
        for (String rn : List.of("live", "proposed")) {
            for (String a : ARCH) {
                List<Engine.Result> rs = main.get(rn).get(a).stream().filter(x -> x.player().atLevel20 != null).toList();
                if (rs.isEmpty()) continue;
                double open = pct(col(rs, x -> x.player().atLevel20[0]), 0.5), clear = pct(col(rs, x -> x.player().atLevel20[1]), 0.5);
                double total = rs.get(0).player().atLevel20[2];
                t.append("| ").append(rn).append(" | ").append(a).append(" | ").append(f0(open)).append(" | ").append(f0(clear)).append(" | ")
                        .append(f0(total)).append(" | ").append(f0(100 * open / total)).append(" % |\n");
                m.put(rn + "." + a, Map.of("open", open, "clearable", clear, "total", total));
            }
        }
        out.put("level20", m);
        return t.toString();
    }

    String economyTable(Map<String, Map<String, List<Engine.Result>>> main) {
        StringBuilder t = new StringBuilder("| Rules | Player | Day | Coins earned | Coins spent | Sink ratio | Balance |\n|---|---|---|---|---|---|---|\n");
        for (String rn : List.of("live", "proposed")) {
            for (String a : ARCH) {
                List<Engine.Result> rs = main.get(rn).get(a);
                for (int d : new int[]{7, 30, 90}) {
                    if (Double.isNaN(snap(rs.get(0), d, "level"))) continue;
                    double e = pct(col(rs, x -> snap(x, d, "coinsEarned")), 0.5), s = pct(col(rs, x -> snap(x, d, "coinsSpent")), 0.5);
                    t.append("| ").append(rn).append(" | ").append(a).append(" | ").append(d).append(" | ").append(f0(e)).append(" | ").append(f0(s))
                            .append(" | ").append(f1(100 * s / Math.max(1, e))).append(" % | ").append(f0(pct(col(rs, x -> snap(x, d, "coins")), 0.5))).append(" |\n");
                }
            }
        }
        return t.toString();
    }

    String armorTable(Map<String, List<Engine.Result>> byArch) {
        StringBuilder t = new StringBuilder("| Player | Day | Player level | Armour level | Armour tier | Max armour (AL 60, tier 6)? |\n|---|---|---|---|---|---|\n");
        for (String a : ARCH) {
            List<Engine.Result> rs = byArch.get(a);
            for (int d : DAYS) {
                if (Double.isNaN(snap(rs.get(0), d, "level"))) continue;
                long max = rs.stream().filter(x -> snap(x, d, "armorLevel") >= 60 && snap(x, d, "armorTier") >= 6).count();
                t.append("| ").append(a).append(" | ").append(d).append(" | ").append(f1(pct(col(rs, x -> snap(x, d, "levelFraction")), 0.5)))
                        .append(" | ").append(f0(pct(col(rs, x -> snap(x, d, "armorLevel")), 0.5)))
                        .append(" | T").append(f0(pct(col(rs, x -> snap(x, d, "armorTier")), 0.5)))
                        .append(" | ").append(max).append(" of ").append(rs.size()).append(" |\n");
            }
        }
        return t.toString();
    }

    // ===================================================================================== further scenario sets

    void styles() {
        StringBuilder t = new StringBuilder("| Rules | Playstyle | Hours to L30 | Hours to L60 | Day-30 level | Day-30 gear power | Day-30 mastery | Top EXP source (share) |\n|---|---|---|---|---|---|---|---|\n");
        Map<String, Object> m = new LinkedHashMap<>();
        int days = quick ? 30 : 60;
        for (Rules r : List.of(live, proposed)) {
            for (Profile.Style s : Profile.Style.values()) {
                List<Engine.Result> rs = run(new Cell(r.name() + ".style." + s, r, Profile.hardcore().style(s), days, quick ? 1 : 2));
                Map<String, Double> sum = new LinkedHashMap<>();
                for (Engine.Result x : rs) x.player().expBySource.forEach((k, v) -> sum.merge(k, v, Double::sum));
                double total = sum.values().stream().mapToDouble(Double::doubleValue).sum();
                Map.Entry<String, Double> top = sum.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(Map.entry("-", 0.0));
                double h30 = pct(col(rs, x -> hoursTo(x, 30)), 0.5), h60 = pct(col(rs, x -> hoursTo(x, 60)), 0.5);
                t.append("| ").append(r.name()).append(" | ").append(s).append(" | ").append(f1(h30)).append(" | ").append(f1(h60)).append(" | ")
                        .append(f1(pct(col(rs, x -> snap(x, 30, "levelFraction")), 0.5))).append(" | ")
                        .append(f0(pct(col(rs, x -> snap(x, 30, "gearPower")), 0.5))).append(" | ")
                        .append(f0(pct(col(rs, x -> snap(x, 30, "masteryTotal")), 0.5))).append(" | ")
                        .append(top.getKey()).append(" (").append(f0(100 * top.getValue() / Math.max(1, total))).append(" %) |\n");
                m.put(r.name() + "." + s, Map.of("hoursTo30", round(h30) == null ? -1 : round(h30), "hoursTo60", round(h60) == null ? -1 : round(h60),
                        "topSource", top.getKey(), "topShare", round(top.getValue() / Math.max(1, total))));
            }
        }
        out.put("playstyles", m);
        tables.put("styles", t.toString());
    }

    final Map<String, Double> exploitDay7 = new LinkedHashMap<>();

    void exploits() {
        StringBuilder t = new StringBuilder("| Rules | Scenario | Day-7 level p50 / p90 | Hours to L60 | EXP per online hour (day 7) | Armour level day 7 |\n|---|---|---|---|---|---|\n");
        Map<String, Object> m = new LinkedHashMap<>();
        for (Rules r : List.of(live, proposed)) {
            for (Profile.Exploit e : Profile.Exploit.values()) {
                Profile prof = Profile.hardcore().exploit(e);
                List<Engine.Result> rs = run(new Cell(r.name() + ".exploit." + e, r, prof, quick ? 7 : 14, quick ? 1 : 2));
                double p50 = pct(col(rs, x -> snap(x, 7, "levelFraction")), 0.5), p90 = pct(col(rs, x -> snap(x, 7, "levelFraction")), 0.9);
                // per ONLINE hour (7 days × hours, minus time locked out): the AFK player is online, not active
                double eph = pct(col(rs, x -> snap(x, 7, "totalExp") / Math.max(1, 7 * x.profile().hoursPerDay() - snap(x, 7, "lockedHours"))), 0.5);
                t.append("| ").append(r.name()).append(" | ").append(e).append(" | ").append(f1(p50)).append(" / ").append(f1(p90)).append(" | ")
                        .append(f1(pct(col(rs, x -> hoursTo(x, 60)), 0.5))).append(" | ").append(f0(eph)).append(" | ")
                        .append(r.hasClassArmor() ? f0(pct(col(rs, x -> snap(x, 7, "armorLevel")), 0.5)) : "—").append(" |\n");
                m.put(r.name() + "." + e, Map.of("day7LevelP50", round(p50), "day7LevelP90", round(p90), "expPerHour", round(eph)));
                if (r == proposed) exploitDay7.put(e.name(), p90);
                if (r == proposed && e == Profile.Exploit.AFK) exploitDay7.put("AFK_ARMOR", pct(col(rs, x -> snap(x, 7, "armorLevel")), 0.9));
                if (r == proposed) exploitDay7.put(e.name() + "_EPH", eph);
            }
        }
        out.put("exploits", m);
        tables.put("exploits", t.toString());
    }

    final Map<String, Double> calib = new LinkedHashMap<>();

    void calibration() {
        StringBuilder t = new StringBuilder("| Calibration | Player | Day-7 level p50 / p90 | Hours to L60 p50 | Deaths per 100 h |\n|---|---|---|---|---|\n");
        for (Profile.Calibration c : List.of(Profile.Calibration.LOW, Profile.Calibration.HIGH)) {
            for (String a : ARCH) {
                List<Engine.Result> rs = run(new Cell("cal." + c.name() + "." + a, proposed, arch(a).cal(c), quick ? 30 : (a.equals("hardcore") ? 60 : 120), quick ? 1 : 2));
                double p50 = pct(col(rs, x -> snap(x, 7, "levelFraction")), 0.5), p90 = pct(col(rs, x -> snap(x, 7, "levelFraction")), 0.9);
                t.append("| ").append(c.name()).append(" | ").append(a).append(" | ").append(f1(p50)).append(" / ").append(f1(p90)).append(" | ")
                        .append(f1(pct(col(rs, x -> hoursTo(x, 60)), 0.5))).append(" | ")
                        .append(f1(pct(col(rs, x -> 100.0 * x.player().deaths / Math.max(1, x.player().activeHours())), 0.5))).append(" |\n");
                calib.put(c.name() + "." + a + ".d7p90", p90);
            }
        }
        out.put("calibration", calib);
        tables.put("calibration", t.toString());
    }

    void sensitivity() {
        Profile.Calibration mid = Profile.Calibration.MID;
        record Var(String name, Profile.Calibration c) {
        }
        List<Var> vars = List.of(new Var("baseline", mid),
                new Var("XP/hour +20 %", mid.with("xp+20", 1, 1, 1, 1, 1, 1.2)),
                new Var("dungeon clear time −20 %", mid.with("dungeon-20", 1, 0.8, 1, 1, 1, 1)),
                new Var("loot drop rate ×2", mid.with("loot×2", 1, 1, 1, 2, 1, 1)),
                new Var("build 15 % stronger", mid.with("build+15", 1, 1, 1, 1, 1.15, 1)),
                new Var("XP/hour ×1.5", mid.with("xp×1.5", 1, 1, 1, 1, 1, 1.5)),
                new Var("XP/hour ×2", mid.with("xp×2", 1, 1, 1, 1, 1, 2.0)),
                new Var("XP/hour ×3", mid.with("xp×3", 1, 1, 1, 1, 1, 3.0)),
                new Var("XP/hour ×4", mid.with("xp×4", 1, 1, 1, 1, 1, 4.0)));
        StringBuilder t = new StringBuilder("| Variation (hardcore 10 h/day) | Day-7 level p50 / p90 | Hours to L60 p50 | Δ vs baseline | Day-7 gear power | 1st mythic (h) | Reaches 60 in 7 days |\n|---|---|---|---|---|---|---|\n");
        Map<String, Object> m = new LinkedHashMap<>();
        double base = Double.NaN;
        for (Var v : vars) {
            List<Engine.Result> rs = run(new Cell("sens." + v.c().name(), proposed, Profile.hardcore().cal(v.c()), quick ? 21 : 45, quick ? 1 : 2));
            double h60 = pct(col(rs, x -> hoursTo(x, 60)), 0.5);
            if (v.name().equals("baseline")) base = h60;
            long in7 = rs.stream().filter(x -> dayTo(x, 60) <= 7).count();
            t.append("| ").append(v.name()).append(" | ").append(f1(pct(col(rs, x -> snap(x, 7, "levelFraction")), 0.5))).append(" / ")
                    .append(f1(pct(col(rs, x -> snap(x, 7, "levelFraction")), 0.9))).append(" | ").append(f1(h60)).append(" | ")
                    .append(Double.isNaN(base) || Double.isNaN(h60) ? "—" : f0(100 * (h60 - base) / base) + " %").append(" | ")
                    .append(f0(pct(col(rs, x -> snap(x, 7, "gearPower")), 0.5))).append(" | ")
                    .append(f1(pct(col(rs, x -> x.player().firstRarity[ItemRarity.MYTHIC.ordinal()]), 0.5))).append(" | ")
                    .append(in7).append(" of ").append(rs.size()).append(" |\n");
            m.put(v.c().name(), Map.of("hoursTo60", round(h60) == null ? -1 : round(h60), "reach60In7Days", in7));
        }
        out.put("sensitivity", m);
        tables.put("sensitivity", t.toString());
    }

    final Map<String, Map<String, Double>> lockStats = new LinkedHashMap<>();

    void deathLocks() {
        StringBuilder t = new StringBuilder("| Lock curve | Player | Deaths per 100 h | Play time lost to locks | Longest run of fully locked days (p90) | Day-30 level | Day-90 level |\n|---|---|---|---|---|---|---|\n");
        StringBuilder lt = new StringBuilder("| Level | 5 | 10 | 20 | 30 | 40 | 50 | 60 |\n|---|---|---|---|---|---|---|---|\n");
        for (ProposedRules.LockCurve c : ProposedRules.LockCurve.values()) {
            ProposedRules r = proposed.withLock(c);
            lt.append("| ").append(c);
            for (int lv : new int[]{5, 10, 20, 30, 40, 50, 60}) lt.append(" | ").append(lockText(r.deathLockMinutes(lv, 0)));
            lt.append(" |\n");
            for (String a : ARCH) {
                List<Engine.Result> rs = run(new Cell("lock." + c + "." + a, r, arch(a), quick ? 30 : 90, quick ? 1 : 2));
                double deaths = pct(col(rs, x -> 100.0 * x.player().deaths / Math.max(1, x.player().activeHours())), 0.5);
                double lost = pct(col(rs, x -> x.player().lockedMinutes / Math.max(1, x.player().lockedMinutes + x.player().activeMinutes)), 0.5);
                double streak = pct(col(rs, x -> x.player().lockStreakMax), 0.9);
                double l30 = pct(col(rs, x -> snap(x, 30, "levelFraction")), 0.5), l90 = pct(col(rs, x -> snap(x, 90, "levelFraction")), 0.5);
                t.append("| ").append(c).append(" | ").append(a).append(" | ").append(f1(deaths)).append(" | ").append(f1(100 * lost)).append(" % | ")
                        .append(f0(streak)).append(" | ").append(f1(l30)).append(" | ").append(f1(l90)).append(" |\n");
                lockStats.put(c + "." + a, Map.of("deathsPer100h", deaths, "lostShare", lost, "streakP90", streak, "level30", l30));
            }
        }
        out.put("deathLock", lockStats);
        tables.put("death-lock-curves", lt.toString());
        tables.put("death-lock", t.toString());
    }

    static String lockText(double minutes) {
        if (minutes < 1) return "30 s";
        if (minutes < 60) return Math.round(minutes) + " min";
        return f1(minutes / 60) + " h";
    }

    void party() {
        StringBuilder t = new StringBuilder("| Rules | Group | Hours to L30 | Hours to L60 | Deaths per 100 h |\n|---|---|---|---|---|\n");
        for (Rules r : List.of(live, proposed)) {
            for (int n : new int[]{1, 4}) {
                List<Engine.Result> rs = run(new Cell(r.name() + ".party" + n, r, Profile.hardcore().party(n), quick ? 30 : 45, quick ? 1 : 2));
                t.append("| ").append(r.name()).append(" | ").append(n == 1 ? "solo" : "party of 4").append(" | ")
                        .append(f1(pct(col(rs, x -> hoursTo(x, 30)), 0.5))).append(" | ").append(f1(pct(col(rs, x -> hoursTo(x, 60)), 0.5))).append(" | ")
                        .append(f1(pct(col(rs, x -> 100.0 * x.player().deaths / Math.max(1, x.player().activeHours())), 0.5))).append(" |\n");
            }
        }
        tables.put("party", t.toString());
    }

    // =============================================================================================== compliance

    void check(String id, String criterion, boolean pass, String value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("criterion", criterion);
        m.put("result", pass ? "PASS" : "FAIL");
        m.put("value", value);
        compliance.add(m);
    }

    void complianceChecks(Map<String, Map<String, List<Engine.Result>>> main) {
        List<Engine.Result> hp = main.get("proposed").get("hardcore"), hl = main.get("live").get("hardcore");
        double h60 = pct(col(hp, x -> hoursTo(x, 60)), 0.5);
        check("C1", "efficient (hardcore, optimal policy) active hours to level 60 within 180–220 h (p50, mid calibration)",
                h60 >= 180 && h60 <= 220, f1(h60) + " h (live: " + f1(pct(col(hl, x -> hoursTo(x, 60)), 0.5)) + " h)");
        double d7 = pct(col(hp, x -> snap(x, 7, "levelFraction")), 0.9);
        double d7max = pct(col(hp, x -> snap(x, 7, "maxGearPct")), 0.9), d7end = pct(col(hp, x -> snap(x, 7, "endgamePct")), 0.9);
        double d7high = calib.getOrDefault("high.hardcore.d7p90", Double.NaN);
        boolean c2 = d7 < 60 && d7high < 60 && exploitDay7.entrySet().stream().filter(e -> !e.getKey().contains("_")).allMatch(e -> e.getValue() < 60);
        check("C2", "10 h/day × 7 days does not reach level 60 / max gear / full endgame (p90; mid, high calibration, every exploit)",
                c2, "day-7 p90 level " + f1(d7) + " (high cal " + f1(d7high) + "), max-gear " + f0(d7max) + " %, endgame " + f0(d7end)
                        + " %; live day-7 p90 level " + f1(pct(col(hl, x -> snap(x, 7, "levelFraction")), 0.9)));
        int dead = 0;
        StringBuilder deadLv = new StringBuilder();
        for (int lv = 1; lv <= 60; lv++) {
            boolean ok = false;
            for (World.Zone z : proposed.world().zones()) for (World.Mob m : z.mobs()) if (!m.elite() && Math.abs(m.level() - lv) <= 3) ok = true;
            if (!ok) {
                dead++;
                deadLv.append(lv).append(' ');
            }
        }
        int deadLive = 0;
        for (int lv = 1; lv <= 60; lv++) {
            boolean ok = false;
            for (World.Zone z : live.world().zones()) for (World.Mob m : z.mobs()) if (!m.elite() && Math.abs(m.level() - lv) <= 3) ok = true;
            if (!ok) deadLive++;
        }
        check("C3", "no dead zone: every level 1–60 has normal mobs within ±3 levels", dead == 0,
                dead + " dead levels " + deadLv.toString().trim() + " (live: " + deadLive + " dead levels)");
        boolean c4 = true;
        for (World.Dungeon d : proposed.world().dungeons()) if (!d.heroic() && proposed.dungeonLootLevel(d, 60) > d.max()) c4 = false;
        check("C4", "dungeon loot level never exceeds the dungeon's band (a level-60 farming dungeon I gets band-I loot)", c4,
                "proposed max(min, min(max, L)); live " + live.dungeonLootLevel(live.world().dungeons().get(0), 60) + " for Хасарын Агуй at L60");
        double sinkA = pct(col(main.get("proposed").get("active"), x -> snap(x, 90, "coinsSpent") / Math.max(1, snap(x, 90, "coinsEarned"))), 0.5);
        double sinkH = pct(col(hp, x -> snap(x, quick ? 60 : 90, "coinsSpent") / Math.max(1, snap(x, quick ? 60 : 90, "coinsEarned"))), 0.5);
        check("C5", "economy: sinks absorb ≥ 60 % of coin income over 90 days (active, hardcore p50)", sinkA >= 0.6 && sinkH >= 0.6,
                "active " + f0(100 * sinkA) + " %, hardcore " + f0(100 * sinkH) + " %");
        double afkEph = exploitDay7.getOrDefault("AFK_EPH", Double.NaN), optEph = exploitDay7.getOrDefault("NONE_EPH", Double.NaN);
        double afkArmor = exploitDay7.getOrDefault("AFK_ARMOR", Double.NaN);
        check("C6", "AFK: no armour progression from idle time and AFK EXP/hour ≤ 30 % of active play", afkArmor <= 1 && afkEph <= 0.3 * optEph,
                "AFK armour level " + f0(afkArmor) + ", AFK EXP/h " + f0(afkEph) + " vs active " + f0(optEph));
        ProposedRules.LockCurve chosen = proposed.lockCurve();
        boolean c7 = true;
        StringBuilder c7v = new StringBuilder();
        for (String a : ARCH) {
            Map<String, Double> ls = lockStats.get(chosen + "." + a);
            if (ls == null) continue;
            boolean ok = ls.get("lostShare") <= 0.20 && ls.get("streakP90") <= 2;
            c7 &= ok;
            c7v.append(a).append(' ').append(f0(100 * ls.get("lostShare"))).append(" % lost, streak ").append(f0(ls.get("streakP90"))).append("; ");
        }
        check("C7", "death lock (" + chosen + "): ≤ 20 % of play time lost and ≤ 2 fully locked days in a row (p90) for every archetype", c7, c7v.toString());
        double asc10 = pct(col(hp, x -> x.player().hoursAtAscension[10] - hoursTo(x, 60)), 0.5);
        double ascReached = pct(col(hp, x -> snap(x, quick ? 60 : 180, "ascension")), 0.5);
        check("C8", "post-60 depth: Ascension X needs ≥ 300 active hours after level 60 (hardcore p50)",
                Double.isNaN(asc10) || asc10 >= 300, Double.isNaN(asc10) ? "not reached in the horizon (median rank " + f0(ascReached) + " at the last day)" : f0(asc10) + " h");
        StringBuilder c9v = new StringBuilder();
        boolean c9 = true;
        for (String a : ARCH) {
            List<Engine.Result> rs = main.get("proposed").get(a);
            double share = pct(col(rs, x -> {
                int n = Math.min(30, x.player().dayMilestones.size()), ok = 0;
                for (int i = 0; i < n; i++) {
                    double gained = x.player().dayLevelFraction.get(i) - (i == 0 ? 1.0 : x.player().dayLevelFraction.get(i - 1));
                    if (x.player().dayMilestones.get(i) > 0 || gained >= 0.2) ok++;
                }
                return (double) ok / Math.max(1, n);
            }), 0.5);
            c9 &= share >= 0.9;
            c9v.append(a).append(' ').append(f0(100 * share)).append(" %; ");
        }
        check("C9", "every session matters: ≥ 90 % of the first 30 days bring a milestone (level, chapter, first clear, armour level/tier, mastery rank, +5 % gear power) or ≥ 20 % of a level (p50)", c9, c9v.toString());
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Double>> l20 = (Map<String, Map<String, Double>>) (Map<?, ?>) out.get("level20");
        Map<String, Double> p20 = l20 == null ? null : l20.get("proposed.hardcore");
        boolean c10 = p20 != null && p20.get("open") / p20.get("total") <= 0.4;
        Map<String, Double> lv20 = l20 == null ? null : l20.get("live.hardcore");
        check("C10", "level 20 reaches at most 40 % of the dungeon ladder", c10,
                p20 == null ? "n/a" : f0(p20.get("open")) + " of " + f0(p20.get("total")) + " open, " + f0(p20.get("clearable")) + " clearable"
                        + (lv20 == null ? "" : " (live: " + f0(lv20.get("open")) + " of " + f0(lv20.get("total")) + ")"));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> st = (Map<String, Map<String, Object>>) (Map<?, ?>) out.get("playstyles");
        double gen = toD(st.get("proposed.GENERALIST").get("hoursTo30")), fastest = Double.MAX_VALUE;
        String fastestName = "";
        double topShare = 0;
        for (Profile.Style s : Profile.Style.values()) {
            double h = toD(st.get("proposed." + s).get("hoursTo30"));
            if (h > 0 && h < fastest) {
                fastest = h;
                fastestName = s.name();
            }
            if (s == Profile.Style.GENERALIST) topShare = toD(st.get("proposed." + s).get("topShare"));
        }
        check("C11", "no single activity dominates: no playstyle reaches L30 > 25 % faster than the generalist; no source > 50 % of the generalist's EXP to 60",
                fastest >= 0.75 * gen && topShare <= 0.5, "generalist " + f1(gen) + " h, fastest " + fastestName + " " + f1(fastest) + " h, largest source share " + f0(100 * topShare) + " %");
        double best = 0;
        String bestName = "";
        for (Map.Entry<String, Double> e : exploitDay7.entrySet()) {
            if (!e.getKey().endsWith("_EPH") || e.getKey().startsWith("NONE") || e.getKey().startsWith("AFK")) continue;
            if (e.getValue() > best) {
                best = e.getValue();
                bestName = e.getKey().replace("_EPH", "");
            }
        }
        check("C12", "no exploit beats normal optimal play by more than ×1.25 in EXP per online hour", best <= 1.25 * optEph,
                bestName + " " + f0(best) + " vs optimal " + f0(optEph) + " EXP/h");
    }

    static double toD(Object o) {
        return o instanceof Number n ? n.doubleValue() : -1;
    }

    String complianceTable() {
        StringBuilder t = new StringBuilder("| # | Criterion | Result | Value |\n|---|---|---|---|\n");
        for (Map<String, Object> c : compliance)
            t.append("| ").append(c.get("id")).append(" | ").append(c.get("criterion")).append(" | **").append(c.get("result")).append("** | ").append(c.get("value")).append(" |\n");
        return t.toString();
    }

    // =================================================================================================== output

    void write(Path json, Path doc) throws IOException {
        Map<String, Object> root;
        if (Files.exists(json)) {
            Object parsed = MiniJson.parse(Files.readString(json, StandardCharsets.UTF_8));
            @SuppressWarnings("unchecked")
            Map<String, Object> m = parsed instanceof Map<?, ?> ? (Map<String, Object>) parsed : new LinkedHashMap<>();
            root = new LinkedHashMap<>(m);
        } else {
            root = new LinkedHashMap<>();
        }
        root.put("simulation", out);
        Files.writeString(json, MiniJson.write(root) + "\n", StandardCharsets.UTF_8);
        String text = Files.exists(doc) ? Files.readString(doc, StandardCharsets.UTF_8) : "# SÜLD progression simulation\n";
        for (Map.Entry<String, String> e : tables.entrySet()) text = replaceBlock(text, e.getKey(), e.getValue());
        Files.writeString(doc, text, StandardCharsets.UTF_8);
    }

    static String replaceBlock(String text, String name, String body) {
        String b = "<!-- sim:begin " + name + " -->", e = "<!-- sim:end " + name + " -->";
        int i = text.indexOf(b), j = text.indexOf(e);
        String block = b + "\n" + body.strip() + "\n" + e;
        if (i < 0 || j < 0) return text + "\n\n### " + name + "\n\n" + block + "\n";
        return text.substring(0, i) + block + text.substring(j + e.length());
    }

    // ===================================================================================================== tune

    /** Finds the curve base for which the hardcore p50 reaches 60 in 200 active hours (bisection on the base). */
    static void tune() {
        double lo = 150, hi = 900;
        for (int it = 0; it < 7; it++) {
            double mid = (lo + hi) / 2;
            ProposedRules r = new ProposedRules(mid, ProposedRules.LockCurve.GEOMETRIC);
            Sim s = new Sim(true, r);
            List<Engine.Result> rs = s.run(new Cell("tune", r, Profile.hardcore(), 40, 3));
            double h = pct(col(rs, x -> hoursTo(x, 60)), 0.5);
            System.out.printf("base %.1f -> %.1f h (reached %.0f %%)%n", mid, h, 100 * reached(col(rs, x -> hoursTo(x, 60))));
            if (Double.isNaN(h) || h > 200) hi = mid; else lo = mid;
        }
    }
}
