package com.conversational.commerce.core.engine.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;

/**
 * UNVERIFIED request shape (docs/verification-log.md): forced tool use with the schema as input_schema.
 * Never route ZDR clients to Fable or Mythos models (E07): the model comes from route.&lt;task&gt;.model, not from code.
 */
public class AnthropicAdapter extends AbstractHttpAdapter
{
    @Override
    public String providerCode()
    {
        return "anthropic";
    }

    @Override
    protected String endpoint(final String model)
    {
        return "/v1/messages";
    }

    @Override
    protected Map<String, String> headers()
    {
        return Map.of("Content-Type", "application/json", "x-api-key", apiKey(), "anthropic-version", "2023-06-01");
    }

    @Override
    protected String buildBody(final String model, final ModelRequest request) throws JsonProcessingException
    {
        final ObjectNode root = object();
        root.put("model", model);
        root.put("max_tokens", request.maxOutputTokens());
        root.put("temperature", request.temperature());
        root.put("system", request.systemPrompt());
        root.putArray("messages").addObject().put("role", "user").put("content", request.payload().json());
        final String toolName = request.promptId().replaceAll("[^A-Za-z0-9_-]", "_");
        final ObjectNode tool = root.putArray("tools").addObject();
        tool.put("name", toolName);
        tool.put("description", "Return the structured answer.");
        tool.set("input_schema", MAPPER.readTree(request.schemaJson()));
        root.putObject("tool_choice").put("type", "tool").put("name", toolName);
        return MAPPER.writeValueAsString(root);
    }

    @Override
    protected JsonNode extractContent(final JsonNode response)
    {
        for (final JsonNode block : response.path("content"))
        {
            if ("tool_use".equals(block.path("type").asText()))
            {
                return block.path("input");
            }
        }
        return null;
    }

    @Override
    protected int[] extractUsage(final JsonNode response)
    {
        return new int[] { response.path("usage").path("input_tokens").asInt(0),
                response.path("usage").path("output_tokens").asInt(0) };
    }
}
