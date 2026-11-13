package com.conversational.commerce.core.engine.provider;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.hybris.platform.servicelayer.exceptions.SystemException;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Shared HTTP handling. Errors follow the owner's convention: non-200 logs the status only (the body may echo user
 * content) and throws SystemException; null content throws SystemException; InterruptedException restores the flag.
 */
public abstract class AbstractHttpAdapter implements ModelProviderAdapter
{
    private static final Logger LOG = LogManager.getLogger(AbstractHttpAdapter.class);
    protected static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TIMEOUT_KEY = "cc.http.requestTimeoutMs";
    private static final String CONNECT_TIMEOUT_KEY = "cc.http.connectTimeoutMs";

    @Resource
    protected CcConfigurationService configurationService;

    private HttpTransport transport;

    public void setConfigurationService(final CcConfigurationService configurationService)
    {
        this.configurationService = configurationService;
    }

    public void setTransport(final HttpTransport transport)
    {
        this.transport = transport;
    }

    @Override
    public String tier()
    {
        return configurationService.getString("cc.provider." + providerCode() + ".tier", "paid");
    }

    @Override
    public boolean isAvailable()
    {
        return StringUtils.isNotBlank(baseUrl()) && StringUtils.isNotBlank(apiKey())
                && configurationService.getBoolean("cc.provider." + providerCode() + ".enabled", true);
    }

    protected String baseUrl()
    {
        return configurationService.getString("cc.provider." + providerCode() + ".baseUrl", "");
    }

    /** Keys are read by alias and never logged. ASSUMPTION: alias defaults to the provider code. */
    protected String apiKey()
    {
        final String alias = configurationService.getString("cc.provider." + providerCode() + ".keyAlias", providerCode());
        return configurationService.getString("cc.provider.key." + alias, "");
    }

    protected abstract String endpoint(String model);

    protected abstract Map<String, String> headers();

    protected abstract String buildBody(String model, ModelRequest request) throws JsonProcessingException;

    protected abstract JsonNode extractContent(JsonNode response);

    protected abstract int[] extractUsage(JsonNode response);

    @Override
    public ModelResult call(final String model, final ModelRequest request)
    {
        final long start = System.currentTimeMillis();
        try
        {
            final String body = buildBody(model, request);
            final HttpTransport.Response response = getTransport().post(baseUrl() + endpoint(model), headers(), body,
                    configurationService.getInt(TIMEOUT_KEY, 8000));
            if (response.status() != 200)
            {
                LOG.warn("Model provider {} returned status {}", providerCode(), response.status()); // status only
                throw new SystemException("Model provider returned status " + response.status());
            }
            final JsonNode root = MAPPER.readTree(response.body());
            final JsonNode content = root == null ? null : extractContent(root);
            if (content == null || content.isNull() || content.isMissingNode())
            {
                throw new SystemException("Model provider returned no content");
            }
            final int[] usage = extractUsage(root);
            return new ModelResult(content, usage[0], usage[1], providerCode(), model, System.currentTimeMillis() - start);
        }
        catch (final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new SystemException("Model call interrupted", e);
        }
        catch (final IOException e)
        {
            // ASSUMPTION: remaining catch blocks were truncated in the owner's screenshot (docs/assumptions.md AS-06).
            LOG.warn("Model provider {} call failed: {}", providerCode(), e.getClass().getSimpleName());
            throw new SystemException("Model provider call failed", e);
        }
    }

    private HttpTransport getTransport()
    {
        if (transport == null)
        {
            transport = new JdkHttpTransport(configurationService.getInt(CONNECT_TIMEOUT_KEY, 3000));
        }
        return transport;
    }

    /** Parses model text as JSON; a non-JSON answer is a schema failure the router may retry. */
    protected JsonNode parseJsonText(final String text)
    {
        try
        {
            return MAPPER.readTree(text);
        }
        catch (final JsonProcessingException e)
        {
            return null;
        }
    }

    protected static ObjectNode object()
    {
        return MAPPER.createObjectNode();
    }
}
