package com.conversational.commerce.core.engine.gateway;

import java.util.OptionalLong;

public interface PriceGateway
{
    /** Customer-specific unit price in minor units. Never passed to a model. */
    OptionalLong unitPriceMinor(String customerId, String productCode);

    String currency();
}
