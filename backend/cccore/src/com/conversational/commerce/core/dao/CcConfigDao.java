package com.conversational.commerce.core.dao;

import com.conversational.commerce.core.model.CCConfigEntryModel;
import com.conversational.commerce.core.model.CCConfigurationModel;
import java.util.List;

public interface CcConfigDao
{
    CCConfigurationModel findConfiguration(String code);

    /** Enabled entries of the configuration for the pack and for all packs (empty pack code). */
    List<CCConfigEntryModel> findEntries(CCConfigurationModel configuration, String packCode);
}
