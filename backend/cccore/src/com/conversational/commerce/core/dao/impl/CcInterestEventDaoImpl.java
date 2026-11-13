package com.conversational.commerce.core.dao.impl;

import com.conversational.commerce.core.dao.CcInterestEventDao;
import com.conversational.commerce.core.model.CCInterestEventModel;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNull;
import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;

public class CcInterestEventDaoImpl implements CcInterestEventDao
{
    private static final String OLDER_THAN =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + CCInterestEventModel._TYPECODE + " AS r}"
            + " WHERE {r:" + CCInterestEventModel.OCCURREDAT + "}<?cutoff";

    @Resource
    private FlexibleSearchService flexibleSearchService;

    @Resource
    private ModelService modelService;

    @Override
    public int purgeBefore(final Date cutoff, final int batchSize)
    {
        validateParameterNotNull(cutoff, "cutoff must not be null");
        final FlexibleSearchQuery query = new FlexibleSearchQuery(OLDER_THAN);
        query.addQueryParameter("cutoff", cutoff);
        query.setCount(Math.max(1, batchSize));
        final List<CCInterestEventModel> batch = flexibleSearchService.<CCInterestEventModel> search(query).getResult();
        modelService.removeAll(batch);
        return batch.size();
    }
}
