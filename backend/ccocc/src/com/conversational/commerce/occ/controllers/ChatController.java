package com.conversational.commerce.occ.controllers;

// ASSUMPTION (AS-04): import lines of the owner's sample were not visible. Verify these against the owner's ChatController.
import com.conversational.commerce.facades.chat.ChatFacade;
import com.conversational.commerce.facades.data.chat.ChatRequestData;
import com.conversational.commerce.facades.data.chat.ChatResponseData;
import com.conversational.commerce.facades.data.chat.ChatStreamEventData;
import com.conversational.commerce.occ.chat.ChatRequestWsDTO;
import com.conversational.commerce.occ.chat.ChatResponseWsDTO;
import com.conversational.commerce.occ.chat.ChatStreamEventWsDTO;
import com.conversational.commerce.occ.security.SessionPropagator;
import com.conversational.commerce.occ.security.UserBindingGuard;
import de.hybris.platform.commercewebservices.core.v2.controllers.BaseController;
import de.hybris.platform.commercewebservicescommons.annotation.ApiVersion;
import de.hybris.platform.commercewebservicescommons.constants.SecuredAccessConstants;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdAndUserIdParam;
import de.hybris.platform.webservicescommons.swagger.ApiFieldsParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Same annotations as the owner's sample except @Secured, which is customer group only (A5), and the added userId path
 * variable for UserBindingGuard. Controller-level exception handling was not shown, so none is added (the OCC default
 * handler returns 400 for IllegalArgumentException).
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}")
@ApiVersion("v2")
public class ChatController extends BaseController
{
    private static final Logger LOG = LogManager.getLogger(ChatController.class);
    private static final String STREAM_TIMEOUT_KEY = "cc.stream.timeoutSeconds";
    private static final String HEARTBEAT_KEY = "cc.stream.heartbeatSeconds";

    @Resource(name = "ccChatFacade")
    private ChatFacade chatFacade;

    @Resource(name = "ccUserBindingGuard")
    private UserBindingGuard userBindingGuard;

    @Resource(name = "ccSessionPropagator")
    private SessionPropagator sessionPropagator;

    @Resource(name = "ccChatStreamExecutor")
    private Executor chatStreamExecutor;

    @Resource(name = "ccChatStreamScheduler")
    private ThreadPoolTaskScheduler chatStreamScheduler;

    @Resource
    private de.hybris.platform.servicelayer.config.ConfigurationService configurationService;

    @Secured({ SecuredAccessConstants.ROLE_CUSTOMERGROUP })
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    @Operation(operationId = "chat", summary = "Send a chat message", description = "Runs one non-streaming assistant turn for the logged-in dealer.")
    @ApiBaseSiteIdAndUserIdParam
    public ChatResponseWsDTO chat(@PathVariable final String userId,
            @Parameter(description = "Chat request", required = true) @RequestBody final ChatRequestWsDTO request,
            @ApiFieldsParam @RequestParam(defaultValue = DEFAULT_FIELD_SET) final String fields)
    {
        userBindingGuard.assertCurrentUser(userId);
        final ChatRequestData requestData = getDataMapper().map(request, ChatRequestData.class);
        final ChatResponseData responseData = chatFacade.chat(requestData);
        return getDataMapper().map(responseData, ChatResponseWsDTO.class, fields);
    }

    @Secured({ SecuredAccessConstants.ROLE_CUSTOMERGROUP })
    @PostMapping(value = "/chat/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Operation(operationId = "chatStream", summary = "Send a chat message and stream the answer",
            description = "Server-sent events: step_started, step_completed, block, text_delta, done, error. Heartbeat comments keep the connection open.")
    @ApiBaseSiteIdAndUserIdParam
    public SseEmitter chatStream(@PathVariable final String userId,
            @Parameter(description = "Chat request", required = true) @RequestBody final ChatRequestWsDTO request,
            @ApiFieldsParam @RequestParam(defaultValue = DEFAULT_FIELD_SET) final String fields)
    {
        userBindingGuard.assertCurrentUser(userId);
        final ChatRequestData requestData = getDataMapper().map(request, ChatRequestData.class);
        chatFacade.validate(requestData); // 400 before any stream starts
        final int timeoutSeconds = configurationService.getConfiguration().getInt(STREAM_TIMEOUT_KEY, 30);
        final int heartbeatSeconds = configurationService.getConfiguration().getInt(HEARTBEAT_KEY, 10);
        final SseEmitter emitter = new SseEmitter(timeoutSeconds * 1000L);
        final AtomicBoolean closed = new AtomicBoolean();
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(e -> closed.set(true));
        final ScheduledFuture<?> heartbeat = chatStreamScheduler.scheduleWithFixedDelay(() ->
        {
            if (!closed.get())
            {
                try
                {
                    emitter.send(SseEmitter.event().comment("hb"));
                }
                catch (final IOException | IllegalStateException e)
                {
                    closed.set(true);
                }
            }
        }, java.time.Duration.ofSeconds(heartbeatSeconds));
        chatStreamExecutor.execute(sessionPropagator.wrap(() ->
        {
            try
            {
                chatFacade.streamChat(requestData, event -> send(emitter, closed, event, fields));
                emitter.complete();
            }
            catch (final StreamClosedException e)
            {
                LOG.debug("Client closed the stream"); // cancel stops the server task at the next event
            }
            catch (final RuntimeException e)
            {
                LOG.warn("Stream failed: {}", e.getClass().getSimpleName()); // class only, never message text
                sendError(emitter, closed);
                emitter.complete();
            }
            finally
            {
                heartbeat.cancel(false);
            }
        }));
        return emitter;
    }

    private void send(final SseEmitter emitter, final AtomicBoolean closed, final ChatStreamEventData event, final String fields)
    {
        if (closed.get())
        {
            throw new StreamClosedException();
        }
        final ChatStreamEventWsDTO dto = getDataMapper().map(event, ChatStreamEventWsDTO.class, fields);
        try
        {
            synchronized (emitter)
            {
                emitter.send(SseEmitter.event().name(dto.getEvent()).data(dto, MediaType.APPLICATION_JSON));
            }
        }
        catch (final IOException | IllegalStateException e)
        {
            closed.set(true);
            throw new StreamClosedException();
        }
    }

    private void sendError(final SseEmitter emitter, final AtomicBoolean closed)
    {
        if (!closed.get())
        {
            try
            {
                emitter.send(SseEmitter.event().name("error").data("{\"event\":\"error\",\"code\":\"internal\"}", MediaType.APPLICATION_JSON));
            }
            catch (final IOException | IllegalStateException e)
            {
                closed.set(true);
            }
        }
    }

    private static class StreamClosedException extends RuntimeException
    {
        StreamClosedException()
        {
            super("stream closed", null, false, false);
        }
    }
}
