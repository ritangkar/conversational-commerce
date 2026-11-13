package com.conversational.commerce.core.dao.impl;

import com.conversational.commerce.core.dao.ChatDao;
import com.conversational.commerce.core.enums.CCEventType;
import com.conversational.commerce.core.enums.CCFeedbackRating;
import com.conversational.commerce.core.model.CCConfigEntryModel;
import com.conversational.commerce.core.model.CCConfigurationModel;
import com.conversational.commerce.core.model.CCFeedbackModel;
import com.conversational.commerce.core.model.CCInterestEventModel;
import com.conversational.commerce.core.model.CCProductBusinessAttributeModel;
import com.conversational.commerce.core.model.CCUsageCounterModel;
import de.hybris.platform.catalog.enums.ArticleApprovalStatus;
import de.hybris.platform.catalog.enums.ProductReferenceTypeEnum;
import de.hybris.platform.catalog.model.ProductReferenceModel;
import de.hybris.platform.classification.ClassificationService;
import de.hybris.platform.classification.features.Feature;
import de.hybris.platform.commerceservices.price.CommercePriceService;
import de.hybris.platform.commerceservices.search.flexiblesearch.PagedFlexibleSearchService;
import de.hybris.platform.commerceservices.search.flexiblesearch.data.SortQueryData;
import de.hybris.platform.commerceservices.search.pagedata.PageableData;
import de.hybris.platform.commerceservices.search.pagedata.SearchPageData;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.jalo.order.price.PriceInformation;
import de.hybris.platform.ordersplitting.model.ConsignmentModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;
import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNull;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.store.services.BaseStoreService;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * All data access of the chat feature in one DAO. Queries are {@code private static final String} constants built from model
 * constants with {r:...} aliases; every value is a named parameter; nothing from a request is concatenated into query text.
 *
 * <p>ASSUMPTION (docs/assumptions.md AS-04, AS-20): written from the owner's textual DAO convention and the visible query excerpt
 * (the full DAO sample was not supplied), returns value types instead of models, and also hosts the few writes of the feature.
 * NOT COMPILED in this repository: it needs a SAP Commerce platform. Model constants and imports are UNVERIFIED.
 */
public class ChatDaoImpl implements ChatDao
{
    private static final String DEFAULT_SORT = "byDateDesc";
    private static final int MAX_LIMIT = 1000;

    // ---- order queries
    private static final String FIND_ORDER_BY_CODE =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + OrderModel._TYPECODE + " AS r}"
            + " WHERE {r:" + OrderModel.CODE + "}=?code"
            + " AND {r:" + OrderModel.USER + "}=?customer"
            + " AND {r:" + OrderModel.VERSIONID + "} IS NULL";

    private static final String SEARCH_ORDERS_BASE =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + OrderModel._TYPECODE + " AS r}"
            + " WHERE {r:" + OrderModel.USER + "}=?customer"
            + " AND {r:" + OrderModel.VERSIONID + "} IS NULL";

    private static final String STATUS_CLAUSE =
            " AND {r:" + OrderModel.STATUS + "} IN ({{SELECT {s:pk} FROM {OrderStatus AS s} WHERE {s:code} IN (?statuses)}})";

    private static final List<String[]> SORTS = List.of(
            new String[] { "byDateDesc", " ORDER BY {r:" + OrderModel.DATE + "} DESC" },
            new String[] { "byDateAsc", " ORDER BY {r:" + OrderModel.DATE + "} ASC" },
            new String[] { "byTotalDesc", " ORDER BY {r:" + OrderModel.TOTALPRICE + "} DESC" });

    // ---- other queries
    private static final String FIND_PRODUCT_BY_CODE =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + ProductModel._TYPECODE + " AS r}"
            + " WHERE {r:" + ProductModel.CODE + "}=?code";

