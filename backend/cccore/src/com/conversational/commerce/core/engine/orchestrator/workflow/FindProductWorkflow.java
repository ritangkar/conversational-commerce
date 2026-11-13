package com.conversational.commerce.core.engine.orchestrator.workflow;

import com.conversational.commerce.core.engine.model.Blocks.Block;
import com.conversational.commerce.core.engine.model.Blocks.ProductCard;
import com.conversational.commerce.core.engine.model.Decision;
import com.conversational.commerce.core.engine.model.Domain.Product;
import com.conversational.commerce.core.engine.model.Intent;
import com.conversational.commerce.core.engine.orchestrator.MoneyFormat;
import com.conversational.commerce.core.engine.orchestrator.RelevanceJudge;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** FIND_PRODUCT (SC-02): search, optional budget filter in Java, relevance judge, hydrate. */
public class FindProductWorkflow extends AbstractWorkflow
{
    @Resource(name = "ccRelevanceJudge")
    private RelevanceJudge relevanceJudge;

    public void setRelevanceJudge(final RelevanceJudge j)
    {
        this.relevanceJudge = j;
    }

    @Override
    public Intent intent()
    {
        return Intent.FIND_PRODUCT;
    }

    @Override
    public Output run(final TurnState state, final Decision decision)
    {
        if (decision.keywords().isEmpty() && decision.categoryHint().isBlank())
        {
            return Output.of(List.of(Block.clarification("b-clarify", "What will you use it for?",
                    List.of("Surface or material", "Size or quantity", "Budget per unit"))), "clarify", Map.of(), Map.of());
        }
        final Map<String, Object> args = new LinkedHashMap<>();
        args.put("keywords", decision.keywords());
        args.put("filters", decision.filters());
        args.put("limit", 20);
        final ToolResult search = tool(state, "step.searchingCatalogue", "searchProducts", args);
        List<Product> candidates = search.as();
        if (decision.maxUnitAmount() != null && !candidates.isEmpty())
        {
            final ToolResult eligible = tool(state, "step.applyingPriceLimit", "getBudgetEligibleProducts",
                    Map.of("candidateCodes", candidates.stream().map(Product::code).toList(), "maxUnitMinor",
                            MoneyFormat.toMinor(decision.maxUnitAmount())));
            final List<String> ok = eligible.as();
            candidates = candidates.stream().filter(p -> ok.contains(p.code())).toList();
        }
        if (candidates.isEmpty())
        {
            return new Output(List.of(), "find_none", Map.of(), Map.of(), null, List.of(), null);
        }
        final List<Product> toJudge = candidates;
        final List<String> constraints = new ArrayList<>(decision.filters().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
        final List<RelevanceJudge.Pick> picks = state.step("step.judgingFit",
                () -> relevanceJudge.judge(state, String.join(" ", decision.keywords()), constraints, toJudge));
        final List<ProductCard> cards = new ArrayList<>();
        for (final RelevanceJudge.Pick pick : picks)
        {
            final Map<String, String> reasons = new LinkedHashMap<>();
            reasons.put("fit", pick.fit());
            pick.matchedAttributes().forEach(a -> reasons.put("matches:" + a, "yes"));
            cards.add(hydrator.card(state, pick.code(), reasons));
        }
        if (cards.isEmpty())
        {
            return new Output(List.of(), "find_none", Map.of(), Map.of(), null, List.of(), null);
        }
        return new Output(List.of(Block.productCards("b-products", cards)), "find_product", Map.of("count", cards.size()),
                Map.of("count", String.valueOf(cards.size())), null, List.of("ADD_TO_LIST"), cards.get(0).code());
    }
}
