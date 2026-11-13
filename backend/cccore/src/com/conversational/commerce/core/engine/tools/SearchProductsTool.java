package com.conversational.commerce.core.engine.tools;

import com.conversational.commerce.core.engine.gateway.CatalogGateway;
import com.conversational.commerce.core.engine.model.Domain.Product;
import com.conversational.commerce.core.engine.model.TraceStep;
import com.conversational.commerce.core.engine.orchestrator.ChatTool;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

public class SearchProductsTool implements ChatTool
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
        return "searchProducts";
    }

    @Override
    public String capabilityId()
    {
        return "PD-01";
    }

    @Override
    public ToolResult execute(final TurnState state, final Map<String, Object> args)
    {
        final List<String> keywords = ToolArgs.strings(args, "keywords", 12);
        final String category = ToolArgs.string(args, "categoryCode", null);
        final Map<String, String> filters = ToolArgs.stringMap(args, "filters", 8);
        final int limit = (int) ToolArgs.number(args, "limit", 1, 50, 10);
        final List<Product> found = catalogGateway.findCandidates(state.pack, keywords, category, filters, limit);
        return ToolResult.of(found, found.stream().map(Product::code).toList(),
                new TraceStep("Searched the catalogue", "Catalogue", "current", found.size() + " candidates"));
    }
}
