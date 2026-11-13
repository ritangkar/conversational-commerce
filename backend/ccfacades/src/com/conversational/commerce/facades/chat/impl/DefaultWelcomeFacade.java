package com.conversational.commerce.facades.chat.impl;

import com.conversational.commerce.core.engine.model.Blocks.Block;
import com.conversational.commerce.core.engine.orchestrator.WelcomeService;
import com.conversational.commerce.facades.chat.WelcomeFacade;
import com.conversational.commerce.facades.data.chat.ChatBlockData;
import com.conversational.commerce.facades.data.chat.WelcomePanelData;
import de.hybris.platform.servicelayer.user.UserService;
import jakarta.annotation.Resource;
import java.time.LocalDate;

public class DefaultWelcomeFacade implements WelcomeFacade
{
    @Resource(name = "ccWelcomeService")
    private WelcomeService welcomeService;

    @Resource(name = "ccChatDataAssembler")
    private ChatDataAssembler chatDataAssembler;

    @Resource
    private UserService userService;

    @Override
    public WelcomePanelData getWelcomePanel()
    {
        final Block block = welcomeService.build(userService.getCurrentUser().getUid(), LocalDate.now());
        final ChatBlockData data = chatDataAssembler.toBlock(block);
        final WelcomePanelData panel = new WelcomePanelData();
        panel.setItems(data.getWelcomeItems());
        return panel;
    }
}
