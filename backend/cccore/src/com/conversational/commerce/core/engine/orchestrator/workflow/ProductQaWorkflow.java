package com.conversational.commerce.core.engine.orchestrator.workflow;

import com.conversational.commerce.core.engine.model.Blocks.Block;
import com.conversational.commerce.core.engine.model.Blocks.Comparison;
import com.conversational.commerce.core.engine.model.Blocks.ComparisonRow;
import com.conversational.commerce.core.engine.model.Decision;
import com.conversational.commerce.core.engine.model.Domain.Product;
import com.conversational.commerce.core.engine.model.Intent;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** PRODUCT_QA (PD-03, PD-05): grounded in approved catalogue fields only. */
public class ProductQaWorkflow extends AbstractWorkflow
{
    @Override
    public Intent intent()
    {
        return Intent.PRODUCT_QA;
    }

    @Override
    public Output run(final TurnState state, final Decision decision)
    {
        List<String> codes = decision.codes();
        if (codes.isEmpty() && !decision.keywords().isEmpty())
        {
            final ToolResult search = tool(state, "step.searchingCatalogue", "searchProducts",
                    Map.of("keywords", decision.keywords(), "limit", 1));
            final List<Product> found = search.as();
            codes = found.stream().map(Product::code).toList();
        }
        final ToolResult details = tool(state, "step.readingProductData", "getProductDetails", Map.of("codes", codes));
        final List<Product> products = details.as();
        if (products.isEmpty())
        {
            return Output.of(List.of(), "qa_none", Map.of(), Map.of());
        }
        final Product product = products.get(0);
        final List<ComparisonRow> rows = new ArrayList<>();
        product.attributes().forEach((k, v) -> rows.add(new ComparisonRow(k, List.of(v))));
        final Comparison table = new Comparison(List.of(product.name()), rows, List.of());
        return new Output(List.of(Block.comparison("b-details", table)), "product_qa", Map.of("product", product.code()), Map.of(),
                table, List.of(), product.code());
    }
}
