package com.conversational.commerce.core.engine.orchestrator;

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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Protected-token round trip (D17, CF-05): codes, order numbers and numbers are replaced by [P1], [P2]... before the call
 * and must all come back in the same order. One retry; otherwise the caller falls back to English with a notice.
 */
public class TranslateStep
{
    private static final Pattern PROTECTED = Pattern.compile("[A-Z]{2,4}-\\d{3,6}|\\d[\\d,.]*\\d|\\d|INR");
    private static final Pattern TOKEN = Pattern.compile("\\[P\\d+]");

    @Resource(name = "ccSanitiser")
    private Sanitiser sanitiser;

    @Resource(name = "ccModelRouter")
    private ModelRouter modelRouter;

    @Resource(name = "ccPromptRepository")
    private PromptRepository promptRepository;

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

    /** Returns the translated text, or empty if identifiers did not survive after one retry. */
    public Optional<String> translate(final TurnState state, final String text, final String from, final String to)
    {
        final List<String> originals = new ArrayList<>();
        final Matcher m = PROTECTED.matcher(text);
        final StringBuilder protectedText = new StringBuilder();
        while (m.find())
        {
            originals.add(m.group());
            m.appendReplacement(protectedText, Matcher.quoteReplacement("[P" + originals.size() + "]"));
        }
        m.appendTail(protectedText);
        for (int attempt = 0; attempt < 2; attempt++)
        {
            final Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("text", protectedText.toString());
            fields.put("sourceLanguage", from);
            fields.put("targetLanguage", to);
            final SanitisedPayload payload = sanitiser.sanitise(ModelTask.TRANSLATE, fields, state.tokens, state.secrets);
            final ModelResult result = modelRouter.call(new ModelRequest(ModelTask.TRANSLATE, "translate.v1",
                    promptRepository.prompt("translate.v1"), payload, promptRepository.schema("translate.v1"), 500, 0.0));
            state.addUsage(result.tokensIn(), result.tokensOut());
            final String out = result.json().path("text").asText("");
            final List<String> tokensBack = new ArrayList<>();
            final Matcher t = TOKEN.matcher(out);
            while (t.find())
            {
                tokensBack.add(t.group());
            }
            final List<String> expected = new ArrayList<>();
            for (int i = 1; i <= originals.size(); i++)
            {
                expected.add("[P" + i + "]");
            }
            if (tokensBack.equals(expected) && !out.isBlank())
            {
                String restored = out;
                for (int i = originals.size(); i >= 1; i--)
                {
                    restored = restored.replace("[P" + i + "]", originals.get(i - 1));
                }
                return Optional.of(restored);
            }
        }
        return Optional.empty();
    }
}
