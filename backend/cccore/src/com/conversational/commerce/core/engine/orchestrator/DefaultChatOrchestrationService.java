package com.conversational.commerce.core.engine.orchestrator;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import com.conversational.commerce.core.engine.model.Blocks.Block;
import com.conversational.commerce.core.engine.model.ChatEvent;
import com.conversational.commerce.core.engine.model.ChatEventListener;
import com.conversational.commerce.core.engine.model.Decision;
import com.conversational.commerce.core.engine.model.Intent;
import com.conversational.commerce.core.engine.model.TurnRequest;
import com.conversational.commerce.core.engine.model.TurnResult;
import com.conversational.commerce.core.engine.provider.ModelRequest;
import com.conversational.commerce.core.engine.provider.ModelResult;
import com.conversational.commerce.core.engine.provider.ModelRouter;
import com.conversational.commerce.core.engine.provider.ModelTask;
import com.conversational.commerce.core.engine.sanitiser.DefaultSanitiser.SanitiserDisabledException;
import com.conversational.commerce.core.engine.sanitiser.OutboundGuard.EgressBlockedException;
import com.conversational.commerce.core.engine.sanitiser.SanitisedPayload;
import com.conversational.commerce.core.engine.sanitiser.Sanitiser;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DefaultChatOrchestrationService implements ChatOrchestrationService
{
    private static final Logger LOG = LogManager.getLogger(DefaultChatOrchestrationService.class);
    private static final Pattern CODE = Pattern.compile("[A-Z]{2,4}-\\d{3,6}");
    private static final int CHUNK_WORDS = 3;
    private static final java.util.Set<String> UNRESOLVED = java.util.Set.of("find_none", "faq_none", "qa_none", "order_none",
            "reorder_none", "next_order_none", "unsupported");

    @Resource(name = "ccConfigurationService")
    private CcConfigurationService configurationService;
    @Resource(name = "ccSanitiser")
    private Sanitiser sanitiser;
    @Resource(name = "ccModelRouter")
    private ModelRouter modelRouter;
    @Resource(name = "ccPromptRepository")
    private PromptRepository promptRepository;
    @Resource(name = "ccDecisionValidator")
    private DecisionValidator decisionValidator;
    @Resource(name = "ccWorkflowRegistry")
    private WorkflowRegistry workflowRegistry;
    @Resource(name = "ccComposer")
    private Composer composer;
    @Resource(name = "ccTranslateStep")
    private TranslateStep translateStep;
    @Resource(name = "ccUsageCapService")
    private UsageCapService usageCapService;
    @Resource(name = "ccInterestEventService")
    private InterestEventService interestEventService;
    @Resource(name = "ccAuditSink")
    private AuditSink auditSink;

    private final LanguageDetector languageDetector = new LanguageDetector();
    private Clock clock = Clock.systemDefaultZone();

    public void setConfigurationService(final CcConfigurationService v)
    {
        this.configurationService = v;
    }

    public void setSanitiser(final Sanitiser v)
    {
        this.sanitiser = v;
    }

    public void setModelRouter(final ModelRouter v)
    {
        this.modelRouter = v;
    }

    public void setPromptRepository(final PromptRepository v)
    {
        this.promptRepository = v;
    }

    public void setDecisionValidator(final DecisionValidator v)
    {
        this.decisionValidator = v;
    }

    public void setWorkflowRegistry(final WorkflowRegistry v)
    {
        this.workflowRegistry = v;
    }

    public void setComposer(final Composer v)
    {
        this.composer = v;
    }

    public void setTranslateStep(final TranslateStep v)
    {
        this.translateStep = v;
    }

    public void setUsageCapService(final UsageCapService v)
    {
        this.usageCapService = v;
    }

    public void setInterestEventService(final InterestEventService v)
    {
        this.interestEventService = v;
    }

    public void setAuditSink(final AuditSink v)
    {
        this.auditSink = v;
    }

    public void setClock(final Clock clock)
    {
        this.clock = clock;
    }

    @Override
    public TurnResult handle(final TurnRequest request, final ChatEventListener listener)
    {
        final LocalDate today = LocalDate.now(clock);
        final TurnState state = new TurnState(request, configurationService.activePack(), today, UUID.randomUUID().toString(),
                listener, configurationService.getInt("orchestration.maxSteps", 8),
                configurationService.getInt("orchestration.maxSeconds", 9));
        final long start = System.currentTimeMillis();
        Intent intent = Intent.CLARIFY;
        final List<Block> blocks = new ArrayList<>();
        final List<String> actions = new ArrayList<>();
        try
        {
            if (!configurationService.isEnabled("PL-04"))
            {
                return finish(state, intent, notice(blocks, "unavailable", "unavailableNotice"), actions, start);
            }
            if (!usageCapService.checkAndIncrement(state.customerId, today))
            {
                return finish(state, intent, notice(blocks, "cap_reached", "usageCap"), actions, start);
            }
            state.language = languageDetector.detect(request.message(), request.uiLocale());
            if (!"en".equals(state.language) && configurationService.isEnabled("CF-05"))
            {
                final Optional<String> english = state.step("step.translating",
                        () -> translateStep.translate(state, request.message(), state.language, "en"));
                if (english.isEmpty())
                {
                    state.language = "en";
                    return finish(state, intent, notice(blocks, "clarify", "translationFailed"), actions, start);
                }
                state.workingMessage = english.get();
            }
            final Decision decision = state.step("step.understanding", () -> decide(state));
            intent = decision.intent();
            if (intent.capabilityId() != null && !configurationService.isEnabled(intent.capabilityId()))
            {
                intent = Intent.UNSUPPORTED;
            }
            final WorkflowTemplate workflow = workflowRegistry.find(intent).orElse(workflowRegistry.find(Intent.CLARIFY).orElseThrow());
            final WorkflowTemplate.Output output = workflow.run(state, decision);
            blocks.addAll(output.blocks());
            actions.addAll(output.suggestedActions());
            interestEventService.maybeRecord(state, decision);
            String text = output.templateKey() == null ? null : textFor(state, output);
            if (text != null && UNRESOLVED.contains(output.templateKey()))
            {
                text = text + " " + configurationService.getString("contact.text", TemplateTexts.get("contact")); // D31
            }
            if (output.templateKey() == null)
            {
                // approved FAQ text is already in the blocks
                return finish(state, intent, blocks, actions, start);
            }
            blocks.add(Block.text("b-text", text));
            return finish(state, intent, translateOut(state, blocks), actions, start);
        }
        catch (final LimitExceededException e)
        {
            audit(state, "limit_exceeded", e.getMessage());
            notice(blocks, "partial", "partialNotice");
            return finish(state, intent, blocks, actions, start);
        }
        catch (final ToolRejectedException e)
        {
            audit(state, "tool_rejected", e.getMessage());
            blocks.clear();
            notice(blocks, "clarify", "toolRejected");
            return finish(state, intent, blocks, actions, start);
        }
        catch (final SanitiserDisabledException | EgressBlockedException | ModelRouter.ModelUnavailableException e)
        {
            audit(state, "model_blocked", e.getClass().getSimpleName());
            blocks.clear();
            notice(blocks, "unavailable", "unavailableNotice");
            blocks.add(Block.text("b-contact", configurationService.getString("contact.text", TemplateTexts.get("contact"))));
            return finish(state, intent, blocks, actions, start);
        }
    }

    private String textFor(final TurnState state, final WorkflowTemplate.Output output)
    {
        if (output.facts().isEmpty() && output.slots().isEmpty())
        {
            final String filled = SlotFiller.fill(TemplateTexts.get(output.templateKey()), output.slots());
            return filled == null ? TemplateTexts.get(output.templateKey()) : filled;
        }
        return state.step("step.writingReply", () -> composer.compose(state, output.templateKey(), output.facts(), output.slots(),
                output.table()));
    }

    private List<Block> notice(final List<Block> blocks, final String templateKey, final String noticeKey)
    {
        blocks.add(Block.notice("b-notice", noticeKey, TemplateTexts.get(templateKey)));
        return blocks;
    }

    private Decision decide(final TurnState state)
    {
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("message", state.workingMessage);
        final List<String> intents = new ArrayList<>();
        for (final Intent i : Intent.values())
        {
            if (i.capabilityId() == null || configurationService.isEnabled(i.capabilityId()))
            {
                intents.add(i.name());
            }
        }
        fields.put("capabilities", intents);
        fields.put("terminology", configurationService.getString("pack." + state.pack + ".terminology", ""));
        fields.put("turns", boundedTurns(state.request));
        fields.put("shortlistCodes", state.request.shortlistCodes().stream().filter(c -> CODE.matcher(c).matches()).limit(10).toList());
        fields.put("language", "en");
        final SanitisedPayload payload = sanitiser.sanitise(ModelTask.DECIDE, fields, state.tokens, state.secrets);
        final ModelResult result = modelRouter.call(new ModelRequest(ModelTask.DECIDE, "decide.v1",
                promptRepository.prompt("decide.v1"), payload, promptRepository.schema("decision.v1"), 500, 0.0));
        state.addUsage(result.tokensIn(), result.tokensOut());
        auditSink.log(AuditEvent.of("model_call").with("responseId", state.responseId).with("stepName", "DECIDE")
                .with("provider", result.provider()).with("model", result.model()).with("promptVersion", "decide.v1")
                .with("tokensIn", result.tokensIn()).with("tokensOut", result.tokensOut()).with("latencyMs", result.latencyMs()));
        return decisionValidator.validate(state, result.json());
    }

    /** Browser-held context is untrusted (A11): bounded here and re-masked by the sanitiser on every call. */
    private List<Map<String, String>> boundedTurns(final TurnRequest request)
    {
        final int maxTurns = configurationService.getInt("context.maxTurns", 6);
        int budget = configurationService.getInt("context.maxChars", 2000);
        final List<Map<String, String>> out = new ArrayList<>();
        final List<TurnRequest.ContextTurn> turns = request.turns();
        for (int i = turns.size() - 1; i >= 0 && out.size() < maxTurns; i--)
        {
            final TurnRequest.ContextTurn t = turns.get(i);
            final String role = "assistant".equals(t.role()) ? "assistant" : "user";
            final String text = t.text() == null ? "" : t.text();
            if (text.length() > budget)
            {
                break;
            }
            budget -= text.length();
            out.add(0, Map.of("role", role, "text", text));
        }
        return out;
    }

    private List<Block> translateOut(final TurnState state, final List<Block> blocks)
    {
        if ("en".equals(state.language) || !configurationService.isEnabled("CF-05"))
        {
            return blocks;
        }
        final List<Block> result = new ArrayList<>();
        for (final Block block : blocks)
        {
            if ("text".equals(block.type()) && block.text() != null)
            {
                final Optional<String> translated = state.step("step.translating",
                        () -> translateStep.translate(state, block.text(), "en", state.language));
                if (translated.isPresent())
                {
                    result.add(block.withText(translated.get()));
                }
                else
                {
                    result.add(block);
                    result.add(Block.notice("b-translation", "translationFailed", TemplateTexts.get("translation_failed")));
                }
            }
            else
            {
                result.add(block);
            }
        }
        return result;
    }

    private TurnResult finish(final TurnState state, final Intent intent, final List<Block> blocks, final List<String> actions,
            final long start)
    {
        for (final Block block : blocks)
        {
            if ("text".equals(block.type()) && block.text() != null)
            {
                state.listener.onEvent(ChatEvent.block(block.withText("")));
                streamText(state, block);
            }
            else
            {
                state.listener.onEvent(ChatEvent.block(block));
            }
        }
        state.listener.onEvent(ChatEvent.done(state.responseId, List.copyOf(state.trace)));
        usageCapService.recordTokens(state.customerId, state.today, state.tokensIn.get() + state.tokensOut.get());
        auditSink.log(AuditEvent.of("turn_completed").with("responseId", state.responseId).with("intent", intent.name())
                .with("workflow", intent.name()).with("tokensIn", state.tokensIn.get()).with("tokensOut", state.tokensOut.get())
                .with("latencyMs", System.currentTimeMillis() - start).with("outcome", "ok"));
        LOG.debug("Turn {} finished with intent {}", state.responseId, intent);
        return new TurnResult(state.responseId, state.language, intent, List.copyOf(blocks), List.copyOf(actions),
                List.copyOf(state.trace), state.tokensIn.get(), state.tokensOut.get());
    }

    private void streamText(final TurnState state, final Block block)
    {
        final String[] words = block.text().split("(?<=\\s)");
        final StringBuilder chunk = new StringBuilder();
        int n = 0;
        for (final String w : words)
        {
            chunk.append(w);
            if (++n % CHUNK_WORDS == 0)
            {
                state.listener.onEvent(ChatEvent.textDelta(block.blockId(), chunk.toString()));
                chunk.setLength(0);
            }
        }
        if (chunk.length() > 0)
        {
            state.listener.onEvent(ChatEvent.textDelta(block.blockId(), chunk.toString()));
        }
    }

    private void audit(final TurnState state, final String event, final String code)
    {
        auditSink.log(AuditEvent.of(event).with("responseId", state.responseId).with("errorCode", code));
    }
}
