package com.conversational.commerce.core.engine.orchestrator;

import java.util.Locale;

public final class MoneyFormat
{
    private MoneyFormat()
    {
    }

    public static String format(final long minor, final String currency)
    {
        return String.format(Locale.ROOT, "%s %,d.%02d", currency, minor / 100, Math.abs(minor % 100));
    }

    public static long toMinor(final double majorAmount)
    {
        return Math.round(majorAmount * 100);
    }
}
