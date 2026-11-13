package com.conversational.commerce.core.engine.gateway;

import com.conversational.commerce.core.engine.model.Domain.Faq;
import java.util.List;

public interface FaqGateway
{
    List<Faq> approvedFaqs(String pack);
}
