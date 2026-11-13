package com.conversational.commerce.core.engine.orchestrator;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import com.conversational.commerce.core.engine.gateway.InterestEventSink;
import com.conversational.commerce.core.engine.model.Decision;
import com.conversational.commerce.core.engine.model.Domain.EventType;
import jakarta.annotation.Resource;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The only writer of interest events (CI-04, D08, A9). The model suggests an enum; the backend decides, and stores only the
 * enum, a code or a normalised token. No text. Writes nothing when privacy.interestEvents.enabled is false.
 */
public class InterestEventService
{
    private static final Pattern TOKEN_OK = Pattern.compile("[a-z]{3,30}");

    @Resource
    private InterestEventSink interestEventSink;

    @Resource
    private CcConfigurationService configurationService;

    public void setInterestEventSink(final InterestEventSink s)
    {
        this.interestEventSink = s;
    }

    public void setConfigurationService(final CcConfigurationService c)
    {
        this.configurationService = c;
    }

    public void maybeRecord(final TurnState state, final Decision decision)
    {
        if (!configurationService.isEnabled("CI-04") || !configurationService.getBoolean("privacy.interestEvents.enabled", true)
                || "NONE".equals(decision.eventSuggestion()))
        {
            return;
        }
        final EventType type = EventType.valueOf(decision.eventSuggestion());
        if (type == EventType.REQUESTED_NOT_FOUND)
        {
            if (!decision.keywords().isEmpty())
            {
                final String token = decision.keywords().get(0).toLowerCase(Locale.ROOT);
                if (TOKEN_OK.matcher(token).matches()) // letters only: no digits, emails or codes can be stored
                {
                    interestEventSink.write(state.customerId, type, token, state.pack);
                }
            }
            return;
        }
        final java.util.Set<String> codes = new java.util.LinkedHashSet<>(decision.codes());
        codes.addAll(state.request.shortlistCodes());
        for (final String code : codes)
        {
            if (state.provenance.contains(code))
            {
                interestEventSink.write(state.customerId, type, code, state.pack);
            }
        }
    }
}
