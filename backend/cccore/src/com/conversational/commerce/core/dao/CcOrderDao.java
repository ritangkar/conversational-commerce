package com.conversational.commerce.core.dao;

import de.hybris.platform.commerceservices.search.pagedata.PageableData;
import de.hybris.platform.commerceservices.search.pagedata.SearchPageData;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import java.util.Date;
import java.util.List;

/** Order lookups for the chat tools. Every method takes the customer and filters on it in the query. */
public interface CcOrderDao
{
    // FUTURE SCOPE: unit-level order access (D33): widen the customer filter to the customer's B2B unit here.

    OrderModel findLatest(CustomerModel customer);

    OrderModel findByCode(String code, CustomerModel customer);

    SearchPageData<OrderModel> searchOrders(CustomerModel customer, OrderSearchFilter filter, String sortCode,
            PageableData pageableData);

    List<OrderModel> findPending(CustomerModel customer, List<String> statusCodes);

    List<OrderModel> findSince(CustomerModel customer, Date since);
}
