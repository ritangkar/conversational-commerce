package com.conversational.commerce.core.engine.demo;

import com.conversational.commerce.core.service.impl.DefaultChatService;
import com.conversational.commerce.core.service.impl.DefaultChatService.ModelTransport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic rule-based stand-in for an OpenAI-shaped endpoint. Records every outbound request body so canary tests can inspect them. */
public class FakeModelTransport implements ModelTransport
{
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern CODE = Pattern.compile("\\b[A-Z]{2,4}-\\d{3,6}\\b");
    private static final Set<String> STOP = Set.of("need", "something", "to", "under", "a", "an", "the", "unit", "per", "for", "and",
            "with", "compare", "these", "three", "tell", "which", "suits", "area", "medium", "wall", "i", "me", "my", "is", "of");

    public final List<String> captured = Collections.synchronizedList(new ArrayList<>());
    public final List<String> tasks = Collections.synchronizedList(new ArrayList<>());
    /** Test switches. */
    public volatile boolean addUnknownCodeToJudge;
    public volatile boolean badComposeText;
    public volatile boolean dropTranslationTokens;
    public volatile boolean unavailable;
    public volatile String forcedIntent;

    @Override
    public Response post(final String url, final java.util.Map<String, String> headers, final String body, final int timeoutMs) throws IOException
    {
        captured.add(body);
        if (unavailable)
        {
            return new Response(503, "");
        }
        final JsonNode request = MAPPER.readTree(body);
        final String promptName = request.path("response_format").path("json_schema").path("name").asText();
        final String task = promptName.startsWith("decide") ? "DECIDE" : promptName.startsWith("judge") ? "JUDGE"
                : promptName.startsWith("translate") ? "TRANSLATE" : promptName.startsWith("explain") ? "EXPLAIN" : "COMPOSE";
        tasks.add(task);
        final JsonNode in = MAPPER.readTree(request.path("messages").path(1).path("content").asText());
        final ObjectNode out = switch (task)
        {
            case "DECIDE" -> decide(in);
            case "JUDGE" -> judge(in);
            case "TRANSLATE" -> translate(in);
            default -> compose(in);
        };
        final ObjectNode response = MAPPER.createObjectNode();
        response.putArray("choices").addObject().putObject("message").put("content", out.toString());
        response.putObject("usage").put("prompt_tokens", body.length() / 4).put("completion_tokens", out.toString().length() / 4);
        return new Response(200, response.toString());
    }

