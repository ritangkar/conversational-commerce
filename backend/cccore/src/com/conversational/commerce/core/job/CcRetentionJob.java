package com.conversational.commerce.core.job;

import com.conversational.commerce.core.engine.orchestrator.RetentionService;
import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.servicelayer.cronjob.AbstractJobPerformable;
import de.hybris.platform.servicelayer.cronjob.PerformResult;
import jakarta.annotation.Resource;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Purges interest events (default 90 days, D08), feedback and usage counters in bounded batches. Logs counts only. */
public class CcRetentionJob extends AbstractJobPerformable<CronJobModel>
{
    private static final Logger LOG = LogManager.getLogger(CcRetentionJob.class);

    @Resource(name = "ccRetentionService")
    private RetentionService retentionService;

    @Override
    public PerformResult perform(final CronJobModel cronJob)
    {
        final int removed = retentionService.purge(Instant.now());
        LOG.info("Retention job removed {} rows", removed);
        return new PerformResult(CronJobResult.SUCCESS, CronJobStatus.FINISHED);
    }
}
