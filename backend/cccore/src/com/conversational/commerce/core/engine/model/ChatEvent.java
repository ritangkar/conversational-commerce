package com.conversational.commerce.core.engine.model;

import com.conversational.commerce.core.engine.model.Blocks.Block;

/** Stream event: step_started, step_completed, block, text_delta, done, error. */
public record ChatEvent(String event, String stepId, String labelKey, String status, Block block, String delta, String code)
{
    public static ChatEvent stepStarted(final String stepId, final String labelKey)
    {
        return new ChatEvent("step_started", stepId, labelKey, "started", null, null, null);
    }

    public static ChatEvent stepCompleted(final String stepId, final String labelKey, final String status)
    {
        return new ChatEvent("step_completed", stepId, labelKey, status, null, null, null);
    }

    public static ChatEvent block(final Block block)
    {
        return new ChatEvent("block", null, null, null, block, null, null);
    }

    public static ChatEvent textDelta(final String blockId, final String delta)
    {
        return new ChatEvent("text_delta", blockId, null, null, null, delta, null);
    }

    public static ChatEvent done()
    {
        return new ChatEvent("done", null, null, null, null, null, null);
    }

    public static ChatEvent error(final String code)
    {
        return new ChatEvent("error", null, null, null, null, null, code);
    }
}
