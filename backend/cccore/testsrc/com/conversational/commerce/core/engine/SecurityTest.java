package com.conversational.commerce.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conversational.commerce.core.engine.demo.DemoDataset;
import com.conversational.commerce.core.engine.demo.EngineFixture;
import com.conversational.commerce.core.engine.model.Capabilities;
import com.conversational.commerce.core.engine.model.ChatEvent;
import com.conversational.commerce.core.engine.model.Intent;
import com.conversational.commerce.core.engine.model.TurnRequest;
import com.conversational.commerce.core.engine.model.TurnResult;
import com.conversational.commerce.core.engine.orchestrator.AuditEvent;
import com.conversational.commerce.core.engine.orchestrator.ToolRejectedException;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import com.conversational.commerce.core.engine.provider.ModelTask;
import com.conversational.commerce.core.engine.sanitiser.DefaultSanitiser.SanitiserDisabledException;
import com.conversational.commerce.core.engine.sanitiser.OutboundGuard.EgressBlockedException;
import com.conversational.commerce.core.engine.sanitiser.TokenMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Canary, isolation, provenance, limits and audit checks (stages 4-6). */
class SecurityTest
{
    private static final List<String> MESSAGES = List.of(
            "I need to prepare my next order. Can you help me identify what I should purchase?",
            "Show my undelivered orders from last month over 20,000 and where the latest one is.",
            "Reorder 00010001 please", "What is the return policy for tyres?", "Show me a waterproof coating for exterior concrete");

    @Test
    void canaryValuesNeverAppearInOutboundPayloads()
    {
        final EngineFixture f = new EngineFixture("tyres");
        for (final String m : MESSAGES)
        {
            f.ask(DemoDataset.TYRES_DEALER, m + " contact " + DemoDataset.CANARY_EMAIL + " or +91 98765 43210");
        }
        f.ask(DemoDataset.TYRES_DEALER, "Prepare my next order");
        assertFalse(f.fake.captured.isEmpty());
        for (final String payload : f.fake.captured)
        {
            for (final String forbidden : List.of("731455", "7,314.55", "7314.55", String.valueOf(DemoDataset.CANARY_MARGIN_MINOR),
                    "913.57", DemoDataset.CANARY_EMAIL, "98765", DemoDataset.TYRES_DEALER, "00010001", "TRK-889", "TRK-OTHER"))
            {
                assertFalse(payload.contains(forbidden), "leaked " + forbidden + " in " + payload);
            }
        }
    }

    @Test
    void outboundGuardBlocksSecretValuesThatSlipThrough()
    {
        final EngineFixture f = new EngineFixture("tyres");
        assertThrows(EgressBlockedException.class, () -> f.sanitiser.sanitise(ModelTask.COMPOSE,
                Map.of("facts", Map.of("note", "price is 731455"), "intent", "x"), new TokenMap(), List.of("731455")));
    }

    @Test
    void neverFieldsAndUnlistedFieldsAreDropped()
    {
        final EngineFixture f = new EngineFixture("tyres");
        final var payload = f.sanitiser.sanitise(ModelTask.DECIDE,
                Map.of("message", "hi", "priceMinor", 5, "customerEmail", "a@b.co", "randomField", 1), new TokenMap(), List.of());
        assertEquals(List.of("customerEmail", "priceMinor", "randomField").stream().sorted().toList(),
                payload.droppedFields().stream().sorted().toList());
        assertFalse(payload.json().contains("a@b.co"));
    }

    @Test
    void sanitiserOffFailsClosedAndChatShowsContactNotice()
    {
        final EngineFixture f = new EngineFixture("tyres");
        f.backoffice.put("capability.PL-03.enabled", "false");
        assertThrows(SanitiserDisabledException.class,
                () -> f.sanitiser.sanitise(ModelTask.DECIDE, Map.of("message", "hi"), new TokenMap(), List.of()));
        final TurnResult r = f.ask(DemoDataset.TYRES_DEALER, "Prepare my next order");
        assertTrue(f.fake.captured.isEmpty(), "no model call when the sanitiser is off");
        assertTrue(r.blocks().stream().anyMatch(b -> "unavailableNotice".equals(b.noticeKey())));
    }

    @Test
    void sanitiserConfigFlagOffAlsoFailsClosed()
    {
        final EngineFixture f = new EngineFixture("tyres");
        f.backoffice.put("cc.sanitiser.enabled", "false");
        f.ask(DemoDataset.TYRES_DEALER, "Prepare my next order");
        assertTrue(f.fake.captured.isEmpty());
    }

