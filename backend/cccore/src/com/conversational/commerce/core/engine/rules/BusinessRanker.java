package com.conversational.commerce.core.engine.rules;

import com.conversational.commerce.core.engine.gateway.BusinessAttributeGateway;
import com.conversational.commerce.core.engine.model.Domain.BusinessAttribute;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Business recommendation controls (CI-03, D24). The only reader of margin and priority. It returns an order of codes
 * and nothing else, so margin cannot reach a model, a response or a log.
 */
public class BusinessRanker
{
    @Resource
    private BusinessAttributeGateway businessAttributeGateway;

    public void setBusinessAttributeGateway(final BusinessAttributeGateway gateway)
    {
        this.businessAttributeGateway = gateway;
    }

    public List<String> rank(final List<String> codes)
    {
        final List<String> ordered = new ArrayList<>(codes);
        ordered.sort(Comparator.comparingLong((String c) -> -score(c))); // stable: ties keep the incoming order
        return ordered;
    }

    private long score(final String code)
    {
        final Optional<BusinessAttribute> attribute = businessAttributeGateway.attributeOf(code);
        return attribute.map(a -> a.priority() * 1_000_000_000L + a.marginMinor()).orElse(0L);
    }
}
