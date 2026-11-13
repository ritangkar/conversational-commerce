package com.conversational.commerce.core.engine.sanitiser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Regex masking rule producing typed placeholders such as [EMAIL_1]. */
public class RegexMaskingRule implements SanitisationRule
{
    public static final RegexMaskingRule EMAIL = new RegexMaskingRule("EMAIL",
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"));
    public static final RegexMaskingRule ORDER_CODE = new RegexMaskingRule("ORDER",
            Pattern.compile("\\b(?:ORD|PO)[-#]?\\d{4,}\\b|\\b\\d{8}\\b"));
    public static final RegexMaskingRule PHONE = new RegexMaskingRule("PHONE",
            Pattern.compile("(?<![\\w-])\\+?\\d[\\d\\s-]{8,}\\d(?![\\w-])"));
    public static final RegexMaskingRule LONG_DIGITS = new RegexMaskingRule("NUM", Pattern.compile("\\b\\d{9,}\\b"));

    private final String type;
    private final Pattern pattern;

    public RegexMaskingRule(final String type, final Pattern pattern)
    {
        this.type = type;
        this.pattern = pattern;
    }

    @Override
    public String apply(final String text, final TokenMap tokens)
    {
        final Matcher matcher = pattern.matcher(text);
        final StringBuilder out = new StringBuilder();
        while (matcher.find())
        {
            matcher.appendReplacement(out, Matcher.quoteReplacement(tokens.register(type, matcher.group())));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
