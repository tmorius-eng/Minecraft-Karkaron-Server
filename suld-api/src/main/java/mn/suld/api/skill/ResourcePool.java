package mn.suld.api.skill;

/**
 * A class resource (Хил, Төвлөрөл, Сүнс, Дөл, Хурд): spent by spells, regenerated over time, and for the warrior
 * also gained by hitting. Pure and synchronized (the HUD reads it).
 */
public final class ResourcePool {

    private final int max;
    private double value;

    public ResourcePool(int max) {
        this.max = Math.max(1, max);
        this.value = this.max;
    }

    public synchronized int max() { return max; }

    public synchronized int value() { return (int) Math.floor(value); }

    public synchronized double fraction() { return value / max; }

    /** Spend {@code cost} if available; false (and nothing spent) otherwise. */
    public synchronized boolean spend(int cost) {
        if (cost < 0 || value < cost) return false;
        value -= cost;
        return true;
    }

    public synchronized void gain(double amount) {
        if (amount <= 0) return;
        value = Math.min(max, value + amount);
    }
}
