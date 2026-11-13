package com.conversational.commerce.core.engine.config;

import com.conversational.commerce.core.engine.model.Capabilities;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layered read over ConfigSource layers, then the caller default.
 * Short-TTL cache, cleared on save. Fail-closed rules per A1.
 */
public class DefaultCcConfigurationService implements CcConfigurationService
{
    private static final String CACHE_TTL_KEY = "cc.config.cacheTtlSeconds";
    private static final String TOGGLE_PREFIX = "capability.";
    private static final String TOGGLE_SUFFIX = ".enabled";
    private static final String PL05_CONFIRM_KEY = "capability.PL-05.confirmDisable";
    private static final String ACTIVE_PACK_KEY = "cc.activePack";
    private static final String SYNTHETIC_KEY = "cc.syntheticDataOnly";

    /** Layers, highest priority first (Backoffice entries, then properties and environment). */
    @Resource
    private List<ConfigSource> sources;

    private Clock clock = Clock.systemUTC();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(Optional<String> value, long loadedAtMs)
    {
    }

    public void setSources(final List<ConfigSource> sources)
    {
        this.sources = sources;
    }

    public void setClock(final Clock clock)
    {
        this.clock = clock;
    }

    @Override
    public boolean isEnabled(final String capabilityId)
    {
        final Optional<String> raw = read(TOGGLE_PREFIX + capabilityId + TOGGLE_SUFFIX);
        final boolean explicitOff = raw.isPresent() && "false".equalsIgnoreCase(raw.get().trim());
        if (!explicitOff)
        {
            return true; // missing or unreadable = on (defaults are seeded by essential data)
        }
        if ("PL-01".equals(capabilityId))
        {
            return true; // configuration cannot switch itself off
        }
        if ("PL-05".equals(capabilityId))
        {
            return !getBoolean(PL05_CONFIRM_KEY, false); // needs the second confirming entry
        }
        // PL-03 and PL-04 off take effect as fail-closed behaviour in their callers (calls blocked / unavailable notice).
        return false;
    }

    @Override
    public String validateToggleChange(final String capabilityId, final boolean newValue)
    {
        if (newValue || !Capabilities.PROTECTED.contains(capabilityId))
        {
            return null;
        }
        if ("PL-01".equals(capabilityId))
        {
            return "PL-01 cannot be disabled by a toggle";
        }
        if ("PL-05".equals(capabilityId))
        {
            return getBoolean(PL05_CONFIRM_KEY, false) ? null : "PL-05 needs the confirming entry " + PL05_CONFIRM_KEY;
        }
        return null; // PL-03/PL-04: allowed, but callers fail closed
    }

    @Override
    public String getString(final String key, final String defaultValue)
    {
        return read(key).orElse(defaultValue);
    }

    @Override
    public int getInt(final String key, final int defaultValue)
    {
        final Optional<String> value = read(key);
        if (value.isEmpty())
        {
            return defaultValue;
        }
        try
        {
            return Integer.parseInt(value.get().trim());
        }
        catch (final NumberFormatException e)
        {
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(final String key, final boolean defaultValue)
    {
        return read(key).map(v -> "true".equalsIgnoreCase(v.trim())).orElse(defaultValue);
    }

    @Override
    public String activePack()
    {
        return getString(ACTIVE_PACK_KEY, "tyres");
    }

    @Override
    public boolean syntheticDataOnly()
    {
        return getBoolean(SYNTHETIC_KEY, true);
    }

    @Override
    public void clearCache()
    {
        cache.clear();
    }

    private Optional<String> read(final String key)
    {
        final long now = clock.millis();
        final long ttlMs = ttlSeconds() * 1000L;
        final Cached cached = cache.get(key);
        if (cached != null && now - cached.loadedAtMs() < ttlMs)
        {
            return cached.value();
        }
        Optional<String> found = Optional.empty();
        for (final ConfigSource source : sources)
        {
            found = source.get(key);
            if (found.isPresent())
            {
                break;
            }
        }
        cache.put(key, new Cached(found, now));
        return found;
    }

    private int ttlSeconds()
    {
        for (final ConfigSource source : sources)
        {
            final Optional<String> v = source.get(CACHE_TTL_KEY);
            if (v.isPresent())
            {
                try
                {
                    return Math.max(0, Integer.parseInt(v.get().trim()));
                }
                catch (final NumberFormatException e)
                {
                    return 10;
                }
            }
        }
        return 10; // ASSUMPTION: design default 10 s (part3 2b.4)
    }
}
