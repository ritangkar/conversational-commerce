package com.conversational.commerce.core.engine.provider;

/** SPI: one adapter per provider (PL-02). Adapters never see raw customer data, only a SanitisedPayload. */
public interface ModelProviderAdapter
{
    String providerCode();

    /** "free" or "paid" from cc.provider.&lt;code&gt;.tier. */
    String tier();

    boolean isAvailable();

    ModelResult call(String model, ModelRequest request);
}
