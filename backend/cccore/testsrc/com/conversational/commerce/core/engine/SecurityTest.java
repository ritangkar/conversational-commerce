package com.conversational.commerce.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conversational.commerce.core.engine.demo.DemoDataset;
import com.conversational.commerce.core.engine.demo.EngineFixture;
import com.conversational.commerce.core.service.ChatService;
import com.conversational.commerce.core.service.ChatService.ChatEvent;
import com.conversational.commerce.core.service.ChatService.PurchaseLine;
import com.conversational.commerce.core.service.ChatService.TurnRequest;
import com.conversational.commerce.core.service.ChatService.TurnResult;
import com.conversational.commerce.core.service.impl.DefaultChatService;
import com.conversational.commerce.core.service.impl.DefaultChatService.EgressBlockedException;
import com.conversational.commerce.core.service.impl.DefaultChatService.SanitiserDisabledException;
import com.conversational.commerce.core.service.impl.DefaultChatService.ToolRejectedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Canary, isolation, provenance, limits, audit and configuration checks. */
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
            for (final String forbidden : List.of("731455", "7,314.55", "7314.55", String.valueOf(DemoDataset.CANARY_MARGIN_MINOR), "913.57",
                    DemoDataset.CANARY_EMAIL, "98765", DemoDataset.TYRES_DEALER, "00010001", "TRK-889", "TRK-OTHER"))
            {
                assertFalse(payload.contains(forbidden), "leaked " + forbidden + " in " + payload);
            }
        }
    }

    @Test
    void outboundGuardBlocksSecretValuesThatSlipThrough()
    {
        final EngineFixture f = new EngineFixture("tyres");
        assertThrows(EgressBlockedException.class, () -> f.service.callModel("COMPOSE", "compose.v1",
                Map.of("facts", Map.of("note", "price is 731455"), "intent", "x"), List.of("731455")));
        assertTrue(f.fake.captured.isEmpty(), "blocked before any transport call");
    }

    @Test
    void neverFieldsAndUnlistedFieldsAreDropped()
    {
        final EngineFixture f = new EngineFixture("tyres");
        f.service.callModel("DECIDE", "decide.v1",
                Map.of("message", "hi", "priceMinor", 5, "customerEmail", "a@b.co", "randomField", 1), List.of());
        final String body = f.fake.captured.get(0);
        assertFalse(body.contains("a@b.co") || body.contains("priceMinor") || body.contains("randomField"), body);
    }

    @Test
    void sanitiserOffFailsClosedAndChatShowsContactNotice()
    {
        final EngineFixture f = new EngineFixture("tyres");
        f.backoffice.put("capability.PL-03.enabled", "false");
        assertThrows(SanitiserDisabledException.class, () -> f.service.callModel("DECIDE", "decide.v1", Map.of("message", "hi"), List.of()));
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
        assertEquals("PL-01 cannot be disabled by a toggle", f.service.validateToggleChange("PL-01", false));
        assertTrue(f.service.validateToggleChange("PL-05", false).contains("confirming"));
        f.backoffice.put("capability.PL-01.enabled", "false");
        assertTrue(f.service.isEnabled("PL-01"), "configuration cannot switch itself off");
        f.backoffice.put("capability.PL-05.enabled", "false");
        assertTrue(f.service.isEnabled("PL-05"), "needs the second confirming entry");
        f.backoffice.put("capability.PL-05.confirmDisable", "true");
        assertFalse(f.service.isEnabled("PL-05"));
        assertNull(f.service.validateToggleChange("PL-08", false));
        assertEquals(43, ChatService.CAPABILITIES.size());
    }

    @Test
    void togglesApplyWithinTtl()
    {
        final EngineFixture f = new EngineFixture("tyres");
        f.properties.put("cc.config.cacheTtlSeconds", "3600");
        assertTrue(f.service.isEnabled("PD-04"));
        f.backoffice.put("capability.PD-04.enabled", "false");
        assertTrue(f.service.isEnabled("PD-04"), "cached until TTL or save");
        f.service.clearConfigCache();
        assertFalse(f.service.isEnabled("PD-04"));
    }

    @Test
    void backofficeValuesWinOverProperties()
    {
        final EngineFixture f = new EngineFixture("tyres");
        assertEquals("tyres", f.service.getPublicConfig().activePack());
        f.backoffice.put("cc.activePack", "paints");
        assertEquals("paints", f.service.getPublicConfig().activePack());
    }

    @Test
    void disabledCapabilityBecomesUnsupported()
    {
        final EngineFixture f = new EngineFixture("tyres");
        f.backoffice.put("capability.RI-05.enabled", "false");
        assertEquals("UNSUPPORTED", f.ask(DemoDataset.TYRES_DEALER, "Prepare my next order").intent());
    }

    @Test
    void unknownAndDisabledToolsAreRejected()
    {
        final EngineFixture f = new EngineFixture("tyres");
        assertThrows(ToolRejectedException.class, () -> f.service.executeTool("c", "deleteEverything", Map.of()));
        f.backoffice.put("capability.PD-01.enabled", "false");
        assertThrows(ToolRejectedException.class, () -> f.service.executeTool("c", "searchProducts", Map.of()));
        assertTrue(f.audited("tool_rejected"));
    }

    @Test
    void badToolArgumentsAreRejected()
    {
        final EngineFixture f = new EngineFixture("tyres");
        assertThrows(ToolRejectedException.class, () -> f.service.executeTool("c", "searchProducts", Map.of("limit", 5000)));
        assertThrows(ToolRejectedException.class, () -> f.service.executeTool("c", "searchProducts", Map.of("keywords", "not-a-list")));
    }

    @Test
    void toolsTakeNoCustomerArgumentAndStayScopedToTheSessionCustomer()
    {
        final EngineFixture f = new EngineFixture("tyres");
        final List<?> orders = (List<?>) f.service.executeTool(DemoDataset.TYRES_DEALER_2, "searchOrders", Map.of("customerId", DemoDataset.TYRES_DEALER));
        assertEquals(1, orders.size(), "a model-supplied customer argument is ignored");
        assertTrue(((List<?>) f.service.executeTool(DemoDataset.TYRES_DEALER_2, "getOrderById", Map.of("orderCode", "00010001"))).isEmpty());
        assertEquals(1, ((List<?>) f.service.executeTool(DemoDataset.TYRES_DEALER_2, "getCustomerPurchaseHistory", Map.of())).size());
    }

    @Test
    void unknownIdsFromTheModelAreDroppedByProvenance()
    {
        final EngineFixture f = new EngineFixture("paints");
        f.fake.addUnknownCodeToJudge = true;
        final TurnResult r = f.ask(DemoDataset.PAINTS_DEALER, "Show me a waterproof coating for exterior concrete");
        assertTrue(r.blocks().stream().filter(b -> b.productCards() != null).flatMap(b -> b.productCards().stream())
                .noneMatch(c -> c.code().equals("XX-9999")));
        assertTrue(f.audited("id_rejected"));
    }

    @Test
    void invalidModelTextFallsBackToTemplate()
    {
        final EngineFixture f = new EngineFixture("tyres");
        f.fake.badComposeText = true;
        final TurnResult r = f.ask(DemoDataset.TYRES_DEALER, "Prepare my next order");
        final String text = r.blocks().stream().filter(b -> b.type().equals("text")).findFirst().orElseThrow().text();
        assertFalse(text.contains("4999") || text.contains("XX-9999"), text);
        assertTrue(f.audited("text_rejected"));
    }

    @Test
    void stepCapStopsARunawayTurn()
    {
        final EngineFixture f = new EngineFixture("tyres");
        f.backoffice.put("orchestration.maxSteps", "2");
        final TurnResult r = f.ask(DemoDataset.TYRES_DEALER, "Prepare my next order");
        assertTrue(r.blocks().stream().anyMatch(b -> "partialNotice".equals(b.noticeKey())));
        assertTrue(f.audited("limit_exceeded"));
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
        assertFalse(f.ask(DemoDataset.TYRES_DEALER_2, "Prepare my next order").blocks().stream().anyMatch(b -> "usageCap".equals(b.noticeKey())),
                "caps are per customer");
    }

    @Test
    void auditNeverContainsPromptsMessagesOrPersonalText()
    {
        final EngineFixture f = new EngineFixture("tyres");
        f.ask(DemoDataset.TYRES_DEALER, "Prepare my next order for " + DemoDataset.CANARY_EMAIL);
        assertFalse(f.audit.isEmpty());
        for (final Map<String, String> e : f.audit)
        {
            final String all = e.toString();
            assertFalse(all.contains("Prepare") || all.contains(DemoDataset.CANARY_EMAIL) || all.contains(DemoDataset.TYRES_DEALER)
                    || all.contains("731455"), all);
        }
        assertThrows(IllegalArgumentException.class, () -> f.service.audit("x", "message", "text"));
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
        assertTrue(f.data.events().stream().allMatch(e -> e.reference().matches("PN-\\d{4}") && e.type().equals("COMPARED")));
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
        assertEquals("OFF_TOPIC", f.ask(DemoDataset.TYRES_DEALER, "tell me a joke about football").intent());
        final TurnResult r = f.ask(DemoDataset.TYRES_DEALER, "please place the order now");
        assertEquals("UNSUPPORTED", r.intent());
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
        assertFalse(events.get(events.size() - 1).responseId() == null || events.get(events.size() - 1).trace().isEmpty());
    }

    @Test
    void userIdBindingRule()
    {
        assertTrue(ChatService.isUserIdAllowed("current", "dealer-1"));
        assertTrue(ChatService.isUserIdAllowed("dealer-1", "dealer-1"));
        assertFalse(ChatService.isUserIdAllowed("dealer-2", "dealer-1"));
        assertFalse(ChatService.isUserIdAllowed("current", "anonymous"));
        assertFalse(ChatService.isUserIdAllowed(null, "dealer-1"));
        assertFalse(ChatService.isUserIdAllowed("current", ""));
    }

    @Test
    void feedbackStoresMetadataOnlyAndValidatesInput()
    {
        final EngineFixture f = new EngineFixture("tyres");
        f.service.saveFeedback("r-1", "up", "NEXT_ORDER", List.of("searchOrders"), "r-1");
        assertEquals("r-1|UP|NEXT_ORDER|searchOrders|r-1", f.data.feedback.get(0));
        assertThrows(IllegalArgumentException.class, () -> f.service.saveFeedback("", "UP", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> f.service.saveFeedback("r", "MEH", null, null, null));
    }

    @Test
    void purchaseListIsRevalidatedAndPricedOnTheServer()
    {
        final EngineFixture f = new EngineFixture("electrical");
        final var result = f.service.validatePurchaseList(DemoDataset.ELECTRICAL_DEALER,
                List.of(new PurchaseLine("EL-4821", 20, null, null), new PurchaseLine("NOPE-1", 1, null, null), new PurchaseLine("EL-9910", 0, null, null)));
        assertEquals("INR 3,000.00", result.lines().get(0).lineFormatted());
        assertEquals(2, result.issues().size());
        assertEquals("Demo purchase list, not your cart", result.demoLabel());
        assertThrows(IllegalArgumentException.class, () -> f.service.validatePurchaseList("c", null));
    }

    @Test
    void retentionRunsAllThreePurges()
    {
        final EngineFixture f = new EngineFixture("tyres");
        assertEquals(0, f.service.purgeExpiredData());
        assertEquals(3, f.data.purgeCalls.get());
        assertTrue(f.audited("retention_run"));
    }

    @Test
    void publicConfigHasNoSecrets()
    {
        final EngineFixture f = new EngineFixture("tyres");
        f.backoffice.put("route.decide.model", "secret-model-id");
        final String json = f.service.getPublicConfig().toString();
        assertFalse(json.contains("secret-model-id") || json.contains("test-only-not-a-key") || json.contains("route."), json);
        assertEquals(43, f.service.getPublicConfig().enabledCapabilities().size());
        assertTrue(DefaultChatService.templateText("unsupported").contains("never place orders"));
    }
}
