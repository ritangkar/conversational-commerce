package com.conversational.commerce.facades.chat.impl;

import com.conversational.commerce.core.service.ChatService;
import com.conversational.commerce.core.service.ChatService.Block;
import com.conversational.commerce.core.service.ChatService.ChatEvent;
import com.conversational.commerce.core.service.ChatService.ContextTurn;
import com.conversational.commerce.core.service.ChatService.ListLine;
import com.conversational.commerce.core.service.ChatService.PublicConfig;
import com.conversational.commerce.core.service.ChatService.PurchaseLine;
import com.conversational.commerce.core.service.ChatService.PurchaseListResult;
import com.conversational.commerce.core.service.ChatService.TraceStep;
import com.conversational.commerce.core.service.ChatService.TurnRequest;
import com.conversational.commerce.core.service.ChatService.TurnResult;
import com.conversational.commerce.facades.chat.ChatFacade;
import com.conversational.commerce.facades.data.chat.ChatBlockData;
import com.conversational.commerce.facades.data.chat.ChatContextData;
import com.conversational.commerce.facades.data.chat.ChatPublicConfigData;
import com.conversational.commerce.facades.data.chat.ChatRequestData;
import com.conversational.commerce.facades.data.chat.ChatResponseData;
import com.conversational.commerce.facades.data.chat.ChatStreamEventData;
import com.conversational.commerce.facades.data.chat.ChatTurnData;
import com.conversational.commerce.facades.data.chat.ComparisonData;
import com.conversational.commerce.facades.data.chat.ComparisonRowData;
import com.conversational.commerce.facades.data.chat.FeedbackData;
import com.conversational.commerce.facades.data.chat.OrderSummaryData;
import com.conversational.commerce.facades.data.chat.ProductCardData;
import com.conversational.commerce.facades.data.chat.PurchaseLineData;
import com.conversational.commerce.facades.data.chat.PurchaseListData;
import com.conversational.commerce.facades.data.chat.PurchaseListLineData;
import com.conversational.commerce.facades.data.chat.PurchaseListResultData;
import com.conversational.commerce.facades.data.chat.RecommendationData;
import com.conversational.commerce.facades.data.chat.TraceStepData;
import com.conversational.commerce.facades.data.chat.WelcomeItemData;
import com.conversational.commerce.facades.data.chat.WelcomePanelData;
import de.hybris.platform.servicelayer.user.UserService;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;

/**
 * Validates input as in the owner's sample (null/blank check, trim, max length, IllegalArgumentException), takes the customer from
 * the authenticated session (never from the request) and maps between *Data and the service types by hand: the owner supplied
 * no populator sample (AS-03).
 */
public class DefaultChatFacade implements ChatFacade
{
    // ASSUMPTION (AS-04): the declaration of MAX_MESSAGE_LENGTH was not visible in the sample; it follows cc.message.maxLength (1000).
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_LIST_LINES = 100;

    @Resource(name = "ccChatService")
    private ChatService chatService;

    @Resource
    private UserService userService;

