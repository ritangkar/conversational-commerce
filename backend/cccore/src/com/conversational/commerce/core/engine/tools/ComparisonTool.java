package com.conversational.commerce.core.engine.tools;

import com.conversational.commerce.core.engine.gateway.CatalogGateway;
import com.conversational.commerce.core.engine.model.Blocks.Comparison;
import com.conversational.commerce.core.engine.model.Blocks.ComparisonRow;
import com.conversational.commerce.core.engine.model.Domain.Product;
import com.conversational.commerce.core.engine.model.TraceStep;
import com.conversational.commerce.core.engine.orchestrator.ChatTool;
import com.conversational.commerce.core.engine.orchestrator.ToolRejectedException;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class ComparisonTool implements ChatTool
{
    public static final String MISSING = "Not available";

    @Resource(name = "ccCatalogGateway")
    private CatalogGateway catalogGateway;

    public void setCatalogGateway(final CatalogGateway catalogGateway)
    {
        this.catalogGateway = catalogGateway;
    }

    @Override
    public String name()
    {
        return "getComparisonAttributes";
    }

    @Override
    public String capabilityId()
    {
        return "PD-04";
    }

    @Override
    public ToolResult execute(final TurnState state, final Map<String, Object> args)
    {
        final List<Product> products = new ArrayList<>();
        for (final String code : ToolArgs.strings(args, "codes", 4))
        {
            catalogGateway.byCode(state.pack, code).ifPresent(products::add);
        }
        if (products.size() < 2)
        {
            throw new ToolRejectedException("need_two_products");
        }
        final TreeSet<String> keys = new TreeSet<>();
        products.forEach(p -> keys.addAll(p.attributes().keySet()));
        final List<ComparisonRow> rows = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        for (final String key : keys)
        {
            final List<String> values = products.stream().map(p -> p.attributes().getOrDefault(key, MISSING)).toList();
            if (values.contains(MISSING))
            {
                notes.add(key + " is not recorded for every product");
            }
            rows.add(new ComparisonRow(key, values));
        }
        final Comparison table = new Comparison(products.stream().map(Product::name).toList(), rows, notes);
        return ToolResult.of(table, products.stream().map(Product::code).toList(),
                new TraceStep("Compared catalogue attributes", "Catalogue", "current", rows.size() + " attributes"));
    }
}
