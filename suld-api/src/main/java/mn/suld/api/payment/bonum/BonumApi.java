package mn.suld.api.payment.bonum;

import mn.suld.api.json.Json;

import java.util.Map;

/**
 * Bonum's merchant e-commerce API as SÜLD calls it (docs/PAYMENT_ARCHITECTURE.md §Bonum). The paths and fields follow
 * the open-source Bonum SDK; the official merchant documentation could not be reached from the build environment, so
 * every name here is <b>to be confirmed against the merchant's sandbox</b> before the gateway is switched on.
 *
 * <ul>
 *   <li>{@link #TOKEN}: {@code GET}, headers {@code Authorization: AppSecret <app secret>} and {@code X-TERMINAL-ID};
 *   answers {@link BonumToken}.</li>
 *   <li>{@link #REFRESH}: {@code GET}, {@code Authorization: Bearer <refresh token>}.</li>
 *   <li>{@link #QR}: {@code POST {amount, transactionId, expiresIn}}; answers {@link BonumQr}.</li>
 *   <li>{@link #INVOICE}: {@code POST {amount, callback, transactionId, expiresIn}}; answers {@link BonumInvoiceReply}
 *   (a hosted payment page).</li>
 *   <li>The webhook: a JSON {@code POST} signed in {@link BonumSignature#HEADER} ({@link BonumWebhook}).</li>
 * </ul>
 */
public final class BonumApi {

    public static final String SANDBOX = "https://testapi.bonum.mn";
    public static final String PRODUCTION = "https://apis.bonum.mn";

    public static final String TOKEN = "/bonum-gateway/ecommerce/auth/create";
    public static final String REFRESH = "/bonum-gateway/ecommerce/auth/refresh";
    public static final String INVOICE = "/bonum-gateway/ecommerce/invoices";
    public static final String QR = "/mpay-service/merchant/transaction/qr/create";

    private BonumApi() {
    }

    /**
     * The object holding a reply's fields: the reply itself, or the object under {@code data}, {@code body} or
     * {@code result} when the reply wraps it (and the field asked for is not at the top).
     */
    static Map<String, Object> fields(String json, String probe) {
        Map<String, Object> top = Json.object(Json.parse(json));
        if (top.containsKey(probe)) return top;
        for (String wrap : new String[]{"data", "body", "result"}) {
            if (top.get(wrap) instanceof Map<?, ?> inner && inner.containsKey(probe)) return Json.object(inner);
        }
        return top;
    }

    static String text(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    static long number(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number n) return Math.round(n.doubleValue());
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Math.round(Double.parseDouble(s.trim()));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
