package com.conversational.commerce.core.platform;

import com.conversational.commerce.core.dao.CcBusinessAttributeDao;
import com.conversational.commerce.core.engine.gateway.BusinessAttributeGateway;
import com.conversational.commerce.core.engine.model.Domain.BusinessAttribute;
import com.conversational.commerce.core.model.CCProductBusinessAttributeModel;
import jakarta.annotation.Resource;
import java.util.Optional;

/** Only BusinessRanker uses this gateway (A10, D24). Margin never leaves the JVM through any other path. */
public class PlatformBusinessAttributeGateway implements BusinessAttributeGateway
{
    @Resource(name = "ccBusinessAttributeDao")
    private CcBusinessAttributeDao ccBusinessAttributeDao;

    @Override
    public Optional<BusinessAttribute> attributeOf(final String productCode)
    {
        final CCProductBusinessAttributeModel model = ccBusinessAttributeDao.findByProductCode(productCode);
        if (model == null)
        {
            return Optional.empty();
        }
        return Optional.of(new BusinessAttribute(model.getPriority() == null ? 0 : model.getPriority(),
                model.getMargin() == null ? 0 : model.getMargin()));
    }
}
