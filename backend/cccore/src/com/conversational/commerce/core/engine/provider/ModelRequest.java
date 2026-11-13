package com.conversational.commerce.core.engine.provider;

import com.conversational.commerce.core.engine.sanitiser.SanitisedPayload;

/** The payload type is {@link SanitisedPayload}: an unsanitised request cannot be constructed. */
public record ModelRequest(ModelTask task, String promptId, String systemPrompt, SanitisedPayload payload, String schemaJson,
        int maxOutputTokens, double temperature)
{
}
