package com.conversational.commerce.facades.chat.impl;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import com.conversational.commerce.core.engine.gateway.CatalogGateway;
import com.conversational.commerce.core.engine.gateway.PriceGateway;
import com.conversational.commerce.core.engine.model.Domain.Product;
import com.conversational.commerce.core.engine.orchestrator.MoneyFormat;
import com.conversational.commerce.facades.chat.PurchaseListFacade;
import com.conversational.commerce.facades.data.chat.PurchaseLineData;
import com.conversational.commerce.facades.data.chat.PurchaseListData;
import com.conversational.commerce.facades.data.chat.PurchaseListLineData;
import com.conversational.commerce.facades.data.chat.PurchaseListResultData;
import de.hybris.platform.servicelayer.user.UserService;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/** PA-02: server-side validation and pricing of the labelled demo list. Not a cart (D03). */
public class DefaultPurchaseListFacade implements PurchaseListFacade
{
    private static final int MAX_LINES = 100;
    private static final int MAX_QUANTITY = 100_000;

    @Resource(name = "ccCatalogGateway")
    private CatalogGateway catalogGateway;

    @Resource(name = "ccPriceGateway")
    private PriceGateway priceGateway;

    @Resource(name = "ccConfigurationService")
    private CcConfigurationService configurationService;

    @Resource
    private UserService userService;

    @Override
    public PurchaseListResultData validate(final PurchaseListData list)
    {
        if (list == null || list.getLines() == null)
        {
            throw new IllegalArgumentException("lines must not be empty");
        }
        if (list.getLines().size() > MAX_LINES)
        {
            throw new IllegalArgumentException("lines must not exceed " + MAX_LINES);
        }
        final String uid = userService.getCurrentUser().getUid();
        final String pack = configurationService.activePack();
        final List<PurchaseListLineData> lines = new ArrayList<>();
        final List<String> issues = new ArrayList<>();
        long total = 0;
        for (final PurchaseLineData in : list.getLines())
        {
            final PurchaseListLineData out = new PurchaseListLineData();
            out.setProductCode(in.getProductCode());
            out.setQuantity(in.getQuantity());
            out.setNote(in.getNote());
            out.setOrigin(in.getOrigin());
            final Optional<Product> product = catalogGateway.byCode(pack, in.getProductCode());
            if (product.isEmpty())
            {
                out.setIssue("Product not found");
            }
            else if (in.getQuantity() == null || in.getQuantity() < 1 || in.getQuantity() > MAX_QUANTITY)
            {
                out.setName(product.get().name());
                out.setIssue("Quantity must be between 1 and " + MAX_QUANTITY);
            }
            else
            {
                out.setName(product.get().name());
                if (product.get().discontinued())
                {
                    out.setIssue("Discontinued");
                }
                final OptionalLong unit = priceGateway.unitPriceMinor(uid, in.getProductCode());
                if (unit.isPresent())
                {
                    final long lineMinor = unit.getAsLong() * in.getQuantity();
                    out.setLineFormatted(MoneyFormat.format(lineMinor, priceGateway.currency()));
                    total += lineMinor;
                }
            }
            if (out.getIssue() != null)
            {
                issues.add(out.getProductCode() + ": " + out.getIssue());
            }
            lines.add(out);
        }
        final PurchaseListResultData result = new PurchaseListResultData();
        result.setLines(lines);
        result.setIssues(issues);
        result.setTotalFormatted(MoneyFormat.format(total, priceGateway.currency()));
        result.setDemoLabel(configurationService.getString("purchaseList.label", "Demo purchase list, not your cart"));
        return result;
    }
}
