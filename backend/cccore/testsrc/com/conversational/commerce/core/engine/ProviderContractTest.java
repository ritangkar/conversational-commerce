package com.conversational.commerce.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conversational.commerce.core.engine.demo.MapConfigSource;
import com.conversational.commerce.core.engine.config.DefaultCcConfigurationService;
import com.conversational.commerce.core.engine.provider.AbstractHttpAdapter;
import com.conversational.commerce.core.engine.provider.AnthropicAdapter;
import com.conversational.commerce.core.engine.provider.DefaultModelRouter;
import com.conversational.commerce.core.engine.provider.GeminiAdapter;
import com.conversational.commerce.core.engine.provider.HttpTransport;
import com.conversational.commerce.core.engine.provider.ModelProviderAdapter;
import com.conversational.commerce.core.engine.provider.ModelRequest;
import com.conversational.commerce.core.engine.provider.ModelResult;
import com.conversational.commerce.core.engine.provider.ModelRouter;
import com.conversational.commerce.core.engine.provider.ModelTask;
import com.conversational.commerce.core.engine.provider.OpenAiAdapter;
import com.conversational.commerce.core.engine.orchestrator.PromptRepository;
import com.conversational.commerce.core.engine.sanitiser.DefaultSanitiser;
import com.conversational.commerce.core.engine.sanitiser.RegexMaskingRule;
import com.conversational.commerce.core.engine.sanitiser.TokenMap;
import de.hybris.platform.servicelayer.exceptions.SystemException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Contract tests from recorded (synthetic) fixtures. No key and no network are needed. Request shapes are UNVERIFIED (S3). */
class ProviderContractTest
{
    private final DefaultCcConfigurationService config = new DefaultCcConfigurationService();
    private final MapConfigSource props = new MapConfigSource();
    private final List<String> sentUrls = new ArrayList<>();
    private final List<String> sentBodies = new ArrayList<>();
    private final List<Map<String, String>> sentHeaders = new ArrayList<>();

    private HttpTransport transport(final int status, final String fixture) throws IOException
    {
        final String body = fixture == null ? "" : Files.readString(Path.of("testsrc/fixtures/providers/" + fixture));
        return (url, headers, requestBody, timeout) ->
        {
            sentUrls.add(url);
            sentBodies.add(requestBody);
            sentHeaders.add(headers);
            return new HttpTransport.Response(status, body);
        };
    }

    private ModelRequest request()
    {
        final DefaultSanitiser sanitiser = new DefaultSanitiser();
        sanitiser.setConfigurationService(config);
        sanitiser.setRules(List.of(RegexMaskingRule.EMAIL));
        final PromptRepository prompts = new PromptRepository();
        return new ModelRequest(ModelTask.COMPOSE, "compose.v1", prompts.prompt("compose.v1"),
                sanitiser.sanitise(ModelTask.COMPOSE, Map.of("intent", "x", "facts", Map.of("n", "a@b.co")), new TokenMap(), List.of()),
                prompts.schema("compose.v1"), 200, 0.0);
    }

    private <T extends AbstractHttpAdapter> T adapter(final T a, final String code, final HttpTransport t)
    {
        config.setSources(List.of(props));
        props.put("cc.provider." + code + ".baseUrl", "https://provider.invalid").put("cc.provider.key." + code, "test-only-not-a-key");
        a.setConfigurationService(config);
        a.setTransport(t);
        return a;
    }

    @Test
    void openAiShapeAndParsing() throws Exception
    {
        final OpenAiAdapter a = adapter(new OpenAiAdapter(), "openai", transport(200, "openai-ok.json"));
        final ModelResult r = a.call("some-model", request());
        assertEquals("hello", r.json().path("text").asText());
        assertEquals(11, r.tokensIn());
        assertEquals(3, r.tokensOut());
        final String body = sentBodies.get(0);
        assertTrue(body.contains("\"store\":false") && body.contains("\"strict\":true") && body.contains("json_schema"));
        assertTrue(sentUrls.get(0).endsWith("/v1/chat/completions"));
        assertEquals("Bearer test-only-not-a-key", sentHeaders.get(0).get("Authorization"));
        assertFalse(body.contains("a@b.co"), "payload was sanitised before the adapter");
    }

