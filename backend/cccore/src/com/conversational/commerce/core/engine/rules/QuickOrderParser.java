package com.conversational.commerce.core.engine.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic grammar for typed or pasted quick-order text (PA-03). Residual text is resolved by catalogue search. */
public class QuickOrderParser
{
    public enum Kind
    {
        CODE, DESCRIPTION, REPLACEMENT_OF
    }

    public record RawLine(String rawText, Kind kind, String codeHint, String description, Integer quantity)
    {
    }

    private static final Pattern CODE = Pattern.compile("\\b([A-Z]{2,4}-\\d{3,6})\\b");
    private static final Pattern QTY_FIRST = Pattern.compile("^(\\d{1,6})\\s*(?:x|×|of|units? of|pcs? of)?\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QTY_AFTER = Pattern.compile("^(.+?)\\s*(?:x|×)\\s*(\\d{1,6})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPLACES = Pattern.compile("(?:replaces?|replacement (?:for|of)|substitute (?:for|of))\\s+"
            + "([A-Z]{2,4}-\\d{3,6})", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILLER = Pattern.compile("^(?:need|i need|please add|add|order|want|and|also)\\s+",
            Pattern.CASE_INSENSITIVE);

    public List<RawLine> parse(final String text)
    {
        final List<RawLine> lines = new ArrayList<>();
        if (text == null)
        {
            return lines;
        }
        for (final String part : text.split("(?i)[\\n;,]+|\\s+and\\s+(?=\\d|whatever)"))
        {
            final String trimmed = part.trim().replaceAll("[.]+$", "");
            final String once = FILLER.matcher(trimmed).replaceFirst("").trim();
            final String segment = FILLER.matcher(once).replaceFirst("").trim();
            if (segment.isEmpty())
            {
                continue;
            }
            lines.add(parseSegment(segment));
        }
        return lines;
    }

    private RawLine parseSegment(final String segment)
    {
        final Matcher replaces = REPLACES.matcher(segment);
        if (replaces.find())
        {
            final Matcher qty = Pattern.compile("^(\\d{1,6})\\b").matcher(segment);
            return new RawLine(segment, Kind.REPLACEMENT_OF, replaces.group(1).toUpperCase(), null,
                    qty.find() ? Integer.valueOf(qty.group(1)) : null);
        }
        Integer quantity = null;
        String rest = segment;
        final Matcher first = QTY_FIRST.matcher(segment);
        final Matcher after = QTY_AFTER.matcher(segment);
        if (first.matches())
        {
            quantity = Integer.valueOf(first.group(1));
            rest = first.group(2).trim();
        }
        else if (after.matches())
        {
            quantity = Integer.valueOf(after.group(2));
            rest = after.group(1).trim();
        }
        final Matcher code = CODE.matcher(rest);
        if (code.find())
        {
            return new RawLine(segment, Kind.CODE, code.group(1), null, quantity);
        }
        return new RawLine(segment, Kind.DESCRIPTION, null, rest, quantity);
    }
}
