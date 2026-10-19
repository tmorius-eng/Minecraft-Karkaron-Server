package mn.suld.api.shop;

/**
 * One card of a shop (content/shops.json): a vanilla {@code material} with its own Mongolian {@code name}, or a SÜLD
 * {@code item} definition id (named by the catalog); {@code amount} per purchase, {@code price} in coins, and the
 * level from which the card unlocks ({@code minLevel}, 1 = always).
 */
public record ShopEntry(String material, String itemId, int amount, long price, String name, int minLevel) {

    public ShopEntry {
        boolean mat = material != null && !material.isBlank(), item = itemId != null && !itemId.isBlank();
        if (mat == item) throw new IllegalArgumentException("a shop entry names exactly one of material or item");
        if (amount < 1 || amount > 64) throw new IllegalArgumentException("amount 1..64: " + amount);
        if (price < 0) throw new IllegalArgumentException("price >= 0: " + price);
        if (mat && (name == null || name.isBlank())) throw new IllegalArgumentException(material + ": a material entry needs its Mongolian name");
        if (minLevel < 1) minLevel = 1;
        name = name == null ? "" : name;
    }

    public boolean isItem() {
        return itemId != null && !itemId.isBlank();
    }
}
