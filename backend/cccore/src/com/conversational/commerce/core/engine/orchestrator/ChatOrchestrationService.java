package com.conversational.commerce.core.engine.orchestrator;

import com.conversational.commerce.core.engine.model.ChatEventListener;
import com.conversational.commerce.core.engine.model.TurnRequest;
import com.conversational.commerce.core.engine.model.TurnResult;

public interface ChatOrchestrationService
{
    /** Runs one turn. Events are pushed to the listener; the same blocks are returned for the non-streaming path. */
    TurnResult handle(TurnRequest request, ChatEventListener listener);
}
