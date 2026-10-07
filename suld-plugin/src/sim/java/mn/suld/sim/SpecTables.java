package mn.suld.sim;

import mn.suld.api.item.ItemRarity;
import mn.suld.api.loot.LootTier;
import mn.suld.api.loot.RarityBand;
import mn.suld.api.mob.MobTier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Writes the exact numbers of the proposed spec into the spec documents (between {@code <!-- spec:begin X -->}
 * markers), straight from {@link ProposedRules}, so the documents can never disagree with what was simulated.
 * Run by {@code ./gradlew :suld-plugin:simulate} after the simulation.
 */
public final class SpecTables {

    public static void main(String[] args) throws IOException {
        ProposedRules r = new ProposedRules();
        write("docs/DIFFICULTY_CURVE.md", "mob-curve", mobCurve(r));
        write("docs/DIFFICULTY_CURVE.md", "gap-rules", gapRules(r));
        write("docs/DUNGEON_PROGRESSION_SPEC.md", "ladder", ladder(r));
        write("docs/GEAR_PROGRESSION_SPEC.md", "bands", bands(r));
        write("docs/GEAR_PROGRESSION_SPEC.md", "par", par(r));
        write("docs/ARMOR_PROGRESSION.md", "armor-need", armorNeed());
        write("docs/ARMOR_PROGRESSION.md", "tiers", tiers());
        write("docs/MASTERY_SPEC.md", "mastery-need", masteryNeed());
        write("docs/ASCENSION_SPEC.md", "ascension-cost", ascensionCost(r));
        write("docs/PROGRESSION_BALANCE_SPEC.md", "curve", curve(r));
        write("docs/DEATH_AND_RECOVERY.md", "lock", lock(r));
        System.out.println("spec tables written");
    }

    static String f(double d) {
        return String.format(Locale.ROOT, d == Math.rint(d) ? "%.0f" : "%.1f", d);
    }

    static String mobCurve(ProposedRules r) {
        StringBuilder t = new StringBuilder("| Level | Normal HP | Normal dmg/hit | Normal EXP | Elite HP / dmg / EXP | Champion HP / dmg | Boss HP (party of 4) / dmg | Par DPS | Par HP | Par armour | Mitigation at par |\n|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (int lv : new int[]{1, 2, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60}) {
            World.Mob n = ProposedRules.mob("x", "x", lv, MobTier.NORMAL, false, null);
            World.Mob e = ProposedRules.mob("x", "x", lv, MobTier.ELITE, false, null);
            World.Mob c = ProposedRules.mob("x", "x", lv, MobTier.CHAMPION, false, null);
            World.Mob b = ProposedRules.mob("x", "x", lv, MobTier.BOSS, false, null);
            double a = ProposedRules.parArmor(lv);
            t.append("| ").append(lv).append(" | ").append(f(n.hp())).append(" | ").append(f(n.dmg())).append(" | ").append(n.exp())
                    .append(" | ").append(f(e.hp())).append(" / ").append(f(e.dmg())).append(" / ").append(e.exp())
                    .append(" | ").append(f(c.hp())).append(" / ").append(f(c.dmg()))
                    .append(" | ").append(f(b.hp())).append(" / ").append(f(b.dmg()))
                    .append(" | ").append(f(Math.round(ProposedRules.parDps(lv)))).append(" | ").append(f(Math.round(ProposedRules.parHp(lv))))
                    .append(" | ").append(f(Math.round(a))).append(" | ").append(Math.round(100 * a / (a + ProposedRules.armorK(lv)))).append(" % |\n");
        }
        return t.toString();
    }

    static String gapRules(ProposedRules r) {
        StringBuilder t = new StringBuilder("| Mob level − player level | Kill EXP | Gear rolls | Damage you deal | Damage you take |\n|---|---|---|---|---|\n");
        for (int g : new int[]{-12, -10, -9, -7, -5, -4, 0, 2, 4, 6, 8, 10, 15}) {
            t.append("| ").append(g > 0 ? "+" + g : String.valueOf(g)).append(" | ×").append(String.format(Locale.ROOT, "%.2f", r.gapExpFactor(g)))
                    .append(" | ").append(r.gapAllowsGear(g) ? "yes" : "no (materials only)")
                    .append(" | ×").append(String.format(Locale.ROOT, "%.2f", r.gapDamageDealt(g)))
                    .append(" | ×").append(String.format(Locale.ROOT, "%.2f", r.gapDamageTaken(g))).append(" |\n");
        }
        return t.toString();
    }

