package com.conversational.commerce.core.dao;

import java.util.Date;

public interface CcFeedbackDao
{
    int purgeBefore(Date cutoff, int batchSize);
}
