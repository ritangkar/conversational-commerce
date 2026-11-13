package com.conversational.commerce.core.engine.tools;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import com.conversational.commerce.core.engine.gateway.PriceGateway;
import com.conversational.commerce.core.engine.model.TraceStep;
import com.conversational.commerce.core.engine.orchestrator.ChatTool;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import com.conversational.commerce.core.engine.rules.BudgetPlanner;
import com.conversational.commerce.core.engine.rules.BudgetPlanner.Candidate;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/** Budget planner (PA-04). Candidates come from this turn's history results; prices stay in Java. */
public class PlanBudgetTool implements ChatTool
{
    @Resource(name = "ccPriceGateway")
    private PriceGateway priceGateway;

    @Resource(name = "ccConfigurationService")
    private CcConfigurationService configurationService;

    private final BudgetPlanner planner = new BudgetPlanner();

    public void setPriceGateway(final PriceGateway priceGateway)
    {
        this.priceGateway = priceGateway;
    }

    public void setConfigurationService(final CcConfigurationService configurationService)
    {
        this.configurationService = configurationService;
    }

    @Override
    public String name()
    {
        return "planBudgetPurchase";
    }

    @Override
    public String capabilityId()
    {
        return "PA-04";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(final TurnState state, final Map<String, Object> args)
    {
        final long budgetMinor = ToolArgs.number(args, "budgetMinor", 1, 1_000_000_000_000L, 0);
        final int cap = configurationService.getInt("budget.candidateCap", 30);
        final List<Candidate> candidates = new ArrayList<>();
        final Object raw = args.get("usualItems"); // List<PurchaseHistoryTool.Entry-like maps> built by the workflow, not by the model
        if (raw instanceof List<?> list)
        {
            for (final Object o : list)
            {
                final Map<String, Object> m = (Map<String, Object>) o;
                final String code = (String) m.get("code");
                final OptionalLong price = priceGateway.unitPriceMinor(state.customerId, code);
                if (price.isPresent() && candidates.size() < cap)
                {
                    candidates.add(new Candidate(code, price.getAsLong(), ((Number) m.get("quantity")).intValue(),
                            ((Number) m.get("score")).intValue()));
                }
            }
        }
        final BudgetPlanner.Plan plan = planner.plan(candidates, budgetMinor);
        return ToolResult.of(plan, plan.lines().stream().map(BudgetPlanner.Line::code).toList(),
                new TraceStep("Fitted your usual items into the budget", "Your orders and your contract prices", "last 180 days",
                        "never above the budget"));
    }
}
