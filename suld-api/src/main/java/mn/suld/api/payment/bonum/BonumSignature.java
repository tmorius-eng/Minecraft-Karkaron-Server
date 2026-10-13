package mn.suld.api.payment.bonum;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * The signature of a Bonum webhook (docs/PAYMENT_ARCHITECTURE.md §Bonum): header {@code x-checksum-v2} carries
 * {@code hex(HMAC-SHA256(body, merchant checksum key))}. Bonum signs the JSON body as sent; some of its senders sign
 * the same JSON without insignificant whitespace, so {@link #verify} accepts either form of the very bytes received
 * (never a re-serialised object). The comparison is constant-time. Pure Java (JCA), no Bukkit; the key is never logged.
 */
public final class BonumSignature {

    /** The header Bonum signs its webhooks with. */
    public static final String HEADER = "x-checksum-v2";

    private BonumSignature() {
    }

    /** Lower-case hex HMAC-SHA256 of {@code body} under {@code key}. */
    public static String hmacSha256Hex(byte[] body, byte[] key) {
        if (key == null || key.length == 0) throw new IllegalArgumentException("empty checksum key");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /**
     * True when {@code header} is the signature of {@code rawBody} (as received, or with its insignificant whitespace
     * removed) under {@code key}. A missing or malformed header, or an empty key, is false.
     */
    public static boolean verify(byte[] rawBody, String header, byte[] key) {
        if (rawBody == null || header == null || key == null || key.length == 0) return false;
        byte[] given;
        try {
            given = HexFormat.of().parseHex(header.trim().toLowerCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (given.length != 32) return false;
        if (MessageDigest.isEqual(given, HexFormat.of().parseHex(hmacSha256Hex(rawBody, key)))) return true;
        byte[] compact = compactJson(rawBody);
        return compact.length != rawBody.length
                && MessageDigest.isEqual(given, HexFormat.of().parseHex(hmacSha256Hex(compact, key)));
    }

    /** The key as bytes (UTF-8), for a key read from the environment. */
    public static byte[] key(String secret) {
        return secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code json} with the whitespace outside strings removed (the bytes inside strings, escapes included, unchanged).
     * Not a parser: an invalid document comes back compacted the same way, and fails the signature.
     */
    static byte[] compactJson(byte[] json) {
        byte[] out = new byte[json.length];
        int n = 0;
        boolean inString = false, escaped = false;
        for (byte b : json) {
            if (inString) {
                out[n++] = b;
                if (escaped) escaped = false;
                else if (b == '\\') escaped = true;
                else if (b == '"') inString = false;
            } else if (b == '"') {
                inString = true;
                out[n++] = b;
            } else if (b != ' ' && b != '\n' && b != '\r' && b != '\t') {
                out[n++] = b;
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }
}
