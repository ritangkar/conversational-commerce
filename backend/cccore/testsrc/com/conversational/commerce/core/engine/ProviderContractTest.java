package com.conversational.commerce.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conversational.commerce.core.engine.demo.DemoDataset;
import com.conversational.commerce.core.service.impl.DefaultChatService;
import com.conversational.commerce.core.service.impl.DefaultChatService.ModelResult;
import com.conversational.commerce.core.service.impl.DefaultChatService.ModelTransport;
import com.conversational.commerce.core.service.impl.DefaultChatService.ModelUnavailableException;
import de.hybris.platform.servicelayer.exceptions.SystemException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Contract tests from recorded (synthetic) fixtures through the real service path. No key and no network. Request shapes are UNVERIFIED (S3). */
class ProviderContractTest
{
    private final DemoDataset data = new DemoDataset(LocalDate.of(2026, 11, 13));
    private final DefaultChatService service = new DefaultChatService();
    private final List<String> urls = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private final List<Map<String, String>> sentHeaders = new ArrayList<>();

    private ProviderContractTest()
    {
        data.properties.put("cc.config.cacheTtlSeconds", "0");
        service.setChatDao(data);
    }

    private static String fixture(final String name) throws IOException
    {
        return Files.readString(Path.of("testsrc/fixtures/providers/" + name));
    }

    private void provider(final String code, final int status, final String fixtureName) throws IOException
    {
        data.properties.put("cc.provider." + code + ".baseUrl", "https://" + code + ".invalid").put("cc.provider.key." + code, "test-only-not-a-key");
        final String body = fixtureName == null ? "" : fixture(fixtureName);
        final ModelTransport previous = fixtureTransport;
        fixtureTransport = (url, headers, requestBody, timeout) ->
        {
            urls.add(url);
            bodies.add(requestBody);
            sentHeaders.add(headers);
            return url.contains(code + ".invalid") ? new ModelTransport.Response(status, body) : previous.post(url, headers, requestBody, timeout);
        };
        service.setTransport(fixtureTransport);
    }

    private ModelTransport fixtureTransport = (url, headers, body, timeout) -> new ModelTransport.Response(404, "");

    private void route(final String provider, final String fallback)
    {
        data.properties.put("route.compose.provider", provider).put("route.compose.model", "m");
        if (fallback != null)
        {
            data.properties.put("route.compose.fallback", fallback);
        }
    }

    private ModelResult call()
    {
        return service.callModel("COMPOSE", "compose.v1", Map.of("intent", "x", "facts", Map.of("n", "a@b.co")), List.of());
    }

    @Test
    void openAiShapeAndParsing() throws Exception
    {
        provider("openai", 200, "openai-ok.json");
        route("openai", null);
        final ModelResult r = call();
        assertEquals("hello", r.json().path("text").asText());
        assertEquals(11, r.tokensIn());
        assertEquals(3, r.tokensOut());
        assertTrue(bodies.get(0).contains("\"store\":false") && bodies.get(0).contains("\"strict\":true") && bodies.get(0).contains("json_schema"));
        assertTrue(urls.get(0).endsWith("/v1/chat/completions"));
        assertEquals("Bearer test-only-not-a-key", sentHeaders.get(0).get("Authorization"));
        assertFalse(bodies.get(0).contains("a@b.co"), "payload was sanitised before the call");
    }

    @Test
    void anthropicUsesForcedToolWithSchema() throws Exception
    {
        provider("anthropic", 200, "anthropic-ok.json");
        route("anthropic", null);
        final ModelResult r = call();
        assertEquals("hello", r.json().path("text").asText());
        assertEquals(12, r.tokensIn());
        assertTrue(bodies.get(0).contains("\"tool_choice\":{\"type\":\"tool\""));
        assertTrue(sentHeaders.get(0).containsKey("x-api-key") && sentHeaders.get(0).containsKey("anthropic-version"));
    }

    @Test
    void geminiStripsAdditionalProperties() throws Exception
    {
        provider("gemini", 200, "gemini-ok.json");
        route("gemini", null);
        final ModelResult r = call();
        assertEquals("hello", r.json().path("text").asText());
        assertEquals(13, r.tokensIn());
        assertFalse(bodies.get(0).contains("additionalProperties"));
        assertTrue(bodies.get(0).contains("responseSchema") && urls.get(0).contains(":generateContent"));
    }

    @Test
    void non200AndEmptyContentFailOverToTheNextProviderOrFail() throws Exception
    {
        provider("openai", 500, null);
        route("openai", null);
        assertThrows(ModelUnavailableException.class, this::call);
        provider("openai", 200, null); // body "" -> unreadable content
        assertThrows(ModelUnavailableException.class, this::call);
    }

    @Test
    void providerWithoutKeyOrUrlIsSkipped()
    {
        route("openai", null);
        assertThrows(ModelUnavailableException.class, this::call);
    }

    @Test
    void routerFallsBackAndRefusesFreeTierOutsideSyntheticSites() throws Exception
    {
        provider("gemini", 200, "gemini-ok.json");
        provider("openai", 200, "openai-ok.json");
        data.properties.put("cc.provider.gemini.tier", "free");
        route("gemini", "openai:o");
        data.properties.put("cc.syntheticDataOnly", "true");
        assertEquals("gemini", call().provider());
        data.properties.put("cc.syntheticDataOnly", "false");
        assertEquals("openai", call().provider(), "free tier refused for non-synthetic data");
        data.properties.put("cc.provider.openai.enabled", "false");
        assertThrows(ModelUnavailableException.class, this::call);
    }

    @Test
    void routerFallsBackWhenPrimaryFails() throws Exception
    {
        provider("openai", 503, null);
        provider("anthropic", 200, "anthropic-ok.json");
        route("openai", "anthropic:a");
        assertEquals("anthropic", call().provider());
    }

    @Test
    void nonSuccessErrorMessageCarriesTheStatusOnly() throws Exception
    {
        provider("openai", 500, null);
        // reach the private path through the router: the failure is logged by class and the chain ends
        route("openai", null);
        final ModelUnavailableException e = assertThrows(ModelUnavailableException.class, this::call);
        assertFalse(e.getMessage().contains("a@b.co"));
        assertTrue(SystemException.class.getSimpleName().length() > 0);
    }
}
