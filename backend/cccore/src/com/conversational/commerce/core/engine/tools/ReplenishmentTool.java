package com.conversational.commerce.core.engine.tools;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import com.conversational.commerce.core.engine.gateway.OrderGateway;
import com.conversational.commerce.core.engine.model.Domain.Order;
import com.conversational.commerce.core.engine.model.Domain.OrderStatus;
import com.conversational.commerce.core.engine.model.TraceStep;
import com.conversational.commerce.core.engine.orchestrator.ChatTool;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import com.conversational.commerce.core.engine.rules.ReplenishmentRule;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

public class ReplenishmentTool implements ChatTool
{
    @Resource
    private OrderGateway orderGateway;

    @Resource
    private CcConfigurationService configurationService;

    private final ReplenishmentRule rule = new ReplenishmentRule();

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
        return "getReplenishmentRecommendations";
    }

    @Override
    public String capabilityId()
    {
        return "RI-01";
    }

    @Override
    public ToolResult execute(final TurnState state, final Map<String, Object> args)
    {
        // FUTURE SCOPE: unit-level replenishment (D34).
        final int window = configurationService.getInt("replenishment.windowDays", 180);
        final int minOrders = configurationService.getInt("replenishment.minOrders", 3);
        final double dueRatio = parseRatio(configurationService.getString("replenishment.dueRatio", "0.9"));
        final List<Order> orders = orderGateway.ordersOf(state.customerId);
        List<ReplenishmentRule.Item> items = rule.compute(orders, state.today, window, minOrders, dueRatio);
        if (Boolean.TRUE.equals(args.get("netPending")))
        {
            final List<Order> pending = orders.stream()
                    .filter(o -> o.status() == OrderStatus.PENDING || o.status() == OrderStatus.PROCESSING).toList();
            items = rule.netAgainstPending(items, pending);
        }
        return ToolResult.of(items, items.stream().map(ReplenishmentRule.Item::code).toList(),
                new TraceStep("Estimated what you are due to reorder", "Your orders in Commerce", "last " + window + " days",
                        "due at " + (int) (dueRatio * 100) + "% of your usual interval; at least " + minOrders + " orders"));
    }

    private double parseRatio(final String value)
    {
        try
        {
            return Double.parseDouble(value);
        }
        catch (final NumberFormatException e)
        {
            return 0.9;
        }
    }
}
