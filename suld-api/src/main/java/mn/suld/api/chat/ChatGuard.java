package mn.suld.api.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat protection for a public server: per-player rate limit, repeated-message block, server/IP advertising block
 * (allow-listed domains pass) and a caps filter that lowers SHOUTED messages instead of blocking them. Pure and
 * thread-safe (chat runs async).
 */
public final class ChatGuard {

    public enum Verdict { ALLOW, TOO_FAST, REPEAT, ADVERT }

    /** {@code text} is the message to send (possibly lowered) when allowed. */
    public record Result(Verdict verdict, String text) {
        public boolean allowed() {
            return verdict == Verdict.ALLOW;
        }
    }

    public record Settings(int maxMessages, long windowMillis, long repeatMillis, boolean blockLinks,
                           List<String> allowedDomains, double capsLimit) {
        public static Settings defaults() {
            return new Settings(4, 5_000, 30_000, true, List.of("suld.mn"), 0.7);
        }
    }

    private static final Pattern IPV4 = Pattern.compile("\\b\\d{1,3}(?:[.,]\\s?\\d{1,3}){3}(?::\\d{2,5})?\\b");
    private static final Pattern DOMAIN = Pattern.compile(
            "(?i)\\b((?:[a-z0-9-]+\\s?(?:\\.|\\(dot\\)|\\[dot\\])\\s?)+(?:com|net|org|gg|io|me|mn|xyz|ru|uk|de|eu|us|co|pro|club|fun|online|site|top|pl|tk|ml|cc|me|ws))\\b(?::\\d{2,5})?");

    private record State(Deque<Long> times, String last, long lastAt) {
    }

    private final Settings settings;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public ChatGuard(Settings settings) {
        this.settings = settings;
    }

    public Result check(UUID player, String message, long now) {
        String text = message.strip();
        State st = states.computeIfAbsent(player, k -> new State(new ArrayDeque<>(), "", 0));
        synchronized (st) {
            Deque<Long> times = st.times();
            while (!times.isEmpty() && now - times.peekFirst() > settings.windowMillis()) times.pollFirst();
            if (times.size() >= settings.maxMessages()) return new Result(Verdict.TOO_FAST, text);
            String norm = normalise(text);
            if (!norm.isEmpty() && norm.equals(st.last()) && now - st.lastAt() < settings.repeatMillis()) {
                return new Result(Verdict.REPEAT, text);
            }
            if (settings.blockLinks() && advertises(text)) return new Result(Verdict.ADVERT, text);
            times.addLast(now);
            states.put(player, new State(times, norm, now));
        }
        return new Result(Verdict.ALLOW, shouting(text) ? text.toLowerCase(Locale.ROOT) : text);
    }

    public void forget(UUID player) {
        states.remove(player);
    }

    private static String normalise(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    boolean advertises(String text) {
        if (IPV4.matcher(text).find()) return true;
        Matcher m = DOMAIN.matcher(text);
        while (m.find()) {
            String host = m.group(1).toLowerCase(Locale.ROOT).replaceAll("\\s|\\(dot\\)|\\[dot\\]", ".").replaceAll("\\.+", ".");
            boolean allowed = false;
            for (String ok : settings.allowedDomains()) {
                String d = ok.toLowerCase(Locale.ROOT);
                if (host.equals(d) || host.endsWith("." + d)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) return true;
        }
        return false;
    }

    boolean shouting(String text) {
        int letters = 0, upper = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
        }
        return letters >= 8 && upper > settings.capsLimit() * letters;
    }
}
