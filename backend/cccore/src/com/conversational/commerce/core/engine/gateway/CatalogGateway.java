package com.conversational.commerce.core.engine.gateway;

import com.conversational.commerce.core.engine.model.Domain.Product;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CatalogGateway
{
    /** Candidates for the active pack. Keywords and filters are matched by the implementation with named parameters only. */
    List<Product> findCandidates(String pack, List<String> keywords, String categoryCode, Map<String, String> filters,
            int limit);

    Optional<Product> byCode(String pack, String code);
}
