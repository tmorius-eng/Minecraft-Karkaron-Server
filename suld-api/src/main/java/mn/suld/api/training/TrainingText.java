package mn.suld.api.training;

import mn.suld.api.skill.Spell;
import mn.suld.api.skill.tree.Effect;
import mn.suld.api.skill.tree.EffectText;
import mn.suld.api.skill.tree.Ultimate;

import java.util.Locale;

/**
 * The Mongolian explanations of the training grounds (docs/TRAINING_GROUNDS.md), built from the game data so the text
 * can never disagree with the numbers: what a spell does and what it just did, when a passive fires and how often it
 * really does, what the ultimate is.
 */
public final class TrainingText {

    private TrainingText() {
    }

    /** {@code 48210} → «48 210»; below ten one decimal («4.5»). */
    public static String number(double v) {
        if (!Double.isFinite(v)) return "0";
        if (Math.abs(v) < 10) return String.format(Locale.ROOT, "%.1f", v);
        long n = Math.round(v);
        String digits = Long.toString(Math.abs(n));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) sb.append(' ');
            sb.append(digits.charAt(i));
        }
        return (n < 0 ? "-" : "") + sb;
    }

    /** A factor with up to two decimals: 2.083 → «2.08», 1.60 → «1.6», 3.0 → «3». */
    public static String factor(double f) {
        String t = String.format(Locale.ROOT, "%.2f", f);
        if (t.contains(".")) t = t.replaceAll("0+$", "").replaceAll("\\.$", "");
        return t;
    }

    /** One decimal without a trailing zero: 1.4 → «1.4», 18.0 → «18». */
    public static String seconds(double s) {
        return EffectText.num(Math.round(s * 10) / 10.0);
    }

    /**
     * After a cast: «Тэнгэрийн Цавчилт III (R-L-R): Урд талын бүх дайснаа цавчина. ATK ×1.6 · 25 Уур · 1.4 сек —
     * 3 бай, 1 930 хохирол».
     *
     * @param power    the damage factor of the cast (spell × rank × modifiers × spell damage), 0 for a spell without damage
     * @param targets  enemies the cast hit in its window
     * @param damage   their total damage
     */
    public static String spell(Spell s, int rank, double power, int cost, String resource, double cooldown, int targets, double damage) {
        StringBuilder sb = new StringBuilder(s.displayName());
        if (rank > 1) sb.append(' ').append(Spell.roman(rank));
        sb.append(" (").append(s.keys()).append("): ").append(s.description());
        if (power > 0) sb.append(" ATK ×").append(factor(power));
        sb.append(power > 0 ? " · " : " ").append(cost).append(' ').append(resource).append(" · ").append(seconds(cooldown)).append(" сек");
        if (power > 0) {
            sb.append(" — ").append(targets == 0 ? "бай оносонгүй" : targets + " бай, " + number(damage) + " хохирол");
        }
        return sb.toString();
    }

    /**
     * When a passive fires: «Уур Хилэн: Цохилт бүрт 25% магадлалтайгаар 8 нөөц сэргээнэ. Хүлээлт 2 сек. Энд 9/37 (24%)».
     */
    public static String passive(String name, Effect.Proc p, TrainingStats.PassiveRate rate) {
        StringBuilder sb = new StringBuilder(name).append(": ").append(EffectText.proc(p));
        if (p.cooldown() > 0) sb.append(" Хүлээлт ").append(seconds(p.cooldown())).append(" сек.");
        if (rate != null && rate.rolls() > 0) {
            sb.append(" Энд ").append(rate.fired()).append('/').append(rate.rolls()).append(" (").append(Math.round(rate.observed())).append("%)");
        }
        return sb.toString();
    }

    /** The ultimate: «Баатрын Их Уур (F): 12 секунд: Хүч II, Хамгаалалт I, Сэргэлт II. Хүлээлт 45 сек». */
    public static String ultimate(Ultimate u, int cooldownSeconds) {
        return u.displayName() + " (F): " + u.description() + " Хүлээлт " + cooldownSeconds + " сек.";
    }

    /** A source's part, for the stats panel: «Тэнгэрийн Цавчилт 46%». */
    public static String share(TrainingStats.Share s) {
        return s.source().name() + " " + Math.round(s.percent()) + "%";
    }

    /** A passive's observed rate against its design, for the stats panel: «Уур Хилэн 9× 24% / 25%». */
    public static String rate(TrainingStats.PassiveRate r) {
        return r.name() + " " + r.fired() + "× " + Math.round(r.observed()) + "% / " + EffectText.num(r.designChance()) + "%";
    }
}
