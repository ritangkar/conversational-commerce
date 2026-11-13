package com.conversational.commerce.core.dao.impl;

import com.conversational.commerce.core.dao.CcProductSearchDao;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;
import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * ASSUMPTION (AS-04): the owner supplied no product DAO. Candidate retrieval by name/description keywords within the pack's
 * root categories. S6 decides whether a Solr CandidateProvider replaces this for large catalogues. NOT COMPILED here.
 * Attribute filters are applied by the gateway on classification values; this DAO never concatenates request text.
 */
public class CcProductSearchDaoImpl implements CcProductSearchDao
{
    private static final String FIND_BY_CODE =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + ProductModel._TYPECODE + " AS r}"
            + " WHERE {r:" + ProductModel.CODE + "}=?code";

    private static final String CANDIDATES_HEAD =
            "SELECT DISTINCT {r:" + ItemModel.PK + "} FROM {" + ProductModel._TYPECODE + " AS r"
            + " JOIN CategoryProductRelation AS cp ON {cp:target}={r:" + ItemModel.PK + "}}"
            + " WHERE {cp:source} IN (?categories)";

    @Resource
    private FlexibleSearchService flexibleSearchService;

    @Override
    public ProductModel findByCode(final String code)
    {
        if (StringUtils.isBlank(code))
        {
            return null;
        }
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_BY_CODE);
        query.addQueryParameter("code", code);
        final SearchResult<ProductModel> result = flexibleSearchService.search(query);
        return CollectionUtils.isNotEmpty(result.getResult()) ? result.getResult().get(0) : null;
    }

    @Override
    public List<ProductModel> findCandidates(final List<String> keywords, final List<String> categoryCodes,
            final Map<String, String> attributeFilters, final int limit)
    {
        if (CollectionUtils.isEmpty(categoryCodes))
        {
            return Collections.emptyList();
        }
        final Map<String, Object> params = new HashMap<>();
        params.put("categories", categoryCodes); // resolved to category PKs by the gateway before this call
        final StringBuilder q = new StringBuilder(CANDIDATES_HEAD);
        int i = 0;
        if (CollectionUtils.isNotEmpty(keywords))
        {
            q.append(" AND (");
            for (final String keyword : keywords)
            {
                if (StringUtils.isBlank(keyword))
                {
                    continue;
                }
                if (i > 0)
                {
                    q.append(" OR ");
                }
                final String name = "kw" + i;
                q.append("LOWER({r:").append(ProductModel.NAME).append("}) LIKE ?").append(name)
                        .append(" OR LOWER({r:").append(ProductModel.DESCRIPTION).append("}) LIKE ?").append(name);
                params.put(name, "%" + keyword.toLowerCase(Locale.ROOT) + "%");
                i++;
            }
            q.append(")");
        }
        final FlexibleSearchQuery query = new FlexibleSearchQuery(q.toString());
        query.addQueryParameters(params);
        query.setCount(Math.min(Math.max(limit, 1), 200));
        return flexibleSearchService.<ProductModel> search(query).getResult();
    }
}
