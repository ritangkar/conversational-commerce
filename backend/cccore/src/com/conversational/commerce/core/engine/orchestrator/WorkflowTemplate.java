package com.conversational.commerce.core.engine.orchestrator;

import com.conversational.commerce.core.engine.model.Blocks.Block;
import com.conversational.commerce.core.engine.model.Blocks.Comparison;
import com.conversational.commerce.core.engine.model.Decision;
import com.conversational.commerce.core.engine.model.Intent;
import java.util.List;
import java.util.Map;

/**
 * SPI (PL-08): an ordered graph of deterministic steps for one intent. The model may choose only the intent; the steps,
 * limits and parallel groups are fixed in Java.
 */
public interface WorkflowTemplate
{
    /** @param blocks blocks without the final text; @param templateKey fixed text key; @param facts text facts for COMPOSE;
     *  @param slots backend-formatted figures; @param table non-null selects the EXPLAIN task and the table check */
    record Output(List<Block> blocks, String templateKey, Map<String, Object> facts, Map<String, String> slots, Comparison table,
            List<String> suggestedActions, String interestCode)
    {
        public static Output of(final List<Block> blocks, final String templateKey, final Map<String, Object> facts,
                final Map<String, String> slots)
        {
            return new Output(blocks, templateKey, facts, slots, null, List.of(), null);
        }
    }

    Intent intent();

    Output run(TurnState state, Decision decision);
}
