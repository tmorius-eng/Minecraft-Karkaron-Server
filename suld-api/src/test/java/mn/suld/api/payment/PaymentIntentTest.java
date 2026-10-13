package mn.suld.api.payment;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentIntentTest {

    static final UUID P = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    static PaymentIntent pending() {
        return PaymentIntent.create(P, 250, 11_000, "bonum", 1_000_000, 15 * 60_000);
    }

    @Test
    void aPaidEventOfTheExactAmountConfirmsOnce() {
        PaymentIntent i = pending();
        assertEquals(PaymentIntent.Verdict.CONFIRM, i.judge(PaymentStatus.PAID, 11_000, "MNT"));
        PaymentIntent c = i.confirmed("bonum-txn-1", 1_100_000);
        assertEquals(PaymentIntent.State.CONFIRMED, c.state());
        assertTrue(c.owed(), "confirmed, not yet fulfilled");
        assertEquals(PaymentIntent.Verdict.DUPLICATE, c.judge(PaymentStatus.PAID, 11_000, "MNT"), "a replay is a no-op");
        PaymentIntent f = c.fulfilled(1_100_500);
        assertFalse(f.owed());
        assertEquals(PaymentIntent.Verdict.DUPLICATE, f.judge(PaymentStatus.PAID, 11_000, "MNT"));
    }

    @Test
    void theAmountAndTheCurrencyMustMatchExactly() {
        PaymentIntent i = pending();
        assertEquals(PaymentIntent.Verdict.MISMATCH, i.judge(PaymentStatus.PAID, 1_100, "MNT"));
        assertEquals(PaymentIntent.Verdict.MISMATCH, i.judge(PaymentStatus.PAID, 11_001, "MNT"));
        assertEquals(PaymentIntent.Verdict.MISMATCH, i.judge(PaymentStatus.PAID, 11_000, "USD"));
    }

    @Test
    void aLatePaymentIsStillOwedAndOtherEventsOnlyMoveAPendingIntent() {
        PaymentIntent i = pending();
        assertTrue(i.expired(1_000_000 + 15 * 60_000));
        PaymentIntent expired = i.closed(PaymentIntent.State.EXPIRED);
        assertEquals(PaymentIntent.Verdict.CONFIRM, expired.judge(PaymentStatus.PAID, 11_000, "MNT"), "the money was taken");
        assertEquals(PaymentIntent.Verdict.IGNORE, expired.judge(PaymentStatus.FAILED, 11_000, "MNT"));
        assertEquals(PaymentIntent.Verdict.FAIL, i.judge(PaymentStatus.FAILED, 0, "MNT"));
        assertEquals(PaymentIntent.Verdict.EXPIRE, i.judge(PaymentStatus.EXPIRED, 0, "MNT"));
        assertEquals(PaymentIntent.Verdict.IGNORE, i.judge(PaymentStatus.PENDING, 0, "MNT"));
        assertThrows(IllegalStateException.class, () -> i.fulfilled(1), "only a confirmed intent is fulfilled");
        assertThrows(IllegalArgumentException.class, () -> i.closed(PaymentIntent.State.CONFIRMED));
        assertThrows(IllegalArgumentException.class, () -> PaymentIntent.create(P, 0, 5000, "bonum", 0, 60_000));
    }
}
