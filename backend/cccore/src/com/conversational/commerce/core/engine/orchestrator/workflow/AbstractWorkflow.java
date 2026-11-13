package com.conversational.commerce.core.engine.orchestrator.workflow;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import com.conversational.commerce.core.engine.orchestrator.Hydrator;
import com.conversational.commerce.core.engine.orchestrator.ToolRegistry;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import com.conversational.commerce.core.engine.orchestrator.WorkflowTemplate;
import jakarta.annotation.Resource;
import java.util.Map;

public abstract class AbstractWorkflow implements WorkflowTemplate
{
    @Resource(name = "ccToolRegistry")
    protected ToolRegistry toolRegistry;

    @Resource(name = "ccHydrator")
    protected Hydrator hydrator;

    @Resource(name = "ccConfigurationService")
    protected CcConfigurationService configurationService;

    public void setToolRegistry(final ToolRegistry r)
    {
        this.toolRegistry = r;
    }

    public void setHydrator(final Hydrator h)
    {
        this.hydrator = h;
    }

    public void setConfigurationService(final CcConfigurationService c)
    {
        this.configurationService = c;
    }

    protected ToolResult tool(final TurnState state, final String labelKey, final String name, final Map<String, Object> args)
    {
        return state.step(labelKey, () -> toolRegistry.execute(state, name, args));
    }

    protected int parallelTimeoutSeconds()
    {
        return configurationService.getInt("orchestration.maxSeconds", 9);
    }
}
