package com.conversational.commerce.core.engine.demo;

import com.conversational.commerce.core.engine.config.DefaultCcConfigurationService;
import com.conversational.commerce.core.engine.model.ChatEvent;
import com.conversational.commerce.core.engine.model.TurnRequest;
import com.conversational.commerce.core.engine.model.TurnResult;
import com.conversational.commerce.core.engine.orchestrator.AuditEvent;
import com.conversational.commerce.core.engine.orchestrator.AuditSink;
import com.conversational.commerce.core.engine.orchestrator.Composer;
import com.conversational.commerce.core.engine.orchestrator.DecisionValidator;
import com.conversational.commerce.core.engine.orchestrator.DefaultChatOrchestrationService;
import com.conversational.commerce.core.engine.orchestrator.Hydrator;
import com.conversational.commerce.core.engine.orchestrator.InterestEventService;
import com.conversational.commerce.core.engine.orchestrator.PromptRepository;
import com.conversational.commerce.core.engine.orchestrator.ProvenanceChecker;
import com.conversational.commerce.core.engine.orchestrator.RelevanceJudge;
import com.conversational.commerce.core.engine.orchestrator.ToolRegistry;
import com.conversational.commerce.core.engine.orchestrator.TranslateStep;
import com.conversational.commerce.core.engine.orchestrator.UsageCapService;
import com.conversational.commerce.core.engine.orchestrator.WelcomeService;
import com.conversational.commerce.core.engine.orchestrator.WorkflowRegistry;
import com.conversational.commerce.core.engine.orchestrator.WorkflowTemplate;
import com.conversational.commerce.core.engine.orchestrator.workflow.BasketReviewWorkflow;
import com.conversational.commerce.core.engine.orchestrator.workflow.BudgetPlanWorkflow;
import com.conversational.commerce.core.engine.orchestrator.workflow.CompareWorkflow;
import com.conversational.commerce.core.engine.orchestrator.workflow.FaqWorkflow;
import com.conversational.commerce.core.engine.orchestrator.workflow.FindProductWorkflow;
import com.conversational.commerce.core.engine.orchestrator.workflow.NextOrderWorkflow;
import com.conversational.commerce.core.engine.orchestrator.workflow.OrderLookupWorkflow;
import com.conversational.commerce.core.engine.orchestrator.workflow.ProductQaWorkflow;
import com.conversational.commerce.core.engine.orchestrator.workflow.QuickOrderWorkflow;
import com.conversational.commerce.core.engine.orchestrator.workflow.ReorderWorkflow;
import com.conversational.commerce.core.engine.orchestrator.workflow.StaticWorkflows;
import com.conversational.commerce.core.engine.provider.DefaultModelRouter;
import com.conversational.commerce.core.engine.rules.BusinessRanker;
import com.conversational.commerce.core.engine.sanitiser.DefaultSanitiser;
import com.conversational.commerce.core.engine.sanitiser.RegexMaskingRule;
import com.conversational.commerce.core.engine.tools.AlternativesTool;
import com.conversational.commerce.core.engine.tools.BudgetEligibleTool;
import com.conversational.commerce.core.engine.tools.ComparisonTool;
import com.conversational.commerce.core.engine.tools.ComplementaryTools;
import com.conversational.commerce.core.engine.tools.FaqTool;
import com.conversational.commerce.core.engine.tools.OrderTools;
import com.conversational.commerce.core.engine.tools.PatternChangeTool;
import com.conversational.commerce.core.engine.tools.PlanBudgetTool;
import com.conversational.commerce.core.engine.tools.ProductDetailsTool;
import com.conversational.commerce.core.engine.tools.PurchaseHistoryTool;
import com.conversational.commerce.core.engine.tools.QuickOrderValidateTool;
import com.conversational.commerce.core.engine.tools.ReplenishmentTool;
import com.conversational.commerce.core.engine.tools.ReviewListTool;
import com.conversational.commerce.core.engine.tools.SearchProductsTool;
import com.conversational.commerce.core.engine.tools.StockInfoTool;
import com.conversational.commerce.core.engine.orchestrator.ChatTool;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Wires the whole engine against the dummy dataset and the fake provider, the way the Spring XML wires it in production. */
public class EngineFixture
{
    public final LocalDate today = LocalDate.of(2026, 11, 13);
    public final DemoDataset data = new DemoDataset(today);
    public final FakeProviderAdapter fake = new FakeProviderAdapter();
    public final MapConfigSource backoffice = new MapConfigSource();
    public final MapConfigSource properties = new MapConfigSource();
    public final DefaultCcConfigurationService config = new DefaultCcConfigurationService();
    public final List<AuditEvent> audit = Collections.synchronizedList(new ArrayList<>());
    public final DefaultChatOrchestrationService orchestrator = new DefaultChatOrchestrationService();
    public final WelcomeService welcome = new WelcomeService();
    public final ToolRegistry toolRegistry = new ToolRegistry();
    public final DefaultSanitiser sanitiser = new DefaultSanitiser();
    public final DefaultModelRouter router = new DefaultModelRouter();