    @Test
    void protectedToggleRules()
    {
        final EngineFixture f = new EngineFixture("tyres");
        assertEquals("PL-01 cannot be disabled by a toggle", f.config.validateToggleChange("PL-01", false));
        assertTrue(f.config.validateToggleChange("PL-05", false).contains("confirming"));
        f.backoffice.put("capability.PL-01.enabled", "false");
        assertTrue(f.config.isEnabled("PL-01"), "configuration cannot switch itself off");
        f.backoffice.put("capability.PL-05.enabled", "false");
        assertTrue(f.config.isEnabled("PL-05"), "needs the second confirming entry");
        f.backoffice.put("capability.PL-05.confirmDisable", "true");
        assertFalse(f.config.isEnabled("PL-05"));
        assertEquals(null, f.config.validateToggleChange("PL-08", false));
        assertEquals(43, Capabilities.ALL.size());
    }

    @Test
    void togglesApplyWithinTtl()
    {
        final EngineFixture f = new EngineFixture("tyres");
        f.properties.put("cc.config.cacheTtlSeconds", "3600");
        assertTrue(f.config.isEnabled("PD-04"));
        f.backoffice.put("capability.PD-04.enabled", "false");
        assertTrue(f.config.isEnabled("PD-04"), "cached until TTL or save");
        f.config.clearCache();
        assertFalse(f.config.isEnabled("PD-04"));
    }

    @Test
    void disabledCapabilityBecomesUnsupported()
    {
        final EngineFixture f = new EngineFixture("tyres");
        f.backoffice.put("capability.RI-05.enabled", "false");
        final TurnResult r = f.ask(DemoDataset.TYRES_DEALER, "Prepare my next order");
        assertEquals(Intent.UNSUPPORTED, r.intent());
    }

    @Test
    void unknownAndDisabledToolsAreRejected()
    {
        final EngineFixture f = new EngineFixture("tyres");
        final TurnState state = new TurnState(new TurnRequest("c", "m", null, "en", List.of(), List.of(), List.of()), "tyres",
                f.today, "r", e -> { }, 8, 9);
        assertThrows(ToolRejectedException.class, () -> f.toolRegistry.execute(state, "deleteEverything", Map.of()));
        f.backoffice.put("capability.PD-01.enabled", "false");
        assertThrows(ToolRejectedException.class, () -> f.toolRegistry.execute(state, "searchProducts", Map.of()));
        assertTrue(f.audit.stream().anyMatch(a -> "tool_rejected".equals(a.fields().get("event"))));
    }

    @Test
    void badToolArgumentsAreRejected()
    {
        final EngineFixture f = new EngineFixture("tyres");
        final TurnState state = new TurnState(new TurnRequest("c", "m", null, "en", List.of(), List.of(), List.of()), "tyres",
                f.today, "r", e -> { }, 8, 9);
        assertThrows(ToolRejectedException.class,
                () -> f.toolRegistry.execute(state, "searchProducts", Map.of("limit", 5000)));
        assertThrows(ToolRejectedException.class,
                () -> f.toolRegistry.execute(state, "searchProducts", Map.of("keywords", "not-a-list")));
    }

    @Test
    void toolsTakeNoCustomerArgumentAndStayScopedToTheSessionCustomer()
    {
        final EngineFixture f = new EngineFixture("tyres");
        final TurnState dealer2 = new TurnState(new TurnRequest(DemoDataset.TYRES_DEALER_2, "m", null, "en", List.of(), List.of(), List.of()),
                "tyres", f.today, "r", e -> { }, 8, 9);
        // a model-supplied customer argument is simply ignored
        final List<?> orders = f.toolRegistry.execute(dealer2, "searchOrders", Map.of("customerId", DemoDataset.TYRES_DEALER)).as();
        assertEquals(1, orders.size());
        final List<?> byId = f.toolRegistry.execute(dealer2, "getOrderById", Map.of("orderCode", "00010001")).as();
        assertTrue(byId.isEmpty(), "dealer 1's order is invisible to dealer 2");
        final List<?> history = f.toolRegistry.execute(dealer2, "getCustomerPurchaseHistory", Map.of()).as();
        assertEquals(1, history.size());
    }

    @Test
    void unknownIdsFromTheModelAreDroppedByProvenance()
    {
        final EngineFixture f = new EngineFixture("paints");
        f.fake.addUnknownCodeToJudge = true;
        final TurnResult r = f.ask(DemoDataset.PAINTS_DEALER, "Show me a waterproof coating for exterior concrete");
        assertTrue(r.blocks().stream().filter(b -> b.productCards() != null).flatMap(b -> b.productCards().stream())
                .noneMatch(c -> c.code().equals("XX-9999")));
        assertTrue(f.audit.stream().anyMatch(a -> "id_rejected".equals(a.fields().get("event"))));
    }

    @Test
    void invalidModelTextFallsBackToTemplate()
    {
        final EngineFixture f = new EngineFixture("tyres");
        f.fake.badComposeText = true;
        final TurnResult r = f.ask(DemoDataset.TYRES_DEALER, "Prepare my next order");
        final String text = r.blocks().stream().filter(b -> b.type().equals("text")).findFirst().orElseThrow().text();
        assertFalse(text.contains("4999") || text.contains("XX-9999"), text);
        assertTrue(f.audit.stream().anyMatch(a -> "text_rejected".equals(a.fields().get("event"))));
    }

