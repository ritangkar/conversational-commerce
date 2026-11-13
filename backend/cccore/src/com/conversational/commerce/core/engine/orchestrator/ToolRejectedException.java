package com.conversational.commerce.core.engine.orchestrator;

public class ToolRejectedException extends RuntimeException
{
    public ToolRejectedException(final String reason)
    {
        super(reason);
    }
}
