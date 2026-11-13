package com.conversational.commerce.core.engine.tools;

import com.conversational.commerce.core.engine.orchestrator.ToolRejectedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Argument validation for model-supplied tool arguments: type, size and range. A violation rejects the step. */
public final class ToolArgs
{
    private ToolArgs()
    {
    }

    public static List<String> strings(final Map<String, Object> args, final String key, final int maxSize)
    {
        final Object value = args.get(key);
        if (value == null)
        {
            return List.of();
        }
        if (!(value instanceof List<?> list) || list.size() > maxSize)
        {
            throw new ToolRejectedException("bad_argument:" + key);
        }
        final List<String> result = new ArrayList<>();
        for (final Object item : list)
        {
            if (!(item instanceof String s) || s.length() > 200)
            {
                throw new ToolRejectedException("bad_argument:" + key);
            }
            result.add(s.trim());
        }
        return result;
    }

    public static String string(final Map<String, Object> args, final String key, final String defaultValue)
    {
        final Object value = args.get(key);
        if (value == null)
        {
            return defaultValue;
        }
        if (!(value instanceof String s) || s.length() > 2000)
        {
            throw new ToolRejectedException("bad_argument:" + key);
        }
        return s.trim();
    }

    public static long number(final Map<String, Object> args, final String key, final long min, final long max,
            final long defaultValue)
    {
        final Object value = args.get(key);
        if (value == null)
        {
            return defaultValue;
        }
        if (!(value instanceof Number n) || n.longValue() < min || n.longValue() > max)
        {
            throw new ToolRejectedException("bad_argument:" + key);
        }
        return n.longValue();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> stringMap(final Map<String, Object> args, final String key, final int maxSize)
    {
        final Object value = args.get(key);
        if (value == null)
        {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map) || map.size() > maxSize)
        {
            throw new ToolRejectedException("bad_argument:" + key);
        }
        return (Map<String, String>) map;
    }
}
