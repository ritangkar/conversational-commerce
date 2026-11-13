package com.conversational.commerce.core.platform;

import com.conversational.commerce.core.dao.CcCoPurchaseDao;
import com.conversational.commerce.core.engine.gateway.CoPurchaseGateway;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

public class PlatformCoPurchaseGateway implements CoPurchaseGateway
{
    @Resource(name = "ccCoPurchaseDao")
    private CcCoPurchaseDao ccCoPurchaseDao;

    @Override
    public Map<String, Integer> pairCounts(final String pack, final String productCode)
    {
        final Date since = Date.from(LocalDate.now().minusDays(365).atStartOfDay(ZoneId.systemDefault()).toInstant());
        return ccCoPurchaseDao.countPairs(productCode, since, 20);
    }
}
