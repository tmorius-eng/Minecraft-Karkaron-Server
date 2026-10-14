package mn.suld.api.payment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A credit package of the store ({@code store.packages}): {@code credits} for {@code amountMnt} tögrög. The price text
 * shown is the config's ({@code price: '₮5,000'}); the amount charged is {@code amount}, or, when a package has none,
 * the digits of its price. Credits buy cosmetics only (zero pay-to-win).
 */
public record StorePackage(int index, long credits, long amountMnt, String price) {

    public StorePackage {
        if (credits <= 0) throw new IllegalArgumentException("package " + index + ": credits must be > 0");
        if (amountMnt <= 0) throw new IllegalArgumentException("package " + index + ": no amount (set amount: or a price with digits)");
        price = price == null || price.isBlank() ? String.format(java.util.Locale.ROOT, "%,d төгрөг", amountMnt) : price;
    }

    /** The product name an intent carries. */
    public String product() {
        return "CREDITS_" + credits;
    }

    /** The packages of the config list, in order; a broken entry is refused with its index (the store stays closed). */
    public static List<StorePackage> parse(List<? extends Map<?, ?>> entries) {
        List<StorePackage> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int i = 0;
        for (Map<?, ?> e : entries) {
            long credits = number(e.get("credits"));
            Object priceObj = e.get("price");
            String price = priceObj == null ? null : String.valueOf(priceObj);
            long amount = e.containsKey("amount") ? number(e.get("amount")) : digits(price);
            StorePackage p = new StorePackage(i++, credits, amount, price);
            if (!seen.add(credits)) throw new IllegalArgumentException("package " + p.index() + ": " + credits + " credits twice");
            out.add(p);
        }
        return List.copyOf(out);
    }

    private static long number(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) return digits(s);
        return 0;
    }

    /** The digits of {@code text} as a number ({@code "₮11,000"} → 11000); 0 for none. */
    static long digits(String text) {
        if (text == null) return 0;
        long v = 0;
        boolean any = false;
        for (int k = 0; k < text.length(); k++) {
            char c = text.charAt(k);
            if (c == '.' && any) break; // a fraction is never charged
            if (c < '0' || c > '9') continue;
            any = true;
            v = Math.multiplyExact(v, 10) + (c - '0');
        }
        return v;
    }
}
