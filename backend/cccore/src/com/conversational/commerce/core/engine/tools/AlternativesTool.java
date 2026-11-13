package com.conversational.commerce.core.engine.tools;

import com.conversational.commerce.core.engine.gateway.CatalogGateway;
import com.conversational.commerce.core.engine.gateway.StockGateway;
import com.conversational.commerce.core.engine.model.Domain.Product;
import com.conversational.commerce.core.engine.orchestrator.ChatTool;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Alternatives (PD-06): the defined replacement of a discontinued item, else same-category items that are in demo stock. */
public class AlternativesTool implements ChatTool
{
    @Resource
    private CatalogGateway catalogGateway;

    @Resource
    private StockGateway stockGateway;

    public void setCatalogGateway(final CatalogGateway catalogGateway)
    {
        this.catalogGateway = catalogGateway;
    }

    public void setStockGateway(final StockGateway stockGateway)
    {
        this.stockGateway = stockGateway;
    }

    @Override
    public String name()
    {
        return "getAlternatives";
    }

    @Override
    public String capabilityId()
    {
        return "PD-06";
    }

    @Override
    public ToolResult execute(final TurnState state, final Map<String, Object> args)
    {
        final String code = ToolArgs.string(args, "code", "");
        final Optional<Product> original = catalogGateway.byCode(state.pack, code);
        final List<Product> alternatives = new ArrayList<>();
        if (original.isPresent())
        {
            final Product product = original.get();
            if (product.replacementCode() != null)
            {
                catalogGateway.byCode(state.pack, product.replacementCode()).ifPresent(alternatives::add);
            }
            if (alternatives.isEmpty())
            {
                for (final Product p : catalogGateway.findCandidates(state.pack, List.of(), product.category(), Map.of(), 10))
                {
                    if (!p.code().equals(code) && !p.discontinued() && stockGateway.stockOf(p.code()).units() > 0
                            && alternatives.size() < 3)
                    {
                        alternatives.add(p);
                    }
                }
            }
        }
        return ToolResult.of(alternatives, alternatives.stream().map(Product::code).toList());
    }
}
