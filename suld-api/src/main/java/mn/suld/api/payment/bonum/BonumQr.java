package mn.suld.api.payment.bonum;

import mn.suld.api.json.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bonum's answer to a QR request ({@code {invoiceId, qrCode, qrImage, links:[{name, description, logo, link, …}]}}):
 * the text to draw as a QR code (the game draws it itself, {@code mn.suld.api.payment.qr.QrCode}), the same code as a
 * base64 PNG, and the bank apps' deep links (shown as chat links).
 */
public record BonumQr(String invoiceId, String qrCode, String qrImage, List<Link> links) {

    /** A bank app's deep link. Only {@code https} and app-scheme links are kept; {@code javascript:} and the like never. */
    public record Link(String name, String link) {
    }

    public BonumQr {
        links = List.copyOf(links);
    }

    public static BonumQr parse(String json) {
        Map<String, Object> m = BonumApi.fields(json, "invoiceId");
        String invoice = BonumApi.text(m, "invoiceId");
        String code = BonumApi.text(m, "qrCode");
        String image = BonumApi.text(m, "qrImage");
        if ((code == null || code.isBlank()) && (image == null || image.isBlank())) {
            throw new IllegalArgumentException("no qrCode or qrImage in the reply");
        }
        List<Link> links = new ArrayList<>();
        if (m.get("links") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> raw)) continue;
                Map<String, Object> l = Json.object(raw);
                String name = BonumApi.text(l, "name"), link = BonumApi.text(l, "link");
                if (name == null || name.isBlank() || !safe(link)) continue;
                links.add(new Link(name.length() > 40 ? name.substring(0, 40) : name, link));
            }
        }
        return new BonumQr(invoice, code, image, links);
    }

    /** A link a chat click may open: http(s) or an app's own scheme, never script or data URLs, at most 1 000 chars. */
    static boolean safe(String link) {
        if (link == null || link.isBlank() || link.length() > 1000) return false;
        int colon = link.indexOf(':');
        if (colon <= 0) return false;
        String scheme = link.substring(0, colon).toLowerCase(java.util.Locale.ROOT);
        if (scheme.equals("javascript") || scheme.equals("data") || scheme.equals("file") || scheme.equals("vbscript")) return false;
        for (int k = 0; k < scheme.length(); k++) {
            char c = scheme.charAt(k);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '+' || c == '-' || c == '.')) return false;
        }
        for (int k = 0; k < link.length(); k++) if (link.charAt(k) < 0x20) return false;
        return true;
    }
}