    // ---------------------------------------------------------------- turns
    @Override
    public ChatResponseData chat(final ChatRequestData request)
    {
        final TurnRequest turn = toTurnRequest(request);
        final TurnResult result = chatService.handle(turn, ChatService.ChatEventListener.NOOP);
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

    @Override
    public void validate(final ChatRequestData request)
    {
        trimmedMessage(request);
    }

    @Override
    public void streamChat(final ChatRequestData request, final Consumer<ChatStreamEventData> listener)
    {
        chatService.handle(toTurnRequest(request), event -> listener.accept(toEvent(event)));
    }

    private String trimmedMessage(final ChatRequestData request)
    {
        if (request == null || StringUtils.isBlank(request.getMessage()))
        {
            throw new IllegalArgumentException("message must not be empty");
        }
        final String message = request.getMessage().trim();
        if (message.length() > MAX_MESSAGE_LENGTH)
        {
            throw new IllegalArgumentException("message must not exceed " + MAX_MESSAGE_LENGTH + " characters");
        }
        return message;
    }

    private TurnRequest toTurnRequest(final ChatRequestData request)
    {
        final String message = trimmedMessage(request);
        final ChatContextData context = request.getContext();
        final List<ContextTurn> turns = new ArrayList<>();
        final List<String> shortlist = new ArrayList<>();
        if (context != null)
        {
            for (final ChatTurnData t : safe(context.getTurns()))
            {
                turns.add(new ContextTurn(t.getRole(), t.getText()));
            }
            shortlist.addAll(safe(context.getShortlistCodes()));
        }
        return new TurnRequest(getCurrentUid(), message, request.getClientRequestId(), request.getUiLocale(), turns, shortlist,
                toPurchaseLines(request.getPurchaseList()));
    }

    private List<PurchaseLine> toPurchaseLines(final List<PurchaseLineData> lines)
    {
        final List<PurchaseLine> out = new ArrayList<>();
        for (final PurchaseLineData l : safe(lines))
        {
            out.add(new PurchaseLine(l.getProductCode(), l.getQuantity() == null ? 0 : l.getQuantity(), l.getNote(), l.getOrigin()));
        }
        return out;
    }

    // ---------------------------------------------------------------- welcome, feedback, list, config
    @Override
    public WelcomePanelData getWelcomePanel()
    {
        final WelcomePanelData panel = new WelcomePanelData();
        panel.setItems(toBlock(chatService.buildWelcome(getCurrentUid())).getWelcomeItems());
        return panel;
    }

    @Override
    public void submitFeedback(final FeedbackData feedback)
    {
        if (feedback == null || StringUtils.isBlank(feedback.getResponseId()))
        {
            throw new IllegalArgumentException("responseId must not be empty");
        }
        chatService.saveFeedback(feedback.getResponseId(), feedback.getRating(), feedback.getIntent(), feedback.getToolsUsed(),
                feedback.getAuditRef());
    }

    @Override
    public PurchaseListResultData validatePurchaseList(final PurchaseListData list)
    {
        if (list == null || list.getLines() == null)
        {
            throw new IllegalArgumentException("lines must not be empty");
        }
        if (list.getLines().size() > MAX_LIST_LINES)
        {
            throw new IllegalArgumentException("lines must not exceed " + MAX_LIST_LINES);
        }
        final PurchaseListResult result = chatService.validatePurchaseList(getCurrentUid(), toPurchaseLines(list.getLines()));
        final PurchaseListResultData data = new PurchaseListResultData();
        data.setLines(map(result.lines(), this::toLine));
        data.setIssues(result.issues());
        data.setTotalFormatted(result.totalFormatted());
        data.setDemoLabel(result.demoLabel());
        return data;
    }

    @Override
    public ChatPublicConfigData getPublicConfig()
    {
        final PublicConfig c = chatService.getPublicConfig();
        final ChatPublicConfigData data = new ChatPublicConfigData();
        data.setEnabledCapabilities(c.enabledCapabilities());
        data.setActivePack(c.activePack());
        data.setInterestNoticeText(c.interestNoticeText());
        data.setInterestEventsEnabled(c.interestEventsEnabled());
        data.setStockDemoNotice(c.stockDemoNotice());
        data.setPurchaseListLabel(c.purchaseListLabel());
        data.setSamplePrompts(c.samplePrompts());
        data.setTerminology(c.terminology());
        data.setThemeTokens(c.themeTokens());
        data.setMaxMessageLength(c.maxMessageLength());
        data.setContactText(c.contactText());
        return data;
    }

    @Override
    public String getCurrentUid()
    {
        return userService.getCurrentUser().getUid();
    }

    @Override
    public void audit(final String event, final String... keyValuePairs)
    {
        chatService.audit(event, keyValuePairs);
    }

    // ---------------------------------------------------------------- manual mapping (service type -> *Data)
    private ChatStreamEventData toEvent(final ChatEvent e)
    {
        final ChatStreamEventData data = new ChatStreamEventData();
        data.setEvent(e.event());
        data.setStepId(e.stepId());
        data.setLabelKey(e.labelKey());
        data.setStatus(e.status());
        data.setBlock(e.block() == null ? null : toBlock(e.block()));
        data.setDelta(e.delta());
        data.setCode(e.code());
        data.setResponseId(e.responseId());
        data.setTrace(map(e.trace(), this::toTrace));
        return data;
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

    private PurchaseListLineData toLine(final ListLine l)
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

    private ChatBlockData toBlock(final Block b)
    {
        final ChatBlockData data = new ChatBlockData();
        data.setBlockId(b.blockId());
        data.setType(b.type());
        data.setText(b.text());
        data.setNoticeKey(b.noticeKey());
        data.setEstimate(b.estimate());
        data.setClarification(b.clarification());
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
        if (b.comparison() != null)
        {
            final ComparisonData d = new ComparisonData();
            d.setColumns(b.comparison().columns());
            d.setRows(map(b.comparison().rows(), r ->
            {
                final ComparisonRowData row = new ComparisonRowData();
                row.setAttributeLabel(r.attributeLabel());
                row.setValues(r.values());
                return row;
            }));
            d.setNotes(b.comparison().notes());
            data.setComparison(d);
        }
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
        data.setPurchaseListLines(map(b.purchaseListLines(), this::toLine));
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
        data.setWelcomeItems(map(b.welcomeItems(), w ->
        {
            final WelcomeItemData d = new WelcomeItemData();
            d.setKind(w.kind());
            d.setTextKey(w.textKey());
            d.setParams(w.params());
            return d;
        }));
        return data;
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
