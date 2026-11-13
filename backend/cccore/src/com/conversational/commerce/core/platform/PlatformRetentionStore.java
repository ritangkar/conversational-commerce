package com.conversational.commerce.core.platform;

import com.conversational.commerce.core.dao.CcFeedbackDao;
import com.conversational.commerce.core.dao.CcInterestEventDao;
import com.conversational.commerce.core.dao.CcUsageCounterDao;
import com.conversational.commerce.core.engine.orchestrator.RetentionService.RetentionStore;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

public class PlatformRetentionStore implements RetentionStore
{
    private static final int BATCH = 500;

    @Resource(name = "ccInterestEventDao")
    private CcInterestEventDao ccInterestEventDao;

    @Resource(name = "ccFeedbackDao")
    private CcFeedbackDao ccFeedbackDao;

    @Resource(name = "ccUsageCounterDao")
    private CcUsageCounterDao ccUsageCounterDao;

    @Override
    public int purgeInterestEventsBefore(final Instant cutoff)
    {
        return loop(() -> ccInterestEventDao.purgeBefore(Date.from(cutoff), BATCH));
    }

    @Override
    public int purgeFeedbackBefore(final Instant cutoff)
    {
        return loop(() -> ccFeedbackDao.purgeBefore(Date.from(cutoff), BATCH));
    }

    @Override
    public int purgeUsageCountersBefore(final Instant cutoff)
    {
        final String key = cutoff.atZone(ZoneId.systemDefault()).toLocalDate().toString();
        return loop(() -> ccUsageCounterDao.purgePeriodsBefore(key, BATCH));
    }

    private int loop(final java.util.function.IntSupplier batch)
    {
        int total = 0;
        int n;
        do
        {
            n = batch.getAsInt();
            total += n;
        }
        while (n >= BATCH);
        return total;
    }
}
