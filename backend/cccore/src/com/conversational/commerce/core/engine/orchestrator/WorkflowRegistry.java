package com.conversational.commerce.core.engine.orchestrator;

import com.conversational.commerce.core.engine.model.Intent;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;

public class WorkflowRegistry
{
    @Resource
    private List<WorkflowTemplate> workflows;

    public void setWorkflows(final List<WorkflowTemplate> workflows)
    {
        this.workflows = workflows;
    }

    public Optional<WorkflowTemplate> find(final Intent intent)
    {
        return workflows.stream().filter(w -> w.intent() == intent).findFirst();
    }
}
