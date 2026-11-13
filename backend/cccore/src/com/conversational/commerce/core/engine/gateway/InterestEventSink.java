package com.conversational.commerce.core.engine.gateway;

import com.conversational.commerce.core.engine.model.Domain.EventType;

public interface InterestEventSink
{
    /** Stores an enumerated event. No text. Called only by InterestEventService. */
    void write(String customerId, EventType type, String reference, String pack);
}
