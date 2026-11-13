package com.conversational.commerce.core.engine.tools;

import com.conversational.commerce.core.engine.gateway.FaqGateway;
import com.conversational.commerce.core.engine.model.Domain.Faq;
import com.conversational.commerce.core.engine.orchestrator.ChatTool;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** FAQ only from approved content (D16). Two modes: "shortlist" (lexical) and "answer" (entry ids chosen from the shortlist). */
public class FaqTool implements ChatTool
{
    @Resource
    private FaqGateway faqGateway;

    public void setFaqGateway(final FaqGateway faqGateway)
    {
        this.faqGateway = faqGateway;
    }

    @Override
    public String name()
    {
        return "getFaqAnswer";
    }

    @Override
    public String capabilityId()
    {
        return "KN-01";
    }

    @Override
    public ToolResult execute(final TurnState state, final Map<String, Object> args)
    {
        final List<Faq> all = faqGateway.approvedFaqs(state.pack);
        final List<String> ids = ToolArgs.strings(args, "entryIds", 3);
        if (!ids.isEmpty())
        {
            final List<Faq> chosen = all.stream().filter(f -> ids.contains(f.id()) && state.provenance.contains(f.id())).toList();
            return ToolResult.of(chosen, chosen.stream().map(Faq::id).toList());
        }
        final Set<String> words = new HashSet<>(ToolArgs.strings(args, "keywords", 12).stream().map(String::toLowerCase).toList());
        final List<Faq> shortlist = new ArrayList<>(all);
        shortlist.sort(Comparator.comparingInt((Faq f) -> -overlap(f, words)).thenComparing(Faq::id));
        final List<Faq> top = shortlist.stream().filter(f -> overlap(f, words) > 0).limit(5).toList();
        return ToolResult.of(top, top.stream().map(Faq::id).toList());
    }

    private int overlap(final Faq faq, final Set<String> words)
    {
        int n = 0;
        final String hay = (faq.question() + " " + faq.answer()).toLowerCase();
        for (final String w : words)
        {
            if (w.length() > 2 && hay.contains(w))
            {
                n++;
            }
        }
        return n;
    }
}