    public EngineFixture(final String pack)
    {
        properties.put("cc.activePack", pack).put("cc.syntheticDataOnly", "true").put("cc.config.cacheTtlSeconds", "0")
                .put("pack." + pack + ".searchFields", "coverage,surface,finish,size,rating,thickness,type")
                .put("replenishment.minOrders", "3").put("orchestration.maxSeconds", "9");
        for (final String task : List.of("decide", "translate", "judge", "compose", "explain"))
        {
            properties.put("route." + task + ".provider", "fake").put("route." + task + ".model", "fake-model");
        }
        config.setSources(List.of(backoffice, properties));
        final AuditSink sink = audit::add;
        sanitiser.setConfigurationService(config);
        sanitiser.setRules(List.of(RegexMaskingRule.EMAIL, RegexMaskingRule.ORDER_CODE, RegexMaskingRule.PHONE,
                RegexMaskingRule.LONG_DIGITS));
        router.setConfigurationService(config);
        router.setAdapters(List.of(fake));
        final PromptRepository prompts = new PromptRepository();
        final BusinessRanker ranker = new BusinessRanker();
        ranker.setBusinessAttributeGateway(data);
        final Hydrator hydrator = new Hydrator();
        hydrator.setCatalogGateway(data);
        hydrator.setPriceGateway(data);
        hydrator.setStockGateway(data);

        final List<ChatTool> tools = new ArrayList<>();
        final SearchProductsTool search = new SearchProductsTool();
        search.setCatalogGateway(data);
        tools.add(search);
        final ProductDetailsTool details = new ProductDetailsTool();
        details.setCatalogGateway(data);
        tools.add(details);
        final ComparisonTool comparison = new ComparisonTool();
        comparison.setCatalogGateway(data);
        tools.add(comparison);
        final AlternativesTool alternatives = new AlternativesTool();
        alternatives.setCatalogGateway(data);
        alternatives.setStockGateway(data);
        tools.add(alternatives);
        final StockInfoTool stock = new StockInfoTool();
        stock.setStockGateway(data);
        tools.add(stock);
        final BudgetEligibleTool eligible = new BudgetEligibleTool();
        eligible.setPriceGateway(data);
        tools.add(eligible);
        for (final OrderTools.Base t : List.<OrderTools.Base>of(new OrderTools.LatestOrderTool(), new OrderTools.OrderByIdTool(),
                new OrderTools.PendingOrdersTool(), new OrderTools.SearchOrdersTool(), new OrderTools.OrderTrackingTool()))
        {
            t.setOrderGateway(data);
            tools.add(t);
        }
        final PurchaseHistoryTool history = new PurchaseHistoryTool();
        history.setOrderGateway(data);
        tools.add(history);
        final ReplenishmentTool replenishment = new ReplenishmentTool();
        replenishment.setOrderGateway(data);
        replenishment.setConfigurationService(config);
        tools.add(replenishment);
        final PatternChangeTool pattern = new PatternChangeTool();
        pattern.setOrderGateway(data);
        pattern.setConfigurationService(config);
        tools.add(pattern);
        for (final ComplementaryTools.Base t : List.<ComplementaryTools.Base>of(new ComplementaryTools.ComplementaryProductsTool(),
                new ComplementaryTools.BundleSuggestionsTool()))
        {
            t.setCoPurchaseGateway(data);
            t.setBusinessRanker(ranker);
            t.setConfigurationService(config);
            tools.add(t);
        }
        final PlanBudgetTool plan = new PlanBudgetTool();
        plan.setPriceGateway(data);
        plan.setConfigurationService(config);
        tools.add(plan);
        final ReviewListTool review = new ReviewListTool();
        review.setCatalogGateway(data);
        review.setOrderGateway(data);
        review.setCoPurchaseGateway(data);
        review.setBusinessRanker(ranker);
        review.setConfigurationService(config);
        tools.add(review);
        final QuickOrderValidateTool quick = new QuickOrderValidateTool();
        quick.setCatalogGateway(data);
        tools.add(quick);
        final FaqTool faq = new FaqTool();
        faq.setFaqGateway(data);
        tools.add(faq);
        toolRegistry.setTools(tools);
        toolRegistry.setConfigurationService(config);
        toolRegistry.setAuditSink(sink);

        final RelevanceJudge judge = new RelevanceJudge();
        judge.setSanitiser(sanitiser);
        judge.setModelRouter(router);
        judge.setPromptRepository(prompts);
        judge.setProvenanceChecker(new ProvenanceChecker());
        judge.setAuditSink(sink);

        final List<WorkflowTemplate> workflows = new ArrayList<>(List.of(new NextOrderWorkflow(), new FindProductWorkflow(),
                new QuickOrderWorkflow(), new BudgetPlanWorkflow(), new OrderLookupWorkflow(), new CompareWorkflow(),
                new BasketReviewWorkflow(), new ReorderWorkflow(), new ProductQaWorkflow(), new FaqWorkflow(),
                new StaticWorkflows.OffTopicWorkflow(), new StaticWorkflows.UnsupportedWorkflow(),
                new StaticWorkflows.ClarifyWorkflow(), new StaticWorkflows.SwitchPackWorkflow()));
        for (final WorkflowTemplate w : workflows)
        {
            final com.conversational.commerce.core.engine.orchestrator.workflow.AbstractWorkflow a =
                    w instanceof com.conversational.commerce.core.engine.orchestrator.workflow.AbstractWorkflow aw ? aw : null;
            if (a != null)
            {
                a.setToolRegistry(toolRegistry);
                a.setHydrator(hydrator);
                a.setConfigurationService(config);
            }
            if (w instanceof FindProductWorkflow f)
            {
                f.setRelevanceJudge(judge);
            }
            if (w instanceof FaqWorkflow f)
            {
                f.setRelevanceJudge(judge);
            }
        }
        final WorkflowRegistry registry = new WorkflowRegistry();
        registry.setWorkflows(workflows);

        final Composer composer = new Composer();
        composer.setSanitiser(sanitiser);
        composer.setModelRouter(router);
        composer.setPromptRepository(prompts);
        composer.setAuditSink(sink);
        final TranslateStep translate = new TranslateStep();
        translate.setSanitiser(sanitiser);
        translate.setModelRouter(router);
        translate.setPromptRepository(prompts);
        final DecisionValidator validator = new DecisionValidator();
        validator.setConfigurationService(config);
        validator.setAuditSink(sink);
        final UsageCapService caps = new UsageCapService();
        caps.setUsageCounterStore(data);
        caps.setConfigurationService(config);
        final InterestEventService interest = new InterestEventService();
        interest.setInterestEventSink(data);
        interest.setConfigurationService(config);

        orchestrator.setConfigurationService(config);
        orchestrator.setSanitiser(sanitiser);
        orchestrator.setModelRouter(router);
        orchestrator.setPromptRepository(prompts);
        orchestrator.setDecisionValidator(validator);
        orchestrator.setWorkflowRegistry(registry);
        orchestrator.setComposer(composer);
        orchestrator.setTranslateStep(translate);
        orchestrator.setUsageCapService(caps);
        orchestrator.setInterestEventService(interest);
        orchestrator.setAuditSink(sink);
        orchestrator.setClock(Clock.fixed(today.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));

        welcome.setToolRegistry(toolRegistry);
        welcome.setHydrator(hydrator);
        welcome.setConfigurationService(config);
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
        return orchestrator.handle(request, events::add);
    }
}
