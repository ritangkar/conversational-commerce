package com.conversational.commerce.core.engine.orchestrator.workflow;

import com.conversational.commerce.core.engine.model.Blocks.Block;
import com.conversational.commerce.core.engine.model.Decision;
import com.conversational.commerce.core.engine.model.Domain.Faq;
import com.conversational.commerce.core.engine.model.Intent;
import com.conversational.commerce.core.engine.orchestrator.RelevanceJudge;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** FAQ (KN-01, D16): lexical shortlist, model picks ids, the answer is the approved text, never model prose. */
public class FaqWorkflow extends AbstractWorkflow
{
    @Resource
    private RelevanceJudge relevanceJudge;

    public void setRelevanceJudge(final RelevanceJudge j)
    {
        this.relevanceJudge = j;
    }

    @Override
    public Intent intent()
    {
        return Intent.FAQ;
    }

    @Override
    public Output run(final TurnState state, final Decision decision)
    {
        final ToolResult shortlist = tool(state, "step.searchingApprovedAnswers", "getFaqAnswer",
                Map.of("keywords", decision.keywords()));
        final List<Faq> candidates = shortlist.as();
        if (candidates.isEmpty())
        {
            return Output.of(List.of(), "faq_none", Map.of(), Map.of());
        }
        final Map<String, String> idToQuestion = new LinkedHashMap<>();
        candidates.forEach(f -> idToQuestion.put(f.id(), f.question()));
        final List<String> chosen = state.step("step.pickingAnswer",
                () -> relevanceJudge.judgeFaq(state, String.join(" ", decision.keywords()), idToQuestion));
        if (chosen.isEmpty())
        {
            return Output.of(List.of(), "faq_none", Map.of(), Map.of());
        }
        final ToolResult answers = tool(state, "step.readingApprovedAnswer", "getFaqAnswer", Map.of("entryIds", chosen.subList(0, 1)));
        final List<Faq> faqs = answers.as();
        return Output.of(List.of(Block.text("b-faq", faqs.get(0).answer())), null, Map.of(), Map.of());
    }
}
