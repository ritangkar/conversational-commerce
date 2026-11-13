package com.conversational.commerce.core.engine.config;

/** A5: the userId path segment must be "current" or the authenticated principal's uid. Anything else is a 403. */
public final class UserBindingRule
{
    private UserBindingRule()
    {
    }

    public static boolean isAllowed(final String pathUserId, final String principalUid)
    {
        if (pathUserId == null || principalUid == null || principalUid.isBlank() || "anonymous".equalsIgnoreCase(principalUid))
        {
            return false;
        }
        return "current".equals(pathUserId) || principalUid.equals(pathUserId);
    }
}
