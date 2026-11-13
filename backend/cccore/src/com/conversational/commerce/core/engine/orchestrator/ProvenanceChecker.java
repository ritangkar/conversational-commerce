package com.conversational.commerce.core.engine.orchestrator;

import java.util.ArrayList;
import java.util.List;

/** A code is accepted only if this turn's tools returned it (D26). Others are dropped and audited as id_rejected. */
public class ProvenanceChecker
{
    public List<String> filter(final TurnState state, final List<String> proposed, final AuditSink audit)
    {
        final List<String> accepted = new ArrayList<>();
        int rejected = 0;
        for (final String code : proposed)
        {
            if (state.provenance.contains(code))
            {
                accepted.add(code);
            }
            else
            {
                rejected++;
            }
        }
        if (rejected > 0)
        {
            audit.log(AuditEvent.of("id_rejected").with("responseId", state.responseId).with("count", rejected));
        }
        return accepted;
    }
}
