package com.conversational.commerce.core.engine.rules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Budget purchase planner (PA-04). Invariant: total never exceeds the budget. Pure integer arithmetic in minor units. */
public class BudgetPlanner
{
    public record Candidate(String code, long unitMinor, int desiredQuantity, int score)
    {
    }

    public record Line(String code, int quantity, long lineMinor, boolean reduced)
    {
    }

    public record Excluded(String code, String reason)
    {
    }

    public record Plan(List<Line> lines, long totalMinor, long budgetMinor, List<Excluded> excluded)
    {
    }

    public Plan plan(final List<Candidate> candidates, final long budgetMinor)
    {
        final List<Candidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingInt(Candidate::score).reversed().thenComparing(Candidate::code));
        final List<Line> lines = new ArrayList<>();
        final List<Excluded> excluded = new ArrayList<>();
        long remaining = Math.max(0, budgetMinor);
        long total = 0;
        for (final Candidate c : ordered)
        {
            if (c.unitMinor() <= 0 || c.desiredQuantity() <= 0)
            {
                excluded.add(new Excluded(c.code(), "NO_VALID_PRICE_OR_QUANTITY"));
                continue;
            }
            final long affordable = remaining / c.unitMinor();
            final int qty = (int) Math.min(c.desiredQuantity(), affordable);
            if (qty < 1)
            {
                excluded.add(new Excluded(c.code(), "OVER_BUDGET"));
                continue;
            }
            final long lineMinor = qty * c.unitMinor();
            lines.add(new Line(c.code(), qty, lineMinor, qty < c.desiredQuantity()));
            remaining -= lineMinor;
            total += lineMinor;
        }
        return new Plan(lines, total, budgetMinor, excluded);
    }
}
