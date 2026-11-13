package com.conversational.commerce.core.platform;

import com.conversational.commerce.core.engine.config.ConfigSource;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import jakarta.annotation.Resource;
import java.util.Optional;

/** Layer 1/2: project.properties, local.properties, environment, Cloud secret (the platform merges them). */
public class PropertiesConfigSource implements ConfigSource
{
    @Resource
    private ConfigurationService configurationService;

    @Override
    public Optional<String> get(final String key)
    {
        if (!configurationService.getConfiguration().containsKey(key))
        {
            return Optional.empty();
        }
        return Optional.ofNullable(configurationService.getConfiguration().getString(key));
    }
}
