package mn.suld.api.payment.bonum;

import java.util.Locale;
import java.util.Map;

/**
 * A Bonum access token ({@code {tokenType, accessToken, expiresIn, refreshToken, refreshExpiresIn, unit}}), with the
 * times it runs out at. It is renewed {@link #EARLY_MS} before then, so no call is made with a token about to lapse.
 * Never logged.
 */
public record BonumToken(String accessToken, String refreshToken, long expiresAt, long refreshExpiresAt) {

    public static final long EARLY_MS = 30_000;

    public boolean usable(long now) {
        return accessToken != null && !accessToken.isBlank() && now < expiresAt - EARLY_MS;
    }

    public boolean refreshable(long now) {
        return refreshToken != null && !refreshToken.isBlank() && now < refreshExpiresAt - EARLY_MS;
    }

    /** The token in a reply received at {@code now}; {@code unit} names the unit of the two lifetimes (seconds if absent). */
    public static BonumToken parse(String json, long now) {
        Map<String, Object> m = BonumApi.fields(json, "accessToken");
        String access = BonumApi.text(m, "accessToken");
        if (access == null || access.isBlank()) throw new IllegalArgumentException("no accessToken in the reply");
        long unit = unitMs(BonumApi.text(m, "unit"));
        long life = Math.max(0, BonumApi.number(m, "expiresIn")) * unit;
        long refreshLife = Math.max(0, BonumApi.number(m, "refreshExpiresIn")) * unit;
        return new BonumToken(access, BonumApi.text(m, "refreshToken"), now + life, now + refreshLife);
    }

    static long unitMs(String unit) {
        String u = unit == null ? "" : unit.trim().toUpperCase(Locale.ROOT);
        if (u.startsWith("MILLI") || u.equals("MS")) return 1;
        if (u.startsWith("MIN")) return 60_000;
        if (u.startsWith("HOUR")) return 3_600_000;
        return 1_000;
    }

    @Override
    public String toString() {
        return "BonumToken[expiresAt=" + expiresAt + ", refreshExpiresAt=" + refreshExpiresAt + "]"; // never the tokens
    }
}
