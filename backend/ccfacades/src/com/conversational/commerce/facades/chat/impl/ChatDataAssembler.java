package com.conversational.commerce.facades.chat.impl;

import com.conversational.commerce.core.engine.model.Blocks.Block;
import com.conversational.commerce.core.engine.model.Blocks.ComparisonRow;
import com.conversational.commerce.core.engine.model.ChatEvent;
import com.conversational.commerce.core.engine.model.TraceStep;
import com.conversational.commerce.core.engine.model.TurnRequest;
import com.conversational.commerce.core.engine.model.TurnResult;
import com.conversational.commerce.facades.data.chat.ChatBlockData;
import com.conversational.commerce.facades.data.chat.ChatContextData;
import com.conversational.commerce.facades.data.chat.ChatRequestData;
import com.conversational.commerce.facades.data.chat.ChatResponseData;
import com.conversational.commerce.facades.data.chat.ChatStreamEventData;
import com.conversational.commerce.facades.data.chat.ChatTurnData;
import com.conversational.commerce.facades.data.chat.ComparisonData;
import com.conversational.commerce.facades.data.chat.ComparisonRowData;
import com.conversational.commerce.facades.data.chat.OrderSummaryData;
import com.conversational.commerce.facades.data.chat.ProductCardData;
import com.conversational.commerce.facades.data.chat.PurchaseLineData;
import com.conversational.commerce.facades.data.chat.PurchaseListLineData;
import com.conversational.commerce.facades.data.chat.RecommendationData;
import com.conversational.commerce.facades.data.chat.TraceStepData;
import com.conversational.commerce.facades.data.chat.WelcomeItemData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/** Manual Data construction (no populators: the owner has not supplied a populator sample, AS-03). */
public class ChatDataAssembler
{
    public TurnRequest toTurnRequest(final String customerUid, final ChatRequestData request, final String trimmedMessage)
    {
        final ChatContextData context = request.getContext();
        final List<TurnRequest.ContextTurn> turns = new ArrayList<>();
        final List<String> shortlist = new ArrayList<>();
        if (context != null)
        {
            for (final ChatTurnData t : safe(context.getTurns()))
            {
                turns.add(new TurnRequest.ContextTurn(t.getRole(), t.getText()));
            }
            shortlist.addAll(safe(context.getShortlistCodes()));
        }
        final List<TurnRequest.PurchaseLine> list = new ArrayList<>();
        for (final PurchaseLineData l : safe(request.getPurchaseList()))
        {
            list.add(new TurnRequest.PurchaseLine(l.getProductCode(), l.getQuantity() == null ? 0 : l.getQuantity(), l.getNote()));
        }
        return new TurnRequest(customerUid, trimmedMessage, request.getClientRequestId(), request.getUiLocale(), turns, shortlist, list);
    }

    public ChatResponseData toResponse(final TurnResult result)
    {
        final ChatResponseData data = new ChatResponseData();
        data.setResponseId(result.responseId());
        data.setLanguage(result.language());
        data.setBlocks(map(result.blocks(), this::toBlock));
        data.setSuggestedActions(result.suggestedActions());
        data.setFeedbackHandle(result.responseId());
        data.setTraceRef(result.responseId());
        data.setTrace(map(result.trace(), this::toTrace));
        data.setNotices(result.blocks().stream().filter(b -> "notice".equals(b.type())).map(Block::noticeKey).toList());
        return data;
    }

    public ChatStreamEventData toEvent(final ChatEvent event)
    {
        final ChatStreamEventData data = new ChatStreamEventData();
        data.setEvent(event.event());
        data.setStepId(event.stepId());
        data.setLabelKey(event.labelKey());
        data.setStatus(event.status());
        data.setBlock(event.block() == null ? null : toBlock(event.block()));
        data.setDelta(event.delta());
        data.setCode(event.code());
        data.setResponseId(event.responseId());
        data.setTrace(map(event.trace(), this::toTrace));
        return data;
    }

    public ChatBlockData toBlock(final Block b)
    {
        final ChatBlockData data = new ChatBlockData();
        data.setBlockId(b.blockId());
        data.setType(b.type());
        data.setText(b.text());
        data.setNoticeKey(b.noticeKey());
        data.setEstimate(b.estimate());
        data.setClarification(b.clarification());
        if (b.productCards() != null)
        {
            data.setProductCards(map(b.productCards(), c ->
            {
                final ProductCardData d = new ProductCardData();
                d.setCode(c.code());
                d.setName(c.name());
                d.setPriceFormatted(c.priceFormatted());
                d.setStockLabel(c.stockLabel());
                d.setStockIsDemo(c.stockIsDemo());
                d.setReasonSlots(c.reasonSlots());
                return d;
            }));
        }
        if (b.comparison() != null)
        {
            final ComparisonData d = new ComparisonData();
            d.setColumns(b.comparison().columns());
            d.setRows(map(b.comparison().rows(), (ComparisonRow r) ->
            {
                final ComparisonRowData row = new ComparisonRowData();
                row.setAttributeLabel(r.attributeLabel());
                row.setValues(r.values());
                return row;
            }));
            d.setNotes(b.comparison().notes());
            data.setComparison(d);
        }
        if (b.orderSummaries() != null)
        {
            data.setOrderSummaries(map(b.orderSummaries(), o ->
            {
                final OrderSummaryData d = new OrderSummaryData();
                d.setCode(o.code());
                d.setPlacedOn(o.placedOn());
                d.setStatusLabel(o.statusLabel());
                d.setTotalFormatted(o.totalFormatted());
                d.setTrackingReference(o.trackingReference());
                d.setStatusSource(o.statusSource());
                return d;
            }));
        }
        if (b.purchaseListLines() != null)
        {
            data.setPurchaseListLines(map(b.purchaseListLines(), this::toLine));
        }
        if (b.recommendations() != null)
        {
            data.setRecommendations(map(b.recommendations(), r ->
            {
                final RecommendationData d = new RecommendationData();
                d.setCode(r.code());
                d.setName(r.name());
                d.setQuantity(r.quantity());
                d.setReasonKey(r.reasonKey());
                d.setEstimate(r.estimate());
                return d;
            }));
        }
        if (b.welcomeItems() != null)
        {
            data.setWelcomeItems(map(b.welcomeItems(), w ->
            {
                final WelcomeItemData d = new WelcomeItemData();
                d.setKind(w.kind());
                d.setTextKey(w.textKey());
                d.setParams(w.params());
                return d;
            }));
        }
        return data;
    }

    public PurchaseListLineData toLine(final com.conversational.commerce.core.engine.model.Blocks.ListLine l)
    {
        final PurchaseListLineData d = new PurchaseListLineData();
        d.setProductCode(l.productCode());
        d.setName(l.name());
        d.setQuantity(l.quantity());
        d.setNote(l.note());
        d.setOrigin(l.origin());
        d.setLineFormatted(l.lineFormatted());
        d.setIssue(l.issue());
        return d;
    }

    private TraceStepData toTrace(final TraceStep t)
    {
        final TraceStepData d = new TraceStepData();
        d.setLabel(t.label());
        d.setSource(t.source());
        d.setWindow(t.window());
        d.setThreshold(t.threshold());
        return d;
    }

    private <S, T> List<T> map(final List<S> source, final Function<S, T> fn)
    {
        return source == null ? Collections.emptyList() : source.stream().map(fn).toList();
    }

    private <T> List<T> safe(final List<T> list)
    {
        return list == null ? Collections.emptyList() : list;
    }
}