    private static final String FIND_PRODUCT_CANDIDATES =
            "SELECT DISTINCT {r:" + ItemModel.PK + "} FROM {" + ProductModel._TYPECODE + " AS r"
            + " JOIN CategoryProductRelation AS cp ON {cp:target}={r:" + ItemModel.PK + "}"
            + " JOIN Category AS c ON {cp:source}={c:" + ItemModel.PK + "}}"
            + " WHERE {c:code} IN (?categories)";

    private static final String CO_PURCHASE_PAIRS =
            "SELECT {p2:" + ProductModel.CODE + "}, COUNT(DISTINCT {e2:" + AbstractOrderEntryModel.ORDER + "}) AS cnt"
            + " FROM {" + OrderEntryModel._TYPECODE + " AS e1"
            + " JOIN " + OrderEntryModel._TYPECODE + " AS e2 ON {e1:" + AbstractOrderEntryModel.ORDER + "}={e2:" + AbstractOrderEntryModel.ORDER + "}"
            + " JOIN " + ProductModel._TYPECODE + " AS p1 ON {e1:" + AbstractOrderEntryModel.PRODUCT + "}={p1:pk}"
            + " JOIN " + ProductModel._TYPECODE + " AS p2 ON {e2:" + AbstractOrderEntryModel.PRODUCT + "}={p2:pk}"
            + " JOIN " + OrderModel._TYPECODE + " AS o ON {e1:" + AbstractOrderEntryModel.ORDER + "}={o:pk}}"
            + " WHERE {p1:" + ProductModel.CODE + "}=?code AND {p2:" + ProductModel.CODE + "}<>?code"
            + " AND {o:" + OrderModel.DATE + "}>=?since"
            + " GROUP BY {p2:" + ProductModel.CODE + "} ORDER BY cnt DESC";

    private static final String FIND_CONFIGURATION =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + CCConfigurationModel._TYPECODE + " AS r}"
            + " WHERE {r:" + CCConfigurationModel.CODE + "}=?code";

    private static final String FIND_CONFIG_ENTRIES =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + CCConfigEntryModel._TYPECODE + " AS r}"
            + " WHERE {r:" + CCConfigEntryModel.CONFIGURATION + "}=?configuration"
            + " AND {r:" + CCConfigEntryModel.ENABLED + "}=?enabled"
            + " AND ({r:" + CCConfigEntryModel.PACKCODE + "}=?packCode OR {r:" + CCConfigEntryModel.PACKCODE + "} IS NULL"
            + " OR {r:" + CCConfigEntryModel.PACKCODE + "}='')";

    private static final String FIND_BUSINESS_ATTRIBUTE =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + CCProductBusinessAttributeModel._TYPECODE + " AS r"
            + " JOIN " + ProductModel._TYPECODE + " AS p ON {r:" + CCProductBusinessAttributeModel.PRODUCT + "}={p:" + ItemModel.PK + "}}"
            + " WHERE {p:" + ProductModel.CODE + "}=?code";

    private static final String FIND_USAGE =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + CCUsageCounterModel._TYPECODE + " AS r}"
            + " WHERE {r:" + CCUsageCounterModel.CUSTOMER + "}=?customer AND {r:" + CCUsageCounterModel.PERIODKEY + "}=?periodKey";

    private static final String EVENTS_OLDER_THAN =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + CCInterestEventModel._TYPECODE + " AS r} WHERE {r:" + CCInterestEventModel.OCCURREDAT + "}<?cutoff";

    private static final String FEEDBACK_OLDER_THAN =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + CCFeedbackModel._TYPECODE + " AS r} WHERE {r:" + CCFeedbackModel.CREATEDAT + "}<?cutoff";

    private static final String USAGE_OLDER_THAN =
            "SELECT {r:" + ItemModel.PK + "} FROM {" + CCUsageCounterModel._TYPECODE + " AS r} WHERE {r:" + CCUsageCounterModel.PERIODKEY + "}<?periodKey";

    @Resource
    private FlexibleSearchService flexibleSearchService;