    static String ladder(ProposedRules r) {
        StringBuilder t = new StringBuilder("| # | Dungeon | Level band | Story gate (chapter #) | Previous clear | Min gear power | Party | Boss HP / dmg | Enrage | Completion EXP | Coins | Loot level | Boss band |\n|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (World.Dungeon d : r.world().dungeons()) {
            ProposedRules.DungeonSpec s = ProposedRules.DUNGEONS.get(Math.min(d.index(), ProposedRules.DUNGEONS.size() - 1));
            t.append("| ").append(d.heroic() ? "H" + (d.index() - ProposedRules.DUNGEONS.size() + 1) : String.valueOf(d.index() + 1)).append(" | ")
                    .append(d.name()).append(" | ").append(d.min()).append("–").append(d.max()).append(" | ")
                    .append(d.heroic() ? "all (42)" : String.valueOf(d.chapterGate() + 1)).append(" | ")
                    .append(d.previous() < 0 ? (d.heroic() ? "Тэнгэрийн Шат" : "—") : r.world().dungeons().get(d.previous()).name()).append(" | ")
                    .append(f(Math.round(d.gpMin()))).append(" | ").append(d.heroic() ? 4 : s.party()).append(" | ")
                    .append(f(d.boss().hp())).append(" / ").append(f(d.boss().dmg())).append(" | ").append(f(d.enrageSeconds())).append(" s | ")
                    .append(d.completionExp()).append(" | ").append(d.coins()).append(" | ")
                    .append(d.heroic() ? "60" : "clamp(L, " + d.min() + ", " + d.max() + ")").append(" | ")
                    .append(d.heroic() ? "WORLD_EVENT" : "BOSS").append(" |\n");
        }
        t.append("\nMythic tiers 1–").append(ProposedRules.MYTHIC_TIERS).append(" of the heroic dungeons: health and damage ×(1 + 0.12·tier), EXP ×(1 + 0.06·tier), chest from the MYTHIC band, entry sigil ")
                .append(ProposedRules.mythicFee(1)).append("–").append(ProposedRules.mythicFee(ProposedRules.MYTHIC_TIERS)).append(" ₮ (crafted).\n");
        return t.toString();
    }

    static String bands(ProposedRules r) {
        StringBuilder t = new StringBuilder("| Source (loot tier) | Live band (items/tiers.json) | Proposed band |\n|---|---|---|\n");
        for (LootTier lt : LootTier.values()) {
            t.append("| ").append(lt).append(" | ").append(band(LiveRules.liveCatalog().band(lt))).append(" | ").append(band(r.loot().catalog().band(lt))).append(" |\n");
        }
        return t.toString();
    }

    static String band(RarityBand b) {
        if (b == null) return "—";
        StringBuilder s = new StringBuilder();
        int total = b.weights().values().stream().mapToInt(Integer::intValue).sum();
        for (ItemRarity ra : ItemRarity.values()) {
            Integer w = b.weights().get(ra);
            if (w == null) continue;
            if (!s.isEmpty()) s.append(" · ");
            s.append(ra.id().substring(0, 1).toUpperCase(Locale.ROOT)).append(ra.id().substring(1, Math.min(3, ra.id().length())))
                    .append(' ').append(Math.round(100.0 * w / total));
        }
        return s.toString();
    }

    static String par(ProposedRules r) {
        StringBuilder t = new StringBuilder("| Level | Par gear power | Typical worn rarity |\n|---|---|---|\n");
        for (int lv : new int[]{5, 10, 20, 30, 40, 50, 60}) {
            String rar = lv < 10 ? "common–uncommon" : lv < 20 ? "uncommon–rare" : lv < 30 ? "rare" : lv < 40 ? "rare–epic" : lv < 50 ? "epic" : lv < 60 ? "epic–legendary" : "legendary";
            t.append("| ").append(lv).append(" | ").append(Math.round(r.parGearPower(lv))).append(" | ").append(rar).append(" |\n");
        }
        return t.toString();
    }

    static String armorNeed() {
        StringBuilder t = new StringBuilder("| Armour level | Armour XP to next | Cumulative | ≈ active hours at 150 XP/h |\n|---|---|---|---|\n");
        long cum = 0;
        for (int a = 1; a <= 60; a++) {
            long n = a < 60 ? ProposedRules.armorNeed(a) : 0;
            if (a == 1 || a % 5 == 0 || a == 59) t.append("| ").append(a).append(" | ").append(n).append(" | ").append(cum).append(" | ").append(f(Math.round(cum / 150.0))).append(" |\n");
            cum += n;
        }
        return t.toString();
    }

