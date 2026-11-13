package com.conversational.commerce.core.dao;

import com.conversational.commerce.core.model.CCProductBusinessAttributeModel;

public interface CcBusinessAttributeDao
{
    /** Only BusinessRanker's gateway may call this. */
    CCProductBusinessAttributeModel findByProductCode(String productCode);
}
