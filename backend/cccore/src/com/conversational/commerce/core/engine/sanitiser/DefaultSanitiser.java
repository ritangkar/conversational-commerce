package com.conversational.commerce.core.engine.sanitiser;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import com.conversational.commerce.core.engine.provider.ModelTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultSanitiser implements Sanitiser
{
    public static class SanitiserDisabledException extends RuntimeException
    {
        public SanitiserDisabledException()
        {
            super("sanitiser disabled: external model calls fail closed");
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private CcConfigurationService configurationService;

    @Resource
    private List<SanitisationRule> rules;

    private final FieldPolicy fieldPolicy = new FieldPolicy();
    private final EgressPolicy egressPolicy = new EgressPolicy();
    private final OutboundGuard outboundGuard = new OutboundGuard();

    public void setConfigurationService(final CcConfigurationService configurationService)
    {
        this.configurationService = configurationService;
    }

    public void setRules(final List<SanitisationRule> rules)
    {
        this.rules = rules;
    }

    @Override
    public SanitisedPayload sanitise(final ModelTask task, final Map<String, Object> fields, final TokenMap tokens,
            final Collection<String> forbiddenValues)
    {
        if (!configurationService.isEnabled("PL-03") || !configurationService.getBoolean("cc.sanitiser.enabled", true))
        {
            throw new SanitiserDisabledException();
        }
        final Set<String> allowed = egressPolicy.allowedFields(task);
        final Map<String, Object> out = new LinkedHashMap<>();
        final List<String> dropped = new ArrayList<>();
        for (final Map.Entry<String, Object> e : fields.entrySet())
        {
            if (!allowed.contains(e.getKey()) || fieldPolicy.classify(e.getKey()) == FieldPolicy.Class_.NEVER)
            {
                dropped.add(e.getKey());
                continue;
            }
            out.put(e.getKey(), mask(e.getValue(), tokens));
        }
        final String json;
        try
        {
            json = MAPPER.writeValueAsString(out);
        }
        catch (final JsonProcessingException e)
        {
            throw new IllegalStateException("payload could not be serialised", e);
        }
        outboundGuard.check(json, forbiddenValues);
        return new SanitisedPayload(json, dropped);
    }

    private Object mask(final Object value, final TokenMap tokens)
    {
        if (value instanceof String text)
        {
            String masked = text;
            for (final SanitisationRule rule : rules)
            {
                masked = rule.apply(masked, tokens);
            }
            return masked;
        }
        if (value instanceof Collection<?> collection)
        {
            final List<Object> list = new ArrayList<>();
            collection.forEach(v -> list.add(mask(v, tokens)));
            return list;
        }
        if (value instanceof Map<?, ?> map)
        {
            final Map<String, Object> copy = new LinkedHashMap<>();
            for (final Map.Entry<?, ?> e : map.entrySet())
            {
                final String key = String.valueOf(e.getKey());
                if (fieldPolicy.classify(key) != FieldPolicy.Class_.NEVER)
                {
                    copy.put(key, mask(e.getValue(), tokens));
                }
            }
            return copy;
        }
        return value;
    }
}
