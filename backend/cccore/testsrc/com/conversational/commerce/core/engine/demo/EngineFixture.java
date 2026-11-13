package com.conversational.commerce.core.engine.demo;

import com.conversational.commerce.core.service.ChatService.ChatEvent;
import com.conversational.commerce.core.service.ChatService.TurnRequest;
import com.conversational.commerce.core.service.ChatService.TurnResult;
import com.conversational.commerce.core.service.impl.DefaultChatService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Wires the real service against the dummy dataset (as ChatDao) and a fake OpenAI-shaped transport. No SAP platform needed. */
public class EngineFixture
{
    public final LocalDate today = LocalDate.of(2026, 11, 13);
    public final DemoDataset data = new DemoDataset(today);
    public final FakeModelTransport fake = new FakeModelTransport();
    public final DemoDataset.MapConfig backoffice = data.backoffice;
    public final DemoDataset.MapConfig properties = data.properties;
    public final List<Map<String, String>> audit = Collections.synchronizedList(new ArrayList<>());
    public final DefaultChatService service = new DefaultChatService();

    public EngineFixture(final String pack)
    {
        properties.put("cc.activePack", pack).put("cc.syntheticDataOnly", "true").put("cc.config.cacheTtlSeconds", "0")
                .put("pack." + pack + ".searchFields", "coverage,surface,finish,size,rating,thickness,type")
                .put("replenishment.minOrders", "3").put("orchestration.maxSeconds", "9")
                .put("cc.provider.openai.baseUrl", "https://provider.invalid").put("cc.provider.key.openai", "test-only-not-a-key");
        for (final String task : List.of("decide", "translate", "judge", "compose", "explain"))
        {
            properties.put("route." + task + ".provider", "openai").put("route." + task + ".model", "fake-model");
        }
        service.setChatDao(data);
        service.setTransport(fake);
        service.setAuditObserver(audit::add);
        service.setClock(Clock.fixed(today.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    }

    public boolean audited(final String event)
    {
        return audit.stream().anyMatch(a -> event.equals(a.get("event")));
    }

    public TurnResult ask(final String customer, final String message)
    {
        return ask(customer, message, new ArrayList<>());
    }

    public TurnResult ask(final String customer, final String message, final List<ChatEvent> events)
    {
        return ask(new TurnRequest(customer, message, "c1", "en", List.of(), List.of(), List.of()), events);
    }

    public TurnResult ask(final TurnRequest request, final List<ChatEvent> events)
    {
        return service.handle(request, events::add);
    }
}
