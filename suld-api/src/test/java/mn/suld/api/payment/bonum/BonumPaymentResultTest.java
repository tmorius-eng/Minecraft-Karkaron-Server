package mn.suld.api.payment.bonum;

import mn.suld.api.payment.PaymentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BonumPaymentResultTest {

    static BonumPaymentResult event(String type, String top, String body, String invoice) {
        return new BonumPaymentResult(type, top, "intent-1", "inv-1", 5000, "MNT", body, invoice, 1L);
    }

    @Test
    void paidOnlyWhenTheInvoiceSaysSo() {
        assertEquals(PaymentStatus.PAID, event("PAYMENT", "SUCCESS", "SUCCESS", "PAID").paymentStatus());
        assertEquals(PaymentStatus.PENDING, event("PAYMENT", "SUCCESS", "SUCCESS", "PENDING").paymentStatus(),
                "a delivered event about an unpaid invoice is not a payment");
        assertEquals(PaymentStatus.PAID, event("PAYMENT", "SUCCESS", "PAID", null).paymentStatus(), "no invoice status: the transaction's");
        assertEquals(PaymentStatus.PENDING, event("PAYMENT", "SUCCESS", null, null).paymentStatus(), "a top-level success alone is not enough");
    }

    @Test
    void failuresExpiriesAndOtherEvents() {
        assertEquals(PaymentStatus.FAILED, event("PAYMENT", "FAILED", null, null).paymentStatus());
        assertEquals(PaymentStatus.FAILED, event("payment", "SUCCESS", "DECLINED", "PENDING").paymentStatus());
        assertEquals(PaymentStatus.EXPIRED, event("PAYMENT", "SUCCESS", null, "expired").paymentStatus());
        assertEquals(PaymentStatus.PENDING, event("CARD-TOKEN", "SUCCESS", "PAID", "PAID").paymentStatus(), "not a purchase");
    }
}
