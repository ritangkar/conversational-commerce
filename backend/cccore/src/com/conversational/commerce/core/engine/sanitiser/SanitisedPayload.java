package com.conversational.commerce.core.engine.sanitiser;

import java.util.List;

/**
 * The only thing a provider adapter accepts. The constructor is package-private so it can be created only by
 * {@link DefaultSanitiser}; this makes "every model call goes through the sanitiser" a compile-time property.
 */
public final class SanitisedPayload
{
    private final String json;
    private final List<String> droppedFields;

    SanitisedPayload(final String json, final List<String> droppedFields)
    {
        this.json = json;
        this.droppedFields = List.copyOf(droppedFields);
    }

    public String json()
    {
        return json;
    }

    public List<String> droppedFields()
    {
        return droppedFields;
    }
}
