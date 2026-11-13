package com.conversational.commerce.core.engine.sanitiser;

import java.util.HashMap;
import java.util.Map;

/** Request-scoped, in-memory map of placeholder to original value. Discarded when the turn ends. */
public class TokenMap
{
    private final Map<String, String> byToken = new HashMap<>();
    private final Map<String, Integer> counters = new HashMap<>();

    public String register(final String type, final String original)
    {
        for (final Map.Entry<String, String> e : byToken.entrySet())
        {
            if (e.getKey().startsWith("[" + type + "_") && e.getValue().equals(original))
            {
                return e.getKey();
            }
        }
        final int n = counters.merge(type, 1, Integer::sum);
        final String token = "[" + type + "_" + n + "]";
        byToken.put(token, original);
        return token;
    }

    /** Restoration happens only in the backend, after output validation. */
    public String restore(final String text)
    {
        String result = text;
        for (final Map.Entry<String, String> e : byToken.entrySet())
        {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }

    public int size()
    {
        return byToken.size();
    }
}
