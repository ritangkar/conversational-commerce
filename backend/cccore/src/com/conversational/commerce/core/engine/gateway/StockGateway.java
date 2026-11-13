package com.conversational.commerce.core.engine.gateway;

import com.conversational.commerce.core.engine.model.Domain.StockInfo;

public interface StockGateway
{
    // FUTURE SCOPE: live stock. The default implementation returns labelled demo values (D27).
    StockInfo stockOf(String productCode);
}
