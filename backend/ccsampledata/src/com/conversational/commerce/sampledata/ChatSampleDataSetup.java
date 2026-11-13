package com.conversational.commerce.sampledata;

import com.conversational.commerce.core.dao.ChatDao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hybris.platform.commerceservices.setup.AbstractSystemSetup;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.initialization.SystemSetup;
import de.hybris.platform.core.initialization.SystemSetup.Process;
import de.hybris.platform.core.initialization.SystemSetup.Type;
import de.hybris.platform.core.initialization.SystemSetupContext;
import de.hybris.platform.core.initialization.SystemSetupParameter;
import de.hybris.platform.core.initialization.SystemSetupParameterMethod;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.order.OrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.ordersplitting.model.ConsignmentModel;
import de.hybris.platform.product.ProductService;
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
 * Sample data only (not production code): loads the essential data (configuration row and toggles), the generated ImpEx per pack
 * (option ccsampledata.pack.&lt;code&gt;) and creates the demo orders with RELATIVE dates from the generated order plans, so a
 * "30-day rhythm" is true on demo day (ImpEx cannot compute relative dates reliably, E17). Orders load only on sites marked
 * syntheticDataOnly (A2).
 *
 * <p>ASSUMPTION AS-10: follows the platform's AbstractSystemSetup pattern and creates orders through ModelService, skipping
 * checkout. NOT COMPILED in this repository: it needs a SAP Commerce platform.
 */
@SystemSetup(extension = "ccsampledata")
public class ChatSampleDataSetup extends AbstractSystemSetup
{
    private static final Logger LOG = LogManager.getLogger(ChatSampleDataSetup.class);
    private static final List<String> PACKS = List.of("tyres", "paints", "electrical", "cement", "plywood");
    private static final String IMPORT_ROOT = "/ccsampledata/import/";
    private static final String ORDER_CODE_PREFIX = "CCDEMO";

    @Resource
    private ModelService modelService;

    @Resource
    private UserService userService;

    @Resource
    private CommonI18NService commonI18NService;

    @Resource
    private ProductService productService;

    @Resource(name = "ccChatDao")
    private ChatDao chatDao;

    @SystemSetup(type = Type.ESSENTIAL, process = Process.ALL)
    public void createEssentialData(final SystemSetupContext context)
    {
        importImpexFile(context, IMPORT_ROOT + "essential/essentialdata-cc-config.impex");
    }

    @SystemSetup(type = Type.PROJECT, process = Process.ALL)
    public void createProjectData(final SystemSetupContext context)
    {
        final List<String> selected = new ArrayList<>();
        for (final String pack : PACKS)
        {
            if (getBooleanSystemSetupParameter(context, "ccsampledata.pack." + pack))
            {
                selected.add(pack);
                importImpexFile(context, IMPORT_ROOT + "projectdata/pack-" + pack + ".impex");
            }
        }
        if (selected.isEmpty())
        {
            return;
        }
        importImpexFile(context, IMPORT_ROOT + "projectdata/all-packs-dealer.impex");
        // Orders need the products and customers from the ImpEx above, so they are created in a second pass.
        final String flag = chatDao.findBackofficeValue("cc.syntheticDataOnly");
        final boolean synthetic = flag == null || "true".equalsIgnoreCase(flag);
        for (final String pack : selected)
        {
            loadOrders(IMPORT_ROOT + "orders/" + pack + ".json", synthetic);
        }
        loadOrders(IMPORT_ROOT + "orders/all-packs.json", synthetic);
    }

    @SystemSetupParameterMethod
    @Override
    public List<SystemSetupParameter> getInitializationOptions()
    {
        final List<SystemSetupParameter> params = new ArrayList<>();
        for (final String pack : PACKS)
        {
            final SystemSetupParameter p = new SystemSetupParameter("ccsampledata.pack." + pack);
            p.setLabel("Load demo pack: " + pack + " (synthetic data only)");
            p.addValue("true", true);
            p.addValue("false", false);
            params.add(p);
        }
        return params;
    }

    private void loadOrders(final String resourcePath, final boolean syntheticDataOnly)
    {
        if (!syntheticDataOnly)
        {
            throw new IllegalStateException("sample orders are loaded only into sites marked syntheticDataOnly");
        }
        final LocalDate today = LocalDate.now();
        final CurrencyModel currency = commonI18NService.getCurrency("INR");
        int created = 0;
        try (InputStream in = ChatSampleDataSetup.class.getResourceAsStream(resourcePath))
        {
            final JsonNode plan = new ObjectMapper().readTree(in);
            int index = 0;
            for (final JsonNode entry : plan)
            {
                final LocalDate date = entry.get("daysAgo").isObject()
                        ? today.withDayOfMonth(1).minusMonths(1).plusDays(entry.get("daysAgo").get("lastMonthDay").asInt())
                        : today.minusDays(entry.get("daysAgo").asInt());
                final String code = ORDER_CODE_PREFIX + Math.abs((resourcePath + index++).hashCode());
                final OrderModel order = modelService.create(OrderModel.class);
                order.setCode(code);
                order.setUser((CustomerModel) userService.getUserForUID(entry.get("dealer").asText()));
                order.setCurrency(currency);
                order.setDate(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
                order.setNet(Boolean.TRUE);
                order.setTotalPrice(entry.get("totalMinor").asLong() / 100.0);
                order.setStatus(OrderStatus.valueOf(mapStatus(entry.get("status").asText())));
                final List<OrderEntryModel> entries = new ArrayList<>();
                int line = 0;
                for (final JsonNode l : entry.get("lines"))
                {
                    final ProductModel product = productService.getProductForCode(l.get("product").asText());
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
        }
        catch (final IOException e)
        {
            LOG.warn("Sample orders could not be read: {}", resourcePath);
        }
        LOG.info("Loaded {} sample orders from {}", created, resourcePath);
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
