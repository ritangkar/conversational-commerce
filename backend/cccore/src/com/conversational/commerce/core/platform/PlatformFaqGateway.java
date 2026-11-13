package com.conversational.commerce.core.platform;

import com.conversational.commerce.core.dao.CcConfigDao;
import com.conversational.commerce.core.engine.gateway.FaqGateway;
import com.conversational.commerce.core.engine.model.Domain.Faq;
import com.conversational.commerce.core.model.CCConfigEntryModel;
import com.conversational.commerce.core.model.CCConfigurationModel;
import com.conversational.commerce.core.enums.CCConfigEntryType;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Locale;

/** Approved FAQ content (D16, KN-01): CCConfigEntry rows of type FAQ; value format "question::answer" (ASSUMPTION AS-03). */
public class PlatformFaqGateway implements FaqGateway
{
    @Resource(name = "ccConfigDao")
    private CcConfigDao ccConfigDao;

    @Resource
    private ConfigurationService configurationService;

    @Override
    public List<Faq> approvedFaqs(final String pack)
    {
        final CCConfigurationModel configuration = ccConfigDao
                .findConfiguration(configurationService.getConfiguration().getString("cc.configuration.code", "default"));
        if (configuration == null)
        {
            return List.of();
        }
        return ccConfigDao.findEntries(configuration, pack).stream().filter(e -> e.getEntryType() == CCConfigEntryType.FAQ)
                .map(e -> toFaq(pack, e)).filter(f -> f != null).toList();
    }

    private Faq toFaq(final String pack, final CCConfigEntryModel entry)
    {
        final String value = entry.getValue(Locale.ENGLISH);
        final String[] parts = value == null ? new String[0] : value.split("::", 2);
        return parts.length == 2 ? new Faq(entry.getKey(), pack, parts[0].trim(), parts[1].trim()) : null;
    }
}
