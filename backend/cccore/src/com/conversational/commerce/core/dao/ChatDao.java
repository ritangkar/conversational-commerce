package com.conversational.commerce.core.dao;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The single data-access interface of the accelerator: orders, products, prices, configuration, FAQ, counters, events,
 * feedback and retention. Every customer-scoped method takes the customer id and filters on it in the query (owner DAO
 * convention). The DAO returns plain value types so the service stays testable without a SAP platform (deviation from
 * "returns models", docs/assumptions.md AS-20).
 */
public interface ChatDao
{
    enum OrderStatus
    {
        PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED
    }

    record Product(String code, String name, String description, String category, String pack, Map<String, String> attributes,
            boolean discontinued, String replacementCode)
    {
    }

    record OrderLine(String productCode, int quantity)
    {
    }

    record Order(String code, String customerId, LocalDate placedOn, OrderStatus status, long totalMinor, String currency,
            List<OrderLine> lines, String trackingReference)
    {
    }

    record Faq(String id, String pack, String question, String answer)
    {
    }

    /** Margin and priority. Read only by the ranking code; never serialised to a model or a customer. */
    record BusinessAttribute(int priority, long marginMinor)
    {
    }

    /** All values are bound as named parameters; none is concatenated into query text. */
    record OrderFilter(Set<OrderStatus> statuses, Long minTotalMinor, Long maxTotalMinor, LocalDate fromDate, LocalDate toDate)
    {
        public static OrderFilter none()
        {
            return new OrderFilter(null, null, null, null, null);
        }
    }

    // ---- configuration layers (project/local properties and environment, then Backoffice entries)
    String findProperty(String key);

    String findBackofficeValue(String key);

    // ---- catalogue and prices
    List<Product> findProducts(String pack, List<String> keywords, String categoryCode, Map<String, String> filters, int limit);

    Product findProduct(String pack, String code);

    /** Customer-specific unit price in minor units, evaluated in the customer's session. Never sent to a model. */
    OptionalLong findUnitPriceMinor(String customerId, String productCode);

    String findCurrency();

    // ---- orders (FUTURE SCOPE: unit-level order access (D33) and unit-level history (D34): widen the customer filter here)
    /** toDate is inclusive for the caller and handled as the exclusive next day in the query. Newest first. */
    List<Order> searchOrders(String customerId, OrderFilter filter, String sortCode, int limit);

    Order findOrderByCode(String customerId, String code);

    // ---- aggregates and content
    /** Anonymous aggregated counts of products bought together with the given product. No customer data. */
    Map<String, Integer> findCoPurchaseCounts(String pack, String productCode);

    BusinessAttribute findBusinessAttribute(String productCode);

    List<Faq> findFaqs(String pack);

    // ---- writes owned by the chat feature
    void saveInterestEvent(String customerId, String eventType, String reference, String pack);

    void saveFeedback(String responseId, String rating, String intent, String toolsUsed, String auditRef);

    /** Adds to the counter for (customer, period) and returns {messages, tokens} after the change. */
    long[] incrementUsage(String customerId, String periodKey, int messages, int tokens);

    long[] findUsage(String customerId, String periodKey);

    // ---- retention (bounded batches; callers loop until fewer than batchSize are returned)
    int purgeInterestEvents(Date cutoff, int batchSize);

    int purgeFeedback(Date cutoff, int batchSize);

    int purgeUsageCounters(String periodKeyBefore, int batchSize);
}
