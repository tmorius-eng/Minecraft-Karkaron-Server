package mn.suld.api.payment.bonum;

import mn.suld.api.payment.PaymentIntent;
import mn.suld.api.payment.PaymentLedger;
import mn.suld.api.payment.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BonumMessagesTest {

    @Test
    void aTokenIsRenewedHalfAMinuteBeforeItLapses() {
        BonumToken t = BonumToken.parse("{\"tokenType\":\"Bearer\",\"accessToken\":\"a.b.c\",\"expiresIn\":600,"
                + "\"refreshToken\":\"r.s.t\",\"refreshExpiresIn\":3600,\"unit\":\"SECONDS\"}", 1_000_000);
        assertEquals(1_000_000 + 600_000, t.expiresAt());
        assertTrue(t.usable(1_000_000 + 569_999));
        assertFalse(t.usable(1_000_000 + 570_000));
        assertTrue(t.refreshable(1_000_000 + 3_000_000));
        assertFalse(t.refreshable(1_000_000 + 3_570_000));
        assertFalse(t.toString().contains("a.b.c"), "a token is never printed");
        assertFalse(t.toString().contains("r.s.t"));
    }

    @Test
    void aTokenInMillisecondsOrWrappedInDataIsRead() {
        BonumToken ms = BonumToken.parse("{\"accessToken\":\"x\",\"expiresIn\":60000,\"unit\":\"MILLISECOND\"}", 0);
        assertEquals(60_000, ms.expiresAt());
        assertFalse(ms.refreshable(0), "no refresh token");
        BonumToken wrapped = BonumToken.parse("{\"success\":true,\"data\":{\"accessToken\":\"y\",\"expiresIn\":\"120\"}}", 0);
        assertEquals("y", wrapped.accessToken());
        assertEquals(120_000, wrapped.expiresAt());
        assertThrows(IllegalArgumentException.class, () -> BonumToken.parse("{\"error\":\"unauthorized\"}", 0));
    }

    @Test
    void anInvoiceCarriesOnlyTheReferenceTheAmountAndItsTime() {
        PaymentIntent i = PaymentIntent.create(UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e"), 250, 11_000, "bonum", 0, 15 * 60_000L);
        BonumInvoice inv = BonumInvoice.of(i, "https://pay.suld.mn/api/bonum/callback", 0);
        String ref = PaymentLedger.merchantReference(i);
        assertEquals(32, ref.length());
        assertEquals("{\"amount\":11000,\"transactionId\":\"" + ref + "\",\"expiresIn\":900}", inv.qrJson());
        assertEquals("{\"amount\":11000,\"callback\":\"https://pay.suld.mn/api/bonum/callback\",\"transactionId\":\"" + ref
                + "\",\"expiresIn\":900}", inv.invoiceJson());
        assertFalse(inv.qrJson().contains("0f8fad5b-d9cb"), "not the player, only the intent");
        assertEquals(60, BonumInvoice.of(i, null, i.expiresAt()).expiresInSeconds(), "at least a minute");
    }

    @Test
    void aQrReplyKeepsTheCodeAndOnlySafeBankLinks() {
        BonumQr qr = BonumQr.parse("{\"invoiceId\":\"INV-77\",\"qrCode\":\"000201010212\",\"qrImage\":\"iVBORw0KGgo=\","
                + "\"links\":[{\"name\":\"Khan bank\",\"description\":\"Хаан банк\",\"logo\":\"https://x/l.png\",\"link\":\"khanbank://q?qPay_QRcode=000201\"},"
                + "{\"name\":\"evil\",\"link\":\"javascript:alert(1)\"},{\"name\":\"web\",\"link\":\"https://bank.example/pay\"},{\"link\":\"https://no-name\"}]}");
        assertEquals("INV-77", qr.invoiceId());
        assertEquals("000201010212", qr.qrCode());
        assertEquals(2, qr.links().size());
        assertEquals("Khan bank", qr.links().get(0).name());
        assertEquals("https://bank.example/pay", qr.links().get(1).link());
        assertFalse(BonumQr.safe("data:text/html,x"));
        assertFalse(BonumQr.safe("https://x/\nSet-Cookie"));
        assertThrows(IllegalArgumentException.class, () -> BonumQr.parse("{\"invoiceId\":\"INV-1\"}"));
    }

    @Test
    void aHostedInvoiceNeedsItsPage() {
        BonumInvoiceReply r = BonumInvoiceReply.parse("{\"invoiceId\":\"INV-9\",\"followUpLink\":\"https://ecommerce.bonum.mn/pay/INV-9\"}");
        assertEquals("https://ecommerce.bonum.mn/pay/INV-9", r.followUpLink());
        assertThrows(IllegalArgumentException.class, () -> BonumInvoiceReply.parse("{\"invoiceId\":\"INV-9\",\"followUpLink\":\"javascript:x\"}"));
    }

    @Test
    void aSignedWebhookIsReadIntoAPaidResult() {
        byte[] key = "dev-checksum".getBytes(StandardCharsets.UTF_8);
        byte[] body = ("{\"type\":\"PAYMENT\",\"status\":\"SUCCESS\",\"message\":\"ok\",\"body\":{\"transactionId\":\"0f8fad5bd9cb469fa16570867728950e\","
                + "\"invoiceId\":\"INV-77\",\"amount\":\"11000\",\"currency\":\"MNT\",\"completedAt\":\"2026-10-13T12:00:00Z\","
                + "\"status\":\"PAID\",\"invoiceStatus\":\"PAID\"}}").getBytes(StandardCharsets.UTF_8);
        assertTrue(BonumSignature.verify(body, BonumSignature.hmacSha256Hex(body, key), key));
        BonumPaymentResult r = BonumWebhook.parse(body);
        assertEquals(PaymentStatus.PAID, r.paymentStatus());
        assertEquals(11_000, r.amount());
        assertEquals("INV-77", r.invoiceId());
        assertEquals(1_791_892_800_000L, r.completedAt());
        assertEquals(UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e"), PaymentLedger.reference(r.transactionId()).orElseThrow());
    }

    @Test
    void aWebhookWithoutABodyObjectOrATransactionIsHandled() {
        BonumPaymentResult flat = BonumWebhook.parse("{\"type\":\"PAYMENT\",\"transactionId\":\"t1\",\"amount\":5000,\"invoiceStatus\":\"EXPIRED\",\"completedAt\":1791892800}"
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(PaymentStatus.EXPIRED, flat.paymentStatus());
        assertEquals(1_791_892_800_000L, flat.completedAt(), "seconds are read as seconds");
        assertNull(BonumWebhook.time("yesterday"));
        assertThrows(IllegalArgumentException.class, () -> BonumWebhook.parse("{\"type\":\"PAYMENT\",\"body\":{}}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> BonumWebhook.parse("not json".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> BonumWebhook.parse(new byte[BonumWebhook.MAX_BODY + 1]));
        BonumPaymentResult token = BonumWebhook.parse("{\"type\":\"CARD-TOKEN\",\"body\":{\"transactionId\":\"t2\",\"status\":\"SUCCESS\"}}"
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(PaymentStatus.PENDING, token.paymentStatus(), "a card token is not a purchase");
    }
}
