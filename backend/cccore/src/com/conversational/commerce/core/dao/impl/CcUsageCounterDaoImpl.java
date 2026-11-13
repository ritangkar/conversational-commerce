package com.conversational.commerce.core.dao.impl;

import com.conversational.commerce.core.dao.CcUsageCounterDao;
import com.conversational.commerce.core.model.CCUsageCounterModel;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNull;
import jakarta.annotation.Resource;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;

public class CcUsageCounterDaoImpl implements CcUsageCounterDao
{
    private static final String FIND =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + CCUsageCounterModel._TYPECODE + " AS r}"
            + " WHERE {r:" + CCUsageCounterModel.CUSTOMER + "}=?customer"
            + " AND {r:" + CCUsageCounterModel.PERIODKEY + "}=?periodKey";

    private static final String OLDER =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + CCUsageCounterModel._TYPECODE + " AS r}"
            + " WHERE {r:" + CCUsageCounterModel.PERIODKEY + "}<?periodKey";

    @Resource
    private FlexibleSearchService flexibleSearchService;

    @Resource
    private ModelService modelService;

    @Override
    public CCUsageCounterModel find(final CustomerModel customer, final String periodKey)
    {
        validateParameterNotNull(customer, "customer must not be null");
        validateParameterNotNull(periodKey, "periodKey must not be null");
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND);
        query.addQueryParameter("customer", customer);
        query.addQueryParameter("periodKey", periodKey);
        final List<CCUsageCounterModel> result = flexibleSearchService.<CCUsageCounterModel> search(query).getResult();
        return CollectionUtils.isNotEmpty(result) ? result.get(0) : null;
    }

    @Override
    public int purgePeriodsBefore(final String periodKey, final int batchSize)
    {
        validateParameterNotNull(periodKey, "periodKey must not be null");
        final FlexibleSearchQuery query = new FlexibleSearchQuery(OLDER);
        query.addQueryParameter("periodKey", periodKey);
        query.setCount(Math.max(1, batchSize));
        final List<CCUsageCounterModel> batch = flexibleSearchService.<CCUsageCounterModel> search(query).getResult();
        modelService.removeAll(batch);
        return batch.size();
    }
}
