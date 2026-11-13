package com.conversational.commerce.core.engine.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Plain value types used by the engine. The SAP-bound gateways convert platform models into these. */
public final class Domain
{
    private Domain()
    {
    }

    public enum OrderStatus
    {
        PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED;

        public boolean isUndelivered()
        {
            return this == PENDING || this == PROCESSING || this == SHIPPED;
        }
    }

    public enum EventType
    {
        VIEWED, COMPARED, SHORTLISTED, REQUESTED_NOT_FOUND
    }

    public record Product(String code, String name, String description, String category, String pack,
            Map<String, String> attributes, boolean discontinued, String replacementCode)
    {
    }

    public record OrderLine(String productCode, int quantity)
    {
    }

    public record Order(String code, String customerId, LocalDate placedOn, OrderStatus status, long totalMinor,
            String currency, List<OrderLine> lines, String trackingReference)
    {
    }

    public record Faq(String id, String pack, String question, String answer)
    {
    }

    /** Margin and priority. Only BusinessRanker may read this; it is never serialised to a model or a customer. */
    public record BusinessAttribute(int priority, long marginMinor)
    {
    }

    public record StockInfo(String code, int units, boolean demo, String label)
    {
    }
}
