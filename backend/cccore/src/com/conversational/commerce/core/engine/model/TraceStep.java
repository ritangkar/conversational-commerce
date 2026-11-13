package com.conversational.commerce.core.engine.model;

/** Whitelisted why-this metadata (CF-08): source, window, threshold. No weights, margin, prompts or model text. */
public record TraceStep(String label, String source, String window, String threshold)
{
}
