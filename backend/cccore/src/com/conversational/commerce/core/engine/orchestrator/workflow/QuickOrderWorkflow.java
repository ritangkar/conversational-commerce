package com.conversational.commerce.core.engine.orchestrator.workflow;

import com.conversational.commerce.core.engine.model.Blocks.Block;
import com.conversational.commerce.core.engine.model.Blocks.ListLine;
import com.conversational.commerce.core.engine.model.Decision;
import com.conversational.commerce.core.engine.model.Intent;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import com.conversational.commerce.core.engine.tools.QuickOrderValidateTool.Line;
import com.conversational.commerce.core.engine.tools.QuickOrderValidateTool.Status;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * QUICK_ORDER (SC-03): deterministic grammar parse of the dealer's own (translated) text, then catalogue validation.
 * ASSUMPTION: the design's "model parse of residual lines" is not used; unresolved lines are returned to the dealer instead.
 */
public class QuickOrderWorkflow extends AbstractWorkflow
{
    @Override
    public Intent intent()
    {
        return Intent.QUICK_ORDER;
    }

    @Override
    public Output run(final TurnState state, final Decision decision)
    {
        final ToolResult result = tool(state, "step.readingYourList", "validateQuickOrderLines",
                Map.of("rawText", state.workingMessage));
        final List<Line> lines = result.as();
        final List<ListLine> listLines = new ArrayList<>();
        final List<String> issues = new ArrayList<>();
        int ok = 0;
        for (final Line line : lines)
        {
            if (line.resolvedCode() != null && line.quantity() != null && line.status() != Status.QUANTITY_NEEDED)
            {
                listLines.add(hydrator.line(state, line.resolvedCode(), line.quantity(), "quick order", line.issue()));
                ok++;
            }
            else
            {
                issues.add(line.rawText() + ": " + (line.issue() == null ? line.status().name() : line.issue()));
            }
        }
        final List<Block> blocks = new ArrayList<>();
        blocks.add(Block.purchaseList("b-list", listLines));
        for (final String issue : issues)
        {
            blocks.add(Block.notice("b-issue-" + blocks.size(), "quickOrderIssue", issue));
        }
        final Map<String, String> slots = new LinkedHashMap<>();
        slots.put("lineCount", String.valueOf(lines.size()));
        slots.put("okCount", String.valueOf(ok));
        slots.put("issueCount", String.valueOf(lines.size() - ok));
        return new Output(blocks, "quick_order", Map.of("lines", lines.size()), slots, null, List.of(), null);
    }
}
