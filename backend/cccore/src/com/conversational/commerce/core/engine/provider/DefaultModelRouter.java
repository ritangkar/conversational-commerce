package com.conversational.commerce.core.engine.provider;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DefaultModelRouter implements ModelRouter
{
    private static final Logger LOG = LogManager.getLogger(DefaultModelRouter.class);

    @Resource
    private CcConfigurationService configurationService;

    @Resource
    private List<ModelProviderAdapter> adapters;

    public void setConfigurationService(final CcConfigurationService configurationService)
    {
        this.configurationService = configurationService;
    }

    public void setAdapters(final List<ModelProviderAdapter> adapters)
    {
        this.adapters = adapters;
    }

    @Override
    public ModelResult call(final ModelRequest request)
    {
        final String prefix = "route." + request.task().name().toLowerCase() + ".";
        final List<String[]> chain = new ArrayList<>();
        final String provider = configurationService.getString(prefix + "provider", "");
        if (StringUtils.isNotBlank(provider))
        {
            chain.add(new String[] { provider, configurationService.getString(prefix + "model", "") });
        }
        for (final String entry : configurationService.getString(prefix + "fallback", "").split(","))
        {
            final String[] parts = entry.trim().split(":", 2);
            if (parts.length == 2 && StringUtils.isNotBlank(parts[0]))
            {
                chain.add(new String[] { parts[0].trim(), parts[1].trim() });
            }
        }
        for (final String[] step : chain)
        {
            final ModelProviderAdapter adapter = find(step[0]);
            if (adapter == null || !adapter.isAvailable())
            {
                continue;
            }
            if ("free".equalsIgnoreCase(adapter.tier()) && !configurationService.syntheticDataOnly())
            {
                LOG.warn("Provider {} skipped: free tier is refused outside synthetic-data sites", adapter.providerCode());
                continue;
            }
            try
            {
                final ModelResult result = adapter.call(step[1], request);
                if (result.json() != null && !result.json().isMissingNode())
                {
                    return result;
                }
            }
            catch (final RuntimeException e)
            {
                LOG.warn("Provider {} failed for task {}: {}", adapter.providerCode(), request.task(), e.getClass().getSimpleName());
            }
        }
        throw new ModelUnavailableException("no provider available for task " + request.task());
    }

    private ModelProviderAdapter find(final String code)
    {
        for (final ModelProviderAdapter adapter : adapters)
        {
            if (adapter.providerCode().equalsIgnoreCase(code))
            {
                return adapter;
            }
        }
        return null;
    }
}
