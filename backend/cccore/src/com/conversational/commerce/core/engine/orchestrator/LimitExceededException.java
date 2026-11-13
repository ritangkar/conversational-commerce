package com.conversational.commerce.core.engine.orchestrator;

public class LimitExceededException extends RuntimeException
{
    public LimitExceededException(final String code)
    {
        super(code);
    }
}
