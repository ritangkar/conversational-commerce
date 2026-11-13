package com.conversational.commerce.core.engine.orchestrator;

import com.conversational.commerce.core.engine.model.ChatEvent;
import com.conversational.commerce.core.engine.model.ChatEventListener;
import com.conversational.commerce.core.engine.model.TraceStep;
import com.conversational.commerce.core.engine.model.TurnRequest;
import com.conversational.commerce.core.engine.sanitiser.TokenMap;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Everything one turn needs. The customer comes from the security context via the facade; tools read it from here and
 * never accept a customer, user or price argument from the model.
 */
public class TurnState
{
    public final String customerId;
    public final String pack;
    public final LocalDate today;
    public final String responseId;
    public final TurnRequest request;
    public final ChatEventListener listener;
    public final TokenMap tokens = new TokenMap();
    /** Product codes, order codes and FAQ ids returned by tools this turn (provenance, D26). */
    public final Set<String> provenance = ConcurrentHashMap.newKeySet();
    /** Values that must never leave in a model payload: formatted prices, margins, uid, email. */
    public final Set<String> secrets = ConcurrentHashMap.newKeySet();
    public final List<TraceStep> trace = Collections.synchronizedList(new ArrayList<>());
    /** The message in English after the translate-in step; used by deterministic parsers. */
    public volatile String workingMessage;
    public volatile String language = "en";
    public final AtomicInteger tokensIn = new AtomicInteger();
    public final AtomicInteger tokensOut = new AtomicInteger();

    private final AtomicInteger steps = new AtomicInteger();
    private final AtomicInteger toolCalls = new AtomicInteger();
    private final int maxSteps;
    private final long deadlineMs;

    public TurnState(final TurnRequest request, final String pack, final LocalDate today, final String responseId,
            final ChatEventListener listener, final int maxSteps, final int maxSeconds)
    {
        this.request = request;
        this.customerId = request.customerId();
        this.pack = pack;
        this.today = today;
        this.responseId = responseId;
        this.listener = listener;
        this.maxSteps = maxSteps;
        this.deadlineMs = System.currentTimeMillis() + maxSeconds * 1000L;
        this.workingMessage = request.message();
        this.secrets.add(request.customerId());
    }

    /** Tool executions are bounded separately from visible steps, so a parallel group is one step but still capped. */
    public void checkToolCall()
    {
        if (toolCalls.incrementAndGet() > maxSteps * 3)
        {
            throw new LimitExceededException("max_tool_calls");
        }
        if (System.currentTimeMillis() > deadlineMs)
        {
            throw new LimitExceededException("max_seconds");
        }
    }

    public void checkLimits()
    {
        if (steps.incrementAndGet() > maxSteps)
        {
            throw new LimitExceededException("max_steps");
        }
        if (System.currentTimeMillis() > deadlineMs)
        {
            throw new LimitExceededException("max_seconds");
        }
    }

    /** Runs a visible step: emits step_started and step_completed around the body and enforces the limits. */
    public <T> T step(final String labelKey, final Supplier<T> body)
    {
        checkLimits();
        final String id = "s" + steps.get();
        listener.onEvent(ChatEvent.stepStarted(id, labelKey));
        try
        {
            final T result = body.get();
            listener.onEvent(ChatEvent.stepCompleted(id, labelKey, "ok"));
            return result;
        }
        catch (final RuntimeException e)
        {
            listener.onEvent(ChatEvent.stepCompleted(id, labelKey, "failed"));
            throw e;
        }
    }

    public void addUsage(final int in, final int out)
    {
        tokensIn.addAndGet(in);
        tokensOut.addAndGet(out);
    }
}
