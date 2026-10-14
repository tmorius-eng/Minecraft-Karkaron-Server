package mn.suld.plugin.payment;

import mn.suld.api.audit.AuditActions;
import mn.suld.api.audit.AuditEvent;
import mn.suld.api.payment.PaymentEvent;
import mn.suld.api.payment.PaymentIntent;
import mn.suld.api.payment.PaymentLedger;
import mn.suld.api.payment.StorePackage;
import mn.suld.api.payment.bonum.BonumInvoice;
import mn.suld.api.payment.bonum.BonumPaymentResult;
import mn.suld.api.payment.bonum.BonumQr;
import mn.suld.api.payment.bonum.BonumSignature;
import mn.suld.api.payment.qr.QrCode;
import mn.suld.api.persistence.PaymentRepository;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.payment.bonum.BonumClient;
import mn.suld.plugin.payment.bonum.BonumWebhookServer;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * The credit store paid through Bonum (docs/PAYMENT_ARCHITECTURE.md, directive Phase 7). Credits buy cosmetics only.
 *
 * <p>Off unless {@code payment.bonum.enabled} is set <i>and</i> the merchant's three secrets are in the environment
 * (their variable names are in the config; their values never are, and are never logged). With it off, {@code /buy}
 * shows the packages and the store link as before.
 *
 * <ol>
 *   <li>{@code /buy} → a package: an intent is stored (PENDING, {@code invoice-expiry-minutes}), Bonum is asked for its
 *   QR invoice, and the player gets the QR on a map in hand plus the bank apps' links in chat. Nothing is granted
 *   here: the player's word is never a payment.</li>
 *   <li>Bonum's signed webhook ({@link BonumWebhookServer}) → {@link PaymentLedger}: the intent is confirmed once, the
 *   credits granted once under its own transaction id, the player told (toast sound, chat), the map taken.</li>
 *   <li>Every minute (and at start) reconcile grants what a crash left owed and expires old intents.</li>
 * </ol>
 * The HTTP calls, the database and the webhook all run off the main thread; the player is only touched on it.
 */
public final class PaymentService {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final Plugin plugin;
    private final SuldServices services;
    private final PaymentRepository repo;
    private final PaymentLedger ledger;
    private final QrMaps maps;
    private final List<StorePackage> packages;
    private final boolean enabled;
    private final String off;
    private final int ttlMinutes;
    private final int openPerPlayer;
    private final String callback;
    private final ZoneId zone;
    private BonumClient client;
    private BonumWebhookServer webhook;
    private BonumWebhookServer.Settings hook;
    private byte[] checksumKey = new byte[0];
    /** Players with a purchase being set up (main thread): one at a time. */
    private final Set<UUID> busy = new HashSet<>();
    /** The last purchase asked for, per player (main thread): one every 20 s. */
    private final Map<UUID, Long> lastAsk = new ConcurrentHashMap<>();
    /** Intents whose QR is out → the player holding it (main thread). */
    private final Map<UUID, UUID> shown = new ConcurrentHashMap<>();

