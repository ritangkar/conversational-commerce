package com.conversational.commerce.core.engine.orchestrator;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Model text may contain {{S:name}} only; the backend inserts amounts, quantities, dates and percentages. */
public final class SlotFiller
{
    private static final Pattern SLOT = Pattern.compile("\\{\\{S:([A-Za-z0-9_]+)}}");

    private SlotFiller()
    {
    }

    /** Returns null if the text uses a slot the backend did not provide. */
    public static String fill(final String text, final Map<String, String> slots)
    {
        final Matcher m = SLOT.matcher(text);
        final StringBuilder out = new StringBuilder();
        while (m.find())
        {
            final String value = slots.get(m.group(1));
            if (value == null)
            {
                return null;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** True if the text has digits outside slots that are not in the allowed literals (A13 rule 2). */
    public static boolean hasLooseDigits(final String text, final Iterable<String> allowedLiterals)
    {
        final String withoutSlots = SLOT.matcher(text).replaceAll("");
        final Matcher digits = Pattern.compile("\\d+(?:[.,]\\d+)*").matcher(withoutSlots);
        while (digits.find())
        {
            boolean allowed = false;
            for (final String literal : allowedLiterals)
            {
                if (literal.contains(digits.group()))
                {
                    allowed = true;
                    break;
                }
            }
            if (!allowed)
            {
                return true;
            }
        }
        return false;
    }
}
