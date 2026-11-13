package com.conversational.commerce.core.engine.orchestrator;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import com.conversational.commerce.core.engine.gateway.UsageCounterStore;
import jakarta.annotation.Resource;
import java.time.LocalDate;

/** Per-customer usage caps (PL-06). Runs before the decision call; over the cap means no model call. */
public class UsageCapService
{
    @Resource
    private UsageCounterStore usageCounterStore;

    @Resource
    private CcConfigurationService configurationService;

    public void setUsageCounterStore(final UsageCounterStore s)
    {
        this.usageCounterStore = s;
    }

    public void setConfigurationService(final CcConfigurationService c)
    {
        this.configurationService = c;
    }

    /** @return true if the turn may proceed (and counts it), false if the customer is over the cap. */
    public boolean checkAndIncrement(final String customerId, final LocalDate today)
    {
        if (!configurationService.isEnabled("PL-06"))
        {
            return true;
        }
        final String period = periodKey(today);
        final long[] current = usageCounterStore.get(customerId, period);
        final int messageCap = configurationService.getInt("usage.cap.messages", 200);
        final int tokenCap = configurationService.getInt("usage.cap.tokens", 200_000);
        if (current[0] >= messageCap || current[1] >= tokenCap)
        {
            return false;
        }
        usageCounterStore.add(customerId, period, 1, 0);
        return true;
    }

    public void recordTokens(final String customerId, final LocalDate today, final int tokens)
    {
        usageCounterStore.add(customerId, periodKey(today), 0, tokens);
    }

    private String periodKey(final LocalDate today)
    {
        return "MONTH".equalsIgnoreCase(configurationService.getString("usage.cap.period", "DAY")) ? today.toString().substring(0, 7)
                : today.toString();
    }
}
