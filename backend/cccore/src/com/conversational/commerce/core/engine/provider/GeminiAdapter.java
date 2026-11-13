package com.conversational.commerce.core.engine.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;

/**
 * UNVERIFIED request shape (docs/verification-log.md): generateContent with responseMimeType and responseSchema.
 * The free tier uses submitted content to improve products (E01): the router refuses it outside synthetic-data sites (A2).
 */
public class GeminiAdapter extends AbstractHttpAdapter
{
    @Override
    public String providerCode()
    {
        return "gemini";
    }

    @Override
    protected String endpoint(final String model)
    {
        return "/v1beta/models/" + model + ":generateContent";
    }

    @Override
    protected Map<String, String> headers()
    {
        return Map.of("Content-Type", "application/json", "x-goog-api-key", apiKey());
    }

    @Override
    protected String buildBody(final String model, final ModelRequest request) throws JsonProcessingException
    {
        final ObjectNode root = object();
        root.putObject("systemInstruction").putArray("parts").addObject().put("text", request.systemPrompt());
        final ObjectNode content = ((ArrayNode) root.putArray("contents")).addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", request.payload().json());
        final ObjectNode config = root.putObject("generationConfig");
        config.put("responseMimeType", "application/json");
        config.put("temperature", request.temperature());
        config.put("maxOutputTokens", request.maxOutputTokens());
        final JsonNode schema = MAPPER.readTree(request.schemaJson());
        stripUnsupported(schema);
        config.set("responseSchema", schema);
        return MAPPER.writeValueAsString(root);
    }

    /** Schema files are written in OpenAI-strict form; Gemini's schema subset has no additionalProperties. */
    private void stripUnsupported(final JsonNode node)
    {
        if (node instanceof ObjectNode object)
        {
            object.remove("additionalProperties");
            object.remove("$schema");
            final Iterator<JsonNode> it = object.elements();
            while (it.hasNext())
            {
                stripUnsupported(it.next());
            }
        }
        else if (node instanceof ArrayNode array)
        {
            array.forEach(this::stripUnsupported);
        }
    }

    @Override
    protected JsonNode extractContent(final JsonNode response)
    {
        final JsonNode text = response.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        return text.isTextual() ? parseJsonText(text.asText()) : null;
    }

    @Override
    protected int[] extractUsage(final JsonNode response)
    {
        return new int[] { response.path("usageMetadata").path("promptTokenCount").asInt(0),
                response.path("usageMetadata").path("candidatesTokenCount").asInt(0) };
    }
}
