package com.conversational.commerce.core.engine.orchestrator.workflow;

import com.conversational.commerce.core.engine.model.Blocks.Block;
import com.conversational.commerce.core.engine.model.Blocks.Recommendation;
import com.conversational.commerce.core.engine.model.Decision;
import com.conversational.commerce.core.engine.model.Intent;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import com.conversational.commerce.core.engine.rules.BasketReviewRule.Flag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** BASKET_REVIEW (SC-08): the list comes from the validated request body (A11), never from server state. */
public class BasketReviewWorkflow extends AbstractWorkflow
{
    @Override
    public Intent intent()
    {
        return Intent.BASKET_REVIEW;
    }

    @Override
    public Output run(final TurnState state, final Decision decision)
    {
        if (state.request.purchaseList().isEmpty())
        {
            return Output.of(List.of(), "basket_empty", Map.of(), Map.of());
        }
        final ToolResult result = tool(state, "step.reviewingYourList", "reviewPurchaseList", Map.of());
        final List<Flag> flags = result.as();
        final List<Recommendation> items = flags.stream()
                .map(f -> hydrator.recommendation(state, f.code(), 0, f.type().name(), f.type() == com.conversational.commerce.core.engine.rules.BasketReviewRule.FlagType.FORGOTTEN_USUAL))
                .toList();
        final Map<String, String> slots = new LinkedHashMap<>();
        slots.put("flagCount", String.valueOf(flags.size()));
        return new Output(List.of(Block.recommendations("b-flags", items, false)), "basket_review", Map.of("flags", flags.size()),
                slots, null, List.of(), null);
    }
}
