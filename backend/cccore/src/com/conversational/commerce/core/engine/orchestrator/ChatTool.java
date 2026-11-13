package com.conversational.commerce.core.engine.orchestrator;

import java.util.Map;

/**
 * SPI (PL-04, PL-08): add a tool by registering a bean of this type. Tools must not accept customer, user or price
 * arguments; the customer is read from {@link TurnState}. A tool that writes is not allowed.
 */
public interface ChatTool
{
    String name();

    String capabilityId();

    ToolResult execute(TurnState state, Map<String, Object> args);
}
