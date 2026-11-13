package com.conversational.commerce.core.engine.orchestrator;

import java.util.Map;

/** Fixed English template texts with {{S:slot}} placeholders. Used when the model text fails validation (A13) and by tests. */
public final class TemplateTexts
{
    private static final Map<String, String> TEXTS = Map.ofEntries(
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

    private TemplateTexts()
    {
    }

    public static String get(final String key)
    {
        final String text = TEXTS.get(key);
        if (text == null)
        {
            throw new IllegalArgumentException("unknown template " + key);
        }
        return text;
    }

    public static Map<String, String> all()
    {
        return TEXTS;
    }
}
