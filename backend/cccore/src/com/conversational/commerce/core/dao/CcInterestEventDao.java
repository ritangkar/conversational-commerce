package com.conversational.commerce.core.dao;

import java.util.Date;

public interface CcInterestEventDao
{
    /** Bounded purge (D08 retention). Returns the number of removed rows. */
    int purgeBefore(Date cutoff, int batchSize);
}
