package com.conversational.commerce.core.engine.rules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/** Cross-sell and bundle suggestions from aggregated co-purchase counts (CI-01, CI-02). Deterministic. */
public class CoPurchaseRule
{
    public record Suggestion(String code, int supportCount)
    {
    }

    public List<Suggestion> complements(final Collection<String> basket, final Function<String, Map<String, Integer>> pairCounts,
            final int minSupport, final int limit)
    {
        final Set<String> inBasket = Set.copyOf(basket);
        final Map<String, Integer> totals = new TreeMap<>();
        for (final String code : basket)
        {
            for (final Map.Entry<String, Integer> e : pairCounts.apply(code).entrySet())
            {
                if (!inBasket.contains(e.getKey()))
                {
                    totals.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
        }
        final List<Suggestion> result = new ArrayList<>();
        totals.forEach((code, count) ->
        {
            if (count >= minSupport)
            {
                result.add(new Suggestion(code, count));
            }
        });
        result.sort(Comparator.comparingInt((Suggestion s) -> -s.supportCount()).thenComparing(Suggestion::code));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }
}
