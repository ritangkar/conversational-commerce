package com.conversational.commerce.core.engine.provider;

import com.fasterxml.jackson.databind.JsonNode;

public record ModelResult(JsonNode json, int tokensIn, int tokensOut, String provider, String model, long latencyMs)
{
}
