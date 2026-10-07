package mn.suld.api.skill.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Mongolian tooltip wording for effects, generated from the data so the text can never disagree with the numbers. */
public final class EffectText {

    private EffectText() {
    }

    public enum Kind { EFFECT, PASSIVE, TRIGGER, COOLDOWN, ULTIMATE, KEYSTONE }

    public record Line(Kind kind, String text) {
    }

    public static String num(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String sign(double v) {
        return v >= 0 ? "+" : "-";
    }

    private static String roman(double v) {
        return switch ((int) Math.round(v)) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "V";
        };
    }

    public static String stat(Effect.Stat s) {
        StatKey k = s.key();
        double v = s.value();
        String sign = k.raises() ? sign(v) : "-";
        return sign + num(Math.abs(v)) + k.unit() + " " + k.label();
    }

    public static String mod(Effect.SpellMod m) {
        ModKey k = m.key();
        return m.spell().displayName() + ": " + sign(m.value()) + num(Math.abs(m.value())) + k.unit() + " " + k.label();
    }

    private static String kind(Effect.Proc p) {
        double a = p.a(), b = p.b();
        return switch (p.kind()) {
            case HEAL -> num(a) + " ❤ эдгээнэ";
            case SHIELD -> num(a) + " ❤ нэмэлт хамгаалалт авна (" + num(b) + " сек)";
            case SPEED -> "Хурд " + roman(a) + " авна (" + num(b) + " сек)";
            case STRENGTH -> "Хүч " + roman(a) + " авна (" + num(b) + " сек)";
            case RESIST -> "Хамгаалалт " + roman(a) + " авна (" + num(b) + " сек)";
            case RESOURCE -> num(a) + " нөөц сэргээнэ";
            case AOE -> "эргэн тойрны дайснуудад " + num(a * 100) + "% хохирол өгнө (" + num(b) + " блок)";
            case IGNITE -> "дайсныг " + num(a) + " сек шатаана";
            case SLOW_AREA -> "ойролцоох дайснуудыг " + num(a) + " сек удаашруулна";
            case BONUS -> "онилсон дайсанд " + num(a * 100) + "% нэмэлт хохирол өгнө";
            case SMITE -> "дайсан дээр аянга буулгаж " + num(a * 100) + "% хохирол өгнө";
            case CHAIN -> "ойролцоох өөр дайсанд " + num(a * 100) + "% хохирол дамжуулна";
            case CLEANSE -> "муу нөлөөг арилгана";
            case SHOVE -> "ойролцоох дайснуудыг түлхэнэ";
        };
    }

    public static String proc(Effect.Proc p) {
        String when = p.event().label();
        String sentence = (p.chance() < 100 ? num(p.chance()) + "% магадлалтайгаар " : "") + kind(p);
        return Character.toUpperCase(when.charAt(0)) + when.substring(1) + " " + sentence + ".";
    }

    /** All tooltip lines of a node, in reading order. */
    public static List<Line> lines(SkillNode n) {
        List<Line> out = new ArrayList<>();
        for (Effect e : n.effects()) {
            switch (e) {
                case Effect.Stat s -> out.add(new Line(Kind.EFFECT, stat(s)));
                case Effect.SpellMod m -> out.add(new Line(Kind.EFFECT, mod(m)));
                case Effect.Proc p -> {
                    out.add(new Line(Kind.PASSIVE, proc(p)));
                    out.add(new Line(Kind.TRIGGER, "Өдөөгч: " + p.event().label()));
                    if (p.cooldown() > 0) out.add(new Line(Kind.COOLDOWN, "Хүлээлт: " + num(p.cooldown()) + " сек"));
                }
                case Effect.Keystone k -> out.add(new Line(Kind.KEYSTONE, k.kind().displayName() + " — " + k.kind().description()));
                case Effect.UnlockUltimate u -> {
                    out.add(new Line(Kind.ULTIMATE, u.ultimate().displayName() + " — " + u.ultimate().description()));
                    out.add(new Line(Kind.TRIGGER, "Өдөөгч: F товч (гар солих)"));
                    out.add(new Line(Kind.COOLDOWN, "Хүлээлт: " + Ultimate.COOLDOWN_SECONDS + " сек"));
                }
            }
        }
        return out;
    }
}
