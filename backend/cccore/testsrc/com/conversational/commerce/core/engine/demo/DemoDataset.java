package com.conversational.commerce.core.engine.demo;

import com.conversational.commerce.core.dao.ChatDao;
import com.conversational.commerce.core.dao.ChatDao.BusinessAttribute;
import com.conversational.commerce.core.dao.ChatDao.Faq;
import com.conversational.commerce.core.dao.ChatDao.Order;
import com.conversational.commerce.core.dao.ChatDao.OrderFilter;
import com.conversational.commerce.core.dao.ChatDao.OrderLine;
import com.conversational.commerce.core.dao.ChatDao.OrderStatus;
import com.conversational.commerce.core.dao.ChatDao.Product;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/** Synthetic dummy data for SC-01..SC-10 as in-memory gateways (tests and local demo only). Nothing here is real. */
public class DemoDataset implements ChatDao
{
    public static final String TYRES_DEALER = "dealer-tyres";
    public static final String TYRES_DEALER_2 = "dealer-tyres-2";
    public static final String PAINTS_DEALER = "dealer-paints";
    public static final String ELECTRICAL_DEALER = "dealer-electrical";
    public static final String CEMENT_DEALER = "dealer-cement";
    public static final String PLYWOOD_DEALER = "dealer-plywood";
    /** Unique canary values: they must never appear in any outbound model payload. */
    public static final long CANARY_PRICE_MINOR = 731455L;
    public static final long CANARY_MARGIN_MINOR = 91357L;
    public static final String CANARY_EMAIL = "canary.dealer@example.invalid";
    public static final String OTHER_CUSTOMER_ORDER = "99887766";

    /** Mutable configuration layers: Backoffice entries win over properties, as in production. */
    public static class MapConfig
    {
        final Map<String, String> values = new ConcurrentHashMap<>();

        public MapConfig put(final String key, final String value)
        {
            values.put(key, value);
            return this;
        }
    }

    public final MapConfig backoffice = new MapConfig();
    public final MapConfig properties = new MapConfig();

    public record Event(String customerId, String type, String reference, String pack)
    {
    }

    private final LocalDate today;
    private final List<Product> products = new ArrayList<>();
    private final Map<String, Long> prices = new HashMap<>();
    private final List<Order> orders = new ArrayList<>();
    private final Map<String, Map<String, Integer>> pairs = new HashMap<>();
    private final Map<String, BusinessAttribute> business = new HashMap<>();
    private final List<Faq> faqs = new ArrayList<>();
    private final List<Event> events = java.util.Collections.synchronizedList(new ArrayList<>());
    private final Map<String, long[]> counters = new ConcurrentHashMap<>();

    public DemoDataset(final LocalDate today)
    {
        this.today = today;
        tyres();
        paints();
        electrical();
        cement();
        plywood();
    }

    public List<Event> events()
    {
        return events;
    }

    private void product(final String code, final String name, final String desc, final String pack, final long priceMinor,
            final String... attrs)
    {
        final Map<String, String> a = new LinkedHashMap<>();
        for (int i = 0; i < attrs.length; i += 2)
        {
            a.put(attrs[i], attrs[i + 1]);
        }
        products.add(new Product(code, name, desc, pack + "-root", pack, a, false, null));
        prices.put(code, priceMinor);
    }

    private void discontinued(final String code, final String name, final String pack, final long price, final String replacement)
    {
        products.add(new Product(code, name, name, pack + "-root", pack, Map.of(), true, replacement));
        prices.put(code, price);
    }

    private void order(final String code, final String customer, final int daysAgo, final OrderStatus status, final long total,
            final String tracking, final Object... lines)
    {
        final List<OrderLine> l = new ArrayList<>();
        for (int i = 0; i < lines.length; i += 2)
        {
            l.add(new OrderLine((String) lines[i], (Integer) lines[i + 1]));
        }
        orders.add(new Order(code, customer, today.minusDays(daysAgo), status, total, "INR", l, tracking));
    }

    private int seq = 1000;

    private String nextCode()
    {
        return "0001" + (++seq);
    }

