package com.conversational.commerce.core.dao.impl;

import com.conversational.commerce.core.dao.CcCoPurchaseDao;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;
import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNull;
import jakarta.annotation.Resource;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/** ASSUMPTION (AS-04): no sample supplied. Counts are aggregated across all customers and carry no customer data. NOT COMPILED here. */
public class CcCoPurchaseDaoImpl implements CcCoPurchaseDao
{
    private static final String PAIRS =
            "SELECT {p2:" + ProductModel.CODE + "}, COUNT(DISTINCT {e2:" + AbstractOrderEntryModel.ORDER + "}) AS cnt"
            + " FROM {" + OrderEntryModel._TYPECODE + " AS e1"
            + " JOIN " + OrderEntryModel._TYPECODE + " AS e2 ON {e1:" + AbstractOrderEntryModel.ORDER + "}={e2:" + AbstractOrderEntryModel.ORDER + "}"
            + " JOIN " + ProductModel._TYPECODE + " AS p1 ON {e1:" + AbstractOrderEntryModel.PRODUCT + "}={p1:pk}"
            + " JOIN " + ProductModel._TYPECODE + " AS p2 ON {e2:" + AbstractOrderEntryModel.PRODUCT + "}={p2:pk}"
            + " JOIN " + OrderModel._TYPECODE + " AS o ON {e1:" + AbstractOrderEntryModel.ORDER + "}={o:pk}}"
            + " WHERE {p1:" + ProductModel.CODE + "}=?code AND {p2:" + ProductModel.CODE + "}<>?code"
            + " AND {o:" + OrderModel.DATE + "}>=?since"
            + " GROUP BY {p2:" + ProductModel.CODE + "} ORDER BY cnt DESC";

    @Resource
    private FlexibleSearchService flexibleSearchService;

    @Override
    public Map<String, Integer> countPairs(final String productCode, final Date since, final int limit)
    {
        validateParameterNotNull(since, "since must not be null");
        final Map<String, Integer> counts = new LinkedHashMap<>();
        if (StringUtils.isBlank(productCode))
        {
            return counts;
        }
        final FlexibleSearchQuery query = new FlexibleSearchQuery(PAIRS);
        query.addQueryParameter("code", productCode);
        query.addQueryParameter("since", since);
        query.setResultClassList(List.of(String.class, Long.class));
        query.setCount(Math.max(1, limit));
        final SearchResult<List<Object>> result = flexibleSearchService.search(query);
        for (final List<Object> row : result.getResult())
        {
            counts.put((String) row.get(0), ((Long) row.get(1)).intValue());
        }
        return counts;
    }
}
