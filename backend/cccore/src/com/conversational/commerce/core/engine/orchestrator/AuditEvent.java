package com.conversational.commerce.core.engine.orchestrator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Metadata-only audit event (PL-05). Only whitelisted keys are accepted, so prompts and message text cannot be logged. */
public final class AuditEvent
{
    public static final Set<String> ALLOWED_KEYS = Set.of("event", "sessionRef", "responseId", "intent", "confidence",
            "workflow", "stepName", "toolName", "outcome", "errorCode", "provider", "model", "promptVersion", "tokensIn",
            "tokensOut", "latencyMs", "timestamp", "count");

    private final Map<String, String> fields = new LinkedHashMap<>();

    private AuditEvent(final String event)
    {
        fields.put("event", event);
    }

    public static AuditEvent of(final String event)
    {
        return new AuditEvent(event);
    }

    public AuditEvent with(final String key, final Object value)
    {
        if (!ALLOWED_KEYS.contains(key))
        {
            throw new IllegalArgumentException("audit key not allowed: " + key);
        }
        fields.put(key, String.valueOf(value));
        return this;
    }

    public Map<String, String> fields()
    {
        return fields;
    }
}
