package com.conversational.commerce.core.engine.orchestrator;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Log4jAuditSink implements AuditSink
{
    private static final Logger AUDIT = LogManager.getLogger("cc.audit");

    @Override
    public void log(final AuditEvent event)
    {
        final StringBuilder line = new StringBuilder();
        for (final Map.Entry<String, String> e : event.fields().entrySet())
        {
            line.append(e.getKey()).append('=').append(e.getValue().replaceAll("[\\s=]", "_")).append(' ');
        }
        AUDIT.info(line.toString().trim());
    }
}
