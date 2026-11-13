package com.conversational.commerce.core.engine.orchestrator;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import com.conversational.commerce.core.engine.model.Blocks.Block;
import com.conversational.commerce.core.engine.model.Blocks.WelcomeItem;
import com.conversational.commerce.core.engine.model.Domain.Order;
import com.conversational.commerce.core.engine.model.TurnRequest;
import com.conversational.commerce.core.engine.model.ChatEventListener;
import com.conversational.commerce.core.engine.rules.PatternChangeRule;
import com.conversational.commerce.core.engine.rules.ReplenishmentRule;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Proactive welcome panel (RI-03, SC-05). Template text only: no model call and no cache (N3 is pending owner approval).
 * Deterministic tools only, so the panel is fast and cannot hallucinate.
 */
public class WelcomeService
{
    @Resource(name = "ccToolRegistry")
    private ToolRegistry toolRegistry;

    @Resource(name = "ccHydrator")
    private Hydrator hydrator;

    @Resource(name = "ccConfigurationService")
    private CcConfigurationService configurationService;

    public void setToolRegistry(final ToolRegistry r)
    {
        this.toolRegistry = r;
    }

    public void setHydrator(final Hydrator h)
    {
        this.hydrator = h;
    }

    public void setConfigurationService(final CcConfigurationService c)
    {
        this.configurationService = c;
    }

    @SuppressWarnings("unchecked")
    public Block build(final String customerId, final LocalDate today)
    {
        if (!configurationService.isEnabled("RI-03"))
        {
            return Block.welcome("welcome", List.of());
        }
        final TurnRequest request = new TurnRequest(customerId, "", null, "en", List.of(), List.of(), List.of());
        final TurnState state = new TurnState(request, configurationService.activePack(), today, UUID.randomUUID().toString(),
                ChatEventListener.NOOP, 8, 9);
        final List<WelcomeItem> items = new ArrayList<>();
        try
        {
            if (configurationService.isEnabled("RI-01"))
            {
                final List<ReplenishmentRule.Item> due = toolRegistry
                        .execute(state, "getReplenishmentRecommendations", Map.of("netPending", true)).as();
                for (final ReplenishmentRule.Item item : due.stream().limit(3).toList())
                {
                    final Map<String, String> params = new LinkedHashMap<>();
                    params.put("code", item.code());
                    params.put("name", hydrator.name(state, item.code()));
                    params.put("quantity", String.valueOf(item.suggestedQuantity()));
                    params.put("estimate", "true");
                    items.add(new WelcomeItem(item.overdue() ? "overdue" : "due", "welcome.due", params));
                }
            }
            if (configurationService.isEnabled("RI-02"))
            {
                final List<Order> pending = toolRegistry.execute(state, "getPendingOrders", Map.of()).as();
                if (!pending.isEmpty())
                {
                    items.add(new WelcomeItem("pending", "welcome.pending", Map.of("count", String.valueOf(pending.size()))));
                }
            }
            if (configurationService.isEnabled("RI-04"))
            {
                final List<PatternChangeRule.Observation> changes = toolRegistry
                        .execute(state, "detectPurchasePatternChanges", Map.of()).as();
                for (final PatternChangeRule.Observation o : changes.stream().limit(2).toList())
                {
                    final Map<String, String> params = new LinkedHashMap<>();
                    params.put("code", o.code());
                    params.put("name", hydrator.name(state, o.code()));
                    params.put("percent", String.valueOf(o.changePercent()));
                    params.put("direction", o.direction().name());
                    params.put("earlierAverage", String.format("%.0f", o.earlierAverage()));
                    params.put("recentAverage", String.format("%.0f", o.recentAverage()));
                    items.add(new WelcomeItem("patternChange", "welcome.patternChange", params));
                }
            }
        }
        catch (final RuntimeException e)
        {
            // A failed tool must not break the page: show what we have.
        }
        return Block.welcome("welcome", items);
    }
}
