package com.conversational.commerce.core.constants;

/**
 * Property keys read through ConfigurationService. Keys only; values live in project.properties (safe defaults)
 * or local.properties / environment / Cloud secret (keys). Services hold the keys they use as
 * {@code private static final String}, as in the owner's samples; this class is the single registry for documentation and tests.
 */
public final class CcPropertyKeys
{
    // ASSUMPTION: Jakarta platform and file location follow the extension layout in docs/assumptions.md AS-01/AS-03.
    public static final String PROVIDER_TIER_PREFIX = "cc.provider.";
    public static final String PROVIDER_KEY_PREFIX = "cc.provider.key.";
    public static final String HTTP_CONNECT_TIMEOUT_MS = "cc.http.connectTimeoutMs";
    public static final String HTTP_REQUEST_TIMEOUT_MS = "cc.http.requestTimeoutMs";
    public static final String HTTP_POOL_SIZE = "cc.http.pool.size";
    public static final String STREAM_TIMEOUT_SECONDS = "cc.stream.timeoutSeconds";
    public static final String STREAM_HEARTBEAT_SECONDS = "cc.stream.heartbeatSeconds";
    public static final String CONFIG_CACHE_TTL_SECONDS = "cc.config.cacheTtlSeconds";
    public static final String MESSAGE_MAX_LENGTH = "cc.message.maxLength";
    public static final String SANITISER_ENABLED = "cc.sanitiser.enabled";

    private CcPropertyKeys()
    {
        // constants only
    }
}
