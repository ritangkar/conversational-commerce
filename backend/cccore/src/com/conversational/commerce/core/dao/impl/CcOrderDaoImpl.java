package com.conversational.commerce.core.dao.impl;

import com.conversational.commerce.core.dao.CcOrderDao;
import com.conversational.commerce.core.dao.OrderSearchFilter;
import de.hybris.platform.commerceservices.search.flexiblesearch.PagedFlexibleSearchService;
import de.hybris.platform.commerceservices.search.flexiblesearch.data.SortQueryData;
import de.hybris.platform.commerceservices.search.pagedata.PageableData;
import de.hybris.platform.commerceservices.search.pagedata.SearchPageData;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;
import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNull;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * ASSUMPTION (docs/assumptions.md AS-04): written from the owner's textual DAO convention table and the visible
 * query excerpt only. The full CppDeliveryIssueDaoImpl was not supplied, so buildFilterClause/createSortQuery are
 * the simplest forms that satisfy the stated rules. NOT COMPILED in this repository's CI (needs a SAP platform).
 */
public class CcOrderDaoImpl implements CcOrderDao
{
    private static final String PARAM_CUSTOMER = "customer";
    private static final String DEFAULT_SORT = "byDateDesc";

    private static final String FIND_BY_CODE =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + OrderModel._TYPECODE + " AS r}"
            + " WHERE {r:" + OrderModel.CODE + "}=?code"
            + " AND {r:" + OrderModel.USER + "}=?customer"
            + " AND {r:" + OrderModel.VERSIONID + "} IS NULL";

    private static final String FIND_LATEST =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + OrderModel._TYPECODE + " AS r}"
            + " WHERE {r:" + OrderModel.USER + "}=?customer"
            + " AND {r:" + OrderModel.VERSIONID + "} IS NULL"
            + " ORDER BY {r:" + OrderModel.DATE + "} DESC";

    private static final String FIND_SINCE =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + OrderModel._TYPECODE + " AS r}"
            + " WHERE {r:" + OrderModel.USER + "}=?customer"
            + " AND {r:" + OrderModel.VERSIONID + "} IS NULL"
            + " AND {r:" + OrderModel.DATE + "}>=?since"
            + " ORDER BY {r:" + OrderModel.DATE + "} DESC";

    private static final String FIND_PENDING =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + OrderModel._TYPECODE + " AS r}"
            + " WHERE {r:" + OrderModel.USER + "}=?customer"
            + " AND {r:" + OrderModel.VERSIONID + "} IS NULL"
            + " AND {r:" + OrderModel.STATUS + "} IN (?statuses)"
            + " ORDER BY {r:" + OrderModel.DATE + "} ASC";

    private static final String SEARCH_BASE =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + OrderModel._TYPECODE + " AS r}"
            + " WHERE {r:" + OrderModel.USER + "}=?customer"
            + " AND {r:" + OrderModel.VERSIONID + "} IS NULL";

    private static final List<SortOption> SORTS = List.of(
            new SortOption("byDateDesc", " ORDER BY {r:" + OrderModel.DATE + "} DESC"),
            new SortOption("byDateAsc", " ORDER BY {r:" + OrderModel.DATE + "} ASC"),
            new SortOption("byTotalDesc", " ORDER BY {r:" + OrderModel.TOTALPRICE + "} DESC"));

    @Resource
    private FlexibleSearchService flexibleSearchService;

    @Resource
    private PagedFlexibleSearchService pagedFlexibleSearchService;

