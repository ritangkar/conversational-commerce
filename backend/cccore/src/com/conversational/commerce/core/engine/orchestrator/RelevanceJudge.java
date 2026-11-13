package com.conversational.commerce.core.engine.orchestrator;

import com.conversational.commerce.core.engine.model.Domain.Product;
import com.conversational.commerce.core.engine.provider.ModelRequest;
import com.conversational.commerce.core.engine.provider.ModelResult;
import com.conversational.commerce.core.engine.provider.ModelRouter;
import com.conversational.commerce.core.engine.provider.ModelTask;
import com.conversational.commerce.core.engine.sanitiser.SanitisedPayload;
import com.conversational.commerce.core.engine.sanitiser.Sanitiser;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JUDGE task: the model picks codes and attribute keys from the candidates; the backend validates both. No tool authority. */
public class RelevanceJudge
{
    public record Pick(String code, String fit, List<String> matchedAttributes)
    {
    }

    @Resource
    private Sanitiser sanitiser;

    @Resource
    private ModelRouter modelRouter;

    @Resource
    private PromptRepository promptRepository;

    @Resource
    private ProvenanceChecker provenanceChecker;

    @Resource
    private AuditSink auditSink;

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

    public void setProvenanceChecker(final ProvenanceChecker p)
    {
        this.provenanceChecker = p;
    }

    public void setAuditSink(final AuditSink a)
    {
        this.auditSink = a;
    }

    /** Candidate descriptors contain product text only (untrusted data), never prices or stock. */
    public List<Pick> judge(final TurnState state, final String need, final List<String> constraints, final List<Product> candidates)
    {
        final List<Map<String, Object>> list = new ArrayList<>();
        final Map<String, List<String>> attributeKeys = new LinkedHashMap<>();
        for (final Product p : candidates)
        {
            final Map<String, Object> c = new LinkedHashMap<>();
            c.put("code", p.code());
            c.put("name", p.name());
            c.put("description", p.description());
            c.put("attributes", p.attributes());
            list.add(c);
            attributeKeys.put(p.code(), new ArrayList<>(p.attributes().keySet()));
        }
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("need", need);
        fields.put("constraints", constraints);
        fields.put("candidates", list);
        fields.put("language", "en");
        return runJudge(state, fields, attributeKeys);
    }

    private List<Pick> runJudge(final TurnState state, final Map<String, Object> fields, final Map<String, List<String>> allowedKeys)
    {
        final SanitisedPayload payload = sanitiser.sanitise(ModelTask.JUDGE, fields, state.tokens, state.secrets);
        final ModelResult result = modelRouter.call(new ModelRequest(ModelTask.JUDGE, "judge.v1",
                promptRepository.prompt("judge.v1"), payload, promptRepository.schema("judge.v1"), 600, 0.0));
        state.addUsage(result.tokensIn(), result.tokensOut());
        final List<Pick> picks = new ArrayList<>();
        final List<String> proposed = new ArrayList<>();
        final JsonNode selected = result.json().path("selected");
        for (final JsonNode node : selected)
        {
            proposed.add(node.path("code").asText());
        }
        final List<String> accepted = provenanceChecker.filter(state, proposed, auditSink);
        for (final JsonNode node : selected)
        {
            final String code = node.path("code").asText();
            if (!accepted.contains(code) || !allowedKeys.containsKey(code))
            {
                continue;
            }
            final List<String> attrs = new ArrayList<>();
            for (final JsonNode a : node.path("matchedAttributes"))
            {
                if (allowedKeys.get(code).contains(a.asText()))
                {
                    attrs.add(a.asText());
                }
            }
            picks.add(new Pick(code, node.path("fit").asText("PARTIAL"), attrs));
        }
        return picks;
    }

    /** FAQ variant: "code" is the FAQ entry id and "name" the approved question. */
    public List<String> judgeFaq(final TurnState state, final String question, final Map<String, String> idToQuestion)
    {
        final List<Map<String, Object>> list = new ArrayList<>();
        final Map<String, List<String>> keys = new LinkedHashMap<>();
        idToQuestion.forEach((id, q) ->
        {
            final Map<String, Object> c = new LinkedHashMap<>();
            c.put("code", id);
            c.put("name", q);
            c.put("description", "");
            c.put("attributes", Map.of());
            list.add(c);
            keys.put(id, List.of());
        });
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("need", question);
        fields.put("constraints", List.of());
        fields.put("candidates", list);
        fields.put("language", "en");
        return runJudge(state, fields, keys).stream().map(Pick::code).toList();
    }
}
