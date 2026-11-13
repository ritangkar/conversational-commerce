package com.conversational.commerce.core.platform;

import com.conversational.commerce.core.dao.CcUsageCounterDao;
import com.conversational.commerce.core.engine.gateway.UsageCounterStore;
import com.conversational.commerce.core.model.CCUsageCounterModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.user.UserService;
import jakarta.annotation.Resource;

/**
 * Usage counters (PL-06). ASSUMPTION (AS-04): the read-modify-write below is not atomic across nodes; the unique index on
 * (customer, periodKey) prevents duplicate rows and a lost update only under-counts by one message. Batch or use an
 * SQL increment if the client needs strict caps.
 */
public class PlatformUsageCounterStore implements UsageCounterStore
{
    @Resource(name = "ccUsageCounterDao")
    private CcUsageCounterDao ccUsageCounterDao;

    @Resource
    private ModelService modelService;

    @Resource
    private UserService userService;

    @Override
    public synchronized long[] add(final String customerId, final String periodKey, final int messages, final int tokens)
    {
        final CustomerModel customer = (CustomerModel) userService.getUserForUID(customerId);
        CCUsageCounterModel counter = ccUsageCounterDao.find(customer, periodKey);
        if (counter == null)
        {
            counter = modelService.create(CCUsageCounterModel.class);
            counter.setCustomer(customer);
            counter.setPeriodKey(periodKey);
            counter.setMessageCount(0);
            counter.setTokenCount(0L);
        }
        counter.setMessageCount(counter.getMessageCount() + messages);
        counter.setTokenCount(counter.getTokenCount() + tokens);
        modelService.save(counter);
        return new long[] { counter.getMessageCount(), counter.getTokenCount() };
    }

    @Override
    public long[] get(final String customerId, final String periodKey)
    {
        final CCUsageCounterModel counter = ccUsageCounterDao.find((CustomerModel) userService.getUserForUID(customerId), periodKey);
        return counter == null ? new long[2] : new long[] { counter.getMessageCount(), counter.getTokenCount() };
    }
}
