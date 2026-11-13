package com.conversational.commerce.sampledata.setup;

import com.conversational.commerce.core.dao.CcProductSearchDao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.order.OrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.ordersplitting.model.ConsignmentModel;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.user.UserService;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Creates sample orders from the generated order plans with RELATIVE dates, so a "30-day rhythm" is true on demo day
 * (ImpEx cannot compute relative dates reliably, E17). Refuses to run unless the site is marked syntheticDataOnly (A2).
 * ASSUMPTION AS-10: order creation through ModelService is the simplest form; orders skip the checkout process. NOT COMPILED here.
 */
public class SampleOrderLoader
{
    private static final Logger LOG = LogManager.getLogger(SampleOrderLoader.class);
    private static final String ORDER_CODE_PREFIX = "CCDEMO";

    @Resource
    private ModelService modelService;

    @Resource
    private UserService userService;

    @Resource
    private CommonI18NService commonI18NService;

    @Resource(name = "ccProductSearchDao")
    private CcProductSearchDao ccProductSearchDao;

    public int load(final String resourcePath, final LocalDate today, final boolean syntheticDataOnly) throws IOException
    {
        if (!syntheticDataOnly)
        {
            throw new IllegalStateException("sample orders are loaded only into sites marked syntheticDataOnly");
        }
        final JsonNode plan;
        try (InputStream in = SampleOrderLoader.class.getResourceAsStream(resourcePath))
        {
            if (in == null)
            {
                throw new IllegalArgumentException("order plan not found: " + resourcePath);
            }
            plan = new ObjectMapper().readTree(in);
        }
        final CurrencyModel currency = commonI18NService.getCurrency("INR");
        int created = 0;
        int index = 0;
        for (final JsonNode entry : plan)
        {
            final String code = ORDER_CODE_PREFIX + Math.abs((resourcePath + index++).hashCode());
            final CustomerModel customer = (CustomerModel) userService.getUserForUID(entry.get("dealer").asText());
            final LocalDate date = entry.get("daysAgo").isObject()
                    ? today.withDayOfMonth(1).minusMonths(1).plusDays(entry.get("daysAgo").get("lastMonthDay").asInt())
                    : today.minusDays(entry.get("daysAgo").asInt());
            final OrderModel order = modelService.create(OrderModel.class);
            order.setCode(code);
            order.setUser(customer);
            order.setCurrency(currency);
            order.setDate(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            order.setNet(Boolean.TRUE);
            order.setTotalPrice(entry.get("totalMinor").asLong() / 100.0);
            order.setStatus(OrderStatus.valueOf(mapStatus(entry.get("status").asText())));
            final List<OrderEntryModel> entries = new ArrayList<>();
            int line = 0;
            for (final JsonNode l : entry.get("lines"))
            {
                final ProductModel product = ccProductSearchDao.findByCode(l.get("product").asText());
                final OrderEntryModel e = modelService.create(OrderEntryModel.class);
                e.setOrder(order);
                e.setProduct(product);
                e.setQuantity(l.get("quantity").asLong());
                e.setUnit(product.getUnit());
                e.setEntryNumber(line++);
                entries.add(e);
            }
            order.setEntries(entries);
            modelService.saveAll(order);
            modelService.saveAll(entries);
            if (!entry.get("tracking").isNull())
            {
                final ConsignmentModel consignment = modelService.create(ConsignmentModel.class);
                consignment.setCode(code + "-C1");
                consignment.setTrackingID(entry.get("tracking").asText());
                consignment.setOrder(order);
                modelService.save(consignment);
            }
            created++;
        }
        LOG.info("Loaded {} sample orders from {}", created, resourcePath);
        return created;
    }

    private String mapStatus(final String planStatus)
    {
        return switch (planStatus)
        {
            case "DELIVERED" -> "COMPLETED";
            case "SHIPPED" -> "READY"; // ASSUMPTION: no SHIPPED value in the OOTB OrderStatus; READY is closest to dispatched
            case "PROCESSING" -> "PROCESSING";
            default -> "CREATED";
        };
    }
}
