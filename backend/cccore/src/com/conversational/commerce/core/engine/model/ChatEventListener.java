package com.conversational.commerce.core.engine.model;

/** The controller adapts this to the SSE emitter; the non-streaming path uses a collecting listener. */
public interface ChatEventListener
{
    void onEvent(ChatEvent event);

    ChatEventListener NOOP = event ->
    {
    };
}
