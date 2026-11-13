package com.conversational.commerce.core.platform;

import com.conversational.commerce.core.dao.CcProductSearchDao;
import com.conversational.commerce.core.engine.gateway.PriceGateway;
import de.hybris.platform.jalo.order.price.PriceInformation;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.commerceservices.price.CommercePriceService;
import de.hybris.platform.store.services.BaseStoreService;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.OptionalLong;

/**
 * Customer-specific price through the platform price service, evaluated in the session of the authenticated customer.
 * ASSUMPTION (AS-04): the chat request runs in the customer's session, so contract prices apply. Never sent to a model.
 */
public class PlatformPriceGateway implements PriceGateway
{
    @Resource(name = "ccProductSearchDao")
    private CcProductSearchDao ccProductSearchDao;

    @Resource
    private CommercePriceService commercePriceService;

    @Resource
    private BaseStoreService baseStoreService;

    @Override
    public OptionalLong unitPriceMinor(final String customerId, final String productCode)
    {
        final ProductModel product = ccProductSearchDao.findByCode(productCode);
        if (product == null)
        {
            return OptionalLong.empty();
        }
        final PriceInformation info = commercePriceService.getWebPriceForProduct(product);
        if (info == null || info.getPriceValue() == null)
        {
            return OptionalLong.empty();
        }
        return OptionalLong.of(BigDecimal.valueOf(info.getPriceValue().getValue()).movePointRight(2).longValue());
    }

    @Override
    public String currency()
    {
        return baseStoreService.getCurrentBaseStore() == null ? "INR"
                : baseStoreService.getCurrentBaseStore().getDefaultCurrency().getIsocode();
    }
}
