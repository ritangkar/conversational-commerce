package com.conversational.commerce.core.engine.config;

public interface CcConfigurationService
{
    boolean isEnabled(String capabilityId);

    String getString(String key, String defaultValue);

    int getInt(String key, int defaultValue);

    boolean getBoolean(String key, boolean defaultValue);

    String activePack();

    boolean syntheticDataOnly();

    /** Returns null if the change is allowed, otherwise the refusal reason (A1). Used by the Backoffice save interceptor. */
    String validateToggleChange(String capabilityId, boolean newValue);

    void clearCache();
}
