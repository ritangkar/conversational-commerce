package com.conversational.commerce.facades.chat;

import com.conversational.commerce.facades.data.chat.ChatRequestData;
import com.conversational.commerce.facades.data.chat.ChatResponseData;
import com.conversational.commerce.facades.data.chat.ChatStreamEventData;
import java.util.function.Consumer;

public interface ChatFacade
{
    /** Non-streaming turn (sample shape). */
    ChatResponseData chat(ChatRequestData request);

    /** Validates the request on the request thread so a bad request returns 400 before any stream starts. */
    void validate(ChatRequestData request);

    /** Streaming turn: events are pushed to the listener on the calling thread. The caller owns threading and timeouts. */
    void streamChat(ChatRequestData request, Consumer<ChatStreamEventData> listener);
}
