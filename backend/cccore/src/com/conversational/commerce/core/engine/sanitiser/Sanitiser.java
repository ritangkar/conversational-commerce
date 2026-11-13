package com.conversational.commerce.core.engine.sanitiser;

import com.conversational.commerce.core.engine.provider.ModelTask;
import java.util.Collection;
import java.util.Map;

public interface Sanitiser
{
    /**
     * Builds the outbound payload for a task: drops fields not allowed for the task or classified NEVER, masks free text,
     * runs the outbound guard. Fails closed when sanitisation is off.
     */
    SanitisedPayload sanitise(ModelTask task, Map<String, Object> fields, TokenMap tokens, Collection<String> forbiddenValues);
}
