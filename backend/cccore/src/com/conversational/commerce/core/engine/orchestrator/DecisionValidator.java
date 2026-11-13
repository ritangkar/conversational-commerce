package com.conversational.commerce.core.engine.orchestrator;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import com.conversational.commerce.core.engine.model.Decision;
import com.conversational.commerce.core.engine.model.Intent;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates the DECIDE output against the catalogue of intents, enabled capabilities and numeric ranges. */
public class DecisionValidator
{
    private static final Set<String> EVENTS = Set.of("VIEWED", "COMPARED", "SHORTLISTED", "REQUESTED_NOT_FOUND");
    private static final Set<String> STATUSES = Set.of("ALL", "UNDELIVERED", "PENDING", "DELIVERED");
    private static final Set<String> PERIODS = Set.of("NONE", "LAST_MONTH", "LAST_30_DAYS", "THIS_MONTH");
    private static final Pattern CODE = Pattern.compile("[A-Z]{2,4}-\\d{3,6}");
    private static final double MAX_AMOUNT = 10_000_000_000.0;

    @Resource
    private CcConfigurationService configurationService;

    @Resource
    private AuditSink auditSink;

    public void setConfigurationService(final CcConfigurationService c)
    {
        this.configurationService = c;
    }

    public void setAuditSink(final AuditSink a)
    {
        this.auditSink = a;
    }

    public Decision validate(final TurnState state, final JsonNode json)
    {
        Intent intent;
        try
        {
            intent = Intent.valueOf(json.path("intent").asText("CLARIFY"));
        }
        catch (final IllegalArgumentException e)
        {
            audit(state, "unknown_intent");
            intent = Intent.CLARIFY;
        }
        final double confidence = Math.max(0, Math.min(1, json.path("confidence").asDouble(0)));
        if (confidence < configurationService.getInt("decision.minConfidencePercent", 40) / 100.0 && intent != Intent.OFF_TOPIC)
        {
            intent = Intent.CLARIFY;
        }
        final Set<String> allowedFilterKeys = new java.util.HashSet<>(Arrays.asList(
                configurationService.getString("pack." + state.pack + ".searchFields", "").split(",")));
        final Map<String, String> filters = new LinkedHashMap<>();
        for (final JsonNode f : json.path("filters"))
        {
            final String key = f.path("key").asText();
            if (allowedFilterKeys.contains(key) && filters.size() < 8)
            {
                filters.put(key, trim(f.path("value").asText(), 60));
            }
            else
            {
                audit(state, "filter_key_rejected");
            }
        }
        final List<String> codes = new ArrayList<>();
        for (final JsonNode c : json.path("codes"))
        {
            if (CODE.matcher(c.asText()).matches() && codes.size() < 10)
            {
                codes.add(c.asText());
            }
        }
        final String status = STATUSES.contains(json.path("orderStatus").asText()) ? json.path("orderStatus").asText() : "ALL";
        final String period = PERIODS.contains(json.path("orderPeriod").asText()) ? json.path("orderPeriod").asText() : "NONE";
        final String event = json.path("eventSuggestion").asText("NONE");
        return new Decision(intent, confidence, trim(json.path("language").asText("en"), 8), strings(json.path("keywords"), 12, 40),
                trim(json.path("categoryHint").asText(""), 60), filters, amount(json.path("budgetAmount")),
                amount(json.path("maxUnitAmount")), codes, status, amount(json.path("orderMinAmount")), period,
                json.path("wantsLatestTracking").asBoolean(false), EVENTS.contains(event) ? event : "NONE",
                trim(json.path("clarifyKey").asText(""), 60));
    }

    private Double amount(final JsonNode node)
    {
        if (!node.isNumber() || node.asDouble() <= 0 || node.asDouble() > MAX_AMOUNT)
        {
            return null;
        }
        return node.asDouble();
    }

    private List<String> strings(final JsonNode array, final int max, final int maxLen)
    {
        final List<String> list = new ArrayList<>();
        for (final JsonNode n : array)
        {
            if (list.size() < max && !n.asText().isBlank())
            {
                list.add(trim(n.asText(), maxLen));
            }
        }
        return list;
    }

    private String trim(final String value, final int max)
    {
        return value.length() > max ? value.substring(0, max) : value;
    }

    private void audit(final TurnState state, final String code)
    {
        auditSink.log(AuditEvent.of("decision_adjusted").with("responseId", state.responseId).with("errorCode", code));
    }
}
