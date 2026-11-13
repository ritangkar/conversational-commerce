package com.conversational.commerce.occ.security;

import de.hybris.platform.core.Registry;
import de.hybris.platform.core.Tenant;
import de.hybris.platform.jalo.JaloSession;

/**
 * Carries the hybris session (and so the customer's price and catalogue context) onto a worker thread.
 * UNVERIFIED (docs/verification-log.md, spike S1): confirm Registry/JaloSession activation calls on the owner's release.
 */
public class SessionPropagator
{
    public Runnable wrap(final Runnable task)
    {
        final JaloSession session = JaloSession.getCurrentSession();
        final Tenant tenant = Registry.getCurrentTenantNoFallback();
        return () ->
        {
            try
            {
                Registry.setCurrentTenant(tenant);
                session.activate();
                task.run();
            }
            finally
            {
                JaloSession.deactivate();
                Registry.unsetCurrentTenant();
            }
        };
    }
}
