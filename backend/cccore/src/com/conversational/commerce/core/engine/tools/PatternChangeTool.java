package com.conversational.commerce.core.engine.tools;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import com.conversational.commerce.core.engine.gateway.OrderGateway;
import com.conversational.commerce.core.engine.model.TraceStep;
import com.conversational.commerce.core.engine.orchestrator.ChatTool;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import com.conversational.commerce.core.engine.rules.PatternChangeRule;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

public class PatternChangeTool implements ChatTool
{
    @Resource(name = "ccOrderGateway")
    private OrderGateway orderGateway;

    @Resource(name = "ccConfigurationService")
    private CcConfigurationService configurationService;

    private final PatternChangeRule rule = new PatternChangeRule();

    public void setOrderGateway(final OrderGateway orderGateway)
    {
        this.orderGateway = orderGateway;
    }

    public void setConfigurationService(final CcConfigurationService configurationService)
    {
        this.configurationService = configurationService;
    }

    @Override
    public String name()
    {
        return "detectPurchasePatternChanges";
    }

    @Override
    public String capabilityId()
    {
        return "RI-04";
    }

    @Override
    public ToolResult execute(final TurnState state, final Map<String, Object> args)
    {
        final int threshold = configurationService.getInt("patternChange.thresholdPct", 30);
        final int minSample = configurationService.getInt("patternChange.minSample", 2);
        final int recent = configurationService.getInt("patternChange.recentOrders", 2);
        final List<PatternChangeRule.Observation> found = rule.detect(orderGateway.ordersOf(state.customerId), threshold,
                minSample, recent);
        return ToolResult.of(found, found.stream().map(PatternChangeRule.Observation::code).toList(),
                new TraceStep("Compared your recent orders with earlier ones", "Your orders in Commerce",
                        "last " + recent + " orders against earlier", "change of " + threshold + "% or more"));
    }
}
