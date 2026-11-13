package com.conversational.commerce.core.engine.model;

import com.conversational.commerce.core.engine.model.Blocks.Block;
import java.util.List;

public record TurnResult(String responseId, String language, Intent intent, List<Block> blocks, List<String> suggestedActions,
        List<TraceStep> trace, int tokensIn, int tokensOut)
{
}
