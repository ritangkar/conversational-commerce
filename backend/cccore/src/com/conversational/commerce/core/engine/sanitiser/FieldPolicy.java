package com.conversational.commerce.core.engine.sanitiser;

import java.util.Locale;
import java.util.Set;

/** Classifies outbound field names (PL-03). NEVER fields are dropped and audited, whatever a task allows. */
public class FieldPolicy
{
    public enum Class_
    {
        ALLOW, TOKENISE, NEVER
    }

    private static final Set<String> NEVER_FRAGMENTS = Set.of("price", "total", "margin", "priority", "customername", "email",
            "phone", "address", "pk", "customer", "uid", "supplier", "amount", "cost");
    private static final Set<String> TOKENISE_FIELDS = Set.of("orderCode", "orderCodes", "dealerRef");

    public Class_ classify(final String fieldName)
    {
        final String lower = fieldName.toLowerCase(Locale.ROOT);
        for (final String fragment : NEVER_FRAGMENTS)
        {
            if (lower.contains(fragment))
            {
                return Class_.NEVER;
            }
        }
        return TOKENISE_FIELDS.contains(fieldName) ? Class_.TOKENISE : Class_.ALLOW;
    }
}
