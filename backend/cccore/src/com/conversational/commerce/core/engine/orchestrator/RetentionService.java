package com.conversational.commerce.core.engine.orchestrator;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.Instant;

/** Retention (D08): bounded purge of interest events (default 90 days), feedback and usage counters. Run by CronJobs. */
public class RetentionService
{
    public interface RetentionStore
    {
        int purgeInterestEventsBefore(Instant cutoff);

        int purgeFeedbackBefore(Instant cutoff);

        int purgeUsageCountersBefore(Instant cutoff);
    }

    @Resource
    private RetentionStore retentionStore;

    @Resource
    private CcConfigurationService configurationService;

    @Resource
    private AuditSink auditSink;

    public void setRetentionStore(final RetentionStore s)
    {
        this.retentionStore = s;
    }

    public void setConfigurationService(final CcConfigurationService c)
    {
        this.configurationService = c;
    }

    public void setAuditSink(final AuditSink a)
    {
        this.auditSink = a;
    }

    public int purge(final Instant now)
    {
        final int events = retentionStore.purgeInterestEventsBefore(
                now.minus(Duration.ofDays(configurationService.getInt("privacy.interestEvents.retentionDays", 90))));
        final int feedback = retentionStore
                .purgeFeedbackBefore(now.minus(Duration.ofDays(configurationService.getInt("privacy.feedback.retentionDays", 180))));
        final int usage = retentionStore
                .purgeUsageCountersBefore(now.minus(Duration.ofDays(configurationService.getInt("usage.retentionDays", 35))));
        auditSink.log(AuditEvent.of("retention_run").with("count", events + feedback + usage));
        return events + feedback + usage;
    }
}
