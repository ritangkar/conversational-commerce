package com.conversational.commerce.facades.chat;

import com.conversational.commerce.facades.data.chat.PurchaseListData;
import com.conversational.commerce.facades.data.chat.PurchaseListResultData;

public interface PurchaseListFacade
{
    /** Validates, prices and checks the browser-held list. The list is untrusted input (A11). */
    PurchaseListResultData validate(PurchaseListData list);
}
