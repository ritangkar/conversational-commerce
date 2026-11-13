package com.conversational.commerce.core.engine.sanitiser;

/** One masking rule applied to free text. Rules are beans and configurable (PL-03, PL-08). */
public interface SanitisationRule
{
    /** Returns the text with matches replaced by typed placeholders registered in the token map. */
    String apply(String text, TokenMap tokens);
}
