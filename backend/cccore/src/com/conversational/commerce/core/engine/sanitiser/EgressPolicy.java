package com.conversational.commerce.core.engine.sanitiser;

import com.conversational.commerce.core.engine.provider.ModelTask;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Exact fields each task may receive (PL-03). Anything else is dropped. */
public class EgressPolicy
{
    private final Map<ModelTask, Set<String>> allowed = new EnumMap<>(ModelTask.class);

    public EgressPolicy()
    {
        allowed.put(ModelTask.DECIDE, Set.of("message", "capabilities", "terminology", "turns", "shortlistCodes", "language"));
        allowed.put(ModelTask.TRANSLATE, Set.of("text", "sourceLanguage", "targetLanguage"));
        allowed.put(ModelTask.JUDGE, Set.of("need", "constraints", "candidates", "language"));
        allowed.put(ModelTask.EXPLAIN, Set.of("intent", "facts", "comparison", "language"));
        allowed.put(ModelTask.COMPOSE, Set.of("intent", "facts", "slots", "language", "style"));
    }

    public Set<String> allowedFields(final ModelTask task)
    {
        return allowed.getOrDefault(task, Set.of());
    }
}
