package com.conversational.commerce.core.engine.orchestrator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Prompts and strict JSON schemas are resource files, versioned by file name (e.g. decide.v1). */
public class PromptRepository
{
    private static final String BASE = "/cccore/";
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String prompt(final String id)
    {
        return load("prompts/" + id + ".txt");
    }

    public String schema(final String id)
    {
        return load("schemas/" + id + ".json");
    }

    private String load(final String path)
    {
        return cache.computeIfAbsent(path, p ->
        {
            try (InputStream in = PromptRepository.class.getResourceAsStream(BASE + p))
            {
                if (in == null)
                {
                    throw new IllegalStateException("resource missing: " + p);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            catch (final IOException e)
            {
                throw new IllegalStateException("resource unreadable: " + p, e);
            }
        });
    }
}
