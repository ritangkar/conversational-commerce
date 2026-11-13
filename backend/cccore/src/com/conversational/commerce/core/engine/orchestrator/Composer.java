package com.conversational.commerce.core.engine.orchestrator;

import com.conversational.commerce.core.engine.model.Blocks.Comparison;
import com.conversational.commerce.core.engine.provider.ModelRequest;
import com.conversational.commerce.core.engine.provider.ModelResult;
import com.conversational.commerce.core.engine.provider.ModelRouter;
import com.conversational.commerce.core.engine.provider.ModelTask;
import com.conversational.commerce.core.engine.sanitiser.SanitisedPayload;
import com.conversational.commerce.core.engine.sanitiser.Sanitiser;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * COMPOSE/EXPLAIN task plus validate-then-reveal (A13): slots only, no loose digits, no unknown product codes, text agrees
 * with the table. Any failure swaps in the fixed template text, so a bad model answer never reaches the dealer.
 */
public class Composer
{
    private static final Pattern CODE = Pattern.compile("\\b[A-Z]{2,4}-\\d{3,6}\\b");

    @Resource
    private Sanitiser sanitiser;

    @Resource
    private ModelRouter modelRouter;

    @Resource
    private PromptRepository promptRepository;

    @Resource
    private AuditSink auditSink;

    private final TextTableChecker tableChecker = new TextTableChecker();

    public void setSanitiser(final Sanitiser s)
    {
        this.sanitiser = s;
    }

    public void setModelRouter(final ModelRouter r)
    {
        this.modelRouter = r;
    }

    public void setPromptRepository(final PromptRepository p)
    {
        this.promptRepository = p;
    }

    public void setAuditSink(final AuditSink a)
    {
        this.auditSink = a;
    }

    /** @param facts text-only facts for the model; amounts are not in here, only slot names are sent. */
    public String compose(final TurnState state, final String templateKey, final Map<String, Object> facts,
            final Map<String, String> slots, final Comparison table)
    {
        final String template = SlotFiller.fill(TemplateTexts.get(templateKey), slots);
        final String fallback = template == null ? TemplateTexts.get(templateKey) : template;
        try
        {
            final ModelTask task = table == null ? ModelTask.COMPOSE : ModelTask.EXPLAIN;
            final String promptId = table == null ? "compose.v1" : "explain.v1";
            final Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("intent", templateKey);
            final Map<String, Object> allFacts = new LinkedHashMap<>(facts);
            if (table != null)
            {
                fields.put("comparison", tableAsMap(table));
            }
            fields.put("facts", allFacts);
            fields.put("slots", new ArrayList<>(slots.keySet()));
            fields.put("language", "en");
            final SanitisedPayload payload = sanitiser.sanitise(task, fields, state.tokens, state.secrets);
            final ModelResult result = modelRouter.call(new ModelRequest(task, promptId, promptRepository.prompt(promptId),
                    payload, promptRepository.schema("compose.v1"), 400, 0.2));
            state.addUsage(result.tokensIn(), result.tokensOut());
            final String modelText = result.json().path("text").asText("");
            return validate(state, modelText, slots, facts, table) ? SlotFiller.fill(modelText, slots) : fallback;
        }
        catch (final RuntimeException e)
        {
            auditSink.log(AuditEvent.of("compose_fallback").with("responseId", state.responseId).with("errorCode",
                    e.getClass().getSimpleName()));
            return fallback;
        }
    }

    private boolean validate(final TurnState state, final String text, final Map<String, String> slots,
            final Map<String, Object> facts, final Comparison table)
    {
        if (text.isBlank() || SlotFiller.fill(text, slots) == null)
        {
            return reject(state, "bad_slot");
        }
        final List<String> allowedLiterals = new ArrayList<>();
        facts.values().forEach(v -> allowedLiterals.add(String.valueOf(v)));
        if (table != null)
        {
            table.rows().forEach(r -> allowedLiterals.addAll(r.values()));
        }
        if (SlotFiller.hasLooseDigits(text, allowedLiterals))
        {
            return reject(state, "loose_digits");
        }
        final Matcher m = CODE.matcher(text);
        while (m.find())
        {
            if (!state.provenance.contains(m.group()))
            {
                return reject(state, "unknown_code");
            }
        }
        if (table != null && !tableChecker.consistent(SlotFiller.fill(text, slots), table))
        {
            return reject(state, "text_table_mismatch");
        }
        return true;
    }

    private boolean reject(final TurnState state, final String reason)
    {
        auditSink.log(AuditEvent.of("text_rejected").with("responseId", state.responseId).with("errorCode", reason));
        return false;
    }

    private Map<String, Object> tableAsMap(final Comparison table)
    {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("columns", table.columns());
        final List<Map<String, Object>> rows = new ArrayList<>();
        table.rows().forEach(r -> rows.add(Map.of("attribute", r.attributeLabel(), "values", r.values())));
        map.put("rows", rows);
        map.put("notes", table.notes());
        return map;
    }
}
