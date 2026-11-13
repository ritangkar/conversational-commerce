package com.conversational.commerce.core.service;

import java.util.List;
import java.util.Map;

/**
 * The whole chat backend behind one interface: orchestration, sanitiser, provider routing, tools, workflows, rules, caps,
 * interest events, welcome, feedback, purchase-list validation, public configuration and retention.
 * AI interprets and communicates; this service validates, executes and hydrates (D26).
 */
public interface ChatService
{
    // ---------------------------------------------------------------- turn input and output
    /** {@code customerId} comes from the Spring Security context via the facade, never from a request body or a model. */
    record TurnRequest(String customerId, String message, String clientRequestId, String uiLocale, List<ContextTurn> turns,
            List<String> shortlistCodes, List<PurchaseLine> purchaseList)
    {
    }

    record ContextTurn(String role, String text)
    {
    }

    record PurchaseLine(String productCode, int quantity, String note, String origin)
    {
    }

    record TurnResult(String responseId, String language, String intent, List<Block> blocks, List<String> suggestedActions,
            List<TraceStep> trace, int tokensIn, int tokensOut)
    {
    }

    /** Whitelisted why-this metadata (CF-08): source, window, threshold. No weights, margin, prompts or model text. */
    record TraceStep(String label, String source, String window, String threshold)
    {
    }

    // ---------------------------------------------------------------- blocks (flat; all figures formatted by the backend)
    record ProductCard(String code, String name, String priceFormatted, String stockLabel, boolean stockIsDemo,
            Map<String, String> reasonSlots)
    {
    }

    record ComparisonRow(String attributeLabel, List<String> values)
    {
    }

    record Comparison(List<String> columns, List<ComparisonRow> rows, List<String> notes)
    {
    }

    record OrderSummary(String code, String placedOn, String statusLabel, String totalFormatted, String trackingReference,
            String statusSource)
    {
    }

    record ListLine(String productCode, String name, int quantity, String note, String origin, String lineFormatted, String issue)
    {
    }

    record Recommendation(String code, String name, int quantity, String reasonKey, boolean estimate)
    {
    }

    record WelcomeItem(String kind, String textKey, Map<String, String> params)
    {
    }

    record Block(String blockId, String type, String text, String noticeKey, List<ProductCard> productCards,
            Comparison comparison, List<OrderSummary> orderSummaries, List<ListLine> purchaseListLines,
            List<Recommendation> recommendations, List<WelcomeItem> welcomeItems, List<String> clarification, boolean estimate)
    {
        public Block withText(final String newText)
        {
            return new Block(blockId, type, newText, noticeKey, productCards, comparison, orderSummaries, purchaseListLines,
                    recommendations, welcomeItems, clarification, estimate);
        }
    }

    /** Stream event: step_started, step_completed, block, text_delta (stepId carries the block id), done, error. */
    record ChatEvent(String event, String stepId, String labelKey, String status, Block block, String delta, String code,
            String responseId, List<TraceStep> trace)
    {
    }

    interface ChatEventListener
    {
        void onEvent(ChatEvent event);

        ChatEventListener NOOP = event ->
        {
        };
    }

    record PurchaseListResult(List<ListLine> lines, List<String> issues, String totalFormatted, String demoLabel)
    {
    }

    /** Public, non-secret configuration for the storefront. Never routes, prompts, thresholds or keys. */
    record PublicConfig(List<String> enabledCapabilities, String activePack, String interestNoticeText, boolean interestEventsEnabled,
            String stockDemoNotice, String purchaseListLabel, List<String> samplePrompts, Map<String, String> terminology,
            Map<String, String> themeTokens, int maxMessageLength, String contactText)
    {
    }

    /** The 43 baseline capability IDs. Each has a toggle capability.&lt;ID&gt;.enabled (seeded by essential data). */
    List<String> CAPABILITIES = List.of("CF-01", "CF-02", "CF-03", "CF-04", "CF-05", "CF-06", "CF-07", "CF-08", "PD-01", "PD-02",
            "PD-03", "PD-04", "PD-05", "PD-06", "PD-07", "PA-01", "PA-02", "PA-03", "PA-04", "PA-05", "PA-06", "PA-07", "OM-01",
            "OM-02", "OM-03", "RI-01", "RI-02", "RI-03", "RI-04", "RI-05", "CI-01", "CI-02", "CI-03", "CI-04", "KN-01", "PL-01",
            "PL-02", "PL-03", "PL-04", "PL-05", "PL-06", "PL-07", "PL-08");

    // ---------------------------------------------------------------- operations
    /** Runs one turn. Events are pushed to the listener; the same blocks are returned for the non-streaming path. */
    TurnResult handle(TurnRequest request, ChatEventListener listener);

    /** Proactive welcome panel (RI-03, SC-05): deterministic tools and template text only. */
    Block buildWelcome(String customerId);

    /** PA-02: validates and prices the browser-held list. The list is untrusted input (A11). */
    PurchaseListResult validatePurchaseList(String customerId, List<PurchaseLine> lines);

    /** CF-07, D30: metadata only. No customer reference and no text. */
    void saveFeedback(String responseId, String rating, String intent, List<String> toolsUsed, String auditRef);

    PublicConfig getPublicConfig();

    /** D08 retention: interest events (default 90 days), feedback and usage counters. Returns the removed row count. */
    int purgeExpiredData();

    /** Backoffice save interceptor hook (A1): null if the change is allowed, otherwise the refusal reason. */
    String validateToggleChange(String capabilityId, boolean newValue);

    void clearConfigCache();

    /** Metadata-only audit line through logger cc.audit (PL-05, A14). Keys outside the whitelist are rejected. */
    void audit(String event, String... keyValuePairs);

    /** A5: the userId path segment must be "current" or the authenticated principal's uid. Anything else is a 403. */
    static boolean isUserIdAllowed(final String pathUserId, final String principalUid)
    {
        if (pathUserId == null || principalUid == null || principalUid.isBlank() || "anonymous".equalsIgnoreCase(principalUid))
        {
            return false;
        }
        return "current".equals(pathUserId) || principalUid.equals(pathUserId);
    }
}