    @Resource
    private PagedFlexibleSearchService pagedFlexibleSearchService;

    @Resource
    private ModelService modelService;

    @Resource
    private UserService userService;

    @Resource
    private ConfigurationService configurationService;

    @Resource
    private CommercePriceService commercePriceService;

    @Resource
    private BaseStoreService baseStoreService;

    @Resource
    private ClassificationService classificationService;

    // ======================================================================== configuration
    @Override
    public String findProperty(final String key)
    {
        return configurationService.getConfiguration().containsKey(key) ? configurationService.getConfiguration().getString(key) : null;
    }

    /** cc.activePack and cc.syntheticDataOnly come from the CCConfiguration row; other keys from enabled entries (pack rows win). */
    @Override
    public String findBackofficeValue(final String key)
    {
        final CCConfigurationModel configuration = findConfiguration();
        if (configuration == null || Boolean.FALSE.equals(configuration.getEnabled()))
        {
            return null;
        }
        if ("cc.activePack".equals(key))
        {
            return configuration.getActivePack();
        }
        if ("cc.syntheticDataOnly".equals(key))
        {
            return String.valueOf(Boolean.TRUE.equals(configuration.getSyntheticDataOnly()));
        }
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_CONFIG_ENTRIES);
        query.addQueryParameter("configuration", configuration);
        query.addQueryParameter("enabled", Boolean.TRUE);
        query.addQueryParameter("packCode", StringUtils.defaultString(configuration.getActivePack()));
        String global = null;
        for (final CCConfigEntryModel entry : flexibleSearchService.<CCConfigEntryModel> search(query).getResult())
        {
            if (key.equals(entry.getKey()))
            {
                if (StringUtils.isNotBlank(entry.getPackCode()))
                {
                    return entry.getValue(Locale.ENGLISH);
                }
                global = entry.getValue(Locale.ENGLISH);
            }
        }
        return global;
    }

    private CCConfigurationModel findConfiguration()
    {
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_CONFIGURATION);
        query.addQueryParameter("code", configurationService.getConfiguration().getString("cc.configuration.code", "default"));
        final SearchResult<CCConfigurationModel> result = flexibleSearchService.search(query);
        return CollectionUtils.isNotEmpty(result.getResult()) ? result.getResult().get(0) : null;
    }

    private String config(final String key)
    {
        final String v = findBackofficeValue(key);
        return v != null ? v : findProperty(key);
    }

    // ======================================================================== catalogue and prices
    @Override
    public List<Product> findProducts(final String pack, final List<String> keywords, final String categoryCode,
            final Map<String, String> filters, final int limit)
    {
        final List<String> categories = categoryCode != null ? List.of(categoryCode)
                : Arrays.asList(StringUtils.defaultString(config("pack." + pack + ".categories")).split(","));
        final Map<String, Object> params = new HashMap<>();
        params.put("categories", categories);
        final StringBuilder q = new StringBuilder(FIND_PRODUCT_CANDIDATES);
        int i = 0;
        for (final String keyword : keywords)
        {
            if (StringUtils.isBlank(keyword))
            {
                continue;
            }
            q.append(i == 0 ? " AND (" : " OR ");
            final String name = "kw" + i++;
            q.append("LOWER({r:").append(ProductModel.NAME).append("}) LIKE ?").append(name).append(" OR LOWER({r:")
                    .append(ProductModel.DESCRIPTION).append("}) LIKE ?").append(name);
            params.put(name, "%" + keyword.toLowerCase(Locale.ROOT) + "%");
        }
        if (i > 0)
        {
            q.append(")");
        }
        final FlexibleSearchQuery query = new FlexibleSearchQuery(q.toString());
        query.addQueryParameters(params);
        query.setCount(Math.min(Math.max(limit * 3, 1), 200));
        final List<Product> result = new ArrayList<>();
        for (final ProductModel model : flexibleSearchService.<ProductModel> search(query).getResult())
        {
            final Product p = convert(pack, model);
            // attribute filters are applied on classification values; keys are whitelisted by the caller
            if (filters.entrySet().stream().allMatch(f -> f.getValue().equalsIgnoreCase(p.attributes().get(f.getKey()))) && result.size() < limit)
            {
                result.add(p);
            }
        }
        return result;
    }

    @Override
    public Product findProduct(final String pack, final String code)
    {
        if (StringUtils.isBlank(code))
        {
            return null;
        }
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_PRODUCT_BY_CODE);
        query.addQueryParameter("code", code);
        final SearchResult<ProductModel> result = flexibleSearchService.search(query);
        return CollectionUtils.isNotEmpty(result.getResult()) ? convert(pack, result.getResult().get(0)) : null;
    }

    /**
     * ASSUMPTIONS (AS-17): attributes come from classification features listed in pack.&lt;pack&gt;.attributes; a product is
     * "discontinued" when its approval status is not APPROVED and its replacement is its first FOLLOWUP reference.
     */
    private Product convert(final String pack, final ProductModel model)
    {
        final Map<String, String> attributes = new LinkedHashMap<>();
        final String mapping = config("pack." + pack + ".attributes");
        if (StringUtils.isNotBlank(mapping) && model.getClassificationClasses() != null)
        {
            for (final Feature feature : classificationService.getFeatures(model).getFeatures())
            {
                for (final String suffix : mapping.split(","))
                {
                    if (feature.getCode().endsWith("." + suffix.trim().toLowerCase(Locale.ROOT)) && feature.getValue() != null)
                    {
                        attributes.put(suffix.trim(), String.valueOf(feature.getValue().getValue()));
                    }
                }
            }
        }
        final boolean discontinued = model.getApprovalStatus() != ArticleApprovalStatus.APPROVED;
        String replacement = null;
        if (discontinued && model.getProductReferences() != null)
        {
            for (final ProductReferenceModel ref : model.getProductReferences())
            {
                if (ref.getReferenceType() == ProductReferenceTypeEnum.FOLLOWUP)
                {
                    replacement = ref.getTarget().getCode();
                    break;
                }
            }
        }
        return new Product(model.getCode(), StringUtils.defaultString(model.getName()), StringUtils.defaultString(model.getDescription()), pack,
                pack, attributes, discontinued, replacement);
    }

    /** ASSUMPTION (AS-04): the chat request runs in the customer's session, so the price service applies contract prices. */
    @Override
    public OptionalLong findUnitPriceMinor(final String customerId, final String productCode)
    {
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_PRODUCT_BY_CODE);
        query.addQueryParameter("code", productCode);
        final List<ProductModel> found = flexibleSearchService.<ProductModel> search(query).getResult();
        if (found.isEmpty())
        {
            return OptionalLong.empty();
        }
        final PriceInformation info = commercePriceService.getWebPriceForProduct(found.get(0));
        return info == null || info.getPriceValue() == null ? OptionalLong.empty()
                : OptionalLong.of(BigDecimal.valueOf(info.getPriceValue().getValue()).movePointRight(2).longValue());
    }

    @Override
    public String findCurrency()
    {
        return baseStoreService.getCurrentBaseStore() == null ? null : baseStoreService.getCurrentBaseStore().getDefaultCurrency().getIsocode();
    }

    // ======================================================================== orders
    @Override
    public List<Order> searchOrders(final String customerId, final OrderFilter filter, final String sortCode, final int limit)
    {
        validateParameterNotNull(customerId, "customerId must not be null");
        final Map<String, Object> params = new HashMap<>();
        params.put("customer", customer(customerId));
        final StringBuilder where = new StringBuilder();
        buildFilterClause(params, filter, where);
        final List<SortQueryData> sortQueries = new ArrayList<>();
        for (final String[] sort : SORTS)
        {
            sortQueries.add(createSortQuery(sort[0], SEARCH_ORDERS_BASE + where + sort[1]));
        }
        final PageableData page = new PageableData();
        page.setCurrentPage(0);
        page.setPageSize(Math.min(Math.max(limit, 1), MAX_LIMIT));
        final String selected = SORTS.stream().anyMatch(s -> s[0].equals(sortCode)) ? sortCode : DEFAULT_SORT;
        final SearchPageData<OrderModel> result = pagedFlexibleSearchService.search(sortQueries, selected, params, page);
        return result.getResults().stream().map(this::convert).toList();
    }

    @Override
    public Order findOrderByCode(final String customerId, final String code)
    {
        validateParameterNotNull(customerId, "customerId must not be null");
        if (StringUtils.isBlank(code))
        {
            return null;
        }
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_ORDER_BY_CODE);
        query.addQueryParameter("code", code);
        query.addQueryParameter("customer", customer(customerId));
        final SearchResult<OrderModel> result = flexibleSearchService.search(query);
        return CollectionUtils.isNotEmpty(result.getResult()) ? convert(result.getResult().get(0)) : null;
    }

    /** Appends AND clauses for the non-null filters and fills the named parameters. toDate is exclusive: the next day. */
    protected void buildFilterClause(final Map<String, Object> params, final OrderFilter filters, final StringBuilder where)
    {
        if (filters == null)
        {
            return;
        }
        if (CollectionUtils.isNotEmpty(filters.statuses()))
        {
            where.append(STATUS_CLAUSE);
            params.put("statuses", platformStatusCodes(filters.statuses()));
        }
        if (filters.minTotalMinor() != null)
        {
            where.append(" AND {r:").append(OrderModel.TOTALPRICE).append("}>=?minTotal");
            params.put("minTotal", BigDecimal.valueOf(filters.minTotalMinor()).movePointLeft(2).doubleValue());
        }
        if (filters.maxTotalMinor() != null)
        {
            where.append(" AND {r:").append(OrderModel.TOTALPRICE).append("}<=?maxTotal");
            params.put("maxTotal", BigDecimal.valueOf(filters.maxTotalMinor()).movePointLeft(2).doubleValue());
        }
        if (filters.fromDate() != null)
        {
            where.append(" AND {r:").append(OrderModel.DATE).append("}>=?fromDate");
            params.put("fromDate", startOfDay(filters.fromDate(), 0));
        }
        if (filters.toDate() != null)
        {
            where.append(" AND {r:").append(OrderModel.DATE).append("}<?toDate");
            params.put("toDate", startOfDay(filters.toDate(), 1));
        }
    }

    protected SortQueryData createSortQuery(final String sortCode, final String query)
    {
        final SortQueryData sortQuery = new SortQueryData();
        sortQuery.setSortCode(sortCode);
        sortQuery.setQuery(query);
        return sortQuery;
    }

    private Date startOfDay(final LocalDate date, final int plusDays)
    {
        return Date.from(date.plusDays(plusDays).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private CustomerModel customer(final String customerId)
    {
        return (CustomerModel) userService.getUserForUID(customerId);
    }

    /** ASSUMPTION (AS-21): OOTB OrderStatus has no SHIPPED value; READY is treated as dispatched, unknown values as in progress. */
    private static final Map<OrderStatus, List<String>> STATUS_CODES = Map.of(
            OrderStatus.PENDING, List.of("CREATED", "PENDING_APPROVAL", "PENDING_APPROVAL_FROM_MERCHANT"),
            OrderStatus.PROCESSING, List.of("PROCESSING", "WAIT_FRAUD_MANUAL_CHECK", "ON_VALIDATION"),
            OrderStatus.SHIPPED, List.of("READY", "SHIPPED", "DISPATCHED"),
            OrderStatus.DELIVERED, List.of("COMPLETED", "DELIVERED"),
            OrderStatus.CANCELLED, List.of("CANCELLED", "CANCELLING"));

    private static List<String> platformStatusCodes(final Set<OrderStatus> statuses)
    {
        final List<String> codes = new ArrayList<>();
        statuses.forEach(s -> codes.addAll(STATUS_CODES.get(s)));
        return codes;
    }

    private static OrderStatus mapStatus(final String code)
    {
        for (final Map.Entry<OrderStatus, List<String>> e : STATUS_CODES.entrySet())
        {
            if (code != null && e.getValue().contains(code))
            {
                return e.getKey();
            }
        }
        return OrderStatus.PROCESSING;
    }

    private Order convert(final OrderModel model)
    {
        final List<OrderLine> lines = model.getEntries().stream()
                .map(e -> new OrderLine(e.getProduct().getCode(), e.getQuantity().intValue())).toList();
        String tracking = null;
        if (model.getConsignments() != null)
        {
            for (final ConsignmentModel c : model.getConsignments())
            {
                if (StringUtils.isNotBlank(c.getTrackingID()))
                {
                    tracking = c.getTrackingID(); // FUTURE SCOPE: carrier tracking (D10): only the Commerce-held reference is returned
                    break;
                }
            }
        }
        return new Order(model.getCode(), model.getUser().getUid(), model.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                mapStatus(model.getStatus() == null ? null : model.getStatus().getCode()),
                BigDecimal.valueOf(model.getTotalPrice() == null ? 0 : model.getTotalPrice()).movePointRight(2).longValue(),
                model.getCurrency() == null ? "" : model.getCurrency().getIsocode(), lines, tracking);
    }

    // ======================================================================== aggregates and content
    @Override
    public Map<String, Integer> findCoPurchaseCounts(final String pack, final String productCode)
    {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        if (StringUtils.isBlank(productCode))
        {
            return counts;
        }
        final FlexibleSearchQuery query = new FlexibleSearchQuery(CO_PURCHASE_PAIRS);
        query.addQueryParameter("code", productCode);
        query.addQueryParameter("since", startOfDay(LocalDate.now().minusDays(365), 0));
        query.setResultClassList(List.of(String.class, Long.class));
        query.setCount(20);
        for (final List<Object> row : flexibleSearchService.<List<Object>> search(query).getResult())
        {
            counts.put((String) row.get(0), ((Long) row.get(1)).intValue());
        }
        return counts;
    }

    @Override
    public BusinessAttribute findBusinessAttribute(final String productCode)
    {
        if (StringUtils.isBlank(productCode))
        {
            return null;
        }
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_BUSINESS_ATTRIBUTE);
        query.addQueryParameter("code", productCode);
        final List<CCProductBusinessAttributeModel> result = flexibleSearchService.<CCProductBusinessAttributeModel> search(query).getResult();
        return CollectionUtils.isEmpty(result) ? null : new BusinessAttribute(result.get(0).getPriority() == null ? 0 : result.get(0).getPriority(),
                result.get(0).getMargin() == null ? 0 : result.get(0).getMargin());
    }

    /** Approved FAQ content (D16): CCConfigEntry rows of type FAQ; value format "question::answer" (AS-03). */
    @Override
    public List<Faq> findFaqs(final String pack)
    {
        final CCConfigurationModel configuration = findConfiguration();
        if (configuration == null)
        {
            return Collections.emptyList();
        }
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_CONFIG_ENTRIES);
        query.addQueryParameter("configuration", configuration);
        query.addQueryParameter("enabled", Boolean.TRUE);
        query.addQueryParameter("packCode", StringUtils.defaultString(pack));
        final List<Faq> faqs = new ArrayList<>();
        for (final CCConfigEntryModel e : flexibleSearchService.<CCConfigEntryModel> search(query).getResult())
        {
            final String value = e.getValue(Locale.ENGLISH);
            final String[] parts = value == null ? new String[0] : value.split("::", 2);
            if ("FAQ".equals(e.getEntryType().getCode()) && parts.length == 2)
            {
                faqs.add(new Faq(e.getKey(), pack, parts[0].trim(), parts[1].trim()));
            }
        }
        return faqs;
    }

    // ======================================================================== writes owned by the chat feature
    @Override
    public void saveInterestEvent(final String customerId, final String eventType, final String reference, final String pack)
    {
        final CCInterestEventModel event = modelService.create(CCInterestEventModel.class);
        event.setCustomer(customer(customerId));
        event.setEventType(CCEventType.valueOf(eventType));
        if ("REQUESTED_NOT_FOUND".equals(eventType))
        {
            event.setNormalisedToken(reference);
        }
        else
        {
            event.setProductCode(reference);
        }
        event.setPackCode(pack);
        event.setOccurredAt(new Date());
        modelService.save(event);
    }

    @Override
    public void saveFeedback(final String responseId, final String rating, final String intent, final String toolsUsed, final String auditRef)
    {
        final CCFeedbackModel model = modelService.create(CCFeedbackModel.class);
        model.setResponseId(responseId);
        model.setRating(CCFeedbackRating.valueOf(rating));
        model.setIntent(intent);
        model.setToolsUsed(toolsUsed);
        model.setAuditRef(auditRef);
        model.setCreatedAt(new Date());
        modelService.save(model);
    }

    /** ASSUMPTION (AS-04): the read-modify-write is not atomic across nodes; a lost update only under-counts. */
    @Override
    public synchronized long[] incrementUsage(final String customerId, final String periodKey, final int messages, final int tokens)
    {
        final CustomerModel customer = customer(customerId);
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_USAGE);
        query.addQueryParameter("customer", customer);
        query.addQueryParameter("periodKey", periodKey);
        final List<CCUsageCounterModel> found = flexibleSearchService.<CCUsageCounterModel> search(query).getResult();
        CCUsageCounterModel counter = CollectionUtils.isEmpty(found) ? null : found.get(0);
        if (counter == null)
        {
            counter = modelService.create(CCUsageCounterModel.class);
            counter.setCustomer(customer);
            counter.setPeriodKey(periodKey);
            counter.setMessageCount(0);
            counter.setTokenCount(0L);
        }
        counter.setMessageCount(counter.getMessageCount() + messages);
        counter.setTokenCount(counter.getTokenCount() + tokens);
        modelService.save(counter);
        return new long[] { counter.getMessageCount(), counter.getTokenCount() };
    }

    @Override
    public long[] findUsage(final String customerId, final String periodKey)
    {
        final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_USAGE);
        query.addQueryParameter("customer", customer(customerId));
        query.addQueryParameter("periodKey", periodKey);
        final List<CCUsageCounterModel> found = flexibleSearchService.<CCUsageCounterModel> search(query).getResult();
        return CollectionUtils.isEmpty(found) ? new long[2] : new long[] { found.get(0).getMessageCount(), found.get(0).getTokenCount() };
    }

    // ======================================================================== retention
    @Override
    public int purgeInterestEvents(final Date cutoff, final int batchSize)
    {
        return purge(EVENTS_OLDER_THAN, "cutoff", cutoff, batchSize);
    }

    @Override
    public int purgeFeedback(final Date cutoff, final int batchSize)
    {
        return purge(FEEDBACK_OLDER_THAN, "cutoff", cutoff, batchSize);
    }

    @Override
    public int purgeUsageCounters(final String periodKeyBefore, final int batchSize)
    {
        return purge(USAGE_OLDER_THAN, "periodKey", periodKeyBefore, batchSize);
    }

    private int purge(final String queryText, final String paramName, final Object value, final int batchSize)
    {
        validateParameterNotNull(value, paramName + " must not be null");
        final FlexibleSearchQuery query = new FlexibleSearchQuery(queryText);
        query.addQueryParameter(paramName, value);
        query.setCount(Math.max(1, batchSize));
        final List<ItemModel> batch = flexibleSearchService.<ItemModel> search(query).getResult();
        modelService.removeAll(batch);
        return batch.size();
    }
}
