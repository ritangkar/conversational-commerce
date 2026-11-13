package com.conversational.commerce.core.engine.sanitiser;

import java.util.Collection;
import java.util.Locale;

/** Last check before any external call: no value the backend marked as secret may appear in the final payload. */
public class OutboundGuard
{
    public static class EgressBlockedException extends RuntimeException
    {
        public EgressBlockedException(final String message)
        {
            super(message);
        }
    }

    /** @param forbiddenValues prices returned in this turn, margin values, customer uid and email. Never logged. */
    public void check(final String finalJson, final Collection<String> forbiddenValues)
    {
        final String haystack = finalJson.toLowerCase(Locale.ROOT);
        for (final String forbidden : forbiddenValues)
        {
            if (forbidden != null && forbidden.length() >= 3 && haystack.contains(forbidden.toLowerCase(Locale.ROOT)))
            {
                throw new EgressBlockedException("egress_blocked"); // value deliberately not in the message
            }
        }
    }
}
