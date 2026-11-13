package com.conversational.commerce.core.engine.provider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Shared java.net.http.HttpClient with a connect timeout, as in the owner's convention. */
public class JdkHttpTransport implements HttpTransport
{
    private final HttpClient client;

    public JdkHttpTransport(final int connectTimeoutMs)
    {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
    }

    @Override
    public Response post(final String url, final Map<String, String> headers, final String body, final int timeoutMs)
            throws IOException, InterruptedException
    {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(builder::header);
        final HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }
}
