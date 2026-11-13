package com.conversational.commerce.core.engine.model;

import java.util.List;
import java.util.Map;

/** Response block parts (A13). All figures are formatted by the backend; none come from model text. */
public final class Blocks
{
    private Blocks()
    {
    }

    public record ProductCard(String code, String name, String priceFormatted, String stockLabel, boolean stockIsDemo,
            Map<String, String> reasonSlots)
    {
    }

    public record ComparisonRow(String attributeLabel, List<String> values)
    {
    }

    public record Comparison(List<String> columns, List<ComparisonRow> rows, List<String> notes)
    {
    }

    public record OrderSummary(String code, String placedOn, String statusLabel, String totalFormatted,
            String trackingReference, String statusSource)
    {
    }

    public record ListLine(String productCode, String name, int quantity, String note, String origin, String lineFormatted,
            String issue)
    {
    }

    public record Recommendation(String code, String name, int quantity, String reasonKey, boolean estimate)
    {
    }

    public record WelcomeItem(String kind, String textKey, Map<String, String> params)
    {
    }

    public record Block(String blockId, String type, String text, String noticeKey, List<ProductCard> productCards,
            Comparison comparison, List<OrderSummary> orderSummaries, List<ListLine> purchaseListLines,
            List<Recommendation> recommendations, List<WelcomeItem> welcomeItems, List<String> clarification,
            boolean estimate)
    {
        public static Block text(final String id, final String text)
        {
            return new Block(id, "text", text, null, null, null, null, null, null, null, null, false);
        }

        public static Block notice(final String id, final String noticeKey, final String text)
        {
            return new Block(id, "notice", text, noticeKey, null, null, null, null, null, null, null, false);
        }

        public static Block productCards(final String id, final List<ProductCard> cards)
        {
            return new Block(id, "productCards", null, null, cards, null, null, null, null, null, null, false);
        }

        public static Block comparison(final String id, final Comparison comparison)
        {
            return new Block(id, "comparison", null, null, null, comparison, null, null, null, null, null, false);
        }

        public static Block orders(final String id, final List<OrderSummary> orders)
        {
            return new Block(id, "orderSummary", null, null, null, null, orders, null, null, null, null, false);
        }

        public static Block purchaseList(final String id, final List<ListLine> lines)
        {
            return new Block(id, "purchaseList", null, null, null, null, null, lines, null, null, null, false);
        }

        public static Block recommendations(final String id, final List<Recommendation> items, final boolean estimate)
        {
            return new Block(id, "recommendationList", null, null, null, null, null, null, items, null, null, estimate);
        }

        public static Block welcome(final String id, final List<WelcomeItem> items)
        {
            return new Block(id, "welcome", null, null, null, null, null, null, null, items, null, false);
        }

        public static Block clarification(final String id, final String text, final List<String> options)
        {
            return new Block(id, "clarification", text, null, null, null, null, null, null, null, options, false);
        }

        public Block withText(final String newText)
        {
            return new Block(blockId, type, newText, noticeKey, productCards, comparison, orderSummaries, purchaseListLines,
                    recommendations, welcomeItems, clarification, estimate);
        }
    }
}
