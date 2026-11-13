package com.conversational.commerce.core.engine.gateway;

import java.util.Map;

public interface CoPurchaseGateway
{
    /** Aggregated, anonymous counts of products bought together with the given product. No customer data. */
    Map<String, Integer> pairCounts(String pack, String productCode);
}