    static String tiers() {
        StringBuilder t = new StringBuilder("| Tier | Name | Armour level | Must have cleared | Mastery rank | Coins | Materials | Class-gear rarity |\n|---|---|---|---|---|---|---|---|\n");
        String[] names = {"", "Эхлэл (beginning)", "Сайжруулсан (improved)", "Элчин (envoy)", "Хааны (royal)", "Тэнгэрлэг (celestial)", "Дээдэс (supreme)"};
        for (int i = 1; i < ProposedRules.TIER_ARMOR_LEVEL.length; i++) {
            String d = ProposedRules.TIER_DUNGEON[i];
            String dn = d == null ? "—" : ProposedRules.DUNGEONS.stream().filter(x -> x.id().equals(d)).findFirst().map(ProposedRules.DungeonSpec::name).orElse(d);
            t.append("| T").append(i).append(" | ").append(names[i]).append(" | ").append(ProposedRules.TIER_ARMOR_LEVEL[i]).append(" | ")
                    .append(dn).append(i == 6 ? " + Ascension III" : "").append(" | ").append(mn.suld.api.classgear.ArmorTier.of(i).mastery()).append(" | ").append(ProposedRules.TIER_COINS[i]).append(" | ")
                    .append(i == 1 ? "—" : (5 * i) + " band materials").append(" | ").append(ProposedRules.TIER_RARITY[i].id()).append(" |\n");
        }
        t.append("\nEnhancement +1…+").append(ProposedRules.MAX_ENHANCE).append(" inside a tier: +2 % item power each, 40 × armour level × step × tier coins; reset by the next tier.\n");
        return t.toString();
    }

    static String masteryNeed() {
        StringBuilder t = new StringBuilder("| Rank | Mastery XP for this rank | Cumulative |\n|---|---|---|\n");
        double cum = 0;
        for (int rk = 0; rk < 10; rk++) {
            double n = ProposedRules.masteryNeed(rk);
            cum += n;
            t.append("| ").append(rk + 1).append(" | ").append(Math.round(n)).append(" | ").append(Math.round(cum)).append(" |\n");
        }
        return t.toString();
    }

    static String ascensionCost(ProposedRules r) {
        StringBuilder t = new StringBuilder("| Rank | Тэнгэрийн оноо (EXP earned at the cap) | Rite coins |\n|---|---|---|\n");
        for (int rk = 0; rk < 10; rk++) {
            t.append("| ").append(roman(rk + 1)).append(" | ").append(Math.round(r.ascensionCost(rk))).append(" | ").append(ProposedRules.ascensionCoins(rk)).append(" |\n");
        }
        return t.toString();
    }

    static String roman(int n) {
        return new String[]{"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"}[n];
    }

    static String curve(ProposedRules r) {
        StringBuilder t = new StringBuilder("| Level | EXP to next (proposed) | Cumulative (proposed) | EXP to next (live) | Cumulative (live) |\n|---|---|---|---|---|\n");
        LiveRules live = new LiveRules();
        long cp = 0, cl = 0;
        for (int lv = 1; lv <= 60; lv++) {
            long np = lv < 60 ? r.curve().expForLevel(lv) : 0, nl = lv < 60 ? live.curve().expForLevel(lv) : 0;
            if (lv == 1 || lv % 5 == 0 || lv == 59) t.append("| ").append(lv).append(" | ").append(np).append(" | ").append(cp).append(" | ").append(nl).append(" | ").append(cl).append(" |\n");
            cp += np;
            cl += nl;
        }
        t.append("| total to 60 | | ").append(cp).append(" | | ").append(cl).append(" |\n");
        return t.toString();
    }

    static String lock(ProposedRules r) {
        StringBuilder t = new StringBuilder("| Level | 1 | 5 | 10 | 15 | 20 | 25 | 30 | 35 | 40 | 45 | 50 | 55 | 60 |\n|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        t.append("| Lock (real time) |");
        for (int lv : new int[]{1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60}) t.append(' ').append(Sim.lockText(r.deathLockMinutes(lv, 0))).append(" |");
        return t.append('\n').toString();
    }

    static void write(String file, String name, String body) throws IOException {
        Path p = Path.of(file);
        String text = Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : "";
        String b = "<!-- spec:begin " + name + " -->", e = "<!-- spec:end " + name + " -->";
        int i = text.indexOf(b), j = text.indexOf(e);
        String block = b + "\n" + body.strip() + "\n" + e;
        text = i < 0 || j < 0 ? text + "\n" + block + "\n" : text.substring(0, i) + block + text.substring(j + e.length());
        Files.writeString(p, text, StandardCharsets.UTF_8);
    }
}
