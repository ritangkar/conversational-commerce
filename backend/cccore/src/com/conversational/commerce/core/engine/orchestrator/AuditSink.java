package com.conversational.commerce.core.engine.orchestrator;

/** SPI (PL-08). Default writes structured lines through logger cc.audit (A14). */
public interface AuditSink
{
    void log(AuditEvent event);
}