    @Override
    public OrderModel findLatest(final CustomerModel customer)
    {
        validateParameterNotNull(customer, "customer must not be null");
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_LATEST);
        query.addQueryParameter(PARAM_CUSTOMER, customer);
        query.setCount(1);
        final SearchResult<OrderModel> result = flexibleSearchService.search(query);
        return CollectionUtils.isNotEmpty(result.getResult()) ? result.getResult().get(0) : null;
    }

    @Override
    public OrderModel findByCode(final String code, final CustomerModel customer)
    {
        validateParameterNotNull(customer, "customer must not be null");
        if (StringUtils.isBlank(code))
        {
            return null;
        }
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_BY_CODE);
        query.addQueryParameter("code", code);
        query.addQueryParameter(PARAM_CUSTOMER, customer);
        final SearchResult<OrderModel> result = flexibleSearchService.search(query);
        return CollectionUtils.isNotEmpty(result.getResult()) ? result.getResult().get(0) : null;
    }

    @Override
    public SearchPageData<OrderModel> searchOrders(final CustomerModel customer, final OrderSearchFilter filter,
            final String sortCode, final PageableData pageableData)
    {
        validateParameterNotNull(customer, "customer must not be null");
        validateParameterNotNull(pageableData, "pageableData must not be null");
        final Map<String, Object> params = new HashMap<>();
        params.put(PARAM_CUSTOMER, customer);
        final StringBuilder where = new StringBuilder();
        buildFilterClause(params, filter, where);
        final List<SortQueryData> sortQueries = new ArrayList<>();
        for (final SortOption option : SORTS)
        {
            sortQueries.add(createSortQuery(option.code(), SEARCH_BASE + where + option.orderBy()));
        }
        final String selected = sortCode != null && SORTS.stream().anyMatch(s -> s.code().equals(sortCode)) ? sortCode : DEFAULT_SORT;
        return pagedFlexibleSearchService.search(sortQueries, selected, params, pageableData);
    }

    @Override
    public List<OrderModel> findPending(final CustomerModel customer, final List<String> statusCodes)
    {
        validateParameterNotNull(customer, "customer must not be null");
        if (CollectionUtils.isEmpty(statusCodes))
        {
            return Collections.emptyList();
        }
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_PENDING);
        query.addQueryParameter(PARAM_CUSTOMER, customer);
        query.addQueryParameter("statuses", statusCodes);
        return flexibleSearchService.<OrderModel> search(query).getResult();
    }

    @Override
    public List<OrderModel> findSince(final CustomerModel customer, final Date since)
    {
        validateParameterNotNull(customer, "customer must not be null");
        validateParameterNotNull(since, "since must not be null");
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_SINCE);
        query.addQueryParameter(PARAM_CUSTOMER, customer);
        query.addQueryParameter("since", since);
        return flexibleSearchService.<OrderModel> search(query).getResult();
    }

    /** Appends AND clauses for the non-blank filters and fills the named parameters. toDate is exclusive: the next day. */
    protected void buildFilterClause(final Map<String, Object> params, final OrderSearchFilter filters, final StringBuilder where)
    {
        if (filters == null)
        {
            return;
        }
        if (CollectionUtils.isNotEmpty(filters.getStatusCodes()))
        {
            where.append(" AND {r:").append(OrderModel.STATUS).append("} IN (?statuses)");
            params.put("statuses", filters.getStatusCodes());
        }
        if (filters.getMinTotal() != null)
        {
            where.append(" AND {r:").append(OrderModel.TOTALPRICE).append("}>=?minTotal");
            params.put("minTotal", filters.getMinTotal());
        }
        if (filters.getMaxTotal() != null)
        {
            where.append(" AND {r:").append(OrderModel.TOTALPRICE).append("}<=?maxTotal");
            params.put("maxTotal", filters.getMaxTotal());
        }
        if (filters.getFromDate() != null)
        {
            where.append(" AND {r:").append(OrderModel.DATE).append("}>=?fromDate");
            params.put("fromDate", truncate(filters.getFromDate(), 0));
        }
        if (filters.getToDate() != null)
        {
            where.append(" AND {r:").append(OrderModel.DATE).append("}<?toDate");
            params.put("toDate", truncate(filters.getToDate(), 1));
        }
    }

    protected SortQueryData createSortQuery(final String sortCode, final String query)
    {
        final SortQueryData sortQuery = new SortQueryData();
        sortQuery.setSortCode(sortCode);
        sortQuery.setQuery(query);
        return sortQuery;
    }

    private Date truncate(final Date date, final int plusDays)
    {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_MONTH, plusDays);
        return calendar.getTime();
    }

    private record SortOption(String code, String orderBy)
    {
    }
}