    public PaymentService(Plugin plugin, SuldServices services, PaymentRepository repo, ExecutorService http) {
        this.plugin = plugin;
        this.services = services;
        this.repo = repo;
        this.maps = new QrMaps(plugin);
        this.zone = services.zone();
        this.ledger = new PaymentLedger(repo, intent -> services.styles().grantPurchase(intent.player(), intent.credits(),
                PaymentLedger.txid(intent), intent.provider()), System::currentTimeMillis);
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("payment.bonum");
        List<StorePackage> parsed;
        String why = null;
        try {
            parsed = StorePackage.parse(plugin.getConfig().getMapList("store.packages"));
        } catch (IllegalArgumentException e) {
            parsed = List.of();
            why = "store.packages: " + e.getMessage();
        }
        this.packages = parsed;
        this.ttlMinutes = c == null ? 15 : Math.max(2, Math.min(60, c.getInt("invoice-expiry-minutes", 15)));
        this.openPerPlayer = c == null ? 2 : Math.max(1, c.getInt("open-intents-per-player", 2));
        this.callback = c == null ? "" : c.getString("callback-url", "");
        if (c == null || !c.getBoolean("enabled", false)) {
            why = why == null ? "payment.bonum.enabled is false" : why;
        } else if (why == null) {
            URI base = null;
            try {
                base = URI.create(c.getString("base-url", mn.suld.api.payment.bonum.BonumApi.SANDBOX).trim());
            } catch (IllegalArgumentException badUrl) {
                // a typo in the URL turns the store off; it never stops the plugin from enabling
            }
            String terminal = env(c.getString("terminal-id-env", "BONUM_TERMINAL_ID"));
            String secret = env(c.getString("app-secret-env", "BONUM_APP_SECRET"));
            String checksum = env(c.getString("checksum-key-env", "BONUM_CHECKSUM_KEY"));
            List<String> missing = new ArrayList<>();
            if (terminal == null) missing.add(c.getString("terminal-id-env", "BONUM_TERMINAL_ID"));
            if (secret == null) missing.add(c.getString("app-secret-env", "BONUM_APP_SECRET"));
            if (checksum == null) missing.add(c.getString("checksum-key-env", "BONUM_CHECKSUM_KEY"));
            if (!missing.isEmpty()) {
                why = "missing environment variables " + String.join(", ", missing);
            } else if (base == null || !BonumClient.safeBase(base)) {
                why = "base-url must be an https URL (plain http only to this machine)";
            } else if (packages.isEmpty()) {
                why = "no store.packages";
            } else {
                this.client = new BonumClient(base, terminal, secret, c.getString("language", "mn"), http);
                this.checksumKey = BonumSignature.key(checksum);
                ConfigurationSection w = c.getConfigurationSection("webhook");
                this.hook = new BonumWebhookServer.Settings(w == null ? "127.0.0.1" : w.getString("bind", "127.0.0.1"),
                        w == null ? 8085 : w.getInt("port", 8085), w == null ? null : w.getString("path"),
                        w == null ? null : w.getString("signature-header"),
                        w == null ? List.of("127.0.0.1") : w.getStringList("trusted-proxies"), w == null ? List.of() : w.getStringList("allowed-ips"));
            }
        }
        this.enabled = why == null;
        this.off = why;
    }

    private static String env(String name) {
        if (name == null || name.isBlank()) return null;
        String v = System.getenv(name.trim());
        return v == null || v.isBlank() ? null : v.trim();
    }

    public boolean enabled() {
        return enabled;
    }

    /** Why the gateway is off (null when on), for the log and staff. */
    public String offReason() {
        return off;
    }

    public List<StorePackage> packages() {
        return packages;
    }

    public void start() {
        if (!enabled) {
            plugin.getLogger().info("[payment] Bonum gateway off: " + off);
            return;
        }
        Bukkit.getPluginManager().registerEvents(maps, plugin);
        webhook = new BonumWebhookServer(hook, checksumKey, this::onProviderEvent, this::log, plugin.getLogger());
        try {
            webhook.start();
            plugin.getLogger().info("[payment] Bonum gateway on: webhook " + hook.bind() + ":" + webhook.port() + hook.path()
                    + ", " + packages.size() + " packages, intents open " + ttlMinutes + " min (unverified against the live Bonum API)");
        } catch (IOException e) {
            plugin.getLogger().warning("[payment] cannot listen on " + hook.bind() + ":" + hook.port() + ": " + e.getMessage()
                    + " — purchases still start, confirmations wait for the webhook");
            webhook = null;
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::reconcile, 20L * 5, 20L * 60);
    }

    public void stop() {
        if (webhook != null) webhook.stop();
        if (enabled) maps.shutdown();
    }

    /** The port the webhook listens on (tests), or -1. */
    public int webhookPort() {
        return webhook == null ? -1 : webhook.port();
    }

