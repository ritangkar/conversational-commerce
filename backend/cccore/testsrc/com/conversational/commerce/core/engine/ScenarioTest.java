package com.conversational.commerce.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conversational.commerce.core.engine.demo.DemoDataset;
import com.conversational.commerce.core.engine.demo.EngineFixture;
import com.conversational.commerce.core.engine.model.Blocks.Block;
import com.conversational.commerce.core.engine.model.ChatEvent;
import com.conversational.commerce.core.engine.model.Intent;
import com.conversational.commerce.core.engine.model.TurnRequest;
import com.conversational.commerce.core.engine.model.TurnResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** SC-01..SC-10 end to end against the deterministic fake provider and dummy data. */
class ScenarioTest
{
    private static Optional<Block> block(final TurnResult r, final String type)
    {
        return r.blocks().stream().filter(b -> b.type().equals(type)).findFirst();
    }

    private static String text(final TurnResult r)
    {
        return block(r, "text").map(Block::text).orElse("");
    }

    @Test
    void sc01NextOrder()
    {
        final EngineFixture f = new EngineFixture("tyres");
        final List<ChatEvent> events = new ArrayList<>();
        final TurnResult r = f.ask(DemoDataset.TYRES_DEALER,
                "I need to prepare my next order. Can you help me identify what I should purchase?", events);
        assertEquals(Intent.NEXT_ORDER, r.intent());
        final Block recs = block(r, "recommendationList").orElseThrow();
        final List<String> codes = recs.recommendations().stream().map(x -> x.code()).toList();
        assertTrue(codes.contains("TY-1001") && codes.contains("TY-1002"), codes.toString());
        assertFalse(codes.contains("TY-1004"), "pending order nets TY-1004 away");
        assertTrue(recs.estimate());
        assertTrue(recs.recommendations().stream().filter(x -> x.code().equals("TY-1002")).findFirst().orElseThrow().reasonKey()
                .equals("OVERDUE"));
        assertTrue(events.stream().anyMatch(e -> e.event().equals("step_started")));
        assertEquals("done", events.get(events.size() - 1).event());
        assertTrue(text(r).contains("2 items look due"), text(r));
        assertTrue(block(r, "productCards").isPresent(), "complements");
    }

    @Test
    void sc02Discovery()
    {
        final EngineFixture f = new EngineFixture("paints");
        final TurnResult r = f.ask(DemoDataset.PAINTS_DEALER,
                "I need something to waterproof an exterior concrete wall, medium coverage, under 3000 a unit.");
        assertEquals(Intent.FIND_PRODUCT, r.intent());
        final List<String> codes = block(r, "productCards").orElseThrow().productCards().stream().map(c -> c.code()).toList();
        assertTrue(codes.contains("PN-2001") && codes.contains("PN-2003"), codes.toString());
        assertFalse(codes.contains("PN-2002"), "4200 per unit is over the limit");
        assertTrue(block(r, "productCards").orElseThrow().productCards().get(0).stockIsDemo());
    }

    @Test
    void sc03QuickOrder()
    {
        final EngineFixture f = new EngineFixture("electrical");
        final TurnResult r = f.ask(DemoDataset.ELECTRICAL_DEALER,
                "Need 20 of EL-4821, 10 x 2.5mm cable 100m, 5 of EL-9910, and whatever replaces EL-1033.");
        assertEquals(Intent.QUICK_ORDER, r.intent());
        final var lines = block(r, "purchaseList").orElseThrow().purchaseListLines();
        assertTrue(lines.stream().anyMatch(l -> l.productCode().equals("EL-4821") && l.quantity() == 20));
        assertTrue(lines.stream().anyMatch(l -> l.productCode().equals("EL-1034") && l.quantity() == 10));
        assertTrue(lines.stream().anyMatch(l -> l.productCode().equals("EL-9910") && l.quantity() == 5));
        assertTrue(r.blocks().stream().anyMatch(b -> "notice".equals(b.type())), "replacement line needs a quantity");
    }

    @Test
    void sc04BudgetPlannerNeverExceedsBudget()
    {
        final EngineFixture f = new EngineFixture("cement");
        final TurnResult r = f.ask(DemoDataset.CEMENT_DEALER, "I have 5 lakh to spend this month. Prepare my usual items.");
        assertEquals(Intent.BUDGET_PLAN, r.intent());
        long total = 0;
        for (final var l : block(r, "purchaseList").orElseThrow().purchaseListLines())
        {
            total += f.data.unitPriceMinor(DemoDataset.CEMENT_DEALER, l.productCode()).getAsLong() * l.quantity();
        }
        assertTrue(total <= 50_000_000L && total > 0, "total " + total);
        assertTrue(text(r).contains("INR 500,000.00"), text(r));
    }

