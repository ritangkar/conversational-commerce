package com.conversational.commerce.core.engine.gateway;

import com.conversational.commerce.core.engine.model.Domain.Order;
import java.util.List;
import java.util.Optional;

public interface OrderGateway
{
    // FUTURE SCOPE: unit-level order access (D33) and unit-level replenishment (D34): widen from the customer to the B2B unit here.
    List<Order> ordersOf(String customerId);

    Optional<Order> orderByCode(String customerId, String code);
}
