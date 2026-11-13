package com.conversational.commerce.core.engine.rules;

import com.conversational.commerce.core.engine.model.Domain.Order;
import com.conversational.commerce.core.engine.model.Domain.OrderLine;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Basket optimisation flags (PA-06) over the validated, browser-held purchase list. */
public class BasketReviewRule
{
    public enum FlagType
    {
        FORGOTTEN_USUAL, DUPLICATE_OF_PENDING, COMPLEMENT
    }

    public record Flag(FlagType type, String code, String detail)
    {
    }

    public List<Flag> review(final List<OrderLine> list, final List<ReplenishmentRule.Item> dueItems,
            final List<Order> pendingOrders, final List<CoPurchaseRule.Suggestion> complements)
    {
        final Set<String> inList = new HashSet<>();
        list.forEach(l -> inList.add(l.productCode()));
        final Set<String> pending = new HashSet<>();
        pendingOrders.forEach(o -> o.lines().forEach(l -> pending.add(l.productCode())));
        final List<Flag> flags = new ArrayList<>();
        for (final ReplenishmentRule.Item item : dueItems)
        {
            if (!inList.contains(item.code()) && !pending.contains(item.code()))
            {
                flags.add(new Flag(FlagType.FORGOTTEN_USUAL, item.code(), "every " + item.intervalDays() + " days (estimate)"));
            }
        }
        for (final OrderLine line : list)
        {
            if (pending.contains(line.productCode()))
            {
                flags.add(new Flag(FlagType.DUPLICATE_OF_PENDING, line.productCode(), "already in a pending order"));
            }
        }
        for (final CoPurchaseRule.Suggestion s : complements)
        {
            if (!inList.contains(s.code()))
            {
                flags.add(new Flag(FlagType.COMPLEMENT, s.code(), "bought together by " + s.supportCount() + " orders"));
            }
        }
        return flags;
    }
}
