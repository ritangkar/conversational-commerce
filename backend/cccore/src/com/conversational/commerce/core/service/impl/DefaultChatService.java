package com.conversational.commerce.core.service.impl;

import com.conversational.commerce.core.dao.ChatDao;
import com.conversational.commerce.core.dao.ChatDao.BusinessAttribute;
import com.conversational.commerce.core.dao.ChatDao.Faq;
import com.conversational.commerce.core.dao.ChatDao.Order;
import com.conversational.commerce.core.dao.ChatDao.OrderFilter;
import com.conversational.commerce.core.dao.ChatDao.OrderLine;
import com.conversational.commerce.core.dao.ChatDao.OrderStatus;
import com.conversational.commerce.core.dao.ChatDao.Product;
import com.conversational.commerce.core.service.ChatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.hybris.platform.servicelayer.exceptions.SystemException;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The whole chat backend in one class (owner request: controller, facade, service, DAO only). Sections: configuration,
 * audit, rules, sanitiser, model routing, tools, workflows, orchestration, welcome, feedback, retention.
 *
 * <p>Invariants (each has a test): the model never sees prices, margin, names, emails, uids or other customers' data;
 * tools take no customer argument (the customer is {@code TurnState.customerId}); every model call is sanitised inside
 * {@link #callModel}; ids the model returns are accepted only if a tool returned them this turn; the audit log accepts
 * whitelisted metadata keys only.
 */
public class DefaultChatService implements ChatService
{
    private static final Logger LOG = LogManager.getLogger(DefaultChatService.class);
    private static final Logger AUDIT = LogManager.getLogger("cc.audit");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource(name = "ccChatDao")
    private ChatDao chatDao;

    private ModelTransport transport;
    private Clock clock = Clock.systemDefaultZone();
    private Consumer<Map<String, String>> auditObserver;

    public void setChatDao(final ChatDao chatDao)
    {
        this.chatDao = chatDao;
    }

    public void setTransport(final ModelTransport transport)
    {
        this.transport = transport;
    }

    public void setClock(final Clock clock)
    {
        this.clock = clock;
    }

    /** Test hook: receives every audit line as a map. */
    public void setAuditObserver(final Consumer<Map<String, String>> auditObserver)
    {
        this.auditObserver = auditObserver;
    }

    /** HTTP seam: the default uses a shared java.net.http.HttpClient with a connect timeout (owner convention). */
    public interface ModelTransport
    {
        record Response(int status, String body)
        {
        }

        Response post(String url, Map<String, String> headers, String body, int timeoutMs) throws IOException, InterruptedException;
    }

    // ==================================================================================== exceptions
    public static class LimitExceededException extends RuntimeException
    {
        public LimitExceededException(final String code)
        {
            super(code);
        }
    }

    public static class ToolRejectedException extends RuntimeException
    {
        public ToolRejectedException(final String reason)
        {
            super(reason);
        }
    }

    public static class SanitiserDisabledException extends RuntimeException
    {
        public SanitiserDisabledException()
        {
            super("sanitiser disabled: external model calls fail closed");
        }
    }

    public static class EgressBlockedException extends RuntimeException
    {
        public EgressBlockedException()
        {
            super("egress_blocked"); // the blocked value is deliberately not in the message
        }
    }

    public static class ModelUnavailableException extends RuntimeException
    {
        public ModelUnavailableException(final String message)
        {
            super(message);
        }
    }

    // ==================================================================================== configuration (PL-01, A1)
    private static final Set<String> PROTECTED = Set.of("PL-01", "PL-03", "PL-04", "PL-05");
    private static final String PL05_CONFIRM_KEY = "capability.PL-05.confirmDisable";
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(Optional<String> value, long loadedAtMs)
    {
    }

    private Optional<String> readRaw(final String key)
    {
        final String backoffice = chatDao.findBackofficeValue(key);
        return Optional.ofNullable(backoffice != null ? backoffice : chatDao.findProperty(key));
    }

    private Optional<String> read(final String key)
    {
        final long now = clock.millis();
        long ttlMs = 10_000L; // ASSUMPTION: design default 10 s
        final Optional<String> ttl = readRaw("cc.config.cacheTtlSeconds");
        if (ttl.isPresent())
        {
            try
            {
                ttlMs = Math.max(0, Integer.parseInt(ttl.get().trim())) * 1000L;
            }
            catch (final NumberFormatException e)
            {
                ttlMs = 10_000L;
            }
        }
        final Cached cached = cache.get(key);
        if (cached != null && now - cached.loadedAtMs() < ttlMs)
        {
            return cached.value();
        }
        final Optional<String> value = readRaw(key);
        cache.put(key, new Cached(value, now));
        return value;
    }

    private String cfg(final String key, final String defaultValue)
    {
        return read(key).orElse(defaultValue);
    }

    private int cfgInt(final String key, final int defaultValue)
    {
        final Optional<String> value = read(key);
        if (value.isEmpty())
        {
            return defaultValue;
        }
        try
        {
            return Integer.parseInt(value.get().trim());
        }
        catch (final NumberFormatException e)
        {
            return defaultValue;
        }
    }

    private double cfgDouble(final String key, final double defaultValue)
    {
        try
        {
            return read(key).map(v -> Double.parseDouble(v.trim())).orElse(defaultValue);
        }
        catch (final NumberFormatException e)
        {
            return defaultValue;
        }
    }

    private boolean cfgBool(final String key, final boolean defaultValue)
    {
        return read(key).map(v -> "true".equalsIgnoreCase(v.trim())).orElse(defaultValue);
    }

    /** Missing or unreadable toggles are on (defaults are seeded by essential data). PL-01 cannot be switched off. */
    public boolean isEnabled(final String capabilityId)
    {
        final Optional<String> raw = read("capability." + capabilityId + ".enabled");
        if (raw.isEmpty() || !"false".equalsIgnoreCase(raw.get().trim()))
        {
            return true;
        }
        if ("PL-01".equals(capabilityId))
        {
            return true;
        }
        if ("PL-05".equals(capabilityId))
        {
            return !cfgBool(PL05_CONFIRM_KEY, false); // needs the second confirming entry
        }
        return false; // PL-03 / PL-04 off take effect as fail-closed behaviour (calls blocked / unavailable notice)
    }

    private String activePack()
    {
        return cfg("cc.activePack", "tyres");
    }

    private boolean syntheticDataOnly()
    {
        return cfgBool("cc.syntheticDataOnly", true);
    }

    @Override
    public String validateToggleChange(final String capabilityId, final boolean newValue)
    {
        if (newValue || !PROTECTED.contains(capabilityId))
        {
            return null;
        }
        if ("PL-01".equals(capabilityId))
        {
            return "PL-01 cannot be disabled by a toggle";
        }
        if ("PL-05".equals(capabilityId))
        {
            return cfgBool(PL05_CONFIRM_KEY, false) ? null : "PL-05 needs the confirming entry " + PL05_CONFIRM_KEY;
        }
        return null; // PL-03 / PL-04: allowed, but callers fail closed
    }

    @Override
    public void clearConfigCache()
    {
        cache.clear();
    }

    @Override
    public PublicConfig getPublicConfig()
    {
        final String pack = activePack();
        return new PublicConfig(CAPABILITIES.stream().filter(this::isEnabled).toList(), pack,
                cfg("privacy.interestEvents.noticeText",
                        "PLACEHOLDER: this assistant records which products you view, compare or ask about to improve suggestions."),
                isEnabled("CI-04") && cfgBool("privacy.interestEvents.enabled", true), cfg("stock.demoNotice", "Demo stock"),
                cfg("purchaseList.label", "Demo purchase list, not your cart"), split(cfg("pack." + pack + ".samplePrompts", ""), "\\|"),
                pairs(cfg("pack." + pack + ".terminology", "")), pairs(cfg("pack." + pack + ".themeTokens", "")),
                cfgInt("cc.message.maxLength", 1000), cfg("contact.text", "Please contact your account manager."));
    }

    private List<String> split(final String value, final String regex)
    {
        return value.isBlank() ? List.of() : Arrays.stream(value.split(regex)).map(String::trim).toList();
    }

    private Map<String, String> pairs(final String value)
    {
        final Map<String, String> map = new LinkedHashMap<>();
        for (final String part : split(value, ";"))
        {
            final String[] kv = part.split("=", 2);
            if (kv.length == 2)
            {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }

    // ==================================================================================== audit (PL-05, A14)
    private static final Set<String> AUDIT_KEYS = Set.of("sessionRef", "responseId", "intent", "confidence", "workflow", "stepName",
            "toolName", "outcome", "errorCode", "provider", "model", "promptVersion", "tokensIn", "tokensOut", "latencyMs", "count");

    @Override
    public void audit(final String event, final String... kv)
    {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("event", event);
        for (int i = 0; i + 1 < kv.length; i += 2)
        {
            if (!AUDIT_KEYS.contains(kv[i]))
            {
                throw new IllegalArgumentException("audit key not allowed: " + kv[i]); // prompts and text cannot be logged
            }
            fields.put(kv[i], kv[i + 1]);
        }
        final StringBuilder line = new StringBuilder();
        fields.forEach((k, v) -> line.append(k).append('=').append(String.valueOf(v).replaceAll("[\\s=]", "_")).append(' '));
        AUDIT.info(line.toString().trim());
        if (auditObserver != null)
        {
            auditObserver.accept(fields);
        }
    }

    // ==================================================================================== rules (pure, deterministic)
    public record ReplenishmentItem(String code, int suggestedQuantity, int intervalDays, int daysSinceLast, boolean overdue,
            boolean estimate)
    {
    }

    /** D11: average interval between the customer's own orders. FUTURE SCOPE: unit-level replenishment (D34). */
    public static List<ReplenishmentItem> replenishment(final List<Order> orders, final LocalDate today, final int windowDays,
            final int minOrders, final double dueRatio)
    {
        final LocalDate from = today.minusDays(windowDays);
        final Map<String, TreeMap<LocalDate, Integer>> byProduct = new TreeMap<>();
        for (final Order order : orders)
        {
            if (order.status() == OrderStatus.CANCELLED || order.placedOn().isBefore(from) || order.placedOn().isAfter(today))
            {
                continue;
            }
            for (final OrderLine line : order.lines())
            {
                byProduct.computeIfAbsent(line.productCode(), k -> new TreeMap<>()).merge(order.placedOn(), line.quantity(),
                        Integer::sum);
            }
        }
        final List<ReplenishmentItem> result = new ArrayList<>();
        for (final Map.Entry<String, TreeMap<LocalDate, Integer>> entry : byProduct.entrySet())
        {
            final TreeMap<LocalDate, Integer> dates = entry.getValue();
            if (dates.size() < Math.max(2, minOrders))
            {
                continue;
            }
            final List<LocalDate> sorted = new ArrayList<>(dates.keySet());
            long gapSum = 0;
            for (int i = 1; i < sorted.size(); i++)
            {
                gapSum += ChronoUnit.DAYS.between(sorted.get(i - 1), sorted.get(i));
            }
            final int interval = (int) Math.max(1, Math.round((double) gapSum / (sorted.size() - 1)));
            final int since = (int) ChronoUnit.DAYS.between(sorted.get(sorted.size() - 1), today);
            final double ratio = (double) since / interval;
            if (ratio < dueRatio)
            {
                continue;
            }
            final int avgQty = (int) Math.max(1, Math.round(dates.values().stream().mapToInt(Integer::intValue).average().orElse(1)));
            result.add(new ReplenishmentItem(entry.getKey(), avgQty, interval, since, ratio > 1.0, true));
        }
        result.sort(Comparator.comparingInt((ReplenishmentItem i) -> i.overdue() ? 0 : 1).thenComparing(ReplenishmentItem::code));
        return result;
    }

    /** RI-02: removes or reduces suggestions already covered by pending orders. */
    public static List<ReplenishmentItem> netAgainstPending(final List<ReplenishmentItem> items, final List<Order> pendingOrders)
    {
        final Map<String, Integer> pendingQty = new TreeMap<>();
        pendingOrders.forEach(o -> o.lines().forEach(l -> pendingQty.merge(l.productCode(), l.quantity(), Integer::sum)));
        final List<ReplenishmentItem> result = new ArrayList<>();
        for (final ReplenishmentItem item : items)
        {
            final int remaining = item.suggestedQuantity() - pendingQty.getOrDefault(item.code(), 0);
            if (remaining > 0)
            {
                result.add(new ReplenishmentItem(item.code(), remaining, item.intervalDays(), item.daysSinceLast(), item.overdue(), true));
            }
        }
        return result;
    }

    public record PatternObservation(String code, double earlierAverage, double recentAverage, int changePercent, String direction,
            int earlierSample, int recentSample)
    {
    }

    /** D12: recent order quantities against the earlier average. */
    public static List<PatternObservation> detectPatternChanges(final List<Order> orders, final int thresholdPct, final int minSample,
            final int recentOrders)
    {
        final List<Order> sorted = new ArrayList<>(orders);
        sorted.removeIf(o -> o.status() == OrderStatus.CANCELLED);
        sorted.sort(Comparator.comparing(Order::placedOn));
        final Map<String, List<Integer>> series = new TreeMap<>();
        sorted.forEach(o -> o.lines().forEach(l -> series.computeIfAbsent(l.productCode(), k -> new ArrayList<>()).add(l.quantity())));
        final List<PatternObservation> result = new ArrayList<>();
        for (final Map.Entry<String, List<Integer>> entry : series.entrySet())
        {
            final List<Integer> q = entry.getValue();
            if (recentOrders < 1 || q.size() < minSample + recentOrders)
            {
                continue;
            }
            final List<Integer> recent = q.subList(q.size() - recentOrders, q.size());
            final List<Integer> earlier = q.subList(0, q.size() - recentOrders);
            final double earlierAvg = earlier.stream().mapToInt(Integer::intValue).average().orElse(0);
            final double recentAvg = recent.stream().mapToInt(Integer::intValue).average().orElse(0);
            if (earlierAvg <= 0)
            {
                continue;
            }
            final int change = (int) Math.round(Math.abs(recentAvg - earlierAvg) / earlierAvg * 100);
            if (change >= thresholdPct)
            {
                result.add(new PatternObservation(entry.getKey(), earlierAvg, recentAvg, change, recentAvg < earlierAvg ? "DOWN" : "UP",
                        earlier.size(), recent.size()));
            }
        }
        result.sort(Comparator.comparingInt((PatternObservation o) -> -o.changePercent()).thenComparing(PatternObservation::code));
        return result;
    }

    public record BudgetCandidate(String code, long unitMinor, int desiredQuantity, int score)
    {
    }

    public record BudgetLine(String code, int quantity, long lineMinor, boolean reduced)
    {
    }

    public record BudgetExcluded(String code, String reason)
    {
    }

    public record BudgetPlan(List<BudgetLine> lines, long totalMinor, long budgetMinor, List<BudgetExcluded> excluded)
    {
    }

    /** PA-04. Invariant: the total never exceeds the budget. Integer arithmetic in minor units. */
    public static BudgetPlan planBudget(final List<BudgetCandidate> candidates, final long budgetMinor)
    {
        final List<BudgetCandidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingInt(BudgetCandidate::score).reversed().thenComparing(BudgetCandidate::code));
        final List<BudgetLine> lines = new ArrayList<>();
        final List<BudgetExcluded> excluded = new ArrayList<>();
        long remaining = Math.max(0, budgetMinor);
        long total = 0;
        for (final BudgetCandidate c : ordered)
        {
            if (c.unitMinor() <= 0 || c.desiredQuantity() <= 0)
            {
                excluded.add(new BudgetExcluded(c.code(), "NO_VALID_PRICE_OR_QUANTITY"));
                continue;
            }
            final int qty = (int) Math.min(c.desiredQuantity(), remaining / c.unitMinor());
            if (qty < 1)
            {
                excluded.add(new BudgetExcluded(c.code(), "OVER_BUDGET"));
                continue;
            }
            final long lineMinor = qty * c.unitMinor();
            lines.add(new BudgetLine(c.code(), qty, lineMinor, qty < c.desiredQuantity()));
            remaining -= lineMinor;
            total += lineMinor;
        }
        return new BudgetPlan(lines, total, budgetMinor, excluded);
    }

    public record CoSuggestion(String code, int supportCount)
    {
    }

    /** CI-01, CI-02: aggregated co-purchase counts, deterministic. */
    public static List<CoSuggestion> complements(final Collection<String> basket, final Function<String, Map<String, Integer>> pairCounts,
            final int minSupport, final int limit)
    {
        final Set<String> inBasket = Set.copyOf(basket);
        final Map<String, Integer> totals = new TreeMap<>();
        for (final String code : basket)
        {
            pairCounts.apply(code).forEach((other, n) ->
            {
                if (!inBasket.contains(other))
                {
                    totals.merge(other, n, Integer::sum);
                }
            });
        }
        final List<CoSuggestion> result = new ArrayList<>();
        totals.forEach((code, count) ->
        {
            if (count >= minSupport)
            {
                result.add(new CoSuggestion(code, count));
            }
        });
        result.sort(Comparator.comparingInt((CoSuggestion s) -> -s.supportCount()).thenComparing(CoSuggestion::code));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    public record BasketFlag(String type, String code, String detail)
    {
    }

    /** PA-06 over the validated, browser-held list. */
    public static List<BasketFlag> reviewBasket(final List<OrderLine> list, final List<ReplenishmentItem> due, final List<Order> pending,
            final List<CoSuggestion> complementSuggestions)
    {
        final Set<String> inList = new HashSet<>();
        list.forEach(l -> inList.add(l.productCode()));
        final Set<String> inPending = new HashSet<>();
        pending.forEach(o -> o.lines().forEach(l -> inPending.add(l.productCode())));
        final List<BasketFlag> flags = new ArrayList<>();
        due.stream().filter(i -> !inList.contains(i.code()) && !inPending.contains(i.code()))
                .forEach(i -> flags.add(new BasketFlag("FORGOTTEN_USUAL", i.code(), "every " + i.intervalDays() + " days (estimate)")));
        list.stream().filter(l -> inPending.contains(l.productCode()))
                .forEach(l -> flags.add(new BasketFlag("DUPLICATE_OF_PENDING", l.productCode(), "already in a pending order")));
        complementSuggestions.stream().filter(s -> !inList.contains(s.code()))
                .forEach(s -> flags.add(new BasketFlag("COMPLEMENT", s.code(), "bought together by " + s.supportCount() + " orders")));
        return flags;
    }

    public record RawQuickLine(String rawText, String kind, String codeHint, String description, Integer quantity)
    {
    }

    private static final Pattern CODE = Pattern.compile("\\b([A-Z]{2,4}-\\d{3,6})\\b");
    private static final Pattern QTY_FIRST = Pattern.compile("^(\\d{1,6})\\s*(?:x|×|of|units? of|pcs? of)?\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern QTY_AFTER = Pattern.compile("^(.+?)\\s*(?:x|×)\\s*(\\d{1,6})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPLACES = Pattern.compile("(?:replaces?|replacement (?:for|of)|substitute (?:for|of))\\s+([A-Z]{2,4}-\\d{3,6})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FILLER = Pattern.compile("^(?:need|i need|please add|add|order|want|and|also)\\s+", Pattern.CASE_INSENSITIVE);

    /** PA-03: deterministic grammar for typed or pasted quick-order text. Residual text is resolved by catalogue search. */
    public static List<RawQuickLine> parseQuickOrder(final String text)
    {
        final List<RawQuickLine> lines = new ArrayList<>();
        if (text == null)
        {
            return lines;
        }
        for (final String part : text.split("(?i)[\\n;,]+|\\s+and\\s+(?=\\d|whatever)"))
        {
            final String trimmed = part.trim().replaceAll("[.]+$", "");
            final String once = FILLER.matcher(trimmed).replaceFirst("").trim();
            final String segment = FILLER.matcher(once).replaceFirst("").trim();
            if (segment.isEmpty())
            {
                continue;
            }
            final Matcher replaces = REPLACES.matcher(segment);
            if (replaces.find())
            {
                final Matcher qty = Pattern.compile("^(\\d{1,6})\\b").matcher(segment);
                lines.add(new RawQuickLine(segment, "REPLACEMENT_OF", replaces.group(1).toUpperCase(Locale.ROOT), null,
                        qty.find() ? Integer.valueOf(qty.group(1)) : null));
                continue;
            }
            Integer quantity = null;
            String rest = segment;
            final Matcher first = QTY_FIRST.matcher(segment);
            final Matcher after = QTY_AFTER.matcher(segment);
            if (first.matches())
            {
                quantity = Integer.valueOf(first.group(1));
                rest = first.group(2).trim();
            }
            else if (after.matches())
            {
                quantity = Integer.valueOf(after.group(2));
                rest = after.group(1).trim();
            }
            final Matcher code = CODE.matcher(rest);
            lines.add(code.find() ? new RawQuickLine(segment, "CODE", code.group(1), null, quantity)
                    : new RawQuickLine(segment, "DESCRIPTION", null, rest, quantity));
        }
        return lines;
    }

    /** CI-03, D24: the only reader of margin and priority. Returns an order of codes and nothing else. */
    private List<String> rankByBusinessControls(final List<String> codes)
    {
        final List<String> ordered = new ArrayList<>(codes);
        ordered.sort(Comparator.comparingLong((String c) ->
        {
            final BusinessAttribute a = chatDao.findBusinessAttribute(c);
            return a == null ? 0L : -(a.priority() * 1_000_000_000L + a.marginMinor());
        })); // stable: ties keep the incoming order
        return ordered;
    }

    // ==================================================================================== turn state
    /** Everything one turn needs. The customer comes from the security context; tools read it here, never from model output. */
    private static final class TurnState
    {
        final String customerId;
        final String pack;
        final LocalDate today;
        final String responseId;
        final TurnRequest request;
        final ChatEventListener listener;
        final Map<String, Integer> maskCounters = new HashMap<>();
        /** Product codes, order codes and FAQ ids returned by tools this turn (provenance, D26). */
        final Set<String> provenance = ConcurrentHashMap.newKeySet();
        /** Values that must never leave in a model payload: formatted prices, margins, uid, email. */
        final Set<String> secrets = ConcurrentHashMap.newKeySet();
        final List<TraceStep> trace = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger tokensIn = new AtomicInteger();
        final AtomicInteger tokensOut = new AtomicInteger();
        volatile String workingMessage;
        volatile String language = "en";
        private final AtomicInteger steps = new AtomicInteger();
        private final AtomicInteger toolCalls = new AtomicInteger();
        private final int maxSteps;
        private final long deadlineMs;

        TurnState(final TurnRequest request, final String pack, final LocalDate today, final ChatEventListener listener, final int maxSteps,
                final int maxSeconds)
        {
            this.request = request;
            this.customerId = request.customerId();
            this.pack = pack;
            this.today = today;
            this.responseId = UUID.randomUUID().toString();
            this.listener = listener;
            this.maxSteps = maxSteps;
            this.deadlineMs = System.currentTimeMillis() + maxSeconds * 1000L;
            this.workingMessage = request.message();
            this.secrets.add(request.customerId());
        }

        void checkTime()
        {
            if (System.currentTimeMillis() > deadlineMs)
            {
                throw new LimitExceededException("max_seconds");
            }
        }

        void checkToolCall()
        {
            if (toolCalls.incrementAndGet() > maxSteps * 3)
            {
                throw new LimitExceededException("max_tool_calls");
            }
            checkTime();
        }

        /** Runs a visible step: step_started and step_completed around the body, with the step and time limits enforced. */
        <T> T step(final String labelKey, final Supplier<T> body)
        {
            if (steps.incrementAndGet() > maxSteps)
            {
                throw new LimitExceededException("max_steps");
            }
            checkTime();
            final String id = "s" + steps.get();
            listener.onEvent(new ChatEvent("step_started", id, labelKey, "started", null, null, null, null, null));
            try
            {
                final T result = body.get();
                listener.onEvent(new ChatEvent("step_completed", id, labelKey, "ok", null, null, null, null, null));
                return result;
            }
            catch (final RuntimeException e)
            {
                listener.onEvent(new ChatEvent("step_completed", id, labelKey, "failed", null, null, null, null, null));
                throw e;
            }
        }
    }

    // ==================================================================================== sanitiser (PL-03, A1)
    private static final Map<String, Set<String>> EGRESS = Map.of(
            "DECIDE", Set.of("message", "capabilities", "terminology", "turns", "shortlistCodes", "language"),
            "TRANSLATE", Set.of("text", "sourceLanguage", "targetLanguage"),
            "JUDGE", Set.of("need", "constraints", "candidates", "language"),
            "EXPLAIN", Set.of("intent", "facts", "comparison", "language"),
            "COMPOSE", Set.of("intent", "facts", "slots", "language", "style"));
    private static final Set<String> NEVER_FRAGMENTS = Set.of("price", "total", "margin", "priority", "customername", "email", "phone",
            "address", "pk", "customer", "uid", "supplier", "amount", "cost");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern ORDER_CODE = Pattern.compile("\\b(?:ORD|PO)[-#]?\\d{4,}\\b|\\b\\d{8}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<![\\w-])\\+?\\d[\\d\\s-]{8,}\\d(?![\\w-])");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\b\\d{9,}\\b");

    private static boolean isNeverField(final String name)
    {
        final String lower = name.toLowerCase(Locale.ROOT);
        return NEVER_FRAGMENTS.stream().anyMatch(lower::contains);
    }

    private static String mask(final String text, final Map<String, Integer> counters)
    {
        String out = text;
        final Object[][] rules = { { "EMAIL", EMAIL }, { "ORDER", ORDER_CODE }, { "PHONE", PHONE }, { "NUM", LONG_DIGITS } };
        for (final Object[] rule : rules)
        {
            final Matcher m = ((Pattern) rule[1]).matcher(out);
            final StringBuilder sb = new StringBuilder();
            while (m.find())
            {
                final int n = counters.merge((String) rule[0], 1, Integer::sum);
                m.appendReplacement(sb, Matcher.quoteReplacement("[" + rule[0] + "_" + n + "]"));
            }
            m.appendTail(sb);
            out = sb.toString();
        }
        return out;
    }

    private static Object maskValue(final Object value, final Map<String, Integer> counters)
    {
        if (value instanceof String text)
        {
            return mask(text, counters);
        }
        if (value instanceof Collection<?> collection)
        {
            final List<Object> list = new ArrayList<>();
            collection.forEach(v -> list.add(maskValue(v, counters)));
            return list;
        }
        if (value instanceof Map<?, ?> map)
        {
            final Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) ->
            {
                if (!isNeverField(String.valueOf(k)))
                {
                    copy.put(String.valueOf(k), maskValue(v, counters));
                }
            });
            return copy;
        }
        return value;
    }

    /** Fails closed when sanitisation is off; drops fields not allowed for the task or classified NEVER; masks free text. */
    private String buildPayload(final String task, final Map<String, Object> fields, final Map<String, Integer> counters,
            final Collection<String> forbiddenValues)
    {
        if (!isEnabled("PL-03") || !cfgBool("cc.sanitiser.enabled", true))
        {
            throw new SanitiserDisabledException();
        }
        final Set<String> allowed = EGRESS.getOrDefault(task, Set.of());
        final Map<String, Object> out = new LinkedHashMap<>();
        fields.forEach((k, v) ->
        {
            if (allowed.contains(k) && !isNeverField(k))
            {
                out.put(k, maskValue(v, counters));
            }
        });
        final String json;
        try
        {
            json = MAPPER.writeValueAsString(out);
        }
        catch (final JsonProcessingException e)
        {
            throw new IllegalStateException("payload could not be serialised", e);
        }
        final String haystack = json.toLowerCase(Locale.ROOT);
        for (final String forbidden : forbiddenValues)
        {
            if (forbidden != null && forbidden.length() >= 3 && haystack.contains(forbidden.toLowerCase(Locale.ROOT)))
            {
                throw new EgressBlockedException(); // outbound guard
            }
        }
        return json;
    }

    /** The only path to a model: sanitises, then routes. Public so tests and the contract checks use the real path. */
    public ModelResult callModel(final String task, final String promptId, final Map<String, Object> fields,
            final Collection<String> forbiddenValues)
    {
        final String payload = buildPayload(task, fields, new HashMap<>(), forbiddenValues);
        return route(task, promptId, payload, task.equals("COMPOSE") || task.equals("EXPLAIN") ? 400 : task.equals("JUDGE") ? 600 : 500,
                "COMPOSE".equals(task) || "EXPLAIN".equals(task) ? 0.2 : 0.0);
    }

    // ==================================================================================== model routing (PL-02)
    public record ModelResult(JsonNode json, int tokensIn, int tokensOut, String provider, String model, long latencyMs)
    {
    }

    private final Map<String, String> resources = new ConcurrentHashMap<>();

    private String resource(final String path)
    {
        return resources.computeIfAbsent(path, p ->
        {
            try (InputStream in = DefaultChatService.class.getResourceAsStream("/cccore/" + p))
            {
                if (in == null)
                {
                    throw new IllegalStateException("resource missing: " + p);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            catch (final IOException e)
            {
                throw new IllegalStateException("resource unreadable: " + p, e);
            }
        });
    }

    private static String schemaIdOf(final String promptId)
    {
        return promptId.startsWith("decide") ? "decision.v1" : promptId.startsWith("judge") ? "judge.v1"
                : promptId.startsWith("translate") ? "translate.v1" : "compose.v1";
    }

    /** Walks route.&lt;task&gt;.provider/model then the fallback chain; refuses free-tier providers outside synthetic sites (A2). */
    private ModelResult route(final String task, final String promptId, final String payload, final int maxTokens, final double temperature)
    {
        final String prefix = "route." + task.toLowerCase(Locale.ROOT) + ".";
        final List<String[]> chain = new ArrayList<>();
        final String provider = cfg(prefix + "provider", "");
        if (StringUtils.isNotBlank(provider))
        {
            chain.add(new String[] { provider, cfg(prefix + "model", "") });
        }
        for (final String entry : cfg(prefix + "fallback", "").split(","))
        {
            final String[] parts = entry.trim().split(":", 2);
            if (parts.length == 2 && StringUtils.isNotBlank(parts[0]))
            {
                chain.add(new String[] { parts[0].trim(), parts[1].trim() });
            }
        }
        for (final String[] step : chain)
        {
            final String code = step[0].toLowerCase(Locale.ROOT);
            final String base = cfg("cc.provider." + code + ".baseUrl", "");
            if (StringUtils.isBlank(base) || StringUtils.isBlank(apiKey(code)) || !cfgBool("cc.provider." + code + ".enabled", true))
            {
                continue;
            }
            if ("free".equalsIgnoreCase(cfg("cc.provider." + code + ".tier", "paid")) && !syntheticDataOnly())
            {
                LOG.warn("Provider {} skipped: free tier is refused outside synthetic-data sites", code);
                continue;
            }
            try
            {
                final ModelResult result = callProvider(code, step[1], promptId, payload, maxTokens, temperature);
                if (result.json() != null && !result.json().isMissingNode() && !result.json().isNull())
                {
                    return result;
                }
            }
            catch (final RuntimeException e)
            {
                LOG.warn("Provider {} failed for task {}: {}", code, task, e.getClass().getSimpleName()); // class only
            }
        }
        throw new ModelUnavailableException("no provider available for task " + task);
    }

    /** Keys are read by alias and never logged. ASSUMPTION: alias defaults to the provider code. */
    private String apiKey(final String code)
    {
        return cfg("cc.provider.key." + cfg("cc.provider." + code + ".keyAlias", code), "");
    }

    private ModelTransport transport()
    {
        if (transport == null)
        {
            final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(cfgInt("cc.http.connectTimeoutMs", 3000))).build();
            transport = (url, headers, body, timeoutMs) ->
            {
                final HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(timeoutMs))
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                headers.forEach(b::header);
                final HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
                return new ModelTransport.Response(r.statusCode(), r.body());
            };
        }
        return transport;
    }

    /**
     * One provider call over HttpClient + Jackson. Error convention: non-200 logs the status only (the body may echo user
     * content) and throws SystemException; null content throws SystemException; InterruptedException restores the flag.
     * Request shapes below are UNVERIFIED (docs/verification-log.md, spike S3).
     */
    private ModelResult callProvider(final String code, final String model, final String promptId, final String payload, final int maxTokens,
            final double temperature)
    {
        final long start = System.currentTimeMillis();
        final String system = resource("prompts/" + promptId + ".txt");
        final String schema = resource("schemas/" + schemaIdOf(promptId) + ".json");
        final String key = apiKey(code);
        final String name = promptId.replaceAll("[^A-Za-z0-9_-]", "_");
        try
        {
            final ObjectNode root = MAPPER.createObjectNode();
            final Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/json");
            final String path;
            switch (code)
            {
                case "openai" ->
                {
                    path = "/v1/chat/completions";
                    headers.put("Authorization", "Bearer " + key);
                    root.put("model", model);
                    root.put("store", false);
                    root.put("temperature", temperature);
                    root.put("max_completion_tokens", maxTokens);
                    final ArrayNode messages = root.putArray("messages");
                    messages.addObject().put("role", "system").put("content", system);
                    messages.addObject().put("role", "user").put("content", payload);
                    final ObjectNode js = root.putObject("response_format").put("type", "json_schema").putObject("json_schema");
                    js.put("name", name).put("strict", true).set("schema", MAPPER.readTree(schema));
                }
                case "anthropic" ->
                {
                    path = "/v1/messages";
                    headers.put("x-api-key", key);
                    headers.put("anthropic-version", "2023-06-01");
                    root.put("model", model).put("max_tokens", maxTokens).put("temperature", temperature).put("system", system);
                    root.putArray("messages").addObject().put("role", "user").put("content", payload);
                    final ObjectNode tool = root.putArray("tools").addObject();
                    tool.put("name", name).put("description", "Return the structured answer.").set("input_schema", MAPPER.readTree(schema));
                    root.putObject("tool_choice").put("type", "tool").put("name", name);
                }
                case "gemini" ->
                {
                    path = "/v1beta/models/" + model + ":generateContent";
                    headers.put("x-goog-api-key", key);
                    root.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
                    final ObjectNode content = root.putArray("contents").addObject().put("role", "user");
                    content.putArray("parts").addObject().put("text", payload);
                    final ObjectNode config = root.putObject("generationConfig");
                    config.put("responseMimeType", "application/json").put("temperature", temperature).put("maxOutputTokens", maxTokens);
                    final JsonNode geminiSchema = MAPPER.readTree(schema);
                    stripUnsupported(geminiSchema); // the schema files are OpenAI-strict; Gemini's subset has no additionalProperties
                    config.set("responseSchema", geminiSchema);
                }
                default -> throw new SystemException("Unknown provider " + code);
            }
            final ModelTransport.Response response = transport().post(cfg("cc.provider." + code + ".baseUrl", "") + path, headers,
                    MAPPER.writeValueAsString(root), cfgInt("cc.http.requestTimeoutMs", 8000));
            if (response.status() != 200)
            {
                LOG.warn("Model provider {} returned status {}", code, response.status()); // status only
                throw new SystemException("Model provider returned status " + response.status());
            }
            final JsonNode body = MAPPER.readTree(response.body());
            JsonNode content = null;
            int in = 0;
            int out = 0;
            if (body != null)
            {
                switch (code)
                {
                    case "openai" ->
                    {
                        content = parseJsonText(body.path("choices").path(0).path("message").path("content"));
                    }
                    case "anthropic" ->
                    {
                        for (final JsonNode block : body.path("content"))
                        {
                            if ("tool_use".equals(block.path("type").asText()))
                            {
                                content = block.path("input");
                            }
                        }
                    }
                    default ->
                    {
                        content = parseJsonText(body.path("candidates").path(0).path("content").path("parts").path(0).path("text"));
                    }
                }
                final JsonNode usage = body.path("usage").isMissingNode() ? body.path("usageMetadata") : body.path("usage");
                in = usage.path("prompt_tokens").asInt(usage.path("input_tokens").asInt(usage.path("promptTokenCount").asInt(0)));
                out = usage.path("completion_tokens").asInt(usage.path("output_tokens").asInt(usage.path("candidatesTokenCount").asInt(0)));
            }
            if (content == null || content.isNull() || content.isMissingNode())
            {
                throw new SystemException("Model provider returned no content");
            }
            return new ModelResult(content, in, out, code, model, System.currentTimeMillis() - start);
        }
        catch (final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new SystemException("Model call interrupted", e);
        }
        catch (final IOException e)
        {
            // ASSUMPTION: remaining catch blocks were truncated in the owner's screenshot (docs/assumptions.md AS-06).
            LOG.warn("Model provider {} call failed: {}", code, e.getClass().getSimpleName());
            throw new SystemException("Model provider call failed", e);
        }
    }

    private static JsonNode parseJsonText(final JsonNode text)
    {
        if (!text.isTextual())
        {
            return null;
        }
        try
        {
            return MAPPER.readTree(text.asText());
        }
        catch (final JsonProcessingException e)
        {
            return null; // a non-JSON answer counts as a failed attempt, so the next provider in the chain is tried
        }
    }

    private static void stripUnsupported(final JsonNode node)
    {
        if (node instanceof ObjectNode object)
        {
            object.remove("additionalProperties");
            object.remove("$schema");
            final Iterator<JsonNode> it = object.elements();
            while (it.hasNext())
            {
                stripUnsupported(it.next());
            }
        }
        else if (node instanceof ArrayNode array)
        {
            array.forEach(DefaultChatService::stripUnsupported);
        }
    }

    /** Calls a model from inside a turn: sanitised with the turn's secrets as forbidden values, tokens counted for PL-05/PL-06. */
    private ModelResult modelCall(final TurnState s, final String task, final String promptId, final Map<String, Object> fields)
    {
        final String payload = buildPayload(task, fields, s.maskCounters, s.secrets);
        final ModelResult r = route(task, promptId, payload, "JUDGE".equals(task) ? 600 : "DECIDE".equals(task) || "TRANSLATE".equals(task) ? 500 : 400,
                "COMPOSE".equals(task) || "EXPLAIN".equals(task) ? 0.2 : 0.0);
        s.tokensIn.addAndGet(r.tokensIn());
        s.tokensOut.addAndGet(r.tokensOut());
        audit("model_call", "responseId", s.responseId, "stepName", task, "provider", r.provider(), "model", r.model(), "promptVersion",
                promptId, "tokensIn", String.valueOf(r.tokensIn()), "tokensOut", String.valueOf(r.tokensOut()), "latencyMs",
                String.valueOf(r.latencyMs()));
        return r;
    }

    // ==================================================================================== tools (PL-04)
    private record ToolResult(Object value, List<String> codes, List<TraceStep> trace)
    {
        @SuppressWarnings("unchecked")
        <T> T as()
        {
            return (T) value;
        }
    }

    private record ToolCall(String tool, Map<String, Object> args)
    {
    }

    /** The tool catalogue: name to capability. No tool accepts a customer, user or price argument; no tool writes. */
    private static final Map<String, String> TOOLS = Map.ofEntries(Map.entry("searchProducts", "PD-01"),
            Map.entry("getProductDetails", "PD-03"), Map.entry("getComparisonAttributes", "PD-04"), Map.entry("getAlternatives", "PD-06"),
            Map.entry("getStockInfo", "PD-06"), Map.entry("getBudgetEligibleProducts", "PD-07"), Map.entry("getLatestOrder", "OM-01"),
            Map.entry("getOrderById", "OM-01"), Map.entry("getPendingOrders", "RI-02"), Map.entry("searchOrders", "OM-02"),
            Map.entry("getOrderTracking", "OM-01"), Map.entry("getCustomerPurchaseHistory", "RI-01"),
            Map.entry("getReplenishmentRecommendations", "RI-01"), Map.entry("detectPurchasePatternChanges", "RI-04"),
            Map.entry("getComplementaryProducts", "CI-01"), Map.entry("getBundleSuggestions", "CI-02"),
            Map.entry("planBudgetPurchase", "PA-04"), Map.entry("reviewPurchaseList", "PA-06"),
            Map.entry("validateQuickOrderLines", "PA-03"), Map.entry("getFaqAnswer", "KN-01"));

    private final ExecutorService toolPool = Executors.newFixedThreadPool(4, r ->
    {
        final Thread t = new Thread(r, "cc-tool");
        t.setDaemon(true);
        return t;
    });

    /** The only way to run a tool: rejects unknown tools and disabled capabilities, records provenance, trace and audit. */
    private ToolResult runTool(final TurnState s, final String name, final Map<String, Object> rawArgs)
    {
        final String capability = TOOLS.get(name);
        if (capability == null)
        {
            audit("tool_rejected", "responseId", s.responseId, "toolName", name, "errorCode", "unknown_tool");
            throw new ToolRejectedException("unknown_tool");
        }
        if (!isEnabled(capability))
        {
            audit("tool_rejected", "responseId", s.responseId, "toolName", name, "errorCode", "capability_disabled");
            throw new ToolRejectedException("capability_disabled");
        }
        s.checkToolCall();
        final Map<String, Object> args = rawArgs == null ? Map.of() : rawArgs;
        final ToolResult r = switch (name)
        {
            case "searchProducts" -> toolSearchProducts(s, args);
            case "getProductDetails" -> toolProductDetails(s, args);
            case "getComparisonAttributes" -> toolComparison(s, args);
            case "getAlternatives" -> toolAlternatives(s, args);
            case "getStockInfo" -> toolStockInfo(s, args);
            case "getBudgetEligibleProducts" -> toolBudgetEligible(s, args);
            case "getLatestOrder" -> toolLatestOrder(s);
            case "getOrderById", "getOrderTracking" -> toolOrderById(s, args, "getOrderTracking".equals(name));
            case "getPendingOrders" -> toolPendingOrders(s);
            case "searchOrders" -> toolSearchOrders(s, args);
            case "getCustomerPurchaseHistory" -> toolHistory(s, args);
            case "getReplenishmentRecommendations" -> toolReplenishment(s, args);
            case "detectPurchasePatternChanges" -> toolPatternChanges(s);
            case "getComplementaryProducts" -> toolComplements(s, args, cfgInt("crossSell.minSupport", 2), 5, "Looked for items bought together");
            case "getBundleSuggestions" -> toolComplements(s, args, cfgInt("bundle.minSupport", 3), 3, "Looked for common bundles");
            case "planBudgetPurchase" -> toolPlanBudget(s, args);
            case "reviewPurchaseList" -> toolReviewList(s);
            case "validateQuickOrderLines" -> toolQuickOrder(s, args);
            case "getFaqAnswer" -> toolFaq(s, args);
            default -> throw new ToolRejectedException("unknown_tool");
        };
        s.provenance.addAll(r.codes());
        s.trace.addAll(r.trace());
        audit("tool_executed", "responseId", s.responseId, "toolName", name, "outcome", "ok", "count", String.valueOf(r.codes().size()));
        return r;
    }

    /** Runs independent tools in parallel (a workflow's parallel group); results keep the call order. */
    private List<ToolResult> runParallel(final TurnState s, final List<ToolCall> calls)
    {
        final List<CompletableFuture<ToolResult>> futures = new ArrayList<>();
        calls.forEach(c -> futures.add(CompletableFuture.supplyAsync(() -> runTool(s, c.tool(), c.args()), toolPool)));
        final List<ToolResult> results = new ArrayList<>();
        for (final CompletableFuture<ToolResult> f : futures)
        {
            try
            {
                results.add(f.get(cfgInt("orchestration.maxSeconds", 9), TimeUnit.SECONDS));
            }
            catch (final InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new LimitExceededException("interrupted");
            }
            catch (final TimeoutException e)
            {
                throw new LimitExceededException("max_seconds");
            }
            catch (final ExecutionException e)
            {
                throw e.getCause() instanceof RuntimeException re ? re : new IllegalStateException(e.getCause());
            }
        }
        return results;
    }

    /** Test and diagnostics entry: runs one tool as the given customer with a fresh turn state. Returns the typed value. */
    public Object executeTool(final String customerId, final String tool, final Map<String, Object> args)
    {
        final TurnState s = new TurnState(new TurnRequest(customerId, "", null, "en", List.of(), List.of(), List.of()), activePack(),
                LocalDate.now(clock), ChatEventListener.NOOP, 8, 9);
        return runTool(s, tool, args).value();
    }

    // ---- argument validation: type, size and range; a violation rejects the step
    private static List<String> argStrings(final Map<String, Object> args, final String key, final int maxSize)
    {
        final Object v = args.get(key);
        if (v == null)
        {
            return List.of();
        }
        if (!(v instanceof List<?> list) || list.size() > maxSize)
        {
            throw new ToolRejectedException("bad_argument:" + key);
        }
        final List<String> out = new ArrayList<>();
        for (final Object o : list)
        {
            if (!(o instanceof String str) || str.length() > 200)
            {
                throw new ToolRejectedException("bad_argument:" + key);
            }
            out.add(str.trim());
        }
        return out;
    }

    private static String argString(final Map<String, Object> args, final String key, final String defaultValue)
    {
        final Object v = args.get(key);
        if (v == null)
        {
            return defaultValue;
        }
        if (!(v instanceof String str) || str.length() > 2000)
        {
            throw new ToolRejectedException("bad_argument:" + key);
        }
        return str.trim();
    }

    private static long argNumber(final Map<String, Object> args, final String key, final long min, final long max, final long defaultValue)
    {
        final Object v = args.get(key);
        if (v == null)
        {
            return defaultValue;
        }
        if (!(v instanceof Number n) || n.longValue() < min || n.longValue() > max)
        {
            throw new ToolRejectedException("bad_argument:" + key);
        }
        return n.longValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> argMap(final Map<String, Object> args, final String key, final int maxSize)
    {
        final Object v = args.get(key);
        if (v == null)
        {
            return Map.of();
        }
        if (!(v instanceof Map<?, ?> m) || m.size() > maxSize)
        {
            throw new ToolRejectedException("bad_argument:" + key);
        }
        return (Map<String, String>) m;
    }

    private static final String ORDERS_SOURCE = "Your orders in Commerce";

    private ToolResult toolSearchProducts(final TurnState s, final Map<String, Object> args)
    {
        final List<Product> found = chatDao.findProducts(s.pack, argStrings(args, "keywords", 12), argString(args, "categoryCode", null),
                argMap(args, "filters", 8), (int) argNumber(args, "limit", 1, 50, 10));
        return new ToolResult(found, found.stream().map(Product::code).toList(),
                List.of(new TraceStep("Searched the catalogue", "Catalogue", "current", found.size() + " candidates")));
    }

    private ToolResult toolProductDetails(final TurnState s, final Map<String, Object> args)
    {
        final List<Product> products = new ArrayList<>();
        for (final String code : argStrings(args, "codes", 10))
        {
            final Product p = chatDao.findProduct(s.pack, code);
            if (p != null)
            {
                products.add(p);
            }
        }
        return new ToolResult(products, products.stream().map(Product::code).toList(), List.of());
    }

    private ToolResult toolComparison(final TurnState s, final Map<String, Object> args)
    {
        final List<Product> products = new ArrayList<>();
        for (final String code : argStrings(args, "codes", 4))
        {
            final Product p = chatDao.findProduct(s.pack, code);
            if (p != null)
            {
                products.add(p);
            }
        }
        if (products.size() < 2)
        {
            throw new ToolRejectedException("need_two_products");
        }
        final TreeMap<String, Boolean> keys = new TreeMap<>();
        products.forEach(p -> p.attributes().keySet().forEach(k -> keys.put(k, true)));
        final List<ComparisonRow> rows = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        for (final String key : keys.keySet())
        {
            final List<String> values = products.stream().map(p -> p.attributes().getOrDefault(key, NOT_AVAILABLE)).toList();
            if (values.contains(NOT_AVAILABLE))
            {
                notes.add(key + " is not recorded for every product");
            }
            rows.add(new ComparisonRow(key, values));
        }
        return new ToolResult(new Comparison(products.stream().map(Product::name).toList(), rows, notes),
                products.stream().map(Product::code).toList(),
                List.of(new TraceStep("Compared catalogue attributes", "Catalogue", "current", rows.size() + " attributes")));
    }

    private static final String NOT_AVAILABLE = "Not available";

    private ToolResult toolAlternatives(final TurnState s, final Map<String, Object> args)
    {
        final String code = argString(args, "code", "");
        final Product original = chatDao.findProduct(s.pack, code);
        final List<Product> alternatives = new ArrayList<>();
        if (original != null)
        {
            if (original.replacementCode() != null)
            {
                final Product replacement = chatDao.findProduct(s.pack, original.replacementCode());
                if (replacement != null)
                {
                    alternatives.add(replacement);
                }
            }
            if (alternatives.isEmpty())
            {
                for (final Product p : chatDao.findProducts(s.pack, List.of(), original.category(), Map.of(), 10))
                {
                    if (!p.code().equals(code) && !p.discontinued() && demoStock(p.code()).units() > 0 && alternatives.size() < 3)
                    {
                        alternatives.add(p);
                    }
                }
            }
        }
        return new ToolResult(alternatives, alternatives.stream().map(Product::code).toList(), List.of());
    }

    /** D27: hardcoded, labelled demo stock; override per product with entry stock.demo.&lt;code&gt;. FUTURE SCOPE: live stock. */
    private record Stock(int units, String label)
    {
    }

    private Stock demoStock(final String code)
    {
        final int units = cfgInt("stock.demo." + code, Math.abs(code.hashCode()) % 200);
        return new Stock(units, units > 20 ? "In stock (demo)" : units > 0 ? "Low stock (demo)" : "Out of stock (demo)");
    }

    private ToolResult toolStockInfo(final TurnState s, final Map<String, Object> args)
    {
        final List<String> codes = argStrings(args, "codes", 20).stream().filter(s.provenance::contains).toList(); // provenance
        return new ToolResult(codes.stream().map(c -> Map.entry(c, demoStock(c))).toList(), codes, List.of());
    }

    private ToolResult toolBudgetEligible(final TurnState s, final Map<String, Object> args)
    {
        final long maxUnitMinor = argNumber(args, "maxUnitMinor", 1, 1_000_000_000_000L, Long.MAX_VALUE);
        final List<String> eligible = new ArrayList<>();
        for (final String code : argStrings(args, "candidateCodes", 50))
        {
            final OptionalLong price = chatDao.findUnitPriceMinor(s.customerId, code);
            if (s.provenance.contains(code) && price.isPresent() && price.getAsLong() <= maxUnitMinor)
            {
                eligible.add(code);
            }
        }
        return new ToolResult(eligible, eligible,
                List.of(new TraceStep("Applied your price limit", "Your contract prices", "current", eligible.size() + " within limit")));
    }

    private ToolResult orderResult(final List<Order> orders, final TraceStep... trace)
    {
        return new ToolResult(orders, orders.stream().map(Order::code).toList(), List.of(trace));
    }

    private ToolResult toolLatestOrder(final TurnState s)
    {
        final List<Order> latest = chatDao.searchOrders(s.customerId, OrderFilter.none(), "byDateDesc", 1);
        return orderResult(latest, new TraceStep("Looked up your latest order", ORDERS_SOURCE, "all time", latest.size() + " found"));
    }

    private ToolResult toolOrderById(final TurnState s, final Map<String, Object> args, final boolean tracking)
    {
        // FUTURE SCOPE: carrier tracking (D10): only the Commerce-held reference and status are returned.
        final String code = argString(args, "orderCode", "");
        final Order order = code.isBlank() ? null : chatDao.findOrderByCode(s.customerId, code);
        final List<Order> list = order == null ? List.of() : List.of(order);
        return tracking ? orderResult(list, new TraceStep("Checked the delivery status", ORDERS_SOURCE, "current", "status held in Commerce"))
                : orderResult(list);
    }

    private ToolResult toolPendingOrders(final TurnState s)
    {
        final List<Order> pending = chatDao.searchOrders(s.customerId, new OrderFilter(Set.of(OrderStatus.PENDING, OrderStatus.PROCESSING),
                null, null, null, null), "byDateAsc", 100);
        return orderResult(pending, new TraceStep("Checked your pending orders", ORDERS_SOURCE, "open orders", pending.size() + " pending"));
    }

    private ToolResult toolSearchOrders(final TurnState s, final Map<String, Object> args)
    {
        final String status = argString(args, "status", "ALL");
        final Set<OrderStatus> statuses = switch (status)
        {
            case "UNDELIVERED" -> Set.of(OrderStatus.PENDING, OrderStatus.PROCESSING, OrderStatus.SHIPPED);
            case "PENDING" -> Set.of(OrderStatus.PENDING, OrderStatus.PROCESSING);
            case "DELIVERED" -> Set.of(OrderStatus.DELIVERED);
            default -> Set.of(OrderStatus.PENDING, OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
        };
        final long min = argNumber(args, "minTotalMinor", 0, 1_000_000_000_000L, 0);
        final long max = argNumber(args, "maxTotalMinor", 0, 1_000_000_000_000L, Long.MAX_VALUE);
        final LocalDate from = argDate(args, "fromDate");
        final LocalDate to = argDate(args, "toDate");
        final List<Order> found = chatDao.searchOrders(s.customerId,
                new OrderFilter(statuses, min, max == Long.MAX_VALUE ? null : max, from, to), "byDateDesc", (int) argNumber(args, "limit", 1, 50, 20));
        return orderResult(found, new TraceStep("Searched your orders", ORDERS_SOURCE, from == null ? "all time" : from + " onwards",
                found.size() + " matches"));
    }

    private static LocalDate argDate(final Map<String, Object> args, final String key)
    {
        final String value = argString(args, key, null);
        try
        {
            return value == null ? null : LocalDate.parse(value);
        }
        catch (final java.time.format.DateTimeParseException e)
        {
            throw new ToolRejectedException("bad_argument:" + key);
        }
    }

    public record HistoryEntry(String code, int orderCount, int totalQuantity, LocalDate lastOrdered)
    {
    }

    private List<Order> ownOrders(final TurnState s, final int windowDays)
    {
        return chatDao.searchOrders(s.customerId, new OrderFilter(null, null, null, s.today.minusDays(windowDays), null), "byDateDesc", 1000)
                .stream().filter(o -> o.status() != OrderStatus.CANCELLED).toList();
    }

    private ToolResult toolHistory(final TurnState s, final Map<String, Object> args)
    {
        // FUTURE SCOPE: unit-level history (D34).
        final int window = (int) argNumber(args, "windowDays", 1, 730, 180);
        final Map<String, int[]> agg = new TreeMap<>();
        final Map<String, LocalDate> last = new TreeMap<>();
        for (final Order o : ownOrders(s, window))
        {
            for (final OrderLine l : o.lines())
            {
                final int[] a = agg.computeIfAbsent(l.productCode(), k -> new int[2]);
                a[0]++;
                a[1] += l.quantity();
                last.merge(l.productCode(), o.placedOn(), (x, y) -> x.isAfter(y) ? x : y);
            }
        }
        final List<HistoryEntry> entries = agg.entrySet().stream()
                .map(e -> new HistoryEntry(e.getKey(), e.getValue()[0], e.getValue()[1], last.get(e.getKey()))).toList();
        return new ToolResult(entries, entries.stream().map(HistoryEntry::code).toList(),
                List.of(new TraceStep("Reviewed your order history", ORDERS_SOURCE, "last " + window + " days", entries.size() + " products")));
    }

    private ToolResult toolReplenishment(final TurnState s, final Map<String, Object> args)
    {
        // FUTURE SCOPE: unit-level replenishment (D34).
        final int window = cfgInt("replenishment.windowDays", 180);
        final int minOrders = cfgInt("replenishment.minOrders", 3);
        final double ratio = cfgDouble("replenishment.dueRatio", 0.9);
        final List<Order> orders = ownOrders(s, Math.max(window, 1));
        List<ReplenishmentItem> items = replenishment(orders, s.today, window, minOrders, ratio);
        if (Boolean.TRUE.equals(args.get("netPending")))
        {
            items = netAgainstPending(items, orders.stream().filter(o -> o.status() == OrderStatus.PENDING || o.status() == OrderStatus.PROCESSING).toList());
        }
        return new ToolResult(items, items.stream().map(ReplenishmentItem::code).toList(),
                List.of(new TraceStep("Estimated what you are due to reorder", ORDERS_SOURCE, "last " + window + " days",
                        "due at " + (int) (ratio * 100) + "% of your usual interval; at least " + minOrders + " orders")));
    }

    private ToolResult toolPatternChanges(final TurnState s)
    {
        final int threshold = cfgInt("patternChange.thresholdPct", 30);
        final int recent = cfgInt("patternChange.recentOrders", 2);
        final List<PatternObservation> found = detectPatternChanges(ownOrders(s, 730), threshold, cfgInt("patternChange.minSample", 2), recent);
        return new ToolResult(found, found.stream().map(PatternObservation::code).toList(), List.of(new TraceStep(
                "Compared your recent orders with earlier ones", ORDERS_SOURCE, "last " + recent + " orders against earlier",
                "change of " + threshold + "% or more")));
    }

    private ToolResult toolComplements(final TurnState s, final Map<String, Object> args, final int minSupport, final int limit, final String label)
    {
        final List<String> basket = argStrings(args, "codes", 20).stream().filter(s.provenance::contains).toList();
        final List<CoSuggestion> suggestions = complements(basket, c -> chatDao.findCoPurchaseCounts(s.pack, c), minSupport, limit);
        final Map<String, Integer> support = new HashMap<>();
        suggestions.forEach(x -> support.put(x.code(), x.supportCount()));
        final List<String> ranked = rankByBusinessControls(suggestions.stream().map(CoSuggestion::code).toList());
        return new ToolResult(ranked.stream().map(c -> new CoSuggestion(c, support.get(c))).toList(), ranked,
                List.of(new TraceStep(label, "Orders placed by dealers (anonymous counts)", "all time", "bought together " + minSupport + "+ times")));
    }

    @SuppressWarnings("unchecked")
    private ToolResult toolPlanBudget(final TurnState s, final Map<String, Object> args)
    {
        final long budgetMinor = argNumber(args, "budgetMinor", 1, 1_000_000_000_000L, 0);
        final int cap = cfgInt("budget.candidateCap", 30);
        final List<BudgetCandidate> candidates = new ArrayList<>();
        if (args.get("usualItems") instanceof List<?> list) // built by the workflow from history, never by the model
        {
            for (final Object o : list)
            {
                final Map<String, Object> m = (Map<String, Object>) o;
                final OptionalLong price = chatDao.findUnitPriceMinor(s.customerId, (String) m.get("code"));
                if (price.isPresent() && candidates.size() < cap)
                {
                    candidates.add(new BudgetCandidate((String) m.get("code"), price.getAsLong(), ((Number) m.get("quantity")).intValue(),
                            ((Number) m.get("score")).intValue()));
                }
            }
        }
        final BudgetPlan plan = planBudget(candidates, budgetMinor);
        return new ToolResult(plan, plan.lines().stream().map(BudgetLine::code).toList(), List.of(new TraceStep(
                "Fitted your usual items into the budget", "Your orders and your contract prices", "last 365 days", "never above the budget")));
    }

    /** PA-06: the browser-held list is untrusted, so it is re-validated against the catalogue here (A11). */
    private ToolResult toolReviewList(final TurnState s)
    {
        final List<OrderLine> valid = new ArrayList<>();
        for (final PurchaseLine line : s.request.purchaseList())
        {
            if (line.quantity() >= 1 && line.quantity() <= 100_000 && chatDao.findProduct(s.pack, line.productCode()) != null)
            {
                valid.add(new OrderLine(line.productCode(), line.quantity()));
            }
        }
        final List<Order> orders = ownOrders(s, 365);
        final List<Order> pending = orders.stream().filter(o -> o.status() == OrderStatus.PENDING || o.status() == OrderStatus.PROCESSING).toList();
        final List<ReplenishmentItem> due = replenishment(orders, s.today, cfgInt("replenishment.windowDays", 180),
                cfgInt("replenishment.minOrders", 3), 0.9);
        final List<CoSuggestion> raw = complements(valid.stream().map(OrderLine::productCode).toList(),
                c -> chatDao.findCoPurchaseCounts(s.pack, c), cfgInt("crossSell.minSupport", 2), 3);
        final List<String> ranked = rankByBusinessControls(raw.stream().map(CoSuggestion::code).toList());
        final List<CoSuggestion> ordered = ranked.stream().map(c -> raw.stream().filter(x -> x.code().equals(c)).findFirst().orElseThrow()).toList();
        final List<BasketFlag> flags = reviewBasket(valid, due, pending, ordered);
        final List<String> codes = new ArrayList<>(valid.stream().map(OrderLine::productCode).toList());
        flags.forEach(f -> codes.add(f.code()));
        return new ToolResult(flags, codes.stream().distinct().toList(), List.of(new TraceStep("Reviewed your list against your history and open orders",
                ORDERS_SOURCE, "last 365 days", flags.size() + " observations")));
    }

    public record QuickLine(String rawText, String resolvedCode, Integer quantity, String status, String issue, List<String> options)
    {
    }

    private ToolResult toolQuickOrder(final TurnState s, final Map<String, Object> args)
    {
        final List<QuickLine> out = new ArrayList<>();
        for (final RawQuickLine raw : parseQuickOrder(argString(args, "rawText", "")))
        {
            out.add(validateQuickLine(s, raw));
        }
        final List<String> codes = new ArrayList<>();
        out.forEach(l ->
        {
            if (l.resolvedCode() != null)
            {
                codes.add(l.resolvedCode());
            }
            codes.addAll(l.options());
        });
        return new ToolResult(out, codes, List.of());
    }

    private QuickLine validateQuickLine(final TurnState s, final RawQuickLine raw)
    {
        Product p;
        if ("DESCRIPTION".equals(raw.kind()))
        {
            final List<String> words = List.of(raw.description().toLowerCase(Locale.ROOT).split("\\s+"));
            final List<Product> all = chatDao.findProducts(s.pack, words, null, Map.of(), 5);
            final List<Product> active = all.stream().filter(c -> !c.discontinued()).toList();
            final List<Product> pool = active.isEmpty() ? all : active;
            final List<Product> strict = pool.stream().filter(c -> words.stream().allMatch(
                    w -> (c.name() + " " + c.description() + " " + String.join(" ", c.attributes().values())).toLowerCase(Locale.ROOT).contains(w))).toList();
            final List<Product> found = strict.isEmpty() ? pool : strict; // every word must match: "cable ties" is not "cable 100m"
            if (found.size() > 1)
            {
                return new QuickLine(raw.rawText(), null, raw.quantity(), "AMBIGUOUS", "Several products match", found.stream().map(Product::code).toList());
            }
            p = found.isEmpty() ? null : found.get(0);
        }
        else
        {
            p = chatDao.findProduct(s.pack, raw.codeHint());
        }
        if (p == null)
        {
            return new QuickLine(raw.rawText(), null, raw.quantity(), "NOT_FOUND", "Not found in the catalogue", List.of());
        }
        String status = "OK";
        String issue = null;
        if (p.discontinued() || "REPLACEMENT_OF".equals(raw.kind()))
        {
            final Product replacement = p.replacementCode() == null ? null : chatDao.findProduct(s.pack, p.replacementCode());
            if (replacement != null)
            {
                issue = p.code() + " is replaced by " + replacement.code();
                p = replacement;
                status = "REPLACED";
            }
            else if (p.discontinued())
            {
                return new QuickLine(raw.rawText(), null, raw.quantity(), "DISCONTINUED_NO_REPLACEMENT", p.code() + " is discontinued with no replacement", List.of());
            }
        }
        return raw.quantity() == null ? new QuickLine(raw.rawText(), p.code(), null, "QUANTITY_NEEDED", "Quantity needed", List.of())
                : new QuickLine(raw.rawText(), p.code(), raw.quantity(), status, issue, List.of());
    }

    /** D16, KN-01: "shortlist" mode returns a lexical shortlist; "entryIds" mode returns approved text for ids chosen from it. */
    private ToolResult toolFaq(final TurnState s, final Map<String, Object> args)
    {
        final List<Faq> all = chatDao.findFaqs(s.pack);
        final List<String> ids = argStrings(args, "entryIds", 3);
        if (!ids.isEmpty())
        {
            final List<Faq> chosen = all.stream().filter(f -> ids.contains(f.id()) && s.provenance.contains(f.id())).toList();
            return new ToolResult(chosen, chosen.stream().map(Faq::id).toList(), List.of());
        }
        final Set<String> words = new HashSet<>(argStrings(args, "keywords", 12).stream().map(w -> w.toLowerCase(Locale.ROOT)).toList());
        final java.util.function.ToIntFunction<Faq> overlap = f ->
        {
            final String hay = (f.question() + " " + f.answer()).toLowerCase(Locale.ROOT);
            return (int) words.stream().filter(w -> w.length() > 2 && hay.contains(w)).count();
        };
        final List<Faq> top = all.stream().filter(f -> overlap.applyAsInt(f) > 0)
                .sorted(Comparator.comparingInt((Faq f) -> -overlap.applyAsInt(f)).thenComparing(Faq::id)).limit(5).toList();
        return new ToolResult(top, top.stream().map(Faq::id).toList(), List.of());
    }

    // ==================================================================================== hydration (D26: after the model is done)
    private static final String CURRENCY_FALLBACK = "INR";

    private String currency()
    {
        final String c = chatDao.findCurrency();
        return c == null || c.isBlank() ? CURRENCY_FALLBACK : c;
    }

    public static String formatMoney(final long minor, final String currency)
    {
        return String.format(Locale.ROOT, "%s %,d.%02d", currency, minor / 100, Math.abs(minor % 100));
    }

    private static long toMinor(final double major)
    {
        return Math.round(major * 100);
    }

    /** Registers every textual form of an amount so the outbound guard can block it. */
    private void markSecret(final TurnState s, final long minor)
    {
        s.secrets.add(String.valueOf(minor));
        s.secrets.add(String.format(Locale.ROOT, "%d.%02d", minor / 100, Math.abs(minor % 100)));
        s.secrets.add(String.format(Locale.ROOT, "%,d.%02d", minor / 100, Math.abs(minor % 100)));
    }

    private String money(final TurnState s, final long minor)
    {
        markSecret(s, minor);
        return formatMoney(minor, currency());
    }

    private String nameOf(final TurnState s, final String code)
    {
        final Product p = chatDao.findProduct(s.pack, code);
        return p == null ? code : p.name();
    }

    private ProductCard card(final TurnState s, final String code, final Map<String, String> reasons)
    {
        final OptionalLong unit = chatDao.findUnitPriceMinor(s.customerId, code);
        final Stock stock = demoStock(code);
        return new ProductCard(code, nameOf(s, code), unit.isPresent() ? money(s, unit.getAsLong()) : null, stock.label(), true, reasons);
    }

    private ListLine line(final TurnState s, final String code, final int quantity, final String origin, final String issue)
    {
        final OptionalLong unit = chatDao.findUnitPriceMinor(s.customerId, code);
        return new ListLine(code, nameOf(s, code), quantity, null, origin, unit.isPresent() ? money(s, unit.getAsLong() * quantity) : null, issue);
    }

    private OrderSummary orderSummary(final Order o)
    {
        return new OrderSummary(o.code(), o.placedOn().toString(), o.status().name(), formatMoney(o.totalMinor(), o.currency()),
                o.trackingReference(), "Commerce order data");
    }

    private static Block block(final String id, final String type, final String text, final String noticeKey)
    {
        return new Block(id, type, text, noticeKey, null, null, null, null, null, null, null, false);
    }

    private static Block notice(final String id, final String noticeKey, final String text)
    {
        return block(id, "notice", text, noticeKey);
    }

    // ==================================================================================== template texts (A13 fallback)
    private static final Map<String, String> TEMPLATES = Map.ofEntries(
            Map.entry("next_order", "Based on your order history, {{S:dueCount}} items look due for reordering ({{S:overdueCount}} overdue). These are estimates from your usual rhythm."),
            Map.entry("next_order_none", "I could not find items due for reordering from your order history."),
            Map.entry("budget_plan", "I fitted your usual items into your budget of {{S:budget}}. The list totals {{S:total}}; {{S:excludedCount}} items did not fit."),
            Map.entry("budget_clarify", "What budget would you like to plan for?"),
            Map.entry("find_product", "I found {{S:count}} products that fit your need."),
            Map.entry("find_none", "I could not find a product that fits. Tell me more about the use, surface or size and I will search again."),
            Map.entry("quick_order", "I read {{S:lineCount}} lines: {{S:okCount}} ready and {{S:issueCount}} that need your attention."),
            Map.entry("order_lookup", "I found {{S:count}} matching orders."),
            Map.entry("order_none", "I could not find matching orders in your account."),
            Map.entry("compare", "Here is how the products compare, based only on the catalogue attributes shown."),
            Map.entry("basket_review", "I reviewed your list and have {{S:flagCount}} observations."),
            Map.entry("basket_empty", "Your purchase list is empty, so there is nothing to review yet."),
            Map.entry("reorder", "I prepared a list from your previous order with {{S:lineCount}} lines."),
            Map.entry("reorder_none", "I could not find that order in your account."),
            Map.entry("product_qa", "Here is what the catalogue says about this product."),
            Map.entry("qa_none", "I could not find that product in the catalogue."),
            Map.entry("faq_none", "I could not find an approved answer to that."),
            Map.entry("off_topic", "I can help with finding products, preparing orders and checking your orders. What would you like to do?"),
            Map.entry("unsupported", "I cannot do that. I can suggest products and prepare a list, but I never place orders. You can check out in the storefront."),
            Map.entry("clarify", "Could you tell me a little more about what you need?"),
            Map.entry("cap_reached", "You have reached your usage limit for now. Please try again later."),
            Map.entry("unavailable", "The assistant is not available right now."),
            Map.entry("contact", "I could not resolve this. Please contact your account manager."),
            Map.entry("translation_failed", "I could not safely translate this reply, so it is shown in English."),
            Map.entry("partial", "I could only complete part of this request in time."));
    /** Fixed template text with {{S:slot}} placeholders (also used by the test fake provider). */
    public static String templateText(final String key)
    {
        return TEMPLATES.get(key);
    }

    private static final Set<String> UNRESOLVED = Set.of("find_none", "faq_none", "qa_none", "order_none", "reorder_none", "next_order_none", "unsupported");
    private static final Pattern SLOT = Pattern.compile("\\{\\{S:([A-Za-z0-9_]+)}}");

    /** Returns null if the text uses a slot the backend did not provide. */
    private static String fillSlots(final String text, final Map<String, String> slots)
    {
        final Matcher m = SLOT.matcher(text);
        final StringBuilder out = new StringBuilder();
        while (m.find())
        {
            final String v = slots.get(m.group(1));
            if (v == null)
            {
                return null;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(v));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static boolean hasLooseDigits(final String text, final Collection<String> allowedLiterals)
    {
        final Matcher digits = Pattern.compile("\\d+(?:[.,]\\d+)*").matcher(SLOT.matcher(text).replaceAll(""));
        while (digits.find())
        {
            final String d = digits.group();
            if (allowedLiterals.stream().noneMatch(l -> l.contains(d)))
            {
                return true;
            }
        }
        return false;
    }

    // ==================================================================================== model-assisted steps
    private record Pick(String code, String fit, List<String> matchedAttributes)
    {
    }

    /** JUDGE: the model picks codes and attribute keys from the candidates; the backend validates both (provenance). */
    private List<Pick> judge(final TurnState s, final String need, final List<String> constraints, final List<Map<String, Object>> candidates,
            final Map<String, List<String>> allowedKeys)
    {
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("need", need);
        fields.put("constraints", constraints);
        fields.put("candidates", candidates);
        fields.put("language", "en");
        final JsonNode selected = modelCall(s, "JUDGE", "judge.v1", fields).json().path("selected");
        final List<Pick> picks = new ArrayList<>();
        int rejected = 0;
        for (final JsonNode node : selected)
        {
            final String code = node.path("code").asText();
            if (!s.provenance.contains(code) || !allowedKeys.containsKey(code))
            {
                rejected++;
                continue;
            }
            final List<String> attrs = new ArrayList<>();
            node.path("matchedAttributes").forEach(a ->
            {
                if (allowedKeys.get(code).contains(a.asText()))
                {
                    attrs.add(a.asText());
                }
            });
            picks.add(new Pick(code, node.path("fit").asText("PARTIAL"), attrs));
        }
        if (rejected > 0)
        {
            audit("id_rejected", "responseId", s.responseId, "count", String.valueOf(rejected));
        }
        return picks;
    }

    /**
     * COMPOSE or EXPLAIN plus validate-then-reveal (A13): slots only, no loose digits, no unknown product codes, text agrees with
     * the table. Any failure swaps in the fixed template, so a bad model answer never reaches the dealer.
     */
    private String compose(final TurnState s, final String templateKey, final Map<String, Object> facts, final Map<String, String> slots,
            final Comparison table)
    {
        final String filled = fillSlots(TEMPLATES.get(templateKey), slots);
        final String fallback = filled == null ? TEMPLATES.get(templateKey) : filled;
        try
        {
            final String task = table == null ? "COMPOSE" : "EXPLAIN";
            final Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("intent", templateKey);
            if (table != null)
            {
                final List<Map<String, Object>> rows = new ArrayList<>();
                table.rows().forEach(r -> rows.add(Map.of("attribute", r.attributeLabel(), "values", r.values())));
                fields.put("comparison", Map.of("columns", table.columns(), "rows", rows, "notes", table.notes()));
            }
            fields.put("facts", facts);
            fields.put("slots", new ArrayList<>(slots.keySet())); // names only: amounts never go to the model
            fields.put("language", "en");
            final String text = modelCall(s, task, table == null ? "compose.v1" : "explain.v1", fields).json().path("text").asText("");
            return acceptModelText(s, text, slots, facts, table) ? fillSlots(text, slots) : fallback;
        }
        catch (final RuntimeException e)
        {
            audit("compose_fallback", "responseId", s.responseId, "errorCode", e.getClass().getSimpleName());
            return fallback;
        }
    }

    private boolean acceptModelText(final TurnState s, final String text, final Map<String, String> slots, final Map<String, Object> facts,
            final Comparison table)
    {
        final String reason;
        final List<String> allowed = new ArrayList<>();
        facts.values().forEach(v -> allowed.add(String.valueOf(v)));
        if (table != null)
        {
            table.rows().forEach(r -> allowed.addAll(r.values()));
        }
        if (text.isBlank() || fillSlots(text, slots) == null)
        {
            reason = "bad_slot";
        }
        else if (hasLooseDigits(text, allowed))
        {
            reason = "loose_digits";
        }
        else if (CODE.matcher(text).results().anyMatch(m -> !s.provenance.contains(m.group())))
        {
            reason = "unknown_code";
        }
        else if (table != null && !textMatchesTable(fillSlots(text, slots), table))
        {
            reason = "text_table_mismatch";
        }
        else
        {
            return true;
        }
        audit("text_rejected", "responseId", s.responseId, "errorCode", reason);
        return false;
    }

    /** Every number/unit the explanation mentions must appear in the comparison table (A13 rule 3). */
    private static boolean textMatchesTable(final String text, final Comparison table)
    {
        final StringBuilder cells = new StringBuilder();
        table.rows().forEach(r -> r.values().forEach(v -> cells.append(v).append('|')));
        final String haystack = cells.toString().toLowerCase(Locale.ROOT);
        return Pattern.compile("\\d+(?:[.,]\\d+)?\\s?[A-Za-z%]*").matcher(text).results()
                .allMatch(m -> haystack.contains(m.group().trim().toLowerCase(Locale.ROOT)));
    }

    private static final Pattern PROTECTED_VALUES = Pattern.compile("[A-Z]{2,4}-\\d{3,6}|\\d[\\d,.]*\\d|\\d|INR");
    private static final Pattern PROTECTED_TOKEN = Pattern.compile("\\[P\\d+]");

    /**
     * D17, CF-05: codes, order numbers and numbers become [P1], [P2]... before the call and must all come back in the same
     * order. One retry; otherwise empty, and the caller falls back to English with a notice.
     */
    private Optional<String> translate(final TurnState s, final String text, final String from, final String to)
    {
        final List<String> originals = new ArrayList<>();
        final Matcher m = PROTECTED_VALUES.matcher(text);
        final StringBuilder protectedText = new StringBuilder();
        while (m.find())
        {
            originals.add(m.group());
            m.appendReplacement(protectedText, Matcher.quoteReplacement("[P" + originals.size() + "]"));
        }
        m.appendTail(protectedText);
        final List<String> expected = new ArrayList<>();
        for (int i = 1; i <= originals.size(); i++)
        {
            expected.add("[P" + i + "]");
        }
        for (int attempt = 0; attempt < 2; attempt++)
        {
            final Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("text", protectedText.toString());
            fields.put("sourceLanguage", from);
            fields.put("targetLanguage", to);
            final String out = modelCall(s, "TRANSLATE", "translate.v1", fields).json().path("text").asText("");
            final List<String> back = PROTECTED_TOKEN.matcher(out).results().map(MatchResult -> MatchResult.group()).toList();
            if (back.equals(expected) && !out.isBlank())
            {
                String restored = out;
                for (int i = originals.size(); i >= 1; i--)
                {
                    restored = restored.replace("[P" + i + "]", originals.get(i - 1));
                }
                return Optional.of(restored);
            }
        }
        return Optional.empty();
    }

    /** Deterministic script-based detection so translation does not depend on the model. ASSUMPTION: Hindi first (N5). */
    private static String detectLanguage(final String text)
    {
        return text.chars().anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.DEVANAGARI) ? "hi" : "en";
    }

    // ==================================================================================== decision (DECIDE) and validation
    private enum Intent
    {
        NEXT_ORDER("RI-05"), FIND_PRODUCT("PD-01"), QUICK_ORDER("PA-03"), BUDGET_PLAN("PA-04"), ORDER_LOOKUP("OM-02"), COMPARE("PD-04"),
        BASKET_REVIEW("PA-06"), REORDER("PA-07"), PRODUCT_QA("PD-03"), FAQ("KN-01"), OFF_TOPIC(null), UNSUPPORTED(null), CLARIFY(null),
        SWITCH_PACK(null);

        final String capabilityId;

        Intent(final String capabilityId)
        {
            this.capabilityId = capabilityId;
        }
    }

    /** Parsed and range-checked DECIDE output. Amounts are major units as the dealer said them. */
    private record Decision(Intent intent, double confidence, List<String> keywords, String categoryHint, Map<String, String> filters,
            Double budgetAmount, Double maxUnitAmount, List<String> codes, String orderStatus, Double orderMinAmount, String orderPeriod,
            boolean wantsLatestTracking, String eventSuggestion)
    {
    }

    private static final Set<String> EVENTS = Set.of("VIEWED", "COMPARED", "SHORTLISTED", "REQUESTED_NOT_FOUND");
    private static final Set<String> ORDER_STATUSES = Set.of("ALL", "UNDELIVERED", "PENDING", "DELIVERED");
    private static final Set<String> ORDER_PERIODS = Set.of("NONE", "LAST_MONTH", "LAST_30_DAYS", "THIS_MONTH");

    private Decision decide(final TurnState s)
    {
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("message", s.workingMessage);
        fields.put("capabilities", Arrays.stream(Intent.values()).filter(i -> i.capabilityId == null || isEnabled(i.capabilityId))
                .map(Enum::name).toList());
        fields.put("terminology", cfg("pack." + s.pack + ".terminology", ""));
        fields.put("turns", boundedTurns(s.request));
        fields.put("shortlistCodes", s.request.shortlistCodes().stream().filter(c -> CODE.matcher(c).matches()).limit(10).toList());
        fields.put("language", "en");
        return validateDecision(s, modelCall(s, "DECIDE", "decide.v1", fields).json());
    }

    /** Browser-held context is untrusted (A11): bounded here and re-masked by the sanitiser on every call. */
    private List<Map<String, String>> boundedTurns(final TurnRequest request)
    {
        int budget = cfgInt("context.maxChars", 2000);
        final int maxTurns = cfgInt("context.maxTurns", 6);
        final List<Map<String, String>> out = new ArrayList<>();
        for (int i = request.turns().size() - 1; i >= 0 && out.size() < maxTurns; i--)
        {
            final ContextTurn t = request.turns().get(i);
            final String text = t.text() == null ? "" : t.text();
            if (text.length() > budget)
            {
                break;
            }
            budget -= text.length();
            out.add(0, Map.of("role", "assistant".equals(t.role()) ? "assistant" : "user", "text", text));
        }
        return out;
    }

    private Decision validateDecision(final TurnState s, final JsonNode json)
    {
        Intent intent;
        try
        {
            intent = Intent.valueOf(json.path("intent").asText("CLARIFY"));
        }
        catch (final IllegalArgumentException e)
        {
            audit("decision_adjusted", "responseId", s.responseId, "errorCode", "unknown_intent");
            intent = Intent.CLARIFY;
        }
        final double confidence = Math.max(0, Math.min(1, json.path("confidence").asDouble(0)));
        if (confidence < cfgInt("decision.minConfidencePercent", 40) / 100.0 && intent != Intent.OFF_TOPIC)
        {
            intent = Intent.CLARIFY;
        }
        final Set<String> allowedFilterKeys = new HashSet<>(Arrays.asList(cfg("pack." + s.pack + ".searchFields", "").split(",")));
        final Map<String, String> filters = new LinkedHashMap<>();
        for (final JsonNode f : json.path("filters"))
        {
            if (allowedFilterKeys.contains(f.path("key").asText()) && filters.size() < 8)
            {
                filters.put(f.path("key").asText(), StringUtils.left(f.path("value").asText(), 60));
            }
            else
            {
                audit("decision_adjusted", "responseId", s.responseId, "errorCode", "filter_key_rejected");
            }
        }
        final List<String> codes = new ArrayList<>();
        json.path("codes").forEach(c ->
        {
            if (CODE.matcher(c.asText()).matches() && codes.size() < 10)
            {
                codes.add(c.asText());
            }
        });
        final List<String> keywords = new ArrayList<>();
        json.path("keywords").forEach(k ->
        {
            if (keywords.size() < 12 && !k.asText().isBlank())
            {
                keywords.add(StringUtils.left(k.asText(), 40));
            }
        });
        final String status = ORDER_STATUSES.contains(json.path("orderStatus").asText()) ? json.path("orderStatus").asText() : "ALL";
        final String period = ORDER_PERIODS.contains(json.path("orderPeriod").asText()) ? json.path("orderPeriod").asText() : "NONE";
        final String event = json.path("eventSuggestion").asText("NONE");
        return new Decision(intent, confidence, keywords, StringUtils.left(json.path("categoryHint").asText(""), 60), filters,
                amount(json.path("budgetAmount")), amount(json.path("maxUnitAmount")), codes, status, amount(json.path("orderMinAmount")),
                period, json.path("wantsLatestTracking").asBoolean(false), EVENTS.contains(event) ? event : "NONE");
    }

    private static Double amount(final JsonNode node)
    {
        return !node.isNumber() || node.asDouble() <= 0 || node.asDouble() > 10_000_000_000.0 ? null : node.asDouble();
    }

    // ==================================================================================== workflows (deterministic graphs per intent)
    /** @param table non-null selects the EXPLAIN task and the text-vs-table check */
    private record Output(List<Block> blocks, String templateKey, Map<String, Object> facts, Map<String, String> slots, Comparison table,
            List<String> actions)
    {
        static Output of(final List<Block> blocks, final String templateKey, final Map<String, Object> facts, final Map<String, String> slots)
        {
            return new Output(blocks, templateKey, facts, slots, null, List.of());
        }
    }

    private ToolResult step(final TurnState s, final String labelKey, final String tool, final Map<String, Object> args)
    {
        return s.step(labelKey, () -> runTool(s, tool, args));
    }

    private Output runWorkflow(final TurnState s, final Decision d)
    {
        return switch (d.intent())
        {
            case NEXT_ORDER -> wfNextOrder(s);
            case FIND_PRODUCT -> wfFindProduct(s, d);
            case QUICK_ORDER -> wfQuickOrder(s);
            case BUDGET_PLAN -> wfBudgetPlan(s, d);
            case ORDER_LOOKUP -> wfOrderLookup(s, d);
            case COMPARE -> wfCompare(s, d);
            case BASKET_REVIEW -> wfBasketReview(s);
            case REORDER -> wfReorder(s);
            case PRODUCT_QA -> wfProductQa(s, d);
            case FAQ -> wfFaq(s, d);
            case OFF_TOPIC -> Output.of(List.of(), "off_topic", Map.of(), Map.of());
            case UNSUPPORTED, SWITCH_PACK -> Output.of(List.of(), "unsupported", Map.of(), Map.of()); // SC-10 is configuration only
            case CLARIFY -> Output.of(List.of(), "clarify", Map.of(), Map.of());
        };
    }

    /** NEXT_ORDER (SC-01, SC-09): [history, replenishment net of pending, pending] then complements. */
    private Output wfNextOrder(final TurnState s)
    {
        final List<ToolResult> group = s.step("step.checkingHistory", () -> runParallel(s,
                List.of(new ToolCall("getCustomerPurchaseHistory", Map.of("windowDays", 180)),
                        new ToolCall("getReplenishmentRecommendations", Map.of("netPending", true)), new ToolCall("getPendingOrders", Map.of()))));
        final List<ReplenishmentItem> due = group.get(1).as();
        final List<Order> pending = group.get(2).as();
        if (due.isEmpty())
        {
            return Output.of(List.of(), "next_order_none", Map.of(), Map.of());
        }
        final List<Recommendation> recs = due.stream()
                .map(i -> new Recommendation(i.code(), nameOf(s, i.code()), i.suggestedQuantity(), i.overdue() ? "OVERDUE" : "DUE", true)).toList();
        final List<Block> blocks = new ArrayList<>();
        blocks.add(new Block("b-due", "recommendationList", null, null, null, null, null, null, recs, null, null, true));
        final List<CoSuggestion> suggestions = step(s, "step.lookingForComplements", "getComplementaryProducts",
                Map.of("codes", due.stream().map(ReplenishmentItem::code).toList())).as();
        if (!suggestions.isEmpty())
        {
            blocks.add(new Block("b-complements", "productCards", null, null,
                    suggestions.stream().map(x -> card(s, x.code(), Map.of("reason", "COMPLEMENT"))).toList(), null, null, null, null, null, null, false));
        }
        final Map<String, String> slots = new LinkedHashMap<>();
        slots.put("dueCount", String.valueOf(due.size()));
        slots.put("overdueCount", String.valueOf(due.stream().filter(ReplenishmentItem::overdue).count()));
        slots.put("pendingCount", String.valueOf(pending.size()));
        return new Output(blocks, "next_order", Map.of("dueCount", due.size(), "pendingOrders", pending.size(), "estimate", true), slots, null,
                List.of("ADD_ALL_TO_LIST"));
    }

    /** FIND_PRODUCT (SC-02): search, optional price limit in Java, relevance judge, hydrate. */
    private Output wfFindProduct(final TurnState s, final Decision d)
    {
        if (d.keywords().isEmpty() && d.categoryHint().isBlank())
        {
            return Output.of(List.of(new Block("b-clarify", "clarification", "What will you use it for?", null, null, null, null, null, null, null,
                    List.of("Surface or material", "Size or quantity", "Budget per unit"), false)), "clarify", Map.of(), Map.of());
        }
        final Map<String, Object> args = new LinkedHashMap<>();
        args.put("keywords", d.keywords());
        args.put("filters", d.filters());
        args.put("limit", 20);
        List<Product> candidates = step(s, "step.searchingCatalogue", "searchProducts", args).as();
        if (d.maxUnitAmount() != null && !candidates.isEmpty())
        {
            final List<String> ok = step(s, "step.applyingPriceLimit", "getBudgetEligibleProducts",
                    Map.of("candidateCodes", candidates.stream().map(Product::code).toList(), "maxUnitMinor", toMinor(d.maxUnitAmount()))).as();
            candidates = candidates.stream().filter(p -> ok.contains(p.code())).toList();
        }
        if (candidates.isEmpty())
        {
            return Output.of(List.of(), "find_none", Map.of(), Map.of());
        }
        final List<Map<String, Object>> descriptors = new ArrayList<>();
        final Map<String, List<String>> keys = new LinkedHashMap<>();
        for (final Product p : candidates) // candidate text only, never prices or stock
        {
            descriptors.add(Map.of("code", p.code(), "name", p.name(), "description", p.description(), "attributes", p.attributes()));
            keys.put(p.code(), new ArrayList<>(p.attributes().keySet()));
        }
        final List<String> constraints = d.filters().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList();
        final List<Pick> picks = s.step("step.judgingFit", () -> judge(s, String.join(" ", d.keywords()), constraints, descriptors, keys));
        final List<ProductCard> cards = new ArrayList<>();
        for (final Pick p : picks)
        {
            final Map<String, String> reasons = new LinkedHashMap<>();
            reasons.put("fit", p.fit());
            p.matchedAttributes().forEach(a -> reasons.put("matches:" + a, "yes"));
            cards.add(card(s, p.code(), reasons));
        }
        if (cards.isEmpty())
        {
            return Output.of(List.of(), "find_none", Map.of(), Map.of());
        }
        return new Output(List.of(new Block("b-products", "productCards", null, null, cards, null, null, null, null, null, null, false)),
                "find_product", Map.of("count", cards.size()), Map.of("count", String.valueOf(cards.size())), null, List.of("ADD_TO_LIST"));
    }

    /** QUICK_ORDER (SC-03). ASSUMPTION: deterministic grammar only; unresolved lines go back to the dealer (AS-16). */
    private Output wfQuickOrder(final TurnState s)
    {
        final List<QuickLine> lines = step(s, "step.readingYourList", "validateQuickOrderLines", Map.of("rawText", s.workingMessage)).as();
        final List<ListLine> listLines = new ArrayList<>();
        final List<Block> blocks = new ArrayList<>();
        int ok = 0;
        for (final QuickLine l : lines)
        {
            if (l.resolvedCode() != null && l.quantity() != null && !"QUANTITY_NEEDED".equals(l.status()))
            {
                listLines.add(line(s, l.resolvedCode(), l.quantity(), "quick order", l.issue()));
                ok++;
            }
        }
        blocks.add(new Block("b-list", "purchaseList", null, null, null, null, null, listLines, null, null, null, false));
        for (final QuickLine l : lines)
        {
            if (l.resolvedCode() == null || l.quantity() == null || "QUANTITY_NEEDED".equals(l.status()))
            {
                blocks.add(notice("b-issue-" + blocks.size(), "quickOrderIssue", l.rawText() + ": " + (l.issue() == null ? l.status() : l.issue())));
            }
        }
        final Map<String, String> slots = new LinkedHashMap<>();
        slots.put("lineCount", String.valueOf(lines.size()));
        slots.put("okCount", String.valueOf(ok));
        slots.put("issueCount", String.valueOf(lines.size() - ok));
        return new Output(blocks, "quick_order", Map.of("lines", lines.size()), slots, null, List.of());
    }

    /** BUDGET_PLAN (SC-04): usual items from history, due items first, fitted into the budget in integer arithmetic. */
    private Output wfBudgetPlan(final TurnState s, final Decision d)
    {
        if (d.budgetAmount() == null)
        {
            return Output.of(List.of(), "budget_clarify", Map.of(), Map.of());
        }
        final List<ToolResult> group = s.step("step.checkingHistory", () -> runParallel(s,
                List.of(new ToolCall("getCustomerPurchaseHistory", Map.of("windowDays", 365)),
                        new ToolCall("getReplenishmentRecommendations", Map.of()))));
        final List<HistoryEntry> history = group.get(0).as();
        final Set<String> dueCodes = new HashSet<>();
        group.get(1).<List<ReplenishmentItem>> as().forEach(i -> dueCodes.add(i.code()));
        final List<Map<String, Object>> usual = new ArrayList<>();
        for (final HistoryEntry e : history)
        {
            usual.add(Map.of("code", e.code(), "quantity", Math.max(1, e.totalQuantity() / Math.max(1, e.orderCount())), "score",
                    e.orderCount() * 10 + (dueCodes.contains(e.code()) ? 100 : 0)));
        }
        final BudgetPlan plan = step(s, "step.fittingBudget", "planBudgetPurchase",
                Map.of("budgetMinor", toMinor(d.budgetAmount()), "usualItems", usual)).as();
        final List<ListLine> lines = plan.lines().stream()
                .map(l -> line(s, l.code(), l.quantity(), "budget plan", l.reduced() ? "Quantity reduced to fit" : null)).toList();
        final Map<String, String> slots = new LinkedHashMap<>();
        slots.put("budget", money(s, plan.budgetMinor()));
        slots.put("total", money(s, plan.totalMinor()));
        slots.put("excludedCount", String.valueOf(plan.excluded().size()));
        return new Output(List.of(new Block("b-plan", "purchaseList", null, null, null, null, null, lines, null, null, null, false)), "budget_plan",
                Map.of("lines", lines.size()), slots, null, List.of("ADD_ALL_TO_LIST"));
    }

    /** ORDER_LOOKUP (SC-06): filters extracted by the model are range-checked, resolved to dates in Java, scoped to the customer. */
    private Output wfOrderLookup(final TurnState s, final Decision d)
    {
        final Map<String, Object> args = new LinkedHashMap<>();
        args.put("status", d.orderStatus());
        if (d.orderMinAmount() != null)
        {
            args.put("minTotalMinor", toMinor(d.orderMinAmount()));
        }
        final LocalDate today = s.today;
        switch (d.orderPeriod())
        {
            case "LAST_MONTH" ->
            {
                final LocalDate first = today.withDayOfMonth(1).minusMonths(1);
                args.put("fromDate", first.toString());
                args.put("toDate", first.withDayOfMonth(first.lengthOfMonth()).toString());
            }
            case "LAST_30_DAYS" ->
            {
                args.put("fromDate", today.minusDays(30).toString());
                args.put("toDate", today.toString());
            }
            case "THIS_MONTH" ->
            {
                args.put("fromDate", today.withDayOfMonth(1).toString());
                args.put("toDate", today.toString());
            }
            default ->
            {
            }
        }
        final List<Order> orders = step(s, "step.searchingOrders", "searchOrders", args).as();
        if (orders.isEmpty())
        {
            return Output.of(List.of(), "order_none", Map.of(), Map.of());
        }
        if (d.wantsLatestTracking())
        {
            step(s, "step.checkingDelivery", "getOrderTracking", Map.of("orderCode", orders.get(0).code()));
        }
        return Output.of(List.of(new Block("b-orders", "orderSummary", null, null, null, null, orders.stream().map(this::orderSummary).toList(), null, null,
                null, null, false)), "order_lookup", Map.of("count", orders.size()), Map.of("count", String.valueOf(orders.size())));
    }

    /** COMPARE (SC-07): products from the message, the browser shortlist (re-validated by the catalogue) or a search; table first. */
    private Output wfCompare(final TurnState s, final Decision d)
    {
        final Set<String> codes = new LinkedHashSet<>(d.codes());
        codes.addAll(s.request.shortlistCodes());
        if (codes.size() < 2 && !d.keywords().isEmpty())
        {
            final List<Product> found = step(s, "step.searchingCatalogue", "searchProducts", Map.of("keywords", d.keywords(), "limit", 3)).as();
            found.forEach(p -> codes.add(p.code()));
        }
        if (codes.size() < 2)
        {
            return Output.of(List.of(new Block("b-clarify", "clarification", "Which products should I compare?", null, null, null, null, null, null, null,
                    List.of(), false)), "clarify", Map.of(), Map.of());
        }
        final Comparison table = step(s, "step.comparingAttributes", "getComparisonAttributes",
                Map.of("codes", new ArrayList<>(codes).subList(0, Math.min(4, codes.size())))).as();
        return new Output(List.of(new Block("b-compare", "comparison", null, null, null, table, null, null, null, null, null, false)), "compare",
                Map.of("attributes", table.rows().size()), Map.of(), table, List.of());
    }

    /** BASKET_REVIEW (SC-08): the list comes from the validated request body (A11), never from server state. */
    private Output wfBasketReview(final TurnState s)
    {
        if (s.request.purchaseList().isEmpty())
        {
            return Output.of(List.of(), "basket_empty", Map.of(), Map.of());
        }
        final List<BasketFlag> flags = step(s, "step.reviewingYourList", "reviewPurchaseList", Map.of()).as();
        final List<Recommendation> items = flags.stream()
                .map(f -> new Recommendation(f.code(), nameOf(s, f.code()), 0, f.type(), "FORGOTTEN_USUAL".equals(f.type()))).toList();
        return new Output(List.of(new Block("b-flags", "recommendationList", null, null, null, null, null, null, items, null, null, false)),
                "basket_review", Map.of("flags", flags.size()), Map.of("flagCount", String.valueOf(flags.size())), null, List.of());
    }

    private static final Pattern ORDER_CODE_IN_TEXT = Pattern.compile("\\b(?:ORD|PO)[-#]?\\d{4,}\\b|\\b\\d{8}\\b");

    /** REORDER (PA-07): lines of one of the customer's own orders become a labelled demo list; discontinued lines use replacements. */
    private Output wfReorder(final TurnState s)
    {
        final Matcher m = ORDER_CODE_IN_TEXT.matcher(s.workingMessage);
        final List<Order> found = (m.find() ? step(s, "step.findingOrder", "getOrderById", Map.of("orderCode", m.group()))
                : step(s, "step.findingOrder", "getLatestOrder", Map.of())).as();
        if (found.isEmpty())
        {
            return Output.of(List.of(), "reorder_none", Map.of(), Map.of());
        }
        final List<ListLine> lines = new ArrayList<>();
        for (final OrderLine l : found.get(0).lines())
        {
            String code = l.productCode();
            String issue = null;
            final List<Product> details = runTool(s, "getProductDetails", Map.of("codes", List.of(code))).as();
            if (!details.isEmpty() && details.get(0).discontinued())
            {
                final List<Product> alternatives = runTool(s, "getAlternatives", Map.of("code", code)).as();
                if (alternatives.isEmpty())
                {
                    lines.add(line(s, code, 0, "reorder", "Discontinued, no replacement"));
                    continue;
                }
                issue = code + " replaced by " + alternatives.get(0).code();
                code = alternatives.get(0).code();
            }
            lines.add(line(s, code, l.quantity(), "reorder", issue));
        }
        return Output.of(List.of(new Block("b-reorder", "purchaseList", null, null, null, null, null, lines, null, null, null, false)), "reorder",
                Map.of("lines", lines.size()), Map.of("lineCount", String.valueOf(lines.size())));
    }

    /** PRODUCT_QA (PD-03, PD-05): grounded in approved catalogue fields only. */
    private Output wfProductQa(final TurnState s, final Decision d)
    {
        List<String> codes = d.codes();
        if (codes.isEmpty() && !d.keywords().isEmpty())
        {
            final List<Product> found = step(s, "step.searchingCatalogue", "searchProducts", Map.of("keywords", d.keywords(), "limit", 1)).as();
            codes = found.stream().map(Product::code).toList();
        }
        final List<Product> products = step(s, "step.readingProductData", "getProductDetails", Map.of("codes", codes)).as();
        if (products.isEmpty())
        {
            return Output.of(List.of(), "qa_none", Map.of(), Map.of());
        }
        final Product p = products.get(0);
        final List<ComparisonRow> rows = new ArrayList<>();
        p.attributes().forEach((k, v) -> rows.add(new ComparisonRow(k, List.of(v))));
        final Comparison table = new Comparison(List.of(p.name()), rows, List.of());
        return new Output(List.of(new Block("b-details", "comparison", null, null, null, table, null, null, null, null, null, false)), "product_qa",
                Map.of("product", p.code()), Map.of(), table, List.of());
    }

    /** FAQ (KN-01, D16): lexical shortlist, the model picks ids, the answer is the approved text, never model prose. */
    private Output wfFaq(final TurnState s, final Decision d)
    {
        final List<Faq> shortlist = step(s, "step.searchingApprovedAnswers", "getFaqAnswer", Map.of("keywords", d.keywords())).as();
        if (shortlist.isEmpty())
        {
            return Output.of(List.of(), "faq_none", Map.of(), Map.of());
        }
        final List<Map<String, Object>> descriptors = new ArrayList<>();
        final Map<String, List<String>> keys = new LinkedHashMap<>();
        shortlist.forEach(f ->
        {
            descriptors.add(Map.of("code", f.id(), "name", f.question(), "description", "", "attributes", Map.of()));
            keys.put(f.id(), List.of());
        });
        final List<Pick> picked = s.step("step.pickingAnswer", () -> judge(s, String.join(" ", d.keywords()), List.of(), descriptors, keys));
        if (picked.isEmpty())
        {
            return Output.of(List.of(), "faq_none", Map.of(), Map.of());
        }
        final List<Faq> answers = step(s, "step.readingApprovedAnswer", "getFaqAnswer", Map.of("entryIds", List.of(picked.get(0).code()))).as();
        return new Output(List.of(block("b-faq", "text", answers.get(0).answer(), null)), null, Map.of(), Map.of(), null, List.of());
    }

    // ==================================================================================== orchestration
    @Override
    public TurnResult handle(final TurnRequest request, final ChatEventListener listener)
    {
        final LocalDate today = LocalDate.now(clock);
        final TurnState s = new TurnState(request, activePack(), today, listener, cfgInt("orchestration.maxSteps", 8),
                cfgInt("orchestration.maxSeconds", 9));
        final long start = System.currentTimeMillis();
        Intent intent = Intent.CLARIFY;
        final List<Block> blocks = new ArrayList<>();
        final List<String> actions = new ArrayList<>();
        try
        {
            if (!isEnabled("PL-04"))
            {
                return finish(s, intent, List.of(notice("b-notice", "unavailableNotice", TEMPLATES.get("unavailable"))), actions, start);
            }
            if (!checkAndIncrementUsage(s.customerId, today))
            {
                return finish(s, intent, List.of(notice("b-notice", "usageCap", TEMPLATES.get("cap_reached"))), actions, start);
            }
            s.language = detectLanguage(request.message());
            if (!"en".equals(s.language) && isEnabled("CF-05"))
            {
                final Optional<String> english = s.step("step.translating", () -> translate(s, request.message(), s.language, "en"));
                if (english.isEmpty())
                {
                    s.language = "en";
                    return finish(s, intent, List.of(notice("b-notice", "translationFailed", TEMPLATES.get("clarify"))), actions, start);
                }
                s.workingMessage = english.get();
            }
            final Decision decision = s.step("step.understanding", () -> decide(s));
            intent = decision.intent();
            if (intent.capabilityId != null && !isEnabled(intent.capabilityId))
            {
                intent = Intent.UNSUPPORTED;
            }
            final Output output = runWorkflow(s, intent == decision.intent() ? decision : new Decision(intent, decision.confidence(), List.of(),
                    "", Map.of(), null, null, List.of(), "ALL", null, "NONE", false, "NONE"));
            blocks.addAll(output.blocks());
            actions.addAll(output.actions());
            recordInterest(s, decision);
            if (output.templateKey() == null)
            {
                return finish(s, intent, blocks, actions, start); // approved FAQ text is already in the blocks
            }
            String text;
            if (output.facts().isEmpty() && output.slots().isEmpty())
            {
                text = TEMPLATES.get(output.templateKey());
            }
            else
            {
                text = s.step("step.writingReply", () -> compose(s, output.templateKey(), output.facts(), output.slots(), output.table()));
            }
            if (UNRESOLVED.contains(output.templateKey()))
            {
                text = text + " " + cfg("contact.text", TEMPLATES.get("contact")); // D31
            }
            blocks.add(block("b-text", "text", text, null));
            return finish(s, intent, translateOut(s, blocks), actions, start);
        }
        catch (final LimitExceededException e)
        {
            audit("limit_exceeded", "responseId", s.responseId, "errorCode", e.getMessage());
            blocks.add(notice("b-notice", "partialNotice", TEMPLATES.get("partial"))); // AS-15: a notice, not partial blocks
            return finish(s, intent, blocks, actions, start);
        }
        catch (final ToolRejectedException e)
        {
            audit("tool_rejected", "responseId", s.responseId, "errorCode", e.getMessage());
            return finish(s, intent, List.of(notice("b-notice", "toolRejected", TEMPLATES.get("clarify"))), actions, start);
        }
        catch (final SanitiserDisabledException | EgressBlockedException | ModelUnavailableException e)
        {
            audit("model_blocked", "responseId", s.responseId, "errorCode", e.getClass().getSimpleName());
            return finish(s, intent, List.of(notice("b-notice", "unavailableNotice", TEMPLATES.get("unavailable")),
                    block("b-contact", "text", cfg("contact.text", TEMPLATES.get("contact")), null)), actions, start);
        }
    }

    private List<Block> translateOut(final TurnState s, final List<Block> blocks)
    {
        if ("en".equals(s.language) || !isEnabled("CF-05"))
        {
            return blocks;
        }
        final List<Block> result = new ArrayList<>();
        for (final Block b : blocks)
        {
            if ("text".equals(b.type()) && b.text() != null)
            {
                final Optional<String> t = s.step("step.translating", () -> translate(s, b.text(), "en", s.language));
                if (t.isPresent())
                {
                    result.add(b.withText(t.get()));
                }
                else
                {
                    result.add(b);
                    result.add(notice("b-translation", "translationFailed", TEMPLATES.get("translation_failed")));
                }
            }
            else
            {
                result.add(b);
            }
        }
        return result;
    }

    /** Validate-then-reveal (A13): blocks first, then text in chunks, then done with the response id and the why-this trace. */
    private TurnResult finish(final TurnState s, final Intent intent, final List<Block> blocks, final List<String> actions, final long start)
    {
        for (final Block b : blocks)
        {
            if ("text".equals(b.type()) && b.text() != null)
            {
                s.listener.onEvent(new ChatEvent("block", null, null, null, b.withText(""), null, null, null, null));
                final String[] words = b.text().split("(?<=\\s)");
                final StringBuilder chunk = new StringBuilder();
                int n = 0;
                for (final String w : words)
                {
                    chunk.append(w);
                    if (++n % 3 == 0)
                    {
                        s.listener.onEvent(new ChatEvent("text_delta", b.blockId(), null, null, null, chunk.toString(), null, null, null));
                        chunk.setLength(0);
                    }
                }
                if (chunk.length() > 0)
                {
                    s.listener.onEvent(new ChatEvent("text_delta", b.blockId(), null, null, null, chunk.toString(), null, null, null));
                }
            }
            else
            {
                s.listener.onEvent(new ChatEvent("block", null, null, null, b, null, null, null, null));
            }
        }
        final List<TraceStep> trace = List.copyOf(s.trace);
        s.listener.onEvent(new ChatEvent("done", null, null, null, null, null, null, s.responseId, trace));
        chatDao.incrementUsage(s.customerId, usagePeriodKey(s.today), 0, s.tokensIn.get() + s.tokensOut.get());
        audit("turn_completed", "responseId", s.responseId, "intent", intent.name(), "workflow", intent.name(), "tokensIn",
                String.valueOf(s.tokensIn.get()), "tokensOut", String.valueOf(s.tokensOut.get()), "latencyMs",
                String.valueOf(System.currentTimeMillis() - start), "outcome", "ok");
        return new TurnResult(s.responseId, s.language, intent.name(), List.copyOf(blocks), List.copyOf(actions), trace, s.tokensIn.get(),
                s.tokensOut.get());
    }

    // ==================================================================================== usage caps (PL-06)
    private String usagePeriodKey(final LocalDate today)
    {
        return "MONTH".equalsIgnoreCase(cfg("usage.cap.period", "DAY")) ? today.toString().substring(0, 7) : today.toString();
    }

    private boolean checkAndIncrementUsage(final String customerId, final LocalDate today)
    {
        if (!isEnabled("PL-06"))
        {
            return true;
        }
        final String period = usagePeriodKey(today);
        final long[] current = chatDao.findUsage(customerId, period);
        if (current[0] >= cfgInt("usage.cap.messages", 200) || current[1] >= cfgInt("usage.cap.tokens", 200_000))
        {
            return false;
        }
        chatDao.incrementUsage(customerId, period, 1, 0);
        return true;
    }

    // ==================================================================================== interest events (CI-04, D08, A9)
    private static final Pattern TOKEN_OK = Pattern.compile("[a-z]{3,30}");

    /** The model suggests an enum; the backend decides and stores only the enum, a code or a letters-only token. No text. */
    private void recordInterest(final TurnState s, final Decision d)
    {
        if (!isEnabled("CI-04") || !cfgBool("privacy.interestEvents.enabled", true) || "NONE".equals(d.eventSuggestion()))
        {
            return;
        }
        if ("REQUESTED_NOT_FOUND".equals(d.eventSuggestion()))
        {
            if (!d.keywords().isEmpty() && TOKEN_OK.matcher(d.keywords().get(0).toLowerCase(Locale.ROOT)).matches())
            {
                chatDao.saveInterestEvent(s.customerId, d.eventSuggestion(), d.keywords().get(0).toLowerCase(Locale.ROOT), s.pack);
            }
            return;
        }
        final Set<String> codes = new LinkedHashSet<>(d.codes());
        codes.addAll(s.request.shortlistCodes());
        codes.stream().filter(s.provenance::contains).forEach(c -> chatDao.saveInterestEvent(s.customerId, d.eventSuggestion(), c, s.pack));
    }

    // ==================================================================================== welcome (RI-03, SC-05)
    @Override
    public Block buildWelcome(final String customerId)
    {
        if (!isEnabled("RI-03"))
        {
            return new Block("welcome", "welcome", null, null, null, null, null, null, null, List.of(), null, false);
        }
        final TurnState s = new TurnState(new TurnRequest(customerId, "", null, "en", List.of(), List.of(), List.of()), activePack(),
                LocalDate.now(clock), ChatEventListener.NOOP, 8, 9);
        final List<WelcomeItem> items = new ArrayList<>();
        try
        {
            if (isEnabled("RI-01"))
            {
                final List<ReplenishmentItem> due = runTool(s, "getReplenishmentRecommendations", Map.of("netPending", true)).as();
                for (final ReplenishmentItem i : due.stream().limit(3).toList())
                {
                    items.add(new WelcomeItem(i.overdue() ? "overdue" : "due", "welcome.due", Map.of("code", i.code(), "name", nameOf(s, i.code()),
                            "quantity", String.valueOf(i.suggestedQuantity()), "estimate", "true")));
                }
            }
            if (isEnabled("RI-02"))
            {
                final List<Order> pending = runTool(s, "getPendingOrders", Map.of()).as();
                if (!pending.isEmpty())
                {
                    items.add(new WelcomeItem("pending", "welcome.pending", Map.of("count", String.valueOf(pending.size()))));
                }
            }
            if (isEnabled("RI-04"))
            {
                final List<PatternObservation> changes = runTool(s, "detectPurchasePatternChanges", Map.of()).as();
                for (final PatternObservation o : changes.stream().limit(2).toList())
                {
                    items.add(new WelcomeItem("patternChange", "welcome.patternChange", Map.of("code", o.code(), "name", nameOf(s, o.code()),
                            "percent", String.valueOf(o.changePercent()), "direction", o.direction(), "earlierAverage",
                            String.format(Locale.ROOT, "%.0f", o.earlierAverage()), "recentAverage", String.format(Locale.ROOT, "%.0f", o.recentAverage()))));
                }
            }
        }
        catch (final RuntimeException e)
        {
            audit("welcome_partial", "errorCode", e.getClass().getSimpleName()); // a failed tool must not break the page
        }
        return new Block("welcome", "welcome", null, null, null, null, null, null, null, items, null, false);
    }

    // ==================================================================================== purchase list (PA-02) and feedback (CF-07)
    @Override
    public PurchaseListResult validatePurchaseList(final String customerId, final List<PurchaseLine> lines)
    {
        if (lines == null)
        {
            throw new IllegalArgumentException("lines must not be empty");
        }
        if (lines.size() > 100)
        {
            throw new IllegalArgumentException("lines must not exceed 100");
        }
        final String pack = activePack();
        final List<ListLine> out = new ArrayList<>();
        final List<String> issues = new ArrayList<>();
        long total = 0;
        for (final PurchaseLine in : lines)
        {
            final Product p = chatDao.findProduct(pack, in.productCode());
            String issue = null;
            String lineText = null;
            if (p == null)
            {
                issue = "Product not found";
            }
            else if (in.quantity() < 1 || in.quantity() > 100_000)
            {
                issue = "Quantity must be between 1 and 100000";
            }
            else
            {
                issue = p.discontinued() ? "Discontinued" : null;
                final OptionalLong unit = chatDao.findUnitPriceMinor(customerId, in.productCode());
                if (unit.isPresent())
                {
                    lineText = formatMoney(unit.getAsLong() * in.quantity(), currency());
                    total += unit.getAsLong() * in.quantity();
                }
            }
            if (issue != null)
            {
                issues.add(in.productCode() + ": " + issue);
            }
            out.add(new ListLine(in.productCode(), p == null ? null : p.name(), in.quantity(), in.note(), in.origin(), lineText, issue));
        }
        return new PurchaseListResult(out, issues, formatMoney(total, currency()), cfg("purchaseList.label", "Demo purchase list, not your cart"));
    }

    @Override
    public void saveFeedback(final String responseId, final String rating, final String intent, final List<String> toolsUsed, final String auditRef)
    {
        if (StringUtils.isBlank(responseId))
        {
            throw new IllegalArgumentException("responseId must not be empty");
        }
        final String r = StringUtils.defaultString(rating).toUpperCase(Locale.ROOT);
        if (!"UP".equals(r) && !"DOWN".equals(r))
        {
            throw new IllegalArgumentException("rating must be UP or DOWN");
        }
        chatDao.saveFeedback(StringUtils.left(responseId, 100), r, StringUtils.left(intent, 100),
                toolsUsed == null ? null : StringUtils.left(String.join(",", toolsUsed), 400), StringUtils.left(auditRef, 100));
    }

    // ==================================================================================== retention (D08)
    @Override
    public int purgeExpiredData()
    {
        final Instant now = clock.instant();
        final int batch = 500;
        final Date interestCutoff = Date.from(now.minus(Duration.ofDays(cfgInt("privacy.interestEvents.retentionDays", 90))));
        final Date feedbackCutoff = Date.from(now.minus(Duration.ofDays(cfgInt("privacy.feedback.retentionDays", 180))));
        final String usageCutoff = now.atZone(ZoneId.systemDefault()).toLocalDate().minusDays(cfgInt("usage.retentionDays", 35)).toString();
        int total = 0;
        int n;
        do
        {
            n = chatDao.purgeInterestEvents(interestCutoff, batch);
            total += n;
        }
        while (n >= batch);
        do
        {
            n = chatDao.purgeFeedback(feedbackCutoff, batch);
            total += n;
        }
        while (n >= batch);
        do
        {
            n = chatDao.purgeUsageCounters(usageCutoff, batch);
            total += n;
        }
        while (n >= batch);
        audit("retention_run", "count", String.valueOf(total)); // counts only
        return total;
    }
}
