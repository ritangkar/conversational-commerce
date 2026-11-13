package com.conversational.commerce.core.engine.orchestrator;

import com.conversational.commerce.core.engine.gateway.CatalogGateway;
import com.conversational.commerce.core.engine.gateway.PriceGateway;
import com.conversational.commerce.core.engine.gateway.StockGateway;
import com.conversational.commerce.core.engine.model.Blocks.ListLine;
import com.conversational.commerce.core.engine.model.Blocks.OrderSummary;
import com.conversational.commerce.core.engine.model.Blocks.ProductCard;
import com.conversational.commerce.core.engine.model.Blocks.Recommendation;
import com.conversational.commerce.core.engine.model.Domain.Order;
import com.conversational.commerce.core.engine.model.Domain.Product;
import com.conversational.commerce.core.engine.model.Domain.StockInfo;
import jakarta.annotation.Resource;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/** Adds names, customer prices, images and labelled demo stock after the model is done (D26). Prices never go to a model. */
public class Hydrator
{
    @Resource(name = "ccCatalogGateway")
    private CatalogGateway catalogGateway;

    @Resource(name = "ccPriceGateway")
    private PriceGateway priceGateway;

    @Resource(name = "ccStockGateway")
    private StockGateway stockGateway;

    public void setCatalogGateway(final CatalogGateway g)
    {
        this.catalogGateway = g;
    }

    public void setPriceGateway(final PriceGateway g)
    {
        this.priceGateway = g;
    }

    public void setStockGateway(final StockGateway g)
    {
        this.stockGateway = g;
    }

    public String name(final TurnState state, final String code)
    {
        return catalogGateway.byCode(state.pack, code).map(Product::name).orElse(code);
    }

    public ProductCard card(final TurnState state, final String code, final Map<String, String> reasonSlots)
    {
        final Optional<Product> product = catalogGateway.byCode(state.pack, code);
        final String price = priceText(state, code);
        final StockInfo stock = stockGateway.stockOf(code);
        return new ProductCard(code, product.map(Product::name).orElse(code), price, stock.label(), stock.demo(), reasonSlots);
    }

    public ListLine line(final TurnState state, final String code, final int quantity, final String origin, final String issue)
    {
        final OptionalLong unit = priceGateway.unitPriceMinor(state.customerId, code);
        String lineText = null;
        if (unit.isPresent())
        {
            final long total = unit.getAsLong() * quantity;
            lineText = MoneyFormat.format(total, priceGateway.currency());
            markSecret(state, total);
        }
        return new ListLine(code, name(state, code), quantity, null, origin, lineText, issue);
    }

    public Recommendation recommendation(final TurnState state, final String code, final int quantity, final String reasonKey,
            final boolean estimate)
    {
        return new Recommendation(code, name(state, code), quantity, reasonKey, estimate);
    }

    public OrderSummary order(final Order order)
    {
        return new OrderSummary(order.code(), order.placedOn().toString(), order.status().name(),
                MoneyFormat.format(order.totalMinor(), order.currency()), order.trackingReference(), "Commerce order data");
    }

    public String format(final TurnState state, final long minor)
    {
        markSecret(state, minor);
        return MoneyFormat.format(minor, priceGateway.currency());
    }

    private String priceText(final TurnState state, final String code)
    {
        final OptionalLong unit = priceGateway.unitPriceMinor(state.customerId, code);
        if (unit.isEmpty())
        {
            return null;
        }
        markSecret(state, unit.getAsLong());
        return MoneyFormat.format(unit.getAsLong(), priceGateway.currency());
    }

    /** Registers every textual form of an amount so the outbound guard can block it. */
    public void markSecret(final TurnState state, final long minor)
    {
        state.secrets.add(String.valueOf(minor));
        state.secrets.add(String.format("%d.%02d", minor / 100, Math.abs(minor % 100)));
        state.secrets.add(String.format(java.util.Locale.ROOT, "%,d.%02d", minor / 100, Math.abs(minor % 100)));
    }
}
