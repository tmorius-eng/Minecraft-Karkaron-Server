package mn.suld.api.payment.bonum;

import java.util.Map;

/** Bonum's answer to a hosted-invoice request: the invoice id and the payment page ({@code followUpLink}). */
public record BonumInvoiceReply(String invoiceId, String followUpLink) {

    public static BonumInvoiceReply parse(String json) {
        Map<String, Object> m = BonumApi.fields(json, "invoiceId");
        String link = BonumApi.text(m, "followUpLink");
        if (link == null || !(link.startsWith("https://") || link.startsWith("http://"))) {
            throw new IllegalArgumentException("no followUpLink in the reply");
        }
        return new BonumInvoiceReply(BonumApi.text(m, "invoiceId"), link);
    }
}
