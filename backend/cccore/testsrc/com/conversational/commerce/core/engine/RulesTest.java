package com.conversational.commerce.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conversational.commerce.core.dao.ChatDao.Order;
import com.conversational.commerce.core.dao.ChatDao.OrderLine;
import com.conversational.commerce.core.dao.ChatDao.OrderStatus;
import com.conversational.commerce.core.service.impl.DefaultChatService;
import com.conversational.commerce.core.service.impl.DefaultChatService.BudgetCandidate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RulesTest
{
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

    private static Order order(final int daysAgo, final OrderStatus status, final String code, final int qty)
    {
        return new Order("o" + daysAgo + code, "c", TODAY.minusDays(daysAgo), status, 1, "INR", List.of(new OrderLine(code, qty)), null);
    }

    @Test
    void replenishmentNeedsMinimumOrdersAndIgnoresCancelled()
    {
        final List<Order> orders = List.of(order(60, OrderStatus.DELIVERED, "A", 10), order(30, OrderStatus.DELIVERED, "A", 10),
                order(10, OrderStatus.CANCELLED, "A", 10));
        assertTrue(DefaultChatService.replenishment(orders, TODAY, 180, 3, 0.9).isEmpty());
    }

    @Test
    void replenishmentFlagsDueAndOverdue()
    {
        final List<Order> orders = new ArrayList<>();
        for (final int d : new int[] { 120, 90, 60, 30 })
        {
            orders.add(order(d, OrderStatus.DELIVERED, "DUE", 40));
        }
        for (final int d : new int[] { 100, 70, 40 })
        {
            orders.add(order(d, OrderStatus.DELIVERED, "LATE", 20));
        }
        orders.add(order(105, OrderStatus.DELIVERED, "FRESH", 1));
        orders.add(order(75, OrderStatus.DELIVERED, "FRESH", 1));
        orders.add(order(2, OrderStatus.DELIVERED, "FRESH", 1));
        final var items = DefaultChatService.replenishment(orders, TODAY, 180, 3, 0.9);
        assertEquals(List.of("LATE", "DUE"), items.stream().map(DefaultChatService.ReplenishmentItem::code).toList());
        assertTrue(items.get(0).overdue());
        assertFalse(items.get(1).overdue() && items.get(1).intervalDays() != 30);
        assertTrue(items.stream().allMatch(DefaultChatService.ReplenishmentItem::estimate));
    }

    @Test
    void patternChangeDetectsDropAndRespectsMinSample()
    {
        final List<Order> orders = new ArrayList<>();
        final int[] qty = { 100, 100, 95, 60, 60 };
        for (int i = 0; i < qty.length; i++)
        {
            orders.add(order(150 - i * 30, OrderStatus.DELIVERED, "P", qty[i]));
        }
        final var found = DefaultChatService.detectPatternChanges(orders, 30, 2, 2);
        assertEquals(1, found.size());
        assertEquals(39, found.get(0).changePercent());
        assertEquals("DOWN", found.get(0).direction());
        assertTrue(DefaultChatService.detectPatternChanges(orders.subList(0, 3), 30, 2, 2).isEmpty(), "not enough sample");
    }

    @Test
    void budgetPlannerNeverExceedsBudgetProperty()
    {
        final Random random = new Random(42);
        for (int run = 0; run < 2000; run++)
        {
            final List<BudgetCandidate> candidates = new ArrayList<>();
            final int n = random.nextInt(15);
            for (int i = 0; i < n; i++)
            {
                candidates.add(new BudgetCandidate("C" + i, random.nextInt(2_000_000) - 10, random.nextInt(500) - 5, random.nextInt(1000)));
            }
            final long budget = random.nextInt(100_000_000) - 1000;
            final DefaultChatService.BudgetPlan plan = DefaultChatService.planBudget(candidates, budget);
            assertTrue(plan.totalMinor() <= Math.max(0, budget), "total " + plan.totalMinor() + " budget " + budget);
            assertEquals(plan.lines().stream().mapToLong(DefaultChatService.BudgetLine::lineMinor).sum(), plan.totalMinor());
        }
    }

    @Test
    void budgetPlannerReducesQuantityBeforeExcluding()
    {
        final var plan = DefaultChatService.planBudget(List.of(new BudgetCandidate("A", 100, 10, 5), new BudgetCandidate("B", 500, 1, 1)), 1200);
        assertEquals(10, plan.lines().get(0).quantity());
        assertEquals("OVER_BUDGET", plan.excluded().get(0).reason());
    }

    @Test
    void quickOrderGrammar()
    {
        final var lines = DefaultChatService.parseQuickOrder("Need 20 of EL-4821, 10 x 2.5mm cable 100m, EL-9910 x 5; whatever replaces EL-1033.");
        assertEquals(4, lines.size());
        assertEquals(20, lines.get(0).quantity());
        assertEquals("DESCRIPTION", lines.get(1).kind());
        assertEquals(5, lines.get(2).quantity());
        assertEquals("REPLACEMENT_OF", lines.get(3).kind());
    }
}
