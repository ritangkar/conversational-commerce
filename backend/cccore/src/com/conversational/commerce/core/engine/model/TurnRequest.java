package com.conversational.commerce.core.engine.model;

import java.util.List;

/**
 * One chat turn. {@code customerId} is set by the facade from the Spring Security context, never from the request body
 * and never from a model output.
 */
public record TurnRequest(String customerId, String message, String clientRequestId, String uiLocale, List<ContextTurn> turns,
        List<String> shortlistCodes, List<PurchaseLine> purchaseList)
{
    public record ContextTurn(String role, String text)
    {
    }

    public record PurchaseLine(String productCode, int quantity, String note)
    {
    }
}