    private ObjectNode decide(final JsonNode in)
    {
        final String message = in.path("message").asText();
        final String m = message.toLowerCase(Locale.ROOT);
        final ObjectNode out = MAPPER.createObjectNode();
        String intent = "CLARIFY";
        double confidence = 0.2;
        final ArrayNode keywords = out.putArray("keywords");
        final ArrayNode codes = out.putArray("codes");
        final ObjectNode filters = MAPPER.createObjectNode();
        Double budget = null;
        Double maxUnit = null;
        Double orderMin = null;
        String status = "ALL";
        String period = "NONE";
        boolean tracking = false;
        String event = "NONE";
        final Matcher code = CODE.matcher(message);
        while (code.find())
        {
            codes.add(code.group());
        }
        final Matcher lakh = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:lakh|lac)").matcher(m);
        if (forcedIntent != null)
        {
            intent = forcedIntent;
            confidence = 0.9;
        }
        else if (m.matches(".*(weather|joke|football|recipe|movie).*"))
        {
            intent = "OFF_TOPIC";
            confidence = 0.9;
        }
        else if (m.matches(".*(place the order|place my order|pay now|cancel my|checkout for me).*"))
        {
            intent = "UNSUPPORTED";
            confidence = 0.9;
        }
        else if (lakh.find() || m.contains("budget"))
        {
            intent = "BUDGET_PLAN";
            confidence = 0.9;
            budget = lakh.reset().find() ? Double.valueOf(lakh.group(1)) * 100000 : null;
        }
        else if (m.contains("undelivered") || (m.contains("order") && m.contains("where")))
        {
            intent = "ORDER_LOOKUP";
            confidence = 0.9;
            status = m.contains("undelivered") ? "UNDELIVERED" : "ALL";
            period = m.contains("last month") ? "LAST_MONTH" : "NONE";
            tracking = m.contains("latest") || m.contains("where");
            final Matcher over = Pattern.compile("(?:over|above|more than)\\s*([\\d,]+)").matcher(m);
            if (over.find())
            {
                orderMin = Double.valueOf(over.group(1).replace(",", ""));
            }
        }
        else if (m.contains("compare"))
        {
            intent = "COMPARE";
            confidence = 0.9;
            addKeywords(keywords, m);
            event = "COMPARED";
        }
        else if (m.contains("review my list") || m.contains("before i go to checkout"))
        {
            intent = "BASKET_REVIEW";
            confidence = 0.9;
        }
        else if (codes.size() > 0 && (m.contains(" x ") || m.contains(" of ") || m.startsWith("need")))
        {
            intent = "QUICK_ORDER";
            confidence = 0.9;
        }
        else if (m.contains("reorder") || m.contains("same as my last") || m.contains("repeat my"))
        {
            intent = "REORDER";
            confidence = 0.9;
        }
        else if (m.contains("next order") || m.contains("prepare my") || m.contains("what should i purchase"))
        {
            intent = "NEXT_ORDER";
            confidence = 0.9;
        }
        else if (m.matches(".*(policy|delivery time|warranty|payment terms).*"))
        {
            intent = "FAQ";
            confidence = 0.8;
            addKeywords(keywords, m);
        }
        else if (codes.size() > 0)
        {
            intent = "PRODUCT_QA";
            confidence = 0.8;
        }
        else if (m.matches(".*(need something|looking for|find|waterproof|suggest|show me).*"))
        {
            intent = "FIND_PRODUCT";
            confidence = 0.9;
            addKeywords(keywords, m);
            final Matcher under = Pattern.compile("under\\s*([\\d,]+)").matcher(m);
            if (under.find())
            {
                maxUnit = Double.valueOf(under.group(1).replace(",", ""));
            }
            if (m.contains("medium coverage"))
            {
                filters.put("coverage", "medium");
            }
        }
        out.put("intent", intent);
        out.put("confidence", confidence);
        out.put("language", "en");
        out.put("categoryHint", "");
        final ArrayNode filterArray = out.putArray("filters");
        filters.fields().forEachRemaining(e -> filterArray.addObject().put("key", e.getKey()).put("value", e.getValue().asText()));
        putNullable(out, "budgetAmount", budget);
        putNullable(out, "maxUnitAmount", maxUnit);
        putNullable(out, "orderMinAmount", orderMin);
        out.put("orderStatus", status);
        out.put("orderPeriod", period);
        out.put("wantsLatestTracking", tracking);
        out.put("eventSuggestion", event);
        out.put("clarifyKey", "");
        return out;
    }

    private void addKeywords(final ArrayNode keywords, final String m)
    {
        for (final String w : m.replaceAll("[^a-z0-9. ]", " ").split("\\s+"))
        {
            if (w.length() > 3 && !STOP.contains(w) && keywords.size() < 8 && !w.matches("\\d.*"))
            {
                keywords.add(w);
            }
        }
    }

    private void putNullable(final ObjectNode out, final String key, final Double value)
    {
        if (value == null)
        {
            out.putNull(key);
        }
        else
        {
            out.put(key, value);
        }
    }

    private ObjectNode judge(final JsonNode in)
    {
        final ObjectNode out = MAPPER.createObjectNode();
        final ArrayNode selected = out.putArray("selected");
        int i = 0;
        final StringBuilder constraints = new StringBuilder();
        in.path("constraints").forEach(c -> constraints.append(c.asText()).append(' '));
        for (final JsonNode c : in.path("candidates"))
        {
            final ObjectNode s = selected.addObject();
            s.put("code", c.path("code").asText());
            s.put("fit", i++ == 0 ? "STRONG" : "PARTIAL");
            final ArrayNode matched = s.putArray("matchedAttributes");
            c.path("attributes").fieldNames().forEachRemaining(k ->
            {
                if (constraints.toString().contains(k + "="))
                {
                    matched.add(k);
                }
            });
        }
        if (addUnknownCodeToJudge)
        {
            selected.addObject().put("code", "XX-9999").put("fit", "STRONG").putArray("matchedAttributes");
        }
        return out;
    }

    private ObjectNode compose(final JsonNode in)
    {
        final String key = in.path("intent").asText();
        final ObjectNode out = MAPPER.createObjectNode();
        if (badComposeText)
        {
            return out.put("text", "Your total is 4999 rupees, buy XX-9999 now.");
        }
        out.put("text", in.has("comparison")
                ? "The table lists the catalogue attributes for each product. Values shown as Not available could not be verified."
                : DefaultChatService.templateText(key));
        return out;
    }

    private ObjectNode translate(final JsonNode in)
    {
        final String text = in.path("text").asText();
        final ObjectNode out = MAPPER.createObjectNode();
        out.put("sourceLanguage", in.path("sourceLanguage").asText());
        out.put("targetLanguage", in.path("targetLanguage").asText());
        if ("en".equals(in.path("targetLanguage").asText()))
        {
            out.put("text", text.contains("अगला ऑर्डर") ? "I need to prepare my next order. Can you help me identify what I should purchase?"
                    : text);
        }
        else
        {
            String translated = "(हिंदी) " + text;
            if (dropTranslationTokens)
            {
                translated = translated.replaceAll("\\[P\\d+]", "");
            }
            out.put("text", translated);
        }
        return out;
    }
}
