package mn.suld.api.payment.bonum;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vectors from Python's {@code hmac.new(key, body, hashlib.sha256).hexdigest()}. */
class BonumSignatureTest {

    static final byte[] KEY = "test-checksum-key".getBytes(StandardCharsets.UTF_8);
    static final byte[] RAW = ("{ \"type\": \"PAYMENT\",\n  \"status\": \"SUCCESS\", \"body\": { \"transactionId\": \"suld-1a2b\", "
            + "\"amount\": 5000, \"note\": \"a b\\\"c\" } }").getBytes(StandardCharsets.UTF_8);
    static final byte[] COMPACT = "{\"type\":\"PAYMENT\",\"status\":\"SUCCESS\",\"body\":{\"transactionId\":\"suld-1a2b\",\"amount\":5000,\"note\":\"a b\\\"c\"}}"
            .getBytes(StandardCharsets.UTF_8);
    static final String RAW_SIG = "3ed3546bfb09a2736414edc87633b847e3a3dad968e7e4513abe745c70b7ec95";
    static final String COMPACT_SIG = "98ad7b06149fcca79acd4b84db558f075e2267305987b9f468d609e50f46e7fc";

    @Test
    void theHexIsPythonsHmac() {
        assertEquals(RAW_SIG, BonumSignature.hmacSha256Hex(RAW, KEY));
        assertEquals(COMPACT_SIG, BonumSignature.hmacSha256Hex(COMPACT, KEY));
    }

    @Test
    void theBodyAsSentOrWithoutWhitespaceIsAccepted() {
        assertTrue(BonumSignature.verify(RAW, RAW_SIG, KEY), "the raw body");
        assertTrue(BonumSignature.verify(RAW, COMPACT_SIG, KEY), "the same JSON without insignificant whitespace");
        assertTrue(BonumSignature.verify(RAW, RAW_SIG.toUpperCase(), KEY), "hex case does not matter");
        assertArrayEquals(COMPACT, BonumSignature.compactJson(RAW), "spaces inside strings are kept");
    }

    @Test
    void anythingElseIsRefused() {
        byte[] tampered = new String(RAW, StandardCharsets.UTF_8).replace("5000", "50000").getBytes(StandardCharsets.UTF_8);
        assertFalse(BonumSignature.verify(tampered, RAW_SIG, KEY), "a changed amount");
        assertFalse(BonumSignature.verify(RAW, RAW_SIG, "another-key".getBytes(StandardCharsets.UTF_8)), "another key");
        assertFalse(BonumSignature.verify(RAW, null, KEY), "no header");
        assertFalse(BonumSignature.verify(RAW, "not-hex", KEY), "a malformed header");
        assertFalse(BonumSignature.verify(RAW, RAW_SIG.substring(0, 32), KEY), "a short header");
        assertFalse(BonumSignature.verify(RAW, RAW_SIG, new byte[0]), "no key configured");
        assertThrows(IllegalArgumentException.class, () -> BonumSignature.hmacSha256Hex(RAW, new byte[0]));
    }
}
