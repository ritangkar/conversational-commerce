package com.conversational.commerce.core.engine.orchestrator.workflow;

import com.conversational.commerce.core.engine.model.Blocks.Block;
import com.conversational.commerce.core.engine.model.Blocks.ListLine;
import com.conversational.commerce.core.engine.model.Decision;
import com.conversational.commerce.core.engine.model.Intent;
import com.conversational.commerce.core.engine.orchestrator.MoneyFormat;
import com.conversational.commerce.core.engine.orchestrator.ToolRegistry.Call;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import com.conversational.commerce.core.engine.rules.BudgetPlanner;
import com.conversational.commerce.core.engine.rules.ReplenishmentRule;
import com.conversational.commerce.core.engine.tools.PurchaseHistoryTool;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** BUDGET_PLAN (SC-04): usual items from history, due items first, fitted into the budget in integer arithmetic. */
public class BudgetPlanWorkflow extends AbstractWorkflow
{
    @Override
    public Intent intent()
    {
        return Intent.BUDGET_PLAN;
    }

    @Override
    public Output run(final TurnState state, final Decision decision)
    {
        if (decision.budgetAmount() == null)
        {
            return Output.of(List.of(), "budget_clarify", Map.of(), Map.of());
        }
        final List<ToolResult> group = state.step("step.checkingHistory", () -> toolRegistry.executeParallel(state,
                List.of(new Call("getCustomerPurchaseHistory", Map.of("windowDays", 365)),
                        new Call("getReplenishmentRecommendations", Map.of())),
                parallelTimeoutSeconds()));
        final List<PurchaseHistoryTool.Entry> history = group.get(0).as();
        final List<ReplenishmentRule.Item> due = group.get(1).as();
        final Set<String> dueCodes = new HashSet<>();
        due.forEach(d -> dueCodes.add(d.code()));
        final List<Map<String, Object>> usual = new ArrayList<>();
        for (final PurchaseHistoryTool.Entry e : history)
        {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", e.code());
            m.put("quantity", Math.max(1, e.totalQuantity() / Math.max(1, e.orderCount())));
            m.put("score", e.orderCount() * 10 + (dueCodes.contains(e.code()) ? 100 : 0));
            usual.add(m);
        }
        final long budgetMinor = MoneyFormat.toMinor(decision.budgetAmount());
        final ToolResult planResult = tool(state, "step.fittingBudget", "planBudgetPurchase",
                Map.of("budgetMinor", budgetMinor, "usualItems", usual));
        final BudgetPlanner.Plan plan = planResult.as();
        final List<ListLine> lines = new ArrayList<>();
        for (final BudgetPlanner.Line l : plan.lines())
        {
            lines.add(hydrator.line(state, l.code(), l.quantity(), "budget plan", l.reduced() ? "Quantity reduced to fit" : null));
        }
        final Map<String, String> slots = new LinkedHashMap<>();
        slots.put("budget", hydrator.format(state, plan.budgetMinor()));
        slots.put("total", hydrator.format(state, plan.totalMinor()));
        slots.put("excludedCount", String.valueOf(plan.excluded().size()));
        final List<Block> blocks = new ArrayList<>(List.of(Block.purchaseList("b-plan", lines)));
        return new Output(blocks, "budget_plan", Map.of("lines", lines.size()), slots, null, List.of("ADD_ALL_TO_LIST"), null);
    }
}
