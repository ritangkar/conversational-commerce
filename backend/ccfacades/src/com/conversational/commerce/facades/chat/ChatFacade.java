package com.conversational.commerce.facades.chat;

import com.conversational.commerce.facades.data.chat.ChatPublicConfigData;
import com.conversational.commerce.facades.data.chat.ChatRequestData;
import com.conversational.commerce.facades.data.chat.ChatResponseData;
import com.conversational.commerce.facades.data.chat.ChatStreamEventData;
import com.conversational.commerce.facades.data.chat.FeedbackData;
import com.conversational.commerce.facades.data.chat.PurchaseListData;
import com.conversational.commerce.facades.data.chat.PurchaseListResultData;
import com.conversational.commerce.facades.data.chat.WelcomePanelData;
import java.util.function.Consumer;

/** The single facade of the chat feature: every method takes and returns *Data and validates its input. */
public interface ChatFacade
{
    /** Non-streaming turn (sample shape). */
    ChatResponseData chat(ChatRequestData request);

    /** Validates the request on the request thread so a bad request returns 400 before any stream starts. */
    void validate(ChatRequestData request);

    /** Streaming turn: events are pushed to the listener on the calling thread. The caller owns threading and timeouts. */
    void streamChat(ChatRequestData request, Consumer<ChatStreamEventData> listener);

    WelcomePanelData getWelcomePanel();

    void submitFeedback(FeedbackData feedback);

    PurchaseListResultData validatePurchaseList(PurchaseListData list);

    ChatPublicConfigData getPublicConfig();

    /** Resolved from the authenticated session, never from the request. Used by the controller's user-binding check. */
    String getCurrentUid();

    /** Audit line for rejected requests (metadata only). */
    void audit(String event, String... keyValuePairs);
}