    @Test
    void stepCapStopsARunawayTurn()
    {
        final EngineFixture f = new EngineFixture("tyres");
        f.backoffice.put("orchestration.maxSteps", "2");
        final TurnResult r = f.ask(DemoDataset.TYRES_DEALER, "Prepare my next order");
        assertTrue(r.blocks().stream().anyMatch(b -> "partialNotice".equals(b.noticeKey())));
        assertTrue(f.audit.stream().anyMatch(a -> "limit_exceeded".equals(a.fields().get("event"))));
    }

    @Test
    void usageCapStopsModelCalls()
    {
        final EngineFixture f = new EngineFixture("tyres");
        f.backoffice.put("usage.cap.messages", "2");
        f.ask(DemoDataset.TYRES_DEALER, "Prepare my next order");
        f.ask(DemoDataset.TYRES_DEALER, "Prepare my next order");
        final int calls = f.fake.captured.size();
        final TurnResult r = f.ask(DemoDataset.TYRES_DEALER, "Prepare my next order");
        assertEquals(calls, f.fake.captured.size(), "no model call over the cap");
        assertTrue(r.blocks().stream().anyMatch(b -> "usageCap".equals(b.noticeKey())));
        final TurnResult other = f.ask(DemoDataset.TYRES_DEALER_2, "Prepare my next order");
        assertFalse(other.blocks().stream().anyMatch(b -> "usageCap".equals(b.noticeKey())), "caps are per customer");
    }

    @Test
    void auditNeverContainsPromptsMessagesOrPersonalText()
    {
        final EngineFixture f = new EngineFixture("tyres");
        final String message = "Prepare my next order for " + DemoDataset.CANARY_EMAIL;
        f.ask(DemoDataset.TYRES_DEALER, message);
        assertFalse(f.audit.isEmpty());
        for (final AuditEvent e : f.audit)
        {
            final String all = e.fields().toString();
            assertFalse(all.contains("Prepare") || all.contains(DemoDataset.CANARY_EMAIL) || all.contains(DemoDataset.TYRES_DEALER)
                    || all.contains("731455"), all);
            assertTrue(AuditEvent.ALLOWED_KEYS.containsAll(e.fields().keySet()));
        }
        assertThrows(IllegalArgumentException.class, () -> AuditEvent.of("x").with("message", "text"));
    }

    @Test
    void interestEventsRespectSwitchAndStoreNoText()
    {
        final EngineFixture f = new EngineFixture("paints");
        final TurnRequest request = new TurnRequest(DemoDataset.PAINTS_DEALER, "Compare these primers", "c", "en", List.of(),
                List.of("PN-3001", "PN-3002"), List.of());
        f.backoffice.put("privacy.interestEvents.enabled", "false");
        f.ask(request, new ArrayList<>());
        assertTrue(f.data.events().isEmpty());
        f.backoffice.put("privacy.interestEvents.enabled", "true");
        f.ask(request, new ArrayList<>());
        assertEquals(2, f.data.events().size());
        assertTrue(f.data.events().stream().allMatch(e -> e.reference().matches("PN-\\d{4}")));
    }

    @Test
    void translationLosingIdentifiersFallsBackToEnglishWithNotice()
    {
        final EngineFixture f = new EngineFixture("cement");
        f.fake.dropTranslationTokens = true;
        final TurnResult r = f.ask(DemoDataset.CEMENT_DEALER, "मेरा अगला ऑर्डर तैयार करने में मदद करें");
        assertTrue(r.blocks().stream().anyMatch(b -> "translationFailed".equals(b.noticeKey())));
        assertFalse(r.blocks().stream().filter(b -> b.type().equals("text")).findFirst().orElseThrow().text().contains("(हिंदी)"));
    }

    @Test
    void offTopicAndUnsupportedAreRedirectedWithoutTools()
    {
        final EngineFixture f = new EngineFixture("tyres");
        assertEquals(Intent.OFF_TOPIC, f.ask(DemoDataset.TYRES_DEALER, "tell me a joke about football").intent());
        final TurnResult r = f.ask(DemoDataset.TYRES_DEALER, "please place the order now");
        assertEquals(Intent.UNSUPPORTED, r.intent());
        assertTrue(r.blocks().stream().filter(b -> b.type().equals("text")).findFirst().orElseThrow().text().contains("never place orders"));
    }

    @Test
    void streamEventsAreOrdered()
    {
        final EngineFixture f = new EngineFixture("tyres");
        final List<ChatEvent> events = new ArrayList<>();
        f.ask(DemoDataset.TYRES_DEALER, "Prepare my next order", events);
        final List<String> names = events.stream().map(ChatEvent::event).toList();
        assertTrue(names.indexOf("step_started") < names.indexOf("block"));
        assertTrue(names.indexOf("block") < names.indexOf("text_delta"));
        assertEquals("done", names.get(names.size() - 1));
        assertEquals(1, names.stream().filter("done"::equals).count());
    }
}
