package com.conversational.commerce.core.engine.orchestrator.workflow;

import com.conversational.commerce.core.engine.model.Blocks.Block;
import com.conversational.commerce.core.engine.model.Blocks.ListLine;
import com.conversational.commerce.core.engine.model.Decision;
import com.conversational.commerce.core.engine.model.Domain.Order;
import com.conversational.commerce.core.engine.model.Domain.OrderLine;
import com.conversational.commerce.core.engine.model.Intent;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import com.conversational.commerce.core.engine.model.Domain.Product;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** REORDER (PA-07): lines of one of the customer's own orders become a labelled demo list; discontinued lines use replacements. */
public class ReorderWorkflow extends AbstractWorkflow
{
    private static final Pattern ORDER_CODE = Pattern.compile("\\b(?:ORD|PO)[-#]?\\d{4,}\\b|\\b\\d{8}\\b");

    @Override
    public Intent intent()
    {
        return Intent.REORDER;
    }

    @Override
    public Output run(final TurnState state, final Decision decision)
    {
        final Matcher m = ORDER_CODE.matcher(state.workingMessage);
        final ToolResult orderResult = m.find()
                ? tool(state, "step.findingOrder", "getOrderById", Map.of("orderCode", m.group()))
                : tool(state, "step.findingOrder", "getLatestOrder", Map.of());
        final List<Order> found = orderResult.as();
        if (found.isEmpty())
        {
            return Output.of(List.of(), "reorder_none", Map.of(), Map.of());
        }
        final List<ListLine> lines = new ArrayList<>();
        for (final OrderLine line : found.get(0).lines())
        {
            final ToolResult details = toolRegistry.execute(state, "getProductDetails", Map.of("codes", List.of(line.productCode())));
            final List<Product> products = details.as();
            String code = line.productCode();
            String issue = null;
            if (!products.isEmpty() && products.get(0).discontinued())
            {
                final ToolResult alt = toolRegistry.execute(state, "getAlternatives", Map.of("code", code));
                final List<Product> alternatives = alt.as();
                if (alternatives.isEmpty())
                {
                    lines.add(hydrator.line(state, code, 0, "reorder", "Discontinued, no replacement"));
                    continue;
                }
                issue = code + " replaced by " + alternatives.get(0).code();
                code = alternatives.get(0).code();
            }
            lines.add(hydrator.line(state, code, line.quantity(), "reorder", issue));
        }
        return Output.of(List.of(Block.purchaseList("b-reorder", lines)), "reorder", Map.of("lines", lines.size()),
                Map.of("lineCount", String.valueOf(lines.size())));
    }
}
