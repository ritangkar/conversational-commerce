package com.conversational.commerce.core.engine.tools;

import com.conversational.commerce.core.engine.gateway.CatalogGateway;
import com.conversational.commerce.core.engine.model.Domain.Product;
import com.conversational.commerce.core.engine.orchestrator.ChatTool;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import com.conversational.commerce.core.engine.rules.QuickOrderParser;
import com.conversational.commerce.core.engine.rules.QuickOrderParser.RawLine;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Quick order (PA-03): grammar parse, then validate each line against the catalogue. */
public class QuickOrderValidateTool implements ChatTool
{
    public enum Status
    {
        OK, REPLACED, DISCONTINUED_NO_REPLACEMENT, NOT_FOUND, AMBIGUOUS, QUANTITY_NEEDED
    }

    public record Line(String rawText, String resolvedCode, Integer quantity, Status status, String issue,
            List<String> options)
    {
    }

    @Resource
    private CatalogGateway catalogGateway;

    private final QuickOrderParser parser = new QuickOrderParser();

    public void setCatalogGateway(final CatalogGateway catalogGateway)
    {
        this.catalogGateway = catalogGateway;
    }

    @Override
    public String name()
    {
        return "validateQuickOrderLines";
    }

    @Override
    public String capabilityId()
    {
        return "PA-03";
    }

    @Override
    public ToolResult execute(final TurnState state, final Map<String, Object> args)
    {
        final String text = ToolArgs.string(args, "rawText", "");
        final List<Line> out = new ArrayList<>();
        for (final RawLine raw : parser.parse(text))
        {
            out.add(validate(state, raw));
        }
        final List<String> codes = out.stream().map(Line::resolvedCode).filter(c -> c != null).toList();
        final List<String> optionCodes = out.stream().flatMap(l -> l.options().stream()).toList();
        final List<String> all = new ArrayList<>(codes);
        all.addAll(optionCodes);
        return ToolResult.of(out, all);
    }

    private Line validate(final TurnState state, final RawLine raw)
    {
        Optional<Product> product = Optional.empty();
        if (raw.kind() == QuickOrderParser.Kind.CODE || raw.kind() == QuickOrderParser.Kind.REPLACEMENT_OF)
        {
            product = catalogGateway.byCode(state.pack, raw.codeHint());
        }
        else
        {
            final List<String> words = List.of(raw.description().toLowerCase().split("\\s+"));
            final List<Product> all = catalogGateway.findCandidates(state.pack, words, null, Map.of(), 5);
            final List<Product> active = all.stream().filter(c -> !c.discontinued()).toList();
            final List<Product> pool = active.isEmpty() ? all : active;
            // every word must match, otherwise "cable ties" would be a candidate for "cable 100m"
            final List<Product> strict = pool.stream().filter(c -> containsAll(c, words)).toList();
            final List<Product> found = strict.isEmpty() ? pool : strict;
            if (found.size() > 1)
            {
                return new Line(raw.rawText(), null, raw.quantity(), Status.AMBIGUOUS, "Several products match",
                        found.stream().map(Product::code).toList());
            }
            product = found.stream().findFirst();
        }
        if (product.isEmpty())
        {
            return new Line(raw.rawText(), null, raw.quantity(), Status.NOT_FOUND, "Not found in the catalogue", List.of());
        }
        Product p = product.get();
        Status status = Status.OK;
        String issue = null;
        if (p.discontinued() || raw.kind() == QuickOrderParser.Kind.REPLACEMENT_OF)
        {
            final Optional<Product> replacement = p.replacementCode() == null ? Optional.empty()
                    : catalogGateway.byCode(state.pack, p.replacementCode());
            if (replacement.isPresent())
            {
                issue = p.code() + " is replaced by " + replacement.get().code();
                p = replacement.get();
                status = Status.REPLACED;
            }
            else if (p.discontinued())
            {
                return new Line(raw.rawText(), null, raw.quantity(), Status.DISCONTINUED_NO_REPLACEMENT,
                        p.code() + " is discontinued with no replacement", List.of());
            }
        }
        if (raw.quantity() == null)
        {
            return new Line(raw.rawText(), p.code(), null, Status.QUANTITY_NEEDED, "Quantity needed", List.of());
        }
        return new Line(raw.rawText(), p.code(), raw.quantity(), status, issue, List.of());
    }

    private boolean containsAll(final Product product, final List<String> words)
    {
        final String hay = (product.name() + " " + product.description() + " " + String.join(" ", product.attributes().values()))
                .toLowerCase();
        return words.stream().allMatch(hay::contains);
    }
}
