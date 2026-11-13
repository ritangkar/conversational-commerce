package com.conversational.commerce.core.engine.provider;

import java.io.IOException;
import java.util.Map;

public interface HttpTransport
{
    record Response(int status, String body)
    {
    }

    Response post(String url, Map<String, String> headers, String body, int timeoutMs) throws IOException, InterruptedException;
}
