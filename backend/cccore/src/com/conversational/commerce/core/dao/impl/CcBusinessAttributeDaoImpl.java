package com.conversational.commerce.core.dao.impl;

import com.conversational.commerce.core.dao.CcBusinessAttributeDao;
import com.conversational.commerce.core.model.CCProductBusinessAttributeModel;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import jakarta.annotation.Resource;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class CcBusinessAttributeDaoImpl implements CcBusinessAttributeDao
{
    private static final String FIND =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + CCProductBusinessAttributeModel._TYPECODE + " AS r"
            + " JOIN " + ProductModel._TYPECODE + " AS p ON {r:" + CCProductBusinessAttributeModel.PRODUCT + "}={p:" + ItemModel.PK + "}}"
            + " WHERE {p:" + ProductModel.CODE + "}=?code";

    @Resource
    private FlexibleSearchService flexibleSearchService;

    @Override
    public CCProductBusinessAttributeModel findByProductCode(final String productCode)
    {
        if (StringUtils.isBlank(productCode))
        {
            return null;
        }
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND);
        query.addQueryParameter("code", productCode);
        final List<CCProductBusinessAttributeModel> result = flexibleSearchService
                .<CCProductBusinessAttributeModel> search(query).getResult();
        return CollectionUtils.isNotEmpty(result) ? result.get(0) : null;
    }
}
