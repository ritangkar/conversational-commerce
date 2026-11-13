package com.conversational.commerce.core.engine.orchestrator;

/** Deterministic script-based detection so translation does not depend on the model. ASSUMPTION: Hindi (Devanagari) first (N5). */
public class LanguageDetector
{
    public String detect(final String text, final String uiLocale)
    {
        for (int i = 0; i < text.length(); i++)
        {
            final Character.UnicodeBlock block = Character.UnicodeBlock.of(text.charAt(i));
            if (block == Character.UnicodeBlock.DEVANAGARI)
            {
                return "hi";
            }
        }
        return "en";
    }
}
