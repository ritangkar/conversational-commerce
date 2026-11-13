package com.conversational.commerce.core.engine.tools;

import com.conversational.commerce.core.engine.gateway.OrderGateway;
import com.conversational.commerce.core.engine.model.Domain.Order;
import com.conversational.commerce.core.engine.model.Domain.OrderLine;
import com.conversational.commerce.core.engine.model.Domain.OrderStatus;
import com.conversational.commerce.core.engine.model.TraceStep;
import com.conversational.commerce.core.engine.orchestrator.ChatTool;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Aggregated purchase history per product (not raw orders). Own history only (D34). */
public class PurchaseHistoryTool implements ChatTool
{
    public record Entry(String code, int orderCount, int totalQuantity, LocalDate lastOrdered)
    {
    }

    @Resource
    private OrderGateway orderGateway;

    public void setOrderGateway(final OrderGateway orderGateway)
    {
        this.orderGateway = orderGateway;
    }

    @Override
    public String name()
    {
        return "getCustomerPurchaseHistory";
    }

    @Override
    public String capabilityId()
    {
        return "RI-01";
    }

    @Override
    public ToolResult execute(final TurnState state, final Map<String, Object> args)
    {
        // FUTURE SCOPE: unit-level history (D34).
        final int window = (int) ToolArgs.number(args, "windowDays", 1, 730, 180);
        final LocalDate from = state.today.minusDays(window);
        final Map<String, int[]> agg = new TreeMap<>();
        final Map<String, LocalDate> last = new TreeMap<>();
        for (final Order o : orderGateway.ordersOf(state.customerId))
        {
            if (o.status() == OrderStatus.CANCELLED || o.placedOn().isBefore(from))
            {
                continue;
            }
            for (final OrderLine l : o.lines())
            {
                final int[] a = agg.computeIfAbsent(l.productCode(), k -> new int[2]);
                a[0]++;
                a[1] += l.quantity();
                last.merge(l.productCode(), o.placedOn(), (x, y) -> x.isAfter(y) ? x : y);
            }
        }
        final List<Entry> entries = agg.entrySet().stream()
                .map(e -> new Entry(e.getKey(), e.getValue()[0], e.getValue()[1], last.get(e.getKey()))).toList();
        return ToolResult.of(entries, entries.stream().map(Entry::code).toList(),
                new TraceStep("Reviewed your order history", "Your orders in Commerce", "last " + window + " days",
                        entries.size() + " products"));
    }
}
