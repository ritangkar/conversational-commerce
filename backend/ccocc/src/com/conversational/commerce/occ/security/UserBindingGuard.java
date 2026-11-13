package com.conversational.commerce.occ.security;

import com.conversational.commerce.core.engine.config.UserBindingRule;
import com.conversational.commerce.core.engine.orchestrator.AuditEvent;
import com.conversational.commerce.core.engine.orchestrator.AuditSink;
import de.hybris.platform.servicelayer.user.UserService;
import jakarta.annotation.Resource;
import org.springframework.security.access.AccessDeniedException;

/**
 * Rejects any userId other than "current" or the authenticated principal with 403 and an audit line (A5).
 * The customer is always taken from the security context, so this guard is defence in depth.
 */
public class UserBindingGuard
{
    @Resource
    private UserService userService;

    @Resource(name = "ccAuditSink")
    private AuditSink auditSink;

    public void assertCurrentUser(final String pathUserId)
    {
        final String uid = userService.getCurrentUser().getUid();
        if (!UserBindingRule.isAllowed(pathUserId, uid))
        {
            auditSink.log(AuditEvent.of("user_binding_rejected").with("outcome", "403"));
            throw new AccessDeniedException("userId does not match the authenticated user");
        }
    }
}
