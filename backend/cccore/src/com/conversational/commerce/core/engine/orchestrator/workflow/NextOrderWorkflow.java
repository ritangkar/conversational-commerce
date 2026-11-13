package com.conversational.commerce.core.engine.orchestrator.workflow;

import com.conversational.commerce.core.engine.model.Blocks.Block;
import com.conversational.commerce.core.engine.model.Blocks.ProductCard;
import com.conversational.commerce.core.engine.model.Blocks.Recommendation;
import com.conversational.commerce.core.engine.model.Decision;
import com.conversational.commerce.core.engine.model.Domain.Order;
import com.conversational.commerce.core.engine.model.Intent;
import com.conversational.commerce.core.engine.orchestrator.ToolRegistry.Call;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import com.conversational.commerce.core.engine.rules.ReplenishmentRule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** NEXT_ORDER (SC-01, SC-09): [history, replenishment net of pending, pending] then complements, rank, hydrate. */
public class NextOrderWorkflow extends AbstractWorkflow
{
    @Override
    public Intent intent()
    {
        return Intent.NEXT_ORDER;
    }

    @Override
    public Output run(final TurnState state, final Decision decision)
    {
        final List<ToolResult> group = state.step("step.checkingHistory", () -> toolRegistry.executeParallel(state,
                List.of(new Call("getCustomerPurchaseHistory", Map.of("windowDays", 180)),
                        new Call("getReplenishmentRecommendations", Map.of("netPending", true)),
                        new Call("getPendingOrders", Map.of())),
                parallelTimeoutSeconds()));
        final List<ReplenishmentRule.Item> due = group.get(1).as();
        final List<Order> pending = group.get(2).as();
        if (due.isEmpty())
        {
            return Output.of(List.of(), "next_order_none", Map.of(), Map.of());
        }
        final List<Recommendation> recommendations = new ArrayList<>();
        for (final ReplenishmentRule.Item item : due)
        {
            recommendations.add(hydrator.recommendation(state, item.code(), item.suggestedQuantity(),
                    item.overdue() ? "OVERDUE" : "DUE", true));
        }
        final List<Block> blocks = new ArrayList<>();
        blocks.add(Block.recommendations("b-due", recommendations, true));
        final ToolResult complements = tool(state, "step.lookingForComplements", "getComplementaryProducts",
                Map.of("codes", due.stream().map(ReplenishmentRule.Item::code).toList()));
        final List<com.conversational.commerce.core.engine.rules.CoPurchaseRule.Suggestion> suggestions = complements.as();
        if (!suggestions.isEmpty())
        {
            final List<ProductCard> cards = suggestions.stream()
                    .map(s -> hydrator.card(state, s.code(), Map.of("reason", "COMPLEMENT"))).toList();
            blocks.add(Block.productCards("b-complements", cards));
        }
        final Map<String, String> slots = new LinkedHashMap<>();
        slots.put("dueCount", String.valueOf(due.size()));
        slots.put("overdueCount", String.valueOf(due.stream().filter(ReplenishmentRule.Item::overdue).count()));
        slots.put("pendingCount", String.valueOf(pending.size()));
        final Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("dueCount", due.size());
        facts.put("pendingOrders", pending.size());
        facts.put("estimate", true);
        return new Output(blocks, "next_order", facts, slots, null, List.of("ADD_ALL_TO_LIST"), null);
    }
}