    private void rhythm(final String customer, final String code, final int qty, final int... daysAgo)
    {
        for (final int d : daysAgo)
        {
            order(nextCode(), customer, d, OrderStatus.DELIVERED, 100000, null, code, qty);
        }
    }

    private void tyres()
    {
        product("TY-1001", "Passenger tyre 205/55 R16", "Tubeless radial for cars", "tyres", 450000, "size", "205/55 R16");
        product("TY-1002", "SUV tyre 235/65 R17", "Radial for SUVs", "tyres", 780000, "size", "235/65 R17");
        product("TY-1003", "Truck tyre 10.00 R20", "Heavy duty truck tyre", "tyres", 1450000, "size", "10.00 R20");
        product("TY-1004", "Tyre valve kit", "Valve and cap kit", "tyres", CANARY_PRICE_MINOR, "type", "kit");
        product("TY-1005", "Wheel balancing weights", "Clip-on weights", "tyres", 9900, "type", "weights");
        rhythm(TYRES_DEALER, "TY-1001", 40, 120, 90, 60, 30);
        rhythm(TYRES_DEALER, "TY-1002", 20, 100, 70, 40);
        rhythm(TYRES_DEALER, "TY-1004", 100, 120, 90, 60, 30);
        order("00010001", TYRES_DEALER, today.getDayOfMonth() + 20, OrderStatus.SHIPPED, 2500000, "TRK-889", "TY-1003", 10);
        order("00010002", TYRES_DEALER, today.getDayOfMonth() + 22, OrderStatus.SHIPPED, 1200000, "TRK-890", "TY-1005", 20);
        order("00010003", TYRES_DEALER, today.getDayOfMonth() + 15, OrderStatus.PROCESSING, 3000000, null, "TY-1003", 30);
        order("00010004", TYRES_DEALER, 3, OrderStatus.PENDING, 800000, null, "TY-1004", 100);
        order(OTHER_CUSTOMER_ORDER, TYRES_DEALER_2, 5, OrderStatus.SHIPPED, 9999900, "TRK-OTHER", "TY-1003", 99);
        pairs.put("TY-1001", Map.of("TY-1004", 8, "TY-1005", 5));
        pairs.put("TY-1002", Map.of("TY-1004", 6, "TY-1005", 3));
        business.put("TY-1005", new BusinessAttribute(5, CANARY_MARGIN_MINOR));
        business.put("TY-1004", new BusinessAttribute(1, 100));
        faqs.add(new Faq("FAQ-T1", "tyres", "What are your delivery times?", "Standard delivery takes three to five working days."));
        faqs.add(new Faq("FAQ-T2", "tyres", "What is the return policy for tyres?", "Unused tyres can be returned within seven days."));
    }

    private void paints()
    {
        product("PN-2001", "Waterproof coating for exterior concrete walls", "Exterior concrete wall waterproofing, medium coverage",
                "paints", 250000, "surface", "concrete", "coverage", "medium", "finish", "matt");
        product("PN-2002", "High build waterproof coating exterior", "Exterior waterproof, high coverage", "paints", 420000,
                "surface", "concrete", "coverage", "high", "finish", "matt");
        product("PN-2003", "Waterproof coating exterior brick walls", "Exterior wall waterproofing, medium coverage", "paints",
                290000, "surface", "brick", "coverage", "medium", "finish", "satin");
        product("PN-2004", "Interior emulsion", "Interior wall paint", "paints", 180000, "surface", "plaster", "coverage", "medium");
        product("PN-3001", "Exterior primer Alpha", "Exterior wall primer", "paints", 210000, "coverage", "medium", "base", "water",
                "coastal", "suitable");
        product("PN-3002", "Exterior primer Beta", "Exterior wall primer", "paints", 230000, "coverage", "medium", "base", "water");
        product("PN-3003", "Exterior primer Gamma", "Exterior wall primer", "paints", 250000, "coverage", "high", "base", "solvent",
                "coastal", "not suitable");
        faqs.add(new Faq("FAQ-P1", "paints", "How long should a primer dry?", "Allow the primer to dry as stated on the can."));
    }

