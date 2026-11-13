package com.conversational.commerce.core.engine.gateway;

import com.conversational.commerce.core.engine.model.Domain.BusinessAttribute;
import java.util.Optional;

public interface BusinessAttributeGateway
{
    Optional<BusinessAttribute> attributeOf(String productCode);
}
