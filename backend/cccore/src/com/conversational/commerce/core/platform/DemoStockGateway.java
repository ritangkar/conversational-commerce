package com.conversational.commerce.core.platform;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import com.conversational.commerce.core.engine.gateway.StockGateway;
import com.conversational.commerce.core.engine.model.Domain.StockInfo;
import jakarta.annotation.Resource;

/** Hardcoded, labelled demo stock (D27). Override per product with entry stock.demo.&lt;code&gt;. */
public class DemoStockGateway implements StockGateway
{
    // FUTURE SCOPE: live stock. Replace this bean by alias with a gateway that reads real availability.

    @Resource(name = "ccConfigurationService")
    private CcConfigurationService configurationService;

    @Override
    public StockInfo stockOf(final String productCode)
    {
        final int units = configurationService.getInt("stock.demo." + productCode, Math.abs(productCode.hashCode()) % 200);
        return new StockInfo(productCode, units, true, units > 20 ? "In stock (demo)" : units > 0 ? "Low stock (demo)" : "Out of stock (demo)");
    }
}