    private void electrical()
    {
        product("EL-4821", "Modular switch 16A", "Switch", "electrical", 15000, "rating", "16A");
        product("EL-9910", "MCB 32A", "Miniature circuit breaker", "electrical", 62000, "rating", "32A");
        discontinued("EL-1033", "Old PVC cable 2.5mm", "electrical", 700000, "EL-1034");
        product("EL-1034", "Copper cable 2.5mm 100m roll", "Copper cable 2.5mm 100m", "electrical", 1100000, "core", "copper");
        product("EL-5050", "Distribution board 8 way", "DB", "electrical", 350000, "ways", "8");
        product("EL-6001", "Junction box", "Box", "electrical", 4000, "type", "ABS");
        product("EL-6002", "Cable ties pack", "Ties", "electrical", 2500, "type", "nylon");
        rhythm(ELECTRICAL_DEALER, "EL-4821", 20, 120, 90, 60, 30);
        rhythm(ELECTRICAL_DEALER, "EL-9910", 5, 100, 70, 40);
        order("00020001", ELECTRICAL_DEALER, 4, OrderStatus.PENDING, 1750000, null, "EL-5050", 5);
        pairs.put("EL-9910", Map.of("EL-6001", 6, "EL-6002", 4));
    }

    private void cement()
    {
        product("CM-1001", "Cement OPC 53 grade 50kg", "Ordinary Portland cement", "cement", 38000);
        product("CM-1002", "Cement PPC 50kg", "Portland pozzolana cement", "cement", 36000);
        product("CM-1003", "Waterproofing additive 5L", "Cement additive", "cement", 95000);
        product("CM-1004", "TMT steel bar 12mm tonne", "Reinforcement steel", "cement", 6200000);
        product("CM-1005", "Binding wire 1kg", "Wire", "cement", 12000);
        for (final int d : new int[] { 150, 120, 90, 60, 30 })
        {
            order(nextCode(), CEMENT_DEALER, d, OrderStatus.DELIVERED, 100000, null, "CM-1001", 400, "CM-1002", 300, "CM-1003", 50,
                    "CM-1004", 5, "CM-1005", 200);
        }
    }

    private void plywood()
    {
        product("PW-1801", "Plywood 18mm BWP", "Boiling waterproof plywood", "plywood", 320000, "thickness", "18mm");
        product("PW-1201", "Plywood 12mm MR", "Moisture resistant plywood", "plywood", 210000, "thickness", "12mm");
        final int[] days = { 150, 120, 90, 60, 30 };
        final int[] qty = { 100, 100, 95, 60, 60 };
        for (int i = 0; i < days.length; i++)
        {
            order(nextCode(), PLYWOOD_DEALER, days[i], OrderStatus.DELIVERED, 100000, null, "PW-1801", qty[i], "PW-1201", 30);
        }
    }

    // ChatDao: configuration
    @Override
    public String findProperty(final String key)
    {
        return properties.values.get(key);
    }

    @Override
    public String findBackofficeValue(final String key)
    {
        return backoffice.values.get(key);
    }

    // ChatDao: catalogue and prices
    @Override
    public List<Product> findProducts(final String pack, final List<String> keywords, final String categoryCode,
            final Map<String, String> filters, final int limit)
    {
        final Map<Product, Integer> scored = new LinkedHashMap<>();
        for (final Product p : products)
        {
            if (!p.pack().equals(pack) || categoryCode != null && !categoryCode.equals(p.category()))
            {
                continue;
            }
            boolean filterOk = true;
            for (final Map.Entry<String, String> f : filters.entrySet())
            {
                filterOk &= f.getValue().equalsIgnoreCase(p.attributes().get(f.getKey()));
            }
            if (!filterOk)
            {
                continue;
            }
            final String hay = (p.name() + " " + p.description() + " " + String.join(" ", p.attributes().values())).toLowerCase(Locale.ROOT);
            int score = 0;
            for (final String k : keywords)
            {
                if (hay.contains(k.toLowerCase(Locale.ROOT)))
                {
                    score++;
                }
            }
            if (keywords.isEmpty() || score > 0)
            {
                scored.put(p, score);
            }
        }
        return scored.entrySet().stream()
                .sorted((a, b) -> b.getValue() != a.getValue().intValue() ? b.getValue() - a.getValue() : a.getKey().code().compareTo(b.getKey().code()))
                .map(Map.Entry::getKey).limit(limit).toList();
    }

