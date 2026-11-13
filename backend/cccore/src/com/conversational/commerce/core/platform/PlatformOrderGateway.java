package com.conversational.commerce.core.platform;

import com.conversational.commerce.core.dao.CcOrderDao;
import com.conversational.commerce.core.engine.gateway.OrderGateway;
import com.conversational.commerce.core.engine.model.Domain.Order;
import com.conversational.commerce.core.engine.model.Domain.OrderLine;
import com.conversational.commerce.core.engine.model.Domain.OrderStatus;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.ordersplitting.model.ConsignmentModel;
import de.hybris.platform.servicelayer.user.UserService;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/** Customer-scoped order access. The customer id is the uid from the Spring Security context (via the facade). */
public class PlatformOrderGateway implements OrderGateway
{
    // FUTURE SCOPE: unit-level order access (D33).

    @Resource(name = "ccOrderDao")
    private CcOrderDao ccOrderDao;

    @Resource
    private UserService userService;

    @Override
    public List<Order> ordersOf(final String customerId)
    {
        final CustomerModel customer = customer(customerId);
        final Date since = Date.from(java.time.LocalDate.now().minusDays(730).atStartOfDay(ZoneId.systemDefault()).toInstant());
        return ccOrderDao.findSince(customer, since).stream().map(this::convert).toList();
    }

    @Override
    public Optional<Order> orderByCode(final String customerId, final String code)
    {
        return Optional.ofNullable(ccOrderDao.findByCode(code, customer(customerId))).map(this::convert);
    }

    private CustomerModel customer(final String customerId)
    {
        return (CustomerModel) userService.getUserForUID(customerId);
    }

    private Order convert(final OrderModel model)
    {
        final List<OrderLine> lines = model.getEntries().stream()
                .map((AbstractOrderEntryModel e) -> new OrderLine(e.getProduct().getCode(), e.getQuantity().intValue())).toList();
        final BigDecimal total = BigDecimal.valueOf(model.getTotalPrice() == null ? 0 : model.getTotalPrice());
        String tracking = null;
        if (model.getConsignments() != null)
        {
            for (final ConsignmentModel c : model.getConsignments())
            {
                if (StringUtils.isNotBlank(c.getTrackingID()))
                {
                    tracking = c.getTrackingID();
                    break;
                }
            }
        }
        // FUTURE SCOPE: carrier tracking (D10): only the Commerce-held reference is returned here.
        return new Order(model.getCode(), model.getUser().getUid(), model.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                map(model.getStatus() == null ? null : model.getStatus().getCode()), total.movePointRight(2).longValue(),
                model.getCurrency() == null ? "" : model.getCurrency().getIsocode(), lines, tracking);
    }

    private OrderStatus map(final String code)
    {
        if (code == null)
        {
            return OrderStatus.PENDING;
        }
        return switch (code)
        {
            case "COMPLETED", "DELIVERED" -> OrderStatus.DELIVERED;
            case "CANCELLED", "CANCELLING" -> OrderStatus.CANCELLED;
            case "SHIPPED", "DISPATCHED" -> OrderStatus.SHIPPED;
            case "CREATED", "PENDING_APPROVAL", "PENDING_APPROVAL_FROM_MERCHANT" -> OrderStatus.PENDING;
            default -> OrderStatus.PROCESSING; // ASSUMPTION: unknown platform statuses are treated as in progress
        };
    }
}
