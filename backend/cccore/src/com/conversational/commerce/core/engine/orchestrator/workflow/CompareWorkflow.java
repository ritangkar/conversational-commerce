package com.conversational.commerce.core.engine.orchestrator.workflow;

import com.conversational.commerce.core.engine.model.Blocks.Block;
import com.conversational.commerce.core.engine.model.Blocks.Comparison;
import com.conversational.commerce.core.engine.model.Decision;
import com.conversational.commerce.core.engine.model.Domain.Product;
import com.conversational.commerce.core.engine.model.Intent;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** COMPARE (SC-07): products from the message, the browser shortlist (re-validated by the catalogue) or a search; table first. */
public class CompareWorkflow extends AbstractWorkflow
{
    @Override
    public Intent intent()
    {
        return Intent.COMPARE;
    }

    @Override
    public Output run(final TurnState state, final Decision decision)
    {
        final Set<String> codes = new LinkedHashSet<>(decision.codes());
        codes.addAll(state.request.shortlistCodes());
        if (codes.size() < 2 && !decision.keywords().isEmpty())
        {
            final ToolResult search = tool(state, "step.searchingCatalogue", "searchProducts",
                    Map.of("keywords", decision.keywords(), "limit", 3));
            final List<Product> found = search.as();
            found.forEach(p -> codes.add(p.code()));
        }
        if (codes.size() < 2)
        {
            return Output.of(List.of(Block.clarification("b-clarify", "Which products should I compare?", List.of())), "clarify",
                    Map.of(), Map.of());
        }
        final List<String> limited = new ArrayList<>(codes).subList(0, Math.min(4, codes.size()));
        final ToolResult result = tool(state, "step.comparingAttributes", "getComparisonAttributes", Map.of("codes", limited));
        final Comparison table = result.as();
        return new Output(List.of(Block.comparison("b-compare", table)), "compare", Map.of("attributes", table.rows().size()),
                Map.of(), table, List.of(), limited.get(0));
    }
}
