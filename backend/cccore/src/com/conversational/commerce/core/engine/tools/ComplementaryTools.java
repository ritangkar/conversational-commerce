package com.conversational.commerce.core.engine.tools;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import com.conversational.commerce.core.engine.gateway.CoPurchaseGateway;
import com.conversational.commerce.core.engine.model.TraceStep;
import com.conversational.commerce.core.engine.orchestrator.ChatTool;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import com.conversational.commerce.core.engine.rules.BusinessRanker;
import com.conversational.commerce.core.engine.rules.CoPurchaseRule;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/** Cross-sell (CI-01) and bundles (CI-02): deterministic co-purchase counts, then BusinessRanker. AI only explains. */
public final class ComplementaryTools
{
    private ComplementaryTools()
    {
    }

    public abstract static class Base implements ChatTool
    {
        @Resource(name = "ccCoPurchaseGateway")
        protected CoPurchaseGateway coPurchaseGateway;

        @Resource(name = "ccBusinessRanker")
        protected BusinessRanker businessRanker;

        @Resource(name = "ccConfigurationService")
        protected CcConfigurationService configurationService;

        protected final CoPurchaseRule rule = new CoPurchaseRule();

        public void setCoPurchaseGateway(final CoPurchaseGateway gateway)
        {
            this.coPurchaseGateway = gateway;
        }

        public void setBusinessRanker(final BusinessRanker ranker)
        {
            this.businessRanker = ranker;
        }

        public void setConfigurationService(final CcConfigurationService configurationService)
        {
            this.configurationService = configurationService;
        }

        protected ToolResult run(final TurnState state, final Map<String, Object> args, final int minSupport, final int limit,
                final String label)
        {
            final List<String> basket = ToolArgs.strings(args, "codes", 20).stream().filter(state.provenance::contains).toList();
            final List<CoPurchaseRule.Suggestion> suggestions = rule.complements(basket,
                    code -> coPurchaseGateway.pairCounts(state.pack, code), minSupport, limit);
            final List<String> ranked = businessRanker.rank(suggestions.stream().map(CoPurchaseRule.Suggestion::code).toList());
            final Map<String, Integer> support = new java.util.HashMap<>();
            suggestions.forEach(s -> support.put(s.code(), s.supportCount()));
            final List<CoPurchaseRule.Suggestion> ordered = ranked.stream()
                    .map(c -> new CoPurchaseRule.Suggestion(c, support.get(c))).toList();
            return ToolResult.of(ordered, ranked,
                    new TraceStep(label, "Orders placed by dealers (anonymous counts)", "all time", "bought together " + minSupport + "+ times"));
        }
    }

    public static class ComplementaryProductsTool extends Base
    {
        @Override
        public String name()
        {
            return "getComplementaryProducts";
        }

        @Override
        public String capabilityId()
        {
            return "CI-01";
        }

        @Override
        public ToolResult execute(final TurnState state, final Map<String, Object> args)
        {
            return run(state, args, configurationService.getInt("crossSell.minSupport", 2), 5, "Looked for items bought together");
        }
    }

    public static class BundleSuggestionsTool extends Base
    {
        @Override
        public String name()
        {
            return "getBundleSuggestions";
        }

        @Override
        public String capabilityId()
        {
            return "CI-02";
        }

        @Override
        public ToolResult execute(final TurnState state, final Map<String, Object> args)
        {
            return run(state, args, configurationService.getInt("bundle.minSupport", 3), 3, "Looked for common bundles");
        }
    }
}
