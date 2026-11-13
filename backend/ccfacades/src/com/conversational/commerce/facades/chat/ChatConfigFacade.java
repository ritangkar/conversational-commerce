package com.conversational.commerce.facades.chat;

import com.conversational.commerce.facades.data.chat.ChatPublicConfigData;

public interface ChatConfigFacade
{
    /** Public, non-secret configuration for the storefront: theme tokens, terminology, sample prompts, enabled capabilities. */
    ChatPublicConfigData getPublicConfig();
}