    @Override
    public Product findProduct(final String pack, final String code)
    {
        return products.stream().filter(p -> p.pack().equals(pack) && p.code().equals(code)).findFirst().orElse(null);
    }

    @Override
    public OptionalLong findUnitPriceMinor(final String customerId, final String productCode)
    {
        final Long p = prices.get(productCode);
        return p == null ? OptionalLong.empty() : OptionalLong.of(p);
    }

    @Override
    public String findCurrency()
    {
        return "INR";
    }

    // ChatDao: orders. Customer isolation is enforced here, as the SAP DAO filters by customer in the query.
    @Override
    public List<Order> searchOrders(final String customerId, final OrderFilter f, final String sortCode, final int limit)
    {
        return orders.stream().filter(o -> o.customerId().equals(customerId))
                .filter(o -> f.statuses() == null || f.statuses().contains(o.status()))
                .filter(o -> f.minTotalMinor() == null || o.totalMinor() >= f.minTotalMinor())
                .filter(o -> f.maxTotalMinor() == null || o.totalMinor() <= f.maxTotalMinor())
                .filter(o -> f.fromDate() == null || !o.placedOn().isBefore(f.fromDate()))
                .filter(o -> f.toDate() == null || o.placedOn().isBefore(f.toDate().plusDays(1))) // toDate exclusive next day
                .sorted("byDateAsc".equals(sortCode) ? Comparator.comparing(Order::placedOn) : Comparator.comparing(Order::placedOn).reversed())
                .limit(limit).toList();
    }

    @Override
    public Order findOrderByCode(final String customerId, final String code)
    {
        return orders.stream().filter(o -> o.customerId().equals(customerId) && o.code().equals(code)).findFirst().orElse(null);
    }

    // ChatDao: aggregates and content
    @Override
    public Map<String, Integer> findCoPurchaseCounts(final String pack, final String productCode)
    {
        return pairs.getOrDefault(productCode, Map.of());
    }

    @Override
    public BusinessAttribute findBusinessAttribute(final String productCode)
    {
        return business.get(productCode);
    }

    @Override
    public List<Faq> findFaqs(final String pack)
    {
        return faqs.stream().filter(f -> f.pack().equals(pack)).toList();
    }

    // ChatDao: writes and counters
    @Override
    public void saveInterestEvent(final String customerId, final String eventType, final String reference, final String pack)
    {
        events.add(new Event(customerId, eventType, reference, pack));
    }

    public final List<String> feedback = java.util.Collections.synchronizedList(new ArrayList<>());

    @Override
    public void saveFeedback(final String responseId, final String rating, final String intent, final String toolsUsed, final String auditRef)
    {
        feedback.add(responseId + "|" + rating + "|" + intent + "|" + toolsUsed + "|" + auditRef);
    }

    @Override
    public long[] incrementUsage(final String customerId, final String periodKey, final int messages, final int tokens)
    {
        final long[] c = counters.computeIfAbsent(customerId + "|" + periodKey, k -> new long[2]);
        synchronized (c)
        {
            c[0] += messages;
            c[1] += tokens;
            return c.clone();
        }
    }

    @Override
    public long[] findUsage(final String customerId, final String periodKey)
    {
        return counters.getOrDefault(customerId + "|" + periodKey, new long[2]).clone();
    }

    // ChatDao: retention (the demo keeps nothing old, so these only count calls)
    public final AtomicInteger purgeCalls = new AtomicInteger();

    @Override
    public int purgeInterestEvents(final Date cutoff, final int batchSize)
    {
        purgeCalls.incrementAndGet();
        return 0;
    }

    @Override
    public int purgeFeedback(final Date cutoff, final int batchSize)
    {
        purgeCalls.incrementAndGet();
        return 0;
    }

    @Override
    public int purgeUsageCounters(final String periodKeyBefore, final int batchSize)
    {
        purgeCalls.incrementAndGet();
        return 0;
    }
}
