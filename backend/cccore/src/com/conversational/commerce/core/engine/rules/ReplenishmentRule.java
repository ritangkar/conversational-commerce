package com.conversational.commerce.core.engine.rules;

import com.conversational.commerce.core.engine.model.Domain.Order;
import com.conversational.commerce.core.engine.model.Domain.OrderLine;
import com.conversational.commerce.core.engine.model.Domain.OrderStatus;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Simple rule-based replenishment (D11): average interval between the customer's own orders. */
public class ReplenishmentRule
{
    // FUTURE SCOPE: unit-level replenishment (D34): feed orders of the whole B2B unit instead of one customer.

    public record Item(String code, int suggestedQuantity, int intervalDays, int daysSinceLast, boolean overdue,
            boolean estimate)
    {
    }

    public List<Item> compute(final List<Order> orders, final LocalDate today, final int windowDays, final int minOrders,
            final double dueRatio)
    {
        final LocalDate from = today.minusDays(windowDays);
        final Map<String, TreeMap<LocalDate, Integer>> byProduct = new TreeMap<>();
        for (final Order order : orders)
        {
            if (order.status() == OrderStatus.CANCELLED || order.placedOn().isBefore(from) || order.placedOn().isAfter(today))
            {
                continue;
            }
            for (final OrderLine line : order.lines())
            {
                byProduct.computeIfAbsent(line.productCode(), k -> new TreeMap<>()).merge(order.placedOn(), line.quantity(),
                        Integer::sum);
            }
        }
        final List<Item> result = new ArrayList<>();
        for (final Map.Entry<String, TreeMap<LocalDate, Integer>> entry : byProduct.entrySet())
        {
            final TreeMap<LocalDate, Integer> dates = entry.getValue();
            if (dates.size() < Math.max(2, minOrders))
            {
                continue;
            }
            final List<LocalDate> sorted = new ArrayList<>(dates.keySet());
            long gapSum = 0;
            for (int i = 1; i < sorted.size(); i++)
            {
                gapSum += ChronoUnit.DAYS.between(sorted.get(i - 1), sorted.get(i));
            }
            final int interval = (int) Math.max(1, Math.round((double) gapSum / (sorted.size() - 1)));
            final int since = (int) ChronoUnit.DAYS.between(sorted.get(sorted.size() - 1), today);
            final double ratio = (double) since / interval;
            if (ratio < dueRatio)
            {
                continue;
            }
            final int avgQty = (int) Math.max(1, Math.round(dates.values().stream().mapToInt(Integer::intValue).average().orElse(1)));
            result.add(new Item(entry.getKey(), avgQty, interval, since, ratio > 1.0, true));
        }
        result.sort(Comparator.comparingInt((Item i) -> i.overdue() ? 0 : 1).thenComparing(Item::code));
        return result;
    }

    /** Removes or reduces suggestions already covered by pending orders (RI-02). */
    public List<Item> netAgainstPending(final List<Item> items, final List<Order> pendingOrders)
    {
        final Map<String, Integer> pendingQty = new TreeMap<>();
        for (final Order order : pendingOrders)
        {
            for (final OrderLine line : order.lines())
            {
                pendingQty.merge(line.productCode(), line.quantity(), Integer::sum);
            }
        }
        final List<Item> result = new ArrayList<>();
        for (final Item item : items)
        {
            final int pending = pendingQty.getOrDefault(item.code(), 0);
            final int remaining = item.suggestedQuantity() - pending;
            if (remaining > 0)
            {
                result.add(new Item(item.code(), remaining, item.intervalDays(), item.daysSinceLast(), item.overdue(), true));
            }
        }
        return result;
    }
}
