package com.conversational.commerce.facades.chat.impl;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import com.conversational.commerce.core.engine.model.Capabilities;
import com.conversational.commerce.facades.chat.ChatConfigFacade;
import com.conversational.commerce.facades.data.chat.ChatPublicConfigData;
import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Never returns keys, routes, prompts, thresholds or anything else a dealer should not see. */
public class DefaultChatConfigFacade implements ChatConfigFacade
{
    @Resource(name = "ccConfigurationService")
    private CcConfigurationService configurationService;

    @Override
    public ChatPublicConfigData getPublicConfig()
    {
        final String pack = configurationService.activePack();
        final ChatPublicConfigData data = new ChatPublicConfigData();
        data.setActivePack(pack);
        data.setEnabledCapabilities(Capabilities.ALL.stream().filter(configurationService::isEnabled).toList());
        data.setInterestEventsEnabled(configurationService.isEnabled("CI-04")
                && configurationService.getBoolean("privacy.interestEvents.enabled", true));
        data.setInterestNoticeText(configurationService.getString("privacy.interestEvents.noticeText",
                "PLACEHOLDER: this assistant records which products you view, compare or ask about to improve suggestions. You can switch this off."));
        data.setStockDemoNotice(configurationService.getString("stock.demoNotice", "Demo stock"));
        data.setPurchaseListLabel(configurationService.getString("purchaseList.label", "Demo purchase list, not your cart"));
        data.setContactText(configurationService.getString("contact.text", "Please contact your account manager."));
        data.setMaxMessageLength(configurationService.getInt("cc.message.maxLength", 1000));
        data.setSamplePrompts(split(configurationService.getString("pack." + pack + ".samplePrompts", ""), "\\|"));
        data.setTerminology(pairs(configurationService.getString("pack." + pack + ".terminology", "")));
        data.setThemeTokens(pairs(configurationService.getString("pack." + pack + ".themeTokens", "")));
        return data;
    }

    private List<String> split(final String value, final String regex)
    {
        return value.isBlank() ? List.of() : Arrays.stream(value.split(regex)).map(String::trim).toList();
    }

    private Map<String, String> pairs(final String value)
    {
        final Map<String, String> map = new LinkedHashMap<>();
        for (final String part : split(value, ";"))
        {
            final String[] kv = part.split("=", 2);
            if (kv.length == 2)
            {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }
}
