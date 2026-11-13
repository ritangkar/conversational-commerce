package com.conversational.commerce.core.dao.impl;

import com.conversational.commerce.core.dao.CcConfigDao;
import com.conversational.commerce.core.model.CCConfigEntryModel;
import com.conversational.commerce.core.model.CCConfigurationModel;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;
import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNull;
import jakarta.annotation.Resource;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class CcConfigDaoImpl implements CcConfigDao
{
    private static final String FIND_CONFIGURATION =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + CCConfigurationModel._TYPECODE + " AS r}"
            + " WHERE {r:" + CCConfigurationModel.CODE + "}=?code";

    private static final String FIND_ENTRIES =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + CCConfigEntryModel._TYPECODE + " AS r}"
            + " WHERE {r:" + CCConfigEntryModel.CONFIGURATION + "}=?configuration"
            + " AND {r:" + CCConfigEntryModel.ENABLED + "}=?enabled"
            + " AND ({r:" + CCConfigEntryModel.PACKCODE + "}=?packCode OR {r:" + CCConfigEntryModel.PACKCODE + "} IS NULL"
            + " OR {r:" + CCConfigEntryModel.PACKCODE + "}='')"
            + " ORDER BY {r:" + CCConfigEntryModel.SORTORDER + "} ASC";

    @Resource
    private FlexibleSearchService flexibleSearchService;

    @Override
    public CCConfigurationModel findConfiguration(final String code)
    {
        if (StringUtils.isBlank(code))
        {
            return null;
        }
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_CONFIGURATION);
        query.addQueryParameter("code", code);
        final SearchResult<CCConfigurationModel> result = flexibleSearchService.search(query);
        return CollectionUtils.isNotEmpty(result.getResult()) ? result.getResult().get(0) : null;
    }

    @Override
    public List<CCConfigEntryModel> findEntries(final CCConfigurationModel configuration, final String packCode)
    {
        validateParameterNotNull(configuration, "configuration must not be null");
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_ENTRIES);
        query.addQueryParameter("configuration", configuration);
        query.addQueryParameter("enabled", Boolean.TRUE);
        query.addQueryParameter("packCode", StringUtils.defaultString(packCode));
        return flexibleSearchService.<CCConfigEntryModel> search(query).getResult();
    }
}