    // ------------------------------------------------------------------ the purchase

    /** A package clicked in {@code /buy} (main thread). */
    public void buy(Player p, StorePackage pkg) {
        p.closeInventory(); // every answer is a chat line: the window would hide it
        if (!enabled) {
            p.sendMessage(Messages.info("Төлбөрийн систем одоогоор нээгдээгүй байна."));
            return;
        }
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        if (busy.contains(id)) {
            p.sendMessage(Messages.info("Таны өмнөх төлбөр бэлтгэгдэж байна — түр хүлээнэ үү."));
            return;
        }
        Long last = lastAsk.get(id);
        if (last != null && now - last < 20_000) {
            p.sendMessage(Messages.info("Дахин оролдохын өмнө " + ((20_000 - (now - last)) / 1000 + 1) + " секунд хүлээнэ үү."));
            return;
        }
        busy.add(id);
        lastAsk.put(id, now);
        p.sendMessage(Messages.info("Bonum-аас нэхэмжлэх авч байна…"));
        long ttl = ttlMinutes * 60_000L;
        repo.open(id, now).thenCompose(open -> {
            if (open.size() >= openPerPlayer) return CompletableFuture.completedFuture((Offer) null);
            PaymentIntent intent = PaymentIntent.create(id, pkg.credits(), pkg.amountMnt(), "bonum", now, ttl);
            return repo.create(intent).thenCompose(v -> client.createQr(BonumInvoice.of(intent, callback, now))
                    .handle((qr, err) -> {
                        if (err != null) plugin.getLogger().warning("[payment] the Bonum QR request failed: " + root(err));
                        return err == null ? qr : null;
                    })
                    .thenCompose(qr -> {
                        if (qr == null) {
                            // the QR call failed: close the intent so it does not hold a slot (a payment for it would still count)
                            return repo.replace(intent.closed(PaymentIntent.State.FAILED), intent.version())
                                    .thenApply(ok -> new Offer(intent, null, true));
                        }
                        PaymentIntent invoiced = intent.withInvoice(qr.invoiceId());
                        return repo.replace(invoiced, intent.version()).thenApply(ok -> new Offer(ok ? invoiced : intent, qr, false));
                    }));
        }).whenComplete((offer, err) -> {
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> offered(p, id, pkg, offer, err));
        });
    }

    /** The purchase set up (or not): the player is told, and shown the QR (main thread). */
    private void offered(Player p, UUID id, StorePackage pkg, Offer offer, Throwable err) {
        {
            busy.remove(id);
            Player now2 = Bukkit.getPlayer(id);
            if (err != null) {
                plugin.getLogger().warning("[payment] an intent for " + id + " could not be stored: " + root(err));
                if (now2 != null) now2.sendMessage(Messages.error("Төлбөр эхлүүлж чадсангүй. Дараа дахин оролдоно уу."));
                return;
            }
            if (offer == null) {
                if (now2 != null) now2.sendMessage(Messages.info("Танд дуусаагүй " + openPerPlayer + " төлбөр байна. Тэдгээрийг төлөх эсвэл хугацаа нь дуусахыг хүлээнэ үү."));
                return;
            }
            services.audit().record(AuditEvent.of(p.getName(), AuditActions.PAYMENT_INTENT, id.toString(),
                    offer.intent().intentId() + " " + offer.intent().product() + " " + offer.intent().amountMinor() + " MNT" + (offer.failed() ? " (no QR)" : "")));
            if (offer.failed() || offer.qr() == null) {
                if (now2 != null) now2.sendMessage(Messages.error("Bonum хариу өгсөнгүй. Түр хүлээгээд дахин оролдоно уу."));
                return;
            }
            if (now2 == null) return; // left meanwhile: the intent simply expires
            show(now2, offer.intent(), offer.qr(), pkg);
        }
    }

    private record Offer(PaymentIntent intent, BonumQr qr, boolean failed) {
    }

    private void show(Player p, PaymentIntent intent, BonumQr qr, StorePackage pkg) {
        String until = CLOCK.format(Instant.ofEpochMilli(intent.expiresAt()).atZone(zone));
        boolean onMap = false;
        if (qr.qrCode() != null && !qr.qrCode().isBlank()) {
            try {
                QrCode code = QrCode.encode(qr.qrCode(), QrCode.Ecc.M);
                if (code.size() > mn.suld.api.payment.qr.QrRaster.MAP - 2 * mn.suld.api.payment.qr.QrRaster.QUIET) {
                    code = QrCode.encode(qr.qrCode(), QrCode.Ecc.L);
                }
                onMap = maps.give(p, intent.intentId(), code, List.of(
                        QrMaps.hint("Банкны аппаараа уншуулж төлнө үү."),
                        QrMaps.hint("Дүн: " + pkg.price() + " · " + pkg.credits() + " Сүлд Кредит"),
                        QrMaps.hint("Хүчинтэй: " + until + " хүртэл")),
                        Component.text("Bonum QR · " + pkg.credits() + " кредит", NamedTextColor.GOLD));
            } catch (IllegalArgumentException tooLong) {
                plugin.getLogger().warning("[payment] the QR text from Bonum does not fit a map (" + qr.qrCode().length() + " chars)");
            }
        }
        if (onMap) shown.put(intent.intentId(), p.getUniqueId());
        p.playSound(p.getLocation(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1f, 1f);
        p.sendMessage(Messages.accent("Төлбөр: ").append(Component.text(pkg.credits() + " Сүлд Кредит · " + pkg.price(), NamedTextColor.GOLD)));
        p.sendMessage(Messages.info(onMap ? "Гартаа байгаа QR газрын зургийг банкны аппаараа уншуулна уу (" + until + " хүртэл)."
                : "QR-ыг газрын зураг дээр гаргаж чадсангүй — доорх банкны холбоосоор төлнө үү (" + until + " хүртэл)."));
        if (!qr.links().isEmpty()) {
            Component line = Component.text("Банкны апп: ", NamedTextColor.GRAY);
            boolean first = true;
            for (BonumQr.Link l : qr.links()) {
                if (!first) line = line.append(Component.text(" · ", NamedTextColor.DARK_GRAY));
                first = false;
                line = line.append(Component.text("[" + l.name() + "]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(l.link())).hoverEvent(HoverEvent.showText(Component.text("Утсан дээрээ нээнэ"))));
            }
            p.sendMessage(line);
        }
        p.sendMessage(Messages.info("Кредит зөвхөн гоёлд зарцуулагдана. Төлбөр баталгаажмагц автоматаар орно."));
        long ticks = Math.max(20, (intent.expiresAt() - System.currentTimeMillis()) / 50);
        UUID intentId = intent.intentId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            UUID holder = shown.remove(intentId);
            if (holder != null) maps.close(intentId, Bukkit.getPlayer(holder)); // the code is no use any more
        }, ticks);
    }

    // ------------------------------------------------------------------ the provider's word

    /** A signed webhook (an HTTP thread): the ledger decides, the player is told on the main thread. */
    CompletableFuture<PaymentLedger.Result> onProviderEvent(BonumPaymentResult r) {
        return ledger.onEvent(r.transactionId(), r.paymentStatus(), r.amount(), r.currency(), r.invoiceId()).thenApply(res -> {
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> settled(res, "webhook"));
            return res;
        });
    }

    private void reconcile() {
        ledger.reconcile().whenComplete((results, err) -> {
            if (err != null) {
                plugin.getLogger().warning("[payment] reconcile failed (retried in a minute): " + root(err));
                return;
            }
            if (results.isEmpty() || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> results.forEach(res -> settled(res, "reconcile")));
        });
    }

    /** What a ledger result means for the player and staff (main thread). */
    private void settled(PaymentLedger.Result res, String via) {
        PaymentIntent i = res.intent();
        if (i == null) return;
        Player p = Bukkit.getPlayer(i.player());
        switch (res.outcome()) {
            case CONFIRMED -> {
                closeMap(i);
                if (!res.granted()) return; // granted by an earlier call: told then
                services.audit().record(AuditEvent.of("bonum", AuditActions.PAYMENT_CONFIRMED, i.player().toString(),
                        i.intentId() + " " + i.product() + " " + i.amountMinor() + " MNT txn " + i.providerTxnId() + " via " + via));
                plugin.getLogger().info("[payment] " + i.intentId() + " paid: +" + i.credits() + " credits to " + i.player() + " (" + via + ")");
                if (p != null) {
                    p.sendMessage(Messages.success("✔ Төлбөр баталгаажлаа: +" + i.credits() + " Сүлд Кредит. Баярлалаа! (/cosmetics)"));
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
                }
            }
            case MISMATCH -> {
                services.audit().record(AuditEvent.of("bonum", AuditActions.PAYMENT_MISMATCH, i.player().toString(),
                        i.intentId() + " expected " + i.amountMinor() + " " + i.currency()));
                plugin.getLogger().severe("[payment] a signed Bonum event paid another amount than intent " + i.intentId() + ": nothing granted, check it by hand");
                for (Player staff : Bukkit.getOnlinePlayers()) {
                    if (staff.hasPermission("suld.admin")) staff.sendMessage(Messages.error("[Төлбөр] " + i.intentId() + ": дүн таарсангүй — шалгана уу."));
                }
                if (p != null) p.sendMessage(Messages.error("Төлбөрийн дүн таарсангүй. Ажилтан шалгаж байна."));
            }
            case FAILED, EXPIRED -> {
                closeMap(i);
                if (p != null && "webhook".equals(via)) {
                    p.sendMessage(Messages.info(res.outcome() == PaymentLedger.Outcome.FAILED ? "Төлбөр цуцлагдлаа." : "Төлбөрийн хугацаа дууслаа."));
                }
            }
            case OWED -> plugin.getLogger().warning("[payment] " + i.intentId() + " is paid but the credits are not granted yet: retried every minute");
            default -> {
            }
        }
    }

    private void closeMap(PaymentIntent i) {
        UUID holder = shown.remove(i.intentId());
        maps.close(i.intentId(), Bukkit.getPlayer(holder != null ? holder : i.player()));
    }

    private void log(PaymentEvent e) {
        repo.log(e).exceptionally(err -> {
            plugin.getLogger().warning("[payment] the event log could not be written: " + root(err));
            return null;
        });
    }

    // ------------------------------------------------------------------ staff

    /** {@code /suld payment <player>}: the player's latest intents. */
    public void describe(UUID player, String name, Consumer<String> out) {
        repo.recent(player, 8).whenComplete((list, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                out.accept("payments " + name + ": FAILED " + root(err));
                return;
            }
            out.accept("payments " + name + ": gateway " + (enabled ? "on" : "off (" + off + ")") + ", " + list.size() + " intent(s)");
            for (PaymentIntent i : list) {
                out.accept("  " + i.intentId() + " " + i.product() + " " + i.amountMinor() + " " + i.currency() + " " + i.state()
                        + (i.owed() ? " OWED" : "") + (i.fulfilledAt() != null ? " fulfilled" : "") + (i.invoiceId() != null ? " invoice " + i.invoiceId() : ""));
            }
        }));
    }

    /** {@code /suld payment reconcile}. */
    public void reconcileNow(Consumer<String> out) {
        ledger.reconcile().whenComplete((results, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                out.accept("reconcile FAILED: " + root(err));
                return;
            }
            results.forEach(res -> settled(res, "reconcile"));
            out.accept("reconcile: " + results.size() + " intent(s) " + results.stream().map(r -> r.outcome().name()).toList());
        }));
    }

    private static String root(Throwable t) {
        while (t.getCause() != null && (t instanceof java.util.concurrent.CompletionException || t instanceof java.util.concurrent.ExecutionException)) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
