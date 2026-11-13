package com.conversational.commerce.core.engine.orchestrator.workflow;

import com.conversational.commerce.core.engine.model.Blocks.Block;
import com.conversational.commerce.core.engine.model.Decision;
import com.conversational.commerce.core.engine.model.Domain.Order;
import com.conversational.commerce.core.engine.model.Intent;
import com.conversational.commerce.core.engine.orchestrator.MoneyFormat;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ORDER_LOOKUP (SC-06): filters extracted by the model are range-checked, resolved to dates in Java, scoped to the customer. */
public class OrderLookupWorkflow extends AbstractWorkflow
{
    @Override
    public Intent intent()
    {
        return Intent.ORDER_LOOKUP;
    }

    @Override
    public Output run(final TurnState state, final Decision decision)
    {
        final Map<String, Object> args = new LinkedHashMap<>();
        args.put("status", decision.orderStatus());
        if (decision.orderMinAmount() != null)
        {
            args.put("minTotalMinor", MoneyFormat.toMinor(decision.orderMinAmount()));
        }
        final LocalDate today = state.today;
        switch (decision.orderPeriod())
        {
            case "LAST_MONTH" ->
            {
                final LocalDate first = today.withDayOfMonth(1).minusMonths(1);
                args.put("fromDate", first.toString());
                args.put("toDate", first.withDayOfMonth(first.lengthOfMonth()).toString());
            }
            case "LAST_30_DAYS" ->
            {
                args.put("fromDate", today.minusDays(30).toString());
                args.put("toDate", today.toString());
            }
            case "THIS_MONTH" ->
            {
                args.put("fromDate", today.withDayOfMonth(1).toString());
                args.put("toDate", today.toString());
            }
            default ->
            {
            }
        }
        final ToolResult found = tool(state, "step.searchingOrders", "searchOrders", args);
        final List<Order> orders = found.as();
        if (orders.isEmpty())
        {
            return Output.of(List.of(), "order_none", Map.of(), Map.of());
        }
        // Tracking for the latest match: read from the same customer-scoped data (FUTURE SCOPE: carrier tracking).
        final Order latest = orders.get(0);
        if (decision.wantsLatestTracking())
        {
            tool(state, "step.checkingDelivery", "getOrderTracking", Map.of("orderCode", latest.code()));
        }
        final Block block = Block.orders("b-orders", orders.stream().map(hydrator::order).toList());
        return Output.of(List.of(block), "order_lookup", Map.of("count", orders.size()), Map.of("count", String.valueOf(orders.size())));
    }
}