    @Test
    void sc05PatternChangeWelcome()
    {
        final EngineFixture f = new EngineFixture("plywood");
        final Block w = f.welcome.build(DemoDataset.PLYWOOD_DEALER, f.today);
        final var item = w.welcomeItems().stream().filter(i -> i.kind().equals("patternChange")).findFirst().orElseThrow();
        assertEquals("PW-1801", item.params().get("code"));
        assertEquals("39", item.params().get("percent"));
    }

    @Test
    void sc06OrderIntelligenceAndIsolation()
    {
        final EngineFixture f = new EngineFixture("tyres");
        final TurnResult r = f.ask(DemoDataset.TYRES_DEALER,
                "Show my undelivered orders from last month over 20,000 and where the latest one is.");
        assertEquals(Intent.ORDER_LOOKUP, r.intent());
        final var orders = block(r, "orderSummary").orElseThrow().orderSummaries();
        assertEquals(2, orders.size(), orders.toString());
        assertTrue(orders.stream().noneMatch(o -> o.code().equals(DemoDataset.OTHER_CUSTOMER_ORDER)));
        final TurnResult other = f.ask(DemoDataset.TYRES_DEALER_2, "Show my undelivered orders from last month over 20,000");
        assertTrue(other.blocks().stream().noneMatch(b -> b.orderSummaries() != null
                && b.orderSummaries().stream().anyMatch(o -> o.code().equals("00010001"))));
    }

    @Test
    void sc07ComparisonWithExplanation()
    {
        final EngineFixture f = new EngineFixture("paints");
        final TurnRequest request = new TurnRequest(DemoDataset.PAINTS_DEALER,
                "Compare these three exterior primers and tell me which suits a coastal area.", "c", "en", List.of(),
                List.of("PN-3001", "PN-3002", "PN-3003"), List.of());
        final TurnResult r = f.ask(request, new ArrayList<>());
        assertEquals(Intent.COMPARE, r.intent());
        final var table = block(r, "comparison").orElseThrow().comparison();
        assertEquals(3, table.columns().size());
        assertTrue(table.notes().stream().anyMatch(n -> n.contains("coastal")), "coastal not recorded for every product");
        assertTrue(text(r).contains("Not available"));
        assertTrue(f.data.events().stream().anyMatch(e -> e.reference().equals("PN-3001")), "COMPARED events written");
    }

    @Test
    void sc08BasketReview()
    {
        final EngineFixture f = new EngineFixture("electrical");
        final TurnRequest request = new TurnRequest(DemoDataset.ELECTRICAL_DEALER, "Review my list before I go to checkout.", "c",
                "en", List.of(), List.of(), List.of(new TurnRequest.PurchaseLine("EL-9910", 5, null),
                        new TurnRequest.PurchaseLine("EL-5050", 10, null), new TurnRequest.PurchaseLine("NOPE-1", 1, null)));
        final TurnResult r = f.ask(request, new ArrayList<>());
        assertEquals(Intent.BASKET_REVIEW, r.intent());
        final var flags = block(r, "recommendationList").orElseThrow().recommendations();
        assertTrue(flags.stream().anyMatch(x -> x.code().equals("EL-4821") && x.reasonKey().equals("FORGOTTEN_USUAL")));
        assertTrue(flags.stream().anyMatch(x -> x.code().equals("EL-5050") && x.reasonKey().equals("DUPLICATE_OF_PENDING")));
        assertEquals(2, flags.stream().filter(x -> x.reasonKey().equals("COMPLEMENT")).count());
    }

    @Test
    void sc09HindiFlow()
    {
        final EngineFixture f = new EngineFixture("cement");
        final TurnResult r = f.ask(DemoDataset.CEMENT_DEALER, "मेरा अगला ऑर्डर तैयार करने में मदद करें");
        assertEquals("hi", r.language());
        assertEquals(Intent.NEXT_ORDER, r.intent());
        assertTrue(text(r).startsWith("(हिंदी)"), text(r));
        assertTrue(f.fake.tasks.contains("TRANSLATE"));
    }

    @Test
    void sc10PackSwitchTakesEffectAfterTtl()
    {
        final EngineFixture f = new EngineFixture("tyres");
        assertEquals("tyres", f.config.activePack());
        f.backoffice.put("cc.activePack", "paints");
        assertEquals("paints", f.config.activePack(), "TTL 0 in tests: effective at once");
        final TurnResult r = f.ask(DemoDataset.PAINTS_DEALER, "Show me a waterproof coating for exterior concrete");
        assertNotNull(block(r, "productCards").orElse(null));
    }
}
