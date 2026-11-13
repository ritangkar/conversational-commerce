package com.conversational.commerce.core.dao;

import de.hybris.platform.core.model.product.ProductModel;
import java.util.List;
import java.util.Map;

public interface CcProductSearchDao
{
    /** Keywords become LIKE parameters; attribute keys must come from the pack whitelist (checked by the caller). */
    List<ProductModel> findCandidates(List<String> keywords, List<String> categoryCodes, Map<String, String> attributeFilters,
            int limit);

    ProductModel findByCode(String code);
}
