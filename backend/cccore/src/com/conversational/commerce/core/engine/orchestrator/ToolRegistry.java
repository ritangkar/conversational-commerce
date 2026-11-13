package com.conversational.commerce.core.engine.orchestrator;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** The only way to run a tool. Rejects unknown tools and disabled capabilities, and records provenance and trace. */
public class ToolRegistry
{
    public record Call(String tool, Map<String, Object> args)
    {
    }

    @Resource(name = "ccChatTools")
    private List<ChatTool> tools;

    @Resource(name = "ccConfigurationService")
    private CcConfigurationService configurationService;

    @Resource(name = "ccAuditSink")
    private AuditSink auditSink;

    private final ExecutorService executor = Executors.newFixedThreadPool(4, r ->
    {
        final Thread t = new Thread(r, "cc-tool");
        t.setDaemon(true);
        return t;
    });

    public void setTools(final List<ChatTool> tools)
    {
        this.tools = tools;
    }

    public void setConfigurationService(final CcConfigurationService configurationService)
    {
        this.configurationService = configurationService;
    }

    public void setAuditSink(final AuditSink auditSink)
    {
        this.auditSink = auditSink;
    }

    public ToolResult execute(final TurnState state, final String name, final Map<String, Object> args)
    {
        final ChatTool tool = tools.stream().filter(t -> t.name().equals(name)).findFirst().orElse(null);
        if (tool == null)
        {
            reject(state, name, "unknown_tool");
        }
        if (!configurationService.isEnabled(tool.capabilityId()))
        {
            reject(state, name, "capability_disabled");
        }
        state.checkToolCall();
        final ToolResult result = tool.execute(state, args == null ? Map.of() : args);
        state.provenance.addAll(result.codes());
        state.trace.addAll(result.trace());
        auditSink.log(AuditEvent.of("tool_executed").with("responseId", state.responseId).with("toolName", name)
                .with("outcome", "ok").with("count", result.codes().size()));
        return result;
    }

    /** Runs independent tools in parallel (a workflow's parallel group); results keep the call order. */
    public List<ToolResult> executeParallel(final TurnState state, final List<Call> calls, final int timeoutSeconds)
    {
        final List<CompletableFuture<ToolResult>> futures = new ArrayList<>();
        for (final Call call : calls)
        {
            futures.add(CompletableFuture.supplyAsync(() -> execute(state, call.tool(), call.args()), executor));
        }
        final List<ToolResult> results = new ArrayList<>();
        for (final CompletableFuture<ToolResult> future : futures)
        {
            try
            {
                results.add(future.get(timeoutSeconds, TimeUnit.SECONDS));
            }
            catch (final InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new LimitExceededException("interrupted");
            }
            catch (final TimeoutException e)
            {
                throw new LimitExceededException("max_seconds");
            }
            catch (final ExecutionException e)
            {
                if (e.getCause() instanceof RuntimeException re)
                {
                    throw re;
                }
                throw new IllegalStateException(e.getCause());
            }
        }
        return results;
    }

    public Map<String, String> catalogue()
    {
        final Map<String, String> map = new HashMap<>();
        tools.forEach(t -> map.put(t.name(), t.capabilityId()));
        return map;
    }

    private void reject(final TurnState state, final String name, final String reason)
    {
        auditSink.log(AuditEvent.of("tool_rejected").with("responseId", state.responseId).with("toolName", name)
                .with("errorCode", reason));
        throw new ToolRejectedException(reason);
    }
}
