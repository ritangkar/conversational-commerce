package com.conversational.commerce.facades.chat.impl;

import com.conversational.commerce.core.engine.model.ChatEventListener;
import com.conversational.commerce.core.engine.model.TurnRequest;
import com.conversational.commerce.core.engine.model.TurnResult;
import com.conversational.commerce.core.engine.orchestrator.ChatOrchestrationService;
import com.conversational.commerce.facades.chat.ChatFacade;
import com.conversational.commerce.facades.data.chat.ChatRequestData;
import com.conversational.commerce.facades.data.chat.ChatResponseData;
import com.conversational.commerce.facades.data.chat.ChatStreamEventData;
import de.hybris.platform.servicelayer.user.UserService;
import jakarta.annotation.Resource;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;

/**
 * The customer always comes from the authenticated session (UserService), never from the request body.
 * Validation follows the owner's sample: null/blank check, trim, max length, IllegalArgumentException.
 */
public class DefaultChatFacade implements ChatFacade
{
    // ASSUMPTION (AS-04): the declaration of MAX_MESSAGE_LENGTH was not visible in the sample; value follows cc.message.maxLength.
    private static final int MAX_MESSAGE_LENGTH = 1000;

    @Resource(name = "ccChatOrchestrationService")
    private ChatOrchestrationService chatOrchestrationService;

    @Resource(name = "ccChatDataAssembler")
    private ChatDataAssembler chatDataAssembler;

    @Resource
    private UserService userService;

    @Override
    public ChatResponseData chat(final ChatRequestData request)
    {
        final String message = validate(request);
        final TurnRequest turn = chatDataAssembler.toTurnRequest(currentUid(), request, message);
        final TurnResult result = chatOrchestrationService.handle(turn, ChatEventListener.NOOP);
        return chatDataAssembler.toResponse(result);
    }

    @Override
    public void streamChat(final ChatRequestData request, final Consumer<ChatStreamEventData> listener)
    {
        final String message = validate(request);
        final TurnRequest turn = chatDataAssembler.toTurnRequest(currentUid(), request, message);
        chatOrchestrationService.handle(turn, event -> listener.accept(chatDataAssembler.toEvent(event)));
    }

    /** Resolves the uid on the request thread; the stream worker must not call this. */
    public String currentUid()
    {
        return userService.getCurrentUser().getUid();
    }

    @Override
    public void validate(final ChatRequestData request)
    {
        trimmed(request);
    }

    private String validate(final ChatRequestData request)
    {
        return trimmed(request);
    }

    private String trimmed(final ChatRequestData request)
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
}
