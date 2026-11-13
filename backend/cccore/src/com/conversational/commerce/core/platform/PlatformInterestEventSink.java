package com.conversational.commerce.core.platform;

import com.conversational.commerce.core.engine.gateway.InterestEventSink;
import com.conversational.commerce.core.engine.model.Domain.EventType;
import com.conversational.commerce.core.enums.CCEventType;
import com.conversational.commerce.core.model.CCInterestEventModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.user.UserService;
import jakarta.annotation.Resource;
import java.util.Date;

/** The only writer of CCInterestEvent. Stores an enum and a code or token, never text. */
public class PlatformInterestEventSink implements InterestEventSink
{
    @Resource
    private ModelService modelService;

    @Resource
    private UserService userService;

    @Override
    public void write(final String customerId, final EventType type, final String reference, final String pack)
    {
        final CCInterestEventModel event = modelService.create(CCInterestEventModel.class);
        event.setCustomer((CustomerModel) userService.getUserForUID(customerId));
        event.setEventType(CCEventType.valueOf(type.name()));
        if (type == EventType.REQUESTED_NOT_FOUND)
        {
            event.setNormalisedToken(reference);
        }
        else
        {
            event.setProductCode(reference);
        }
        event.setPackCode(pack);
        event.setOccurredAt(new Date());
        modelService.save(event);
    }
}
