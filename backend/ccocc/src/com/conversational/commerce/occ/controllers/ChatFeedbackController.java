package com.conversational.commerce.occ.controllers;

// ASSUMPTION (AS-04): import lines of the owner sample were not visible; verify against the owner ChatController.
import com.conversational.commerce.occ.security.UserBindingGuard;
import de.hybris.platform.commercewebservices.core.v2.controllers.BaseController;
import de.hybris.platform.commercewebservicescommons.annotation.ApiVersion;
import de.hybris.platform.commercewebservicescommons.constants.SecuredAccessConstants;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdAndUserIdParam;
import de.hybris.platform.webservicescommons.swagger.ApiFieldsParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.conversational.commerce.facades.chat.FeedbackFacade;
import com.conversational.commerce.facades.data.chat.FeedbackData;
import com.conversational.commerce.occ.chat.FeedbackWsDTO;

@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}")
@ApiVersion("v2")
public class ChatFeedbackController extends BaseController
{
    @Resource(name = "ccFeedbackFacade")
    private FeedbackFacade feedbackFacade;

    @Resource(name = "ccUserBindingGuard")
    private UserBindingGuard userBindingGuard;

    @Secured({ SecuredAccessConstants.ROLE_CUSTOMERGROUP })
    @PostMapping(value = "/chat/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Operation(operationId = "chatFeedback", summary = "Thumbs feedback", description = "Stores a thumbs rating with metadata only (no text, no customer).")
    @ApiBaseSiteIdAndUserIdParam
    public void submitFeedback(@PathVariable final String userId,
            @Parameter(description = "Feedback", required = true) @RequestBody final FeedbackWsDTO feedback)
    {
        userBindingGuard.assertCurrentUser(userId);
        final FeedbackData data = getDataMapper().map(feedback, FeedbackData.class);
        feedbackFacade.submit(data);
    }
}
