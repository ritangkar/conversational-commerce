package com.conversational.commerce.core.engine.orchestrator;

import com.conversational.commerce.core.engine.model.Blocks.Comparison;
import com.conversational.commerce.core.engine.model.Blocks.ComparisonRow;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Every number/unit the explanation mentions must appear in the comparison table (A13 rule 3). */
public class TextTableChecker
{
    private static final Pattern VALUE = Pattern.compile("\\d+(?:[.,]\\d+)?\\s?[A-Za-z%]*");

    public boolean consistent(final String text, final Comparison table)
    {
        final StringBuilder cells = new StringBuilder();
        for (final ComparisonRow row : table.rows())
        {
            row.values().forEach(v -> cells.append(v).append('|'));
        }
        final String haystack = cells.toString().toLowerCase();
        final Matcher m = VALUE.matcher(text);
        while (m.find())
        {
            if (!haystack.contains(m.group().trim().toLowerCase()))
            {
                return false;
            }
        }
        return true;
    }
}