    @Test
    void anthropicUsesForcedToolWithSchema() throws Exception
    {
        final AnthropicAdapter a = adapter(new AnthropicAdapter(), "anthropic", transport(200, "anthropic-ok.json"));
        final ModelResult r = a.call("some-model", request());
        assertEquals("hello", r.json().path("text").asText());
        assertEquals(12, r.tokensIn());
        assertTrue(sentBodies.get(0).contains("\"tool_choice\":{\"type\":\"tool\""));
        assertTrue(sentHeaders.get(0).containsKey("x-api-key") && sentHeaders.get(0).containsKey("anthropic-version"));
    }

    @Test
    void geminiStripsAdditionalProperties() throws Exception
    {
        final GeminiAdapter a = adapter(new GeminiAdapter(), "gemini", transport(200, "gemini-ok.json"));
        final ModelResult r = a.call("some-model", request());
        assertEquals("hello", r.json().path("text").asText());
        assertEquals(13, r.tokensIn());
        assertFalse(sentBodies.get(0).contains("additionalProperties"));
        assertTrue(sentBodies.get(0).contains("responseSchema"));
        assertTrue(sentUrls.get(0).contains(":generateContent"));
    }

    @Test
    void non200ThrowsSystemExceptionWithoutLeakingTheBody() throws Exception
    {
        final OpenAiAdapter a = adapter(new OpenAiAdapter(), "openai", transport(500, null));
        final SystemException e = assertThrows(SystemException.class, () -> a.call("m", request()));
        assertTrue(e.getMessage().contains("500"));
    }

    @Test
    void nullContentThrowsSystemException() throws Exception
    {
        final OpenAiAdapter a = adapter(new OpenAiAdapter(), "openai", (u, h, b, t) -> new HttpTransport.Response(200, "{}"));
        assertThrows(SystemException.class, () -> a.call("m", request()));
    }

    @Test
    void adapterWithoutKeyOrUrlIsUnavailable()
    {
        config.setSources(List.of(props));
        final OpenAiAdapter a = new OpenAiAdapter();
        a.setConfigurationService(config);
        assertFalse(a.isAvailable());
    }

    @Test
    void routerFallsBackAndRefusesFreeTierOutsideSyntheticSites() throws Exception
    {
        final GeminiAdapter gemini = adapter(new GeminiAdapter(), "gemini", transport(200, "gemini-ok.json"));
        final OpenAiAdapter openai = adapter(new OpenAiAdapter(), "openai", transport(200, "openai-ok.json"));
        props.put("cc.config.cacheTtlSeconds", "0").put("cc.provider.gemini.tier", "free").put("route.compose.provider", "gemini").put("route.compose.model", "g")
                .put("route.compose.fallback", "openai:o");
        final DefaultModelRouter router = new DefaultModelRouter();
        router.setConfigurationService(config);
        router.setAdapters(List.<ModelProviderAdapter>of(gemini, openai));
        props.put("cc.syntheticDataOnly", "true");
        assertEquals("gemini", router.call(request()).provider());
        props.put("cc.syntheticDataOnly", "false");
        assertEquals("openai", router.call(request()).provider(), "free tier refused for non-synthetic data");
        props.put("cc.provider.openai.enabled", "false");
        assertThrows(ModelRouter.ModelUnavailableException.class, () -> router.call(request()));
    }

    @Test
    void routerFallsBackWhenPrimaryFails() throws Exception
    {
        final OpenAiAdapter failing = adapter(new OpenAiAdapter(), "openai", transport(503, null));
        final AnthropicAdapter ok = adapter(new AnthropicAdapter(), "anthropic", transport(200, "anthropic-ok.json"));
        props.put("route.compose.provider", "openai").put("route.compose.model", "o").put("route.compose.fallback", "anthropic:a");
        final DefaultModelRouter router = new DefaultModelRouter();
        router.setConfigurationService(config);
        router.setAdapters(List.<ModelProviderAdapter>of(failing, ok));
        assertEquals("anthropic", router.call(request()).provider());
    }
}
