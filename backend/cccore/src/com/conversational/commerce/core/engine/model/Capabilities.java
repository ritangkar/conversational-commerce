package com.conversational.commerce.core.engine.model;

import java.util.List;
import java.util.Set;

/** The 43 baseline capability IDs (design part4, section 3.1). Each has a toggle capability.&lt;ID&gt;.enabled. */
public final class Capabilities
{
    public static final List<String> ALL = List.of("CF-01", "CF-02", "CF-03", "CF-04", "CF-05", "CF-06", "CF-07", "CF-08",
            "PD-01", "PD-02", "PD-03", "PD-04", "PD-05", "PD-06", "PD-07", "PA-01", "PA-02", "PA-03", "PA-04", "PA-05",
            "PA-06", "PA-07", "OM-01", "OM-02", "OM-03", "RI-01", "RI-02", "RI-03", "RI-04", "RI-05", "CI-01", "CI-02",
            "CI-03", "CI-04", "KN-01", "PL-01", "PL-02", "PL-03", "PL-04", "PL-05", "PL-06", "PL-07", "PL-08");

    /** Fail-closed capabilities (A1). */
    public static final Set<String> PROTECTED = Set.of("PL-01", "PL-03", "PL-04", "PL-05");

    private Capabilities()
    {
    }
}
