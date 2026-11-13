package com.conversational.commerce.core.dao;

import java.util.Date;
import java.util.Map;

public interface CcCoPurchaseDao
{
    /** Anonymous aggregated counts: how many orders contained both the product and another product since the date. */
    Map<String, Integer> countPairs(String productCode, Date since, int limit);
}
