package com.conversational.commerce.core.engine.tools;

import com.conversational.commerce.core.engine.gateway.OrderGateway;
import com.conversational.commerce.core.engine.model.Domain.Order;
import com.conversational.commerce.core.engine.model.Domain.OrderStatus;
import com.conversational.commerce.core.engine.model.TraceStep;
import com.conversational.commerce.core.engine.orchestrator.ChatTool;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Order tools (OM-01..03, RI-02). Every query is scoped to state.customerId; nothing customer-related is a tool argument. */
public final class OrderTools
{
    private static final String SOURCE = "Your orders in Commerce";

    private OrderTools()
    {
    }

    public abstract static class Base implements ChatTool
    {
        @Resource(name = "ccOrderGateway")
        protected OrderGateway orderGateway;

        public void setOrderGateway(final OrderGateway orderGateway)
        {
            this.orderGateway = orderGateway;
        }

        @Override
        public String capabilityId()
        {
            return "OM-01";
        }
    }

    public static class LatestOrderTool extends Base
    {
        @Override
        public String name()
        {
            return "getLatestOrder";
        }

        @Override
        public ToolResult execute(final TurnState state, final Map<String, Object> args)
        {
            final Optional<Order> latest = orderGateway.ordersOf(state.customerId).stream()
                    .filter(o -> o.status() != OrderStatus.CANCELLED).max(Comparator.comparing(Order::placedOn));
            final List<Order> list = latest.map(List::of).orElse(List.of());
            return ToolResult.of(list, list.stream().map(Order::code).toList(),
                    new TraceStep("Looked up your latest order", SOURCE, "all time", list.size() + " found"));
        }
    }

    public static class OrderByIdTool extends Base
    {
        @Override
        public String name()
        {
            return "getOrderById";
        }

        @Override
        public ToolResult execute(final TurnState state, final Map<String, Object> args)
        {
            final String code = ToolArgs.string(args, "orderCode", "");
            final List<Order> list = code.isBlank() ? List.of()
                    : orderGateway.orderByCode(state.customerId, code).map(List::of).orElse(List.of());
            return ToolResult.of(list, list.stream().map(Order::code).toList());
        }
    }

    public static class PendingOrdersTool extends Base
    {
        @Override
        public String name()
        {
            return "getPendingOrders";
        }

        @Override
        public String capabilityId()
        {
            return "RI-02";
        }

        @Override
        public ToolResult execute(final TurnState state, final Map<String, Object> args)
        {
            final List<Order> list = orderGateway.ordersOf(state.customerId).stream()
                    .filter(o -> o.status() == OrderStatus.PENDING || o.status() == OrderStatus.PROCESSING)
                    .sorted(Comparator.comparing(Order::placedOn)).toList();
            return ToolResult.of(list, list.stream().map(Order::code).toList(),
                    new TraceStep("Checked your pending orders", SOURCE, "open orders", list.size() + " pending"));
        }
    }

    public static class SearchOrdersTool extends Base
    {
        @Override
        public String name()
        {
            return "searchOrders";
        }

        @Override
        public String capabilityId()
        {
            return "OM-02";
        }

        @Override
        public ToolResult execute(final TurnState state, final Map<String, Object> args)
        {
            final String statusArg = ToolArgs.string(args, "status", "ALL");
            final long minMinor = ToolArgs.number(args, "minTotalMinor", 0, 1_000_000_000_000L, 0);
            final long maxMinor = ToolArgs.number(args, "maxTotalMinor", 0, 1_000_000_000_000L, Long.MAX_VALUE);
            final LocalDate from = date(args, "fromDate");
            final LocalDate to = date(args, "toDate");
            final int limit = (int) ToolArgs.number(args, "limit", 1, 50, 20);
            final List<Order> list = orderGateway.ordersOf(state.customerId).stream()
                    .filter(o -> statusMatches(statusArg, o.status()))
                    .filter(o -> o.totalMinor() >= minMinor && o.totalMinor() <= maxMinor)
                    .filter(o -> from == null || !o.placedOn().isBefore(from))
                    // toDate is inclusive for the dealer, handled as the exclusive next day (owner DAO convention)
                    .filter(o -> to == null || o.placedOn().isBefore(to.plusDays(1)))
                    .sorted(Comparator.comparing(Order::placedOn).reversed()).limit(limit).toList();
            return ToolResult.of(list, list.stream().map(Order::code).toList(),
                    new TraceStep("Searched your orders", SOURCE, from == null ? "all time" : from + " onwards",
                            list.size() + " matches"));
        }

        private boolean statusMatches(final String requested, final OrderStatus status)
        {
            return switch (requested)
            {
                case "UNDELIVERED" -> status.isUndelivered();
                case "PENDING" -> status == OrderStatus.PENDING || status == OrderStatus.PROCESSING;
                case "DELIVERED" -> status == OrderStatus.DELIVERED;
                default -> status != OrderStatus.CANCELLED;
            };
        }

        private LocalDate date(final Map<String, Object> args, final String key)
        {
            final String value = ToolArgs.string(args, key, null);
            try
            {
                return value == null ? null : LocalDate.parse(value);
            }
            catch (final java.time.format.DateTimeParseException e)
            {
                throw new com.conversational.commerce.core.engine.orchestrator.ToolRejectedException("bad_argument:" + key);
            }
        }
    }

    public static class OrderTrackingTool extends Base
    {
        // FUTURE SCOPE: carrier tracking. Today only the Commerce-held reference and status are returned (D10).
        @Override
        public String name()
        {
            return "getOrderTracking";
        }

        @Override
        public ToolResult execute(final TurnState state, final Map<String, Object> args)
        {
            final String code = ToolArgs.string(args, "orderCode", "");
            final List<Order> list = code.isBlank() ? List.of()
                    : orderGateway.orderByCode(state.customerId, code).map(List::of).orElse(List.of());
            return ToolResult.of(list, list.stream().map(Order::code).toList(),
                    new TraceStep("Checked the delivery status", SOURCE, "current", "status held in Commerce"));
        }
    }
}
