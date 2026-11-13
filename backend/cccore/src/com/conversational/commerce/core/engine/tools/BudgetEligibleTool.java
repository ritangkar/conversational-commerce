package com.conversational.commerce.core.engine.tools;

import com.conversational.commerce.core.engine.gateway.PriceGateway;
import com.conversational.commerce.core.engine.model.TraceStep;
import com.conversational.commerce.core.engine.orchestrator.ChatTool;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/** Returns eligible codes only. Prices are evaluated in Java and never returned to the model (D06, D26). */
public class BudgetEligibleTool implements ChatTool
{
    @Resource(name = "ccPriceGateway")
    private PriceGateway priceGateway;

    public void setPriceGateway(final PriceGateway priceGateway)
    {
        this.priceGateway = priceGateway;
    }

    @Override
    public String name()
    {
        return "getBudgetEligibleProducts";
    }

    @Override
    public String capabilityId()
    {
        return "PD-07";
    }

    @Override
    public ToolResult execute(final TurnState state, final Map<String, Object> args)
    {
        final long maxUnitMinor = ToolArgs.number(args, "maxUnitMinor", 1, 1_000_000_000_000L, Long.MAX_VALUE);
        final List<String> eligible = new ArrayList<>();
        for (final String code : ToolArgs.strings(args, "candidateCodes", 50))
        {
            final OptionalLong price = priceGateway.unitPriceMinor(state.customerId, code);
            if (state.provenance.contains(code) && price.isPresent() && price.getAsLong() <= maxUnitMinor)
            {
                eligible.add(code);
            }
        }
        return ToolResult.of(eligible, eligible,
                new TraceStep("Applied your price limit", "Your contract prices", "current", eligible.size() + " within limit"));
    }
}
