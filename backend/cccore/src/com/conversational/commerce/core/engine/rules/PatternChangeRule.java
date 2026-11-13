package com.conversational.commerce.core.engine.rules;

import com.conversational.commerce.core.engine.model.Domain.Order;
import com.conversational.commerce.core.engine.model.Domain.OrderLine;
import com.conversational.commerce.core.engine.model.Domain.OrderStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Purchase-pattern change detection (D12): recent order quantities against the earlier average. */
public class PatternChangeRule
{
    public enum Direction
    {
        DOWN, UP
    }

    public record Observation(String code, double earlierAverage, double recentAverage, int changePercent,
            Direction direction, int earlierSample, int recentSample)
    {
    }

    public List<Observation> detect(final List<Order> orders, final int thresholdPct, final int minSample,
            final int recentOrders)
    {
        final List<Order> sortedOrders = new ArrayList<>(orders);
        sortedOrders.removeIf(o -> o.status() == OrderStatus.CANCELLED);
        sortedOrders.sort(Comparator.comparing(Order::placedOn));
        final Map<String, List<Integer>> series = new TreeMap<>();
        for (final Order order : sortedOrders)
        {
            for (final OrderLine line : order.lines())
            {
                series.computeIfAbsent(line.productCode(), k -> new ArrayList<>()).add(line.quantity());
            }
        }
        final List<Observation> result = new ArrayList<>();
        for (final Map.Entry<String, List<Integer>> entry : series.entrySet())
        {
            final List<Integer> q = entry.getValue();
            if (recentOrders < 1 || q.size() < minSample + recentOrders)
            {
                continue;
            }
            final List<Integer> recent = q.subList(q.size() - recentOrders, q.size());
            final List<Integer> earlier = q.subList(0, q.size() - recentOrders);
            final double earlierAvg = earlier.stream().mapToInt(Integer::intValue).average().orElse(0);
            final double recentAvg = recent.stream().mapToInt(Integer::intValue).average().orElse(0);
            if (earlierAvg <= 0)
            {
                continue;
            }
            final int change = (int) Math.round(Math.abs(recentAvg - earlierAvg) / earlierAvg * 100);
            if (change >= thresholdPct)
            {
                result.add(new Observation(entry.getKey(), earlierAvg, recentAvg, change,
                        recentAvg < earlierAvg ? Direction.DOWN : Direction.UP, earlier.size(), recent.size()));
            }
        }
        result.sort(Comparator.comparingInt((Observation o) -> -o.changePercent()).thenComparing(Observation::code));
        return result;
    }
}
