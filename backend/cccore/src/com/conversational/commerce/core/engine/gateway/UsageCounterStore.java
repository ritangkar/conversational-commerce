package com.conversational.commerce.core.engine.gateway;

public interface UsageCounterStore
{
    /** Atomically adds to the counter for (customer, period) and returns {messages, tokens} after the change. */
    long[] add(String customerId, String periodKey, int messages, int tokens);

    long[] get(String customerId, String periodKey);
}
