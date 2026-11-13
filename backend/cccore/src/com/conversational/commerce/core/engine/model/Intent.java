package com.conversational.commerce.core.engine.model;

/** Closed set of intents the decision call may return (part2 workflow table). */
public enum Intent
{
    NEXT_ORDER("RI-05"), FIND_PRODUCT("PD-01"), QUICK_ORDER("PA-03"), BUDGET_PLAN("PA-04"), ORDER_LOOKUP("OM-02"),
    COMPARE("PD-04"), BASKET_REVIEW("PA-06"), REORDER("PA-07"), PRODUCT_QA("PD-03"), FAQ("KN-01"), OFF_TOPIC(null),
    UNSUPPORTED(null), CLARIFY(null), SWITCH_PACK(null);

    private final String capabilityId;

    Intent(final String capabilityId)
    {
        this.capabilityId = capabilityId;
    }

    public String capabilityId()
    {
        return capabilityId;
    }
}
