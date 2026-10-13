package mn.suld.api.web;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A token bucket per client (directive 2026-10-13 Phase 8: «rate limit by client IP using token bucket»): each client
 * may burst {@code capacity} requests and then {@code perSecond} a second. The client key is only held in memory, for
 * as long as the client is active ({@link #evictIdle}); it is never logged or returned. Thread-safe (the web server's
 * worker threads share it), clock-injected, pure Java.
 */
public final class RateLimiter {

    private final double capacity;
    private final double perSecond;
    private final int maxClients;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final class Bucket {
        double tokens;
        long last;

        Bucket(double tokens, long last) {
            this.tokens = tokens;
            this.last = last;
        }
    }

    /**
     * @param capacity   the burst a client may make
     * @param perSecond  how fast its bucket refills
     * @param maxClients the most clients remembered at once (beyond it, idle ones are dropped first; a flood of new
     *                   keys can never grow the map without bound)
     */
    public RateLimiter(double capacity, double perSecond, int maxClients) {
        if (capacity < 1 || perSecond <= 0 || maxClients < 1) throw new IllegalArgumentException("bad rate limit");
        this.capacity = capacity;
        this.perSecond = perSecond;
        this.maxClients = maxClients;
    }

    /** True when {@code client} may make a request at {@code now} (epoch ms); takes one token. */
    public boolean allow(String client, long now) {
        if (client == null) client = "";
        if (buckets.size() >= maxClients && !buckets.containsKey(client)) {
            evictIdle(now);
            if (buckets.size() >= maxClients) return false; // every remembered client is busy: new ones wait
        }
        Bucket b = buckets.computeIfAbsent(client, k -> new Bucket(capacity, now));
        synchronized (b) {
            double elapsed = Math.max(0, now - b.last) / 1000.0;
            b.tokens = Math.min(capacity, b.tokens + elapsed * perSecond);
            b.last = Math.max(b.last, now);
            if (b.tokens < 1) return false;
            b.tokens -= 1;
            return true;
        }
    }

    /** Forgets the clients whose bucket is full again (they have been idle long enough). */
    public void evictIdle(long now) {
        for (Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator(); it.hasNext(); ) {
            Bucket b = it.next().getValue();
            synchronized (b) {
                if (b.tokens + Math.max(0, now - b.last) / 1000.0 * perSecond >= capacity) it.remove();
            }
        }
    }

    /** Clients remembered right now. */
    public int clients() {
        return buckets.size();
    }
}
