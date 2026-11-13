package com.conversational.commerce.core.engine.tools;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import com.conversational.commerce.core.engine.gateway.CatalogGateway;
import com.conversational.commerce.core.engine.gateway.CoPurchaseGateway;
import com.conversational.commerce.core.engine.gateway.OrderGateway;
import com.conversational.commerce.core.engine.model.Domain.Order;
import com.conversational.commerce.core.engine.model.Domain.OrderLine;
import com.conversational.commerce.core.engine.model.Domain.OrderStatus;
import com.conversational.commerce.core.engine.model.TraceStep;
import com.conversational.commerce.core.engine.model.TurnRequest.PurchaseLine;
import com.conversational.commerce.core.engine.orchestrator.ChatTool;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import com.conversational.commerce.core.engine.rules.BasketReviewRule;
import com.conversational.commerce.core.engine.rules.BusinessRanker;
import com.conversational.commerce.core.engine.rules.CoPurchaseRule;
import com.conversational.commerce.core.engine.rules.ReplenishmentRule;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Basket review (PA-06) over the browser-held list. The list is untrusted: it is re-validated against the catalogue here (A11). */
public class ReviewListTool implements ChatTool
{
    @Resource
    private CatalogGateway catalogGateway;

    @Resource
    private OrderGateway orderGateway;

    @Resource
    private CoPurchaseGateway coPurchaseGateway;

    @Resource
    private BusinessRanker businessRanker;

    @Resource
    private CcConfigurationService configurationService;

    private final ReplenishmentRule replenishment = new ReplenishmentRule();
    private final CoPurchaseRule coPurchase = new CoPurchaseRule();
    private final BasketReviewRule review = new BasketReviewRule();

    public void setCatalogGateway(final CatalogGateway g)
    {
        this.catalogGateway = g;
    }

    public void setOrderGateway(final OrderGateway g)
    {
        this.orderGateway = g;
    }

    public void setCoPurchaseGateway(final CoPurchaseGateway g)
    {
        this.coPurchaseGateway = g;
    }

    public void setBusinessRanker(final BusinessRanker r)
    {
        this.businessRanker = r;
    }

    public void setConfigurationService(final CcConfigurationService c)
    {
        this.configurationService = c;
    }

    @Override
    public String name()
    {
        return "reviewPurchaseList";
    }

    @Override
    public String capabilityId()
    {
        return "PA-06";
    }

    @Override
    public ToolResult execute(final TurnState state, final Map<String, Object> args)
    {
        final List<OrderLine> valid = new ArrayList<>();
        for (final PurchaseLine line : state.request.purchaseList())
        {
            if (line.quantity() >= 1 && line.quantity() <= 100_000 && catalogGateway.byCode(state.pack, line.productCode()).isPresent())
            {
                valid.add(new OrderLine(line.productCode(), line.quantity()));
            }
        }
        final List<Order> orders = orderGateway.ordersOf(state.customerId);
        final List<Order> pending = orders.stream()
                .filter(o -> o.status() == OrderStatus.PENDING || o.status() == OrderStatus.PROCESSING).toList();
        final List<ReplenishmentRule.Item> due = replenishment.compute(orders, state.today,
                configurationService.getInt("replenishment.windowDays", 180),
                configurationService.getInt("replenishment.minOrders", 3), 0.9);
        final List<CoPurchaseRule.Suggestion> complements = coPurchase.complements(valid.stream().map(OrderLine::productCode).toList(),
                code -> coPurchaseGateway.pairCounts(state.pack, code), 2, 3);
        final List<String> rankedComplements = businessRanker.rank(complements.stream().map(CoPurchaseRule.Suggestion::code).toList());
        final List<CoPurchaseRule.Suggestion> orderedComplements = rankedComplements.stream()
                .map(c -> complements.stream().filter(s -> s.code().equals(c)).findFirst().orElseThrow()).toList();
        final List<BasketReviewRule.Flag> flags = review.review(valid, due, pending, orderedComplements);
        final List<String> codes = new ArrayList<>();
        valid.forEach(l -> codes.add(l.productCode()));
        flags.forEach(f -> codes.add(f.code()));
        return ToolResult.of(flags, codes.stream().distinct().toList(),
                new TraceStep("Reviewed your list against your history and open orders", "Your orders in Commerce", "last 180 days",
                        flags.size() + " observations"));
    }
}
