package com.conversational.commerce.core.platform;

import com.conversational.commerce.core.dao.CcProductSearchDao;
import com.conversational.commerce.core.engine.config.CcConfigurationService;
import com.conversational.commerce.core.engine.gateway.CatalogGateway;
import com.conversational.commerce.core.engine.model.Domain.Product;
import de.hybris.platform.catalog.enums.ProductReferenceTypeEnum;
import de.hybris.platform.catalog.model.ProductReferenceModel;
import de.hybris.platform.classification.ClassificationService;
import de.hybris.platform.classification.features.Feature;
import de.hybris.platform.classification.features.FeatureList;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.catalog.enums.ArticleApprovalStatus;
import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Pack-scoped catalogue access. ASSUMPTIONS (AS-04), to be replaced by the owner's conventions:
 * pack root categories come from property-style entries pack.&lt;pack&gt;.categories (comma separated codes);
 * attributes come from classification features listed in pack.&lt;pack&gt;.attributes (comma separated feature code suffixes);
 * a product is "discontinued" when its approval status is not APPROVED, and its replacement is its first FOLLOWUP reference.
 * NOT COMPILED here: needs a SAP platform.
 */
public class PlatformCatalogGateway implements CatalogGateway
{
    @Resource(name = "ccProductSearchDao")
    private CcProductSearchDao ccProductSearchDao;

    @Resource(name = "ccConfigurationService")
    private CcConfigurationService configurationService;

    @Resource
    private ClassificationService classificationService;

    @Override
    public List<Product> findCandidates(final String pack, final List<String> keywords, final String categoryCode,
            final Map<String, String> filters, final int limit)
    {
        final List<String> categories = categoryCode != null ? List.of(categoryCode)
                : Arrays.asList(configurationService.getString("pack." + pack + ".categories", "").split(","));
        final List<ProductModel> found = ccProductSearchDao.findCandidates(keywords, categories, filters, limit * 3);
        return found.stream().map(p -> convert(pack, p)).filter(p -> matches(p, filters)).limit(limit).toList();
    }

    @Override
    public Optional<Product> byCode(final String pack, final String code)
    {
        final ProductModel model = ccProductSearchDao.findByCode(code);
        return model == null ? Optional.empty() : Optional.of(convert(pack, model));
    }

    private boolean matches(final Product product, final Map<String, String> filters)
    {
        return filters.entrySet().stream().allMatch(f -> f.getValue().equalsIgnoreCase(product.attributes().get(f.getKey())));
    }

    private Product convert(final String pack, final ProductModel model)
    {
        final Map<String, String> attributes = new LinkedHashMap<>();
        final String mapping = configurationService.getString("pack." + pack + ".attributes", "");
        if (StringUtils.isNotBlank(mapping) && model.getClassificationClasses() != null)
        {
            final FeatureList features = classificationService.getFeatures(model);
            for (final Feature feature : features.getFeatures())
            {
                for (final String suffix : mapping.split(","))
                {
                    if (feature.getCode().endsWith("." + suffix.trim().toLowerCase()) && feature.getValue() != null)
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
        return new Product(model.getCode(), StringUtils.defaultString(model.getName()), StringUtils.defaultString(model.getDescription()),
                pack, pack, attributes, discontinued, replacement);
    }
}
