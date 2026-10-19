package mn.suld.api.shop;

import java.util.List;

/** A merchant's stock (content/shops.json): id {@code shop.*}, a Mongolian name and the cards in order. */
public record ShopDefinition(String id, String name, List<ShopEntry> stock) {

    public ShopDefinition {
        if (id == null || !id.matches("shop\\.[a-z0-9_]{2,40}")) throw new IllegalArgumentException("a shop id looks like shop.general: " + id);
        if (name == null || name.isBlank()) throw new IllegalArgumentException(id + ": a shop needs a name");
        stock = List.copyOf(stock);
        if (stock.isEmpty()) throw new IllegalArgumentException(id + ": a shop needs stock");
    }
}
