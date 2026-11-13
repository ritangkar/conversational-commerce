package com.conversational.commerce.core.engine.tools;

import com.conversational.commerce.core.engine.gateway.CatalogGateway;
import com.conversational.commerce.core.engine.model.Domain.Product;
import com.conversational.commerce.core.engine.orchestrator.ChatTool;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves codes against the active pack's catalogue and returns approved fields only (name, description, attributes). */
public class ProductDetailsTool implements ChatTool
{
    @Resource
    private CatalogGateway catalogGateway;

    public void setCatalogGateway(final CatalogGateway catalogGateway)
    {
        this.catalogGateway = catalogGateway;
    }

    @Override
    public String name()
    {
        return "getProductDetails";
    }

    @Override
    public String capabilityId()
    {
        return "PD-03";
    }

    @Override
    public ToolResult execute(final TurnState state, final Map<String, Object> args)
    {
        final List<Product> products = new ArrayList<>();
        for (final String code : ToolArgs.strings(args, "codes", 10))
        {
            final Optional<Product> product = catalogGateway.byCode(state.pack, code);
            product.ifPresent(products::add);
        }
        return ToolResult.of(products, products.stream().map(Product::code).toList());
    }
}
