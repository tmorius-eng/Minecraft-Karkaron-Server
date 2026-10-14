package mn.suld.api.payment;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StorePackageTest {

    private static Map<String, Object> pack(Object credits, Object price, Object amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("credits", credits);
        if (price != null) m.put("price", price);
        if (amount != null) m.put("amount", amount);
        return m;
    }

    @Test
    void theShippedPackagesChargeTheirPrice() {
        List<StorePackage> ps = StorePackage.parse(List.of(pack(100, "₮5,000", null), pack(250, "₮11,000", null),
                pack(600, "₮25,000", null), pack(1300, "₮50,000", null), pack(2800, "₮100,000", null)));
        assertEquals(5, ps.size());
        assertEquals(5_000, ps.get(0).amountMnt());
        assertEquals(11_000, ps.get(1).amountMnt());
        assertEquals(100_000, ps.get(4).amountMnt());
        assertEquals("CREDITS_250", ps.get(1).product());
        assertEquals(1, ps.get(1).index());
        assertEquals("₮11,000", ps.get(1).price());
    }

    @Test
    void anAmountOverridesThePriceText() {
        StorePackage p = StorePackage.parse(List.of(pack(100, "₮5,000 (хямдрал)", 4_500))).get(0);
        assertEquals(4_500, p.amountMnt());
        assertEquals("₮5,000 (хямдрал)", p.price());
        assertEquals("4,500 төгрөг", StorePackage.parse(List.of(pack(100, null, 4_500))).get(0).price());
    }

    @Test
    void aFractionIsNeverCharged() {
        assertEquals(5_000, StorePackage.digits("₮5,000.50"));
        assertEquals(0, StorePackage.digits("үнэгүй"));
        assertEquals(0, StorePackage.digits(null));
    }

    @Test
    void aBrokenPackageClosesTheStore() {
        assertThrows(IllegalArgumentException.class, () -> StorePackage.parse(List.of(pack(0, "₮5,000", null))));
        assertThrows(IllegalArgumentException.class, () -> StorePackage.parse(List.of(pack(100, "тун удахгүй", null))));
        assertThrows(IllegalArgumentException.class, () -> StorePackage.parse(List.of(pack(100, "₮5,000", null), pack(100, "₮6,000", null))));
    }
}
