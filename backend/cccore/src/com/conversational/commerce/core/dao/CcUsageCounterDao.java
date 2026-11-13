package com.conversational.commerce.core.dao;

import com.conversational.commerce.core.model.CCUsageCounterModel;
import de.hybris.platform.core.model.user.CustomerModel;

public interface CcUsageCounterDao
{
    CCUsageCounterModel find(CustomerModel customer, String periodKey);

    /** Removes counters whose period key sorts before the given key (keys are ISO dates or months). */
    int purgePeriodsBefore(String periodKey, int batchSize);
}
