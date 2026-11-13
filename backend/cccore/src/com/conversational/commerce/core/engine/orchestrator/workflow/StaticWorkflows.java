package com.conversational.commerce.core.engine.orchestrator.workflow;

import com.conversational.commerce.core.engine.model.Decision;
import com.conversational.commerce.core.engine.model.Intent;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import com.conversational.commerce.core.engine.orchestrator.WorkflowTemplate;
import java.util.List;
import java.util.Map;

/** OFF_TOPIC, UNSUPPORTED, CLARIFY and SWITCH_PACK: no tools, fixed text (CF-06, D28). SC-10 is configuration only. */
public final class StaticWorkflows
{
    private StaticWorkflows()
    {
    }

    public abstract static class Fixed implements WorkflowTemplate
    {
        private final Intent intent;
        private final String templateKey;

        Fixed(final Intent intent, final String templateKey)
        {
            this.intent = intent;
            this.templateKey = templateKey;
        }

        @Override
        public Intent intent()
        {
            return intent;
        }

        @Override
        public Output run(final TurnState state, final Decision decision)
        {
            return new Output(List.of(), templateKey, Map.of(), Map.of(), null, List.of(), null);
        }
    }

    public static class OffTopicWorkflow extends Fixed
    {
        public OffTopicWorkflow()
        {
            super(Intent.OFF_TOPIC, "off_topic");
        }
    }

    public static class UnsupportedWorkflow extends Fixed
    {
        public UnsupportedWorkflow()
        {
            super(Intent.UNSUPPORTED, "unsupported");
        }
    }

    public static class ClarifyWorkflow extends Fixed
    {
        public ClarifyWorkflow()
        {
            super(Intent.CLARIFY, "clarify");
        }
    }

    public static class SwitchPackWorkflow extends Fixed
    {
        public SwitchPackWorkflow()
        {
            super(Intent.SWITCH_PACK, "unsupported");
        }
    }
}
