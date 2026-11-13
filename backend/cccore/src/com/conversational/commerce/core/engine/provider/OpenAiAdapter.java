package com.conversational.commerce.core.engine.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;

/** UNVERIFIED request shape (docs/verification-log.md): chat completions with a strict JSON schema, store=false (E05). */
public class OpenAiAdapter extends AbstractHttpAdapter
{
    @Override
    public String providerCode()
    {
        return "openai";
    }

    @Override
    protected String endpoint(final String model)
    {
        return "/v1/chat/completions";
    }

    @Override
    protected Map<String, String> headers()
    {
        return Map.of("Content-Type", "application/json", "Authorization", "Bearer " + apiKey());
    }

    @Override
    protected String buildBody(final String model, final ModelRequest request) throws JsonProcessingException
    {
        final ObjectNode root = object();
        root.put("model", model);
        root.put("store", false);
        root.put("temperature", request.temperature());
        root.put("max_completion_tokens", request.maxOutputTokens());
        final ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.systemPrompt());
        messages.addObject().put("role", "user").put("content", request.payload().json());
        final ObjectNode format = root.putObject("response_format");
        format.put("type", "json_schema");
        final ObjectNode js = format.putObject("json_schema");
        js.put("name", request.promptId().replaceAll("[^A-Za-z0-9_-]", "_"));
        js.put("strict", true);
        js.set("schema", MAPPER.readTree(request.schemaJson()));
        return MAPPER.writeValueAsString(root);
    }

    @Override
    protected JsonNode extractContent(final JsonNode response)
    {
        final JsonNode text = response.path("choices").path(0).path("message").path("content");
        return text.isTextual() ? parseJsonText(text.asText()) : null;
    }

    @Override
    protected int[] extractUsage(final JsonNode response)
    {
        return new int[] { response.path("usage").path("prompt_tokens").asInt(0),
                response.path("usage").path("completion_tokens").asInt(0) };
    }
}
