package com.conversational.commerce.core.engine.config;

import java.util.Optional;

/** One configuration layer. The platform adapter reads project.properties/local.properties and Backoffice entries. */
public interface ConfigSource
{
    Optional<String> get(String key);
}
