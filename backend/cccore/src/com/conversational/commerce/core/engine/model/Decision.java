package com.conversational.commerce.core.engine.model;

import java.util.List;
import java.util.Map;

/** Parsed and range-checked output of the DECIDE call. Amounts are in major units as the dealer said them. */
public record Decision(Intent intent, double confidence, String language, List<String> keywords, String categoryHint,
        Map<String, String> filters, Double budgetAmount, Double maxUnitAmount, List<String> codes, String orderStatus,
        Double orderMinAmount, String orderPeriod, boolean wantsLatestTracking, String eventSuggestion, String clarifyKey)
{
}
