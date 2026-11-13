package com.conversational.commerce.core.engine.orchestrator;

import com.conversational.commerce.core.engine.model.TraceStep;
import java.util.List;

/** Typed payload stays in Java; codes feed the provenance set; trace is whitelisted metadata only. */
public record ToolResult(Object value, List<String> codes, List<TraceStep> trace)
{
    @SuppressWarnings("unchecked")
    public <T> T as()
    {
        return (T) value;
    }

    public static ToolResult of(final Object value, final List<String> codes, final TraceStep... trace)
    {
        return new ToolResult(value, codes, List.of(trace));
    }
}
