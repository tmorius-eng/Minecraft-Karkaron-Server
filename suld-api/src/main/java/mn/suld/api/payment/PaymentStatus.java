package mn.suld.api.payment;

/** What a provider says about a payment, mapped from its own words (e.g. {@code BonumPaymentResult#status}). */
public enum PaymentStatus {
    /** Not decided yet (or an event that decides nothing). */
    PENDING,
    /** The money was taken: the intent is owed. */
    PAID,
    /** The payment was refused or cancelled. */
    FAILED,
    /** The invoice ran out before anyone paid. */
    EXPIRED
}
