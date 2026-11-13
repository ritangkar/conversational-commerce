package com.conversational.commerce.core.platform;

import com.conversational.commerce.core.dao.CcConfigDao;
import com.conversational.commerce.core.engine.config.ConfigSource;
import com.conversational.commerce.core.model.CCConfigEntryModel;
import com.conversational.commerce.core.model.CCConfigurationModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Layer 3: non-secret runtime configuration edited in Backoffice. Keys cc.activePack and cc.syntheticDataOnly come from the
 * CCConfiguration row; every other key from enabled CCConfigEntry rows (pack-specific rows win over global rows).
 * ASSUMPTION (AS-03): the configuration row code comes from property cc.configuration.code, default "default".
 */
public class BackofficeConfigSource implements ConfigSource
{
    @Resource(name = "ccConfigDao")
    private CcConfigDao ccConfigDao;

    @Resource
    private ConfigurationService configurationService;

    @Override
    public Optional<String> get(final String key)
    {
        final CCConfigurationModel configuration = ccConfigDao
                .findConfiguration(configurationService.getConfiguration().getString("cc.configuration.code", "default"));
        if (configuration == null || Boolean.FALSE.equals(configuration.getEnabled()))
        {
            return Optional.empty();
        }
        if ("cc.activePack".equals(key))
        {
            return Optional.ofNullable(configuration.getActivePack());
        }
        if ("cc.syntheticDataOnly".equals(key))
        {
            return Optional.of(String.valueOf(Boolean.TRUE.equals(configuration.getSyntheticDataOnly())));
        }
        final List<CCConfigEntryModel> entries = ccConfigDao.findEntries(configuration, configuration.getActivePack());
        CCConfigEntryModel global = null;
        for (final CCConfigEntryModel entry : entries)
        {
            if (key.equals(entry.getKey()))
            {
                if (StringUtils.isNotBlank(entry.getPackCode()))
                {
                    return Optional.ofNullable(entry.getValue(Locale.ENGLISH));
                }
                global = entry;
            }
        }
        return global == null ? Optional.empty() : Optional.ofNullable(global.getValue(Locale.ENGLISH));
    }
}
