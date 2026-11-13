package com.conversational.commerce.facades.chat.impl;

import com.conversational.commerce.core.enums.CCFeedbackRating;
import com.conversational.commerce.core.model.CCFeedbackModel;
import com.conversational.commerce.facades.chat.FeedbackFacade;
import com.conversational.commerce.facades.data.chat.FeedbackData;
import de.hybris.platform.servicelayer.model.ModelService;
import jakarta.annotation.Resource;
import java.util.Date;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;

/** Thumbs feedback (CF-07, D30, A7): metadata only. No customer reference and no text are stored. */
public class DefaultFeedbackFacade implements FeedbackFacade
{
    private static final int MAX_FIELD_LENGTH = 100;

    @Resource
    private ModelService modelService;

    @Override
    public void submit(final FeedbackData feedback)
    {
        if (feedback == null || StringUtils.isBlank(feedback.getResponseId()))
        {
            throw new IllegalArgumentException("responseId must not be empty");
        }
        final CCFeedbackRating rating;
        try
        {
            rating = CCFeedbackRating.valueOf(StringUtils.defaultString(feedback.getRating()).toUpperCase(Locale.ROOT));
        }
        catch (final IllegalArgumentException e)
        {
            throw new IllegalArgumentException("rating must be UP or DOWN");
        }
        final CCFeedbackModel model = modelService.create(CCFeedbackModel.class);
        model.setResponseId(StringUtils.left(feedback.getResponseId(), MAX_FIELD_LENGTH));
        model.setRating(rating);
        model.setIntent(StringUtils.left(feedback.getIntent(), MAX_FIELD_LENGTH));
        model.setToolsUsed(feedback.getToolsUsed() == null ? null : StringUtils.left(String.join(",", feedback.getToolsUsed()), 400));
        model.setAuditRef(StringUtils.left(feedback.getAuditRef(), MAX_FIELD_LENGTH));
        model.setCreatedAt(new Date());
        modelService.save(model);
    }
}
