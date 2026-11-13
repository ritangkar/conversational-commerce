package com.conversational.commerce.core.engine.demo;

import com.conversational.commerce.core.engine.config.ConfigSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MapConfigSource implements ConfigSource
{
    private final Map<String, String> values = new ConcurrentHashMap<>();

    public MapConfigSource put(final String key, final String value)
    {
        values.put(key, value);
        return this;
    }

    @Override
    public Optional<String> get(final String key)
    {
        return Optional.ofNullable(values.get(key));
    }
}
