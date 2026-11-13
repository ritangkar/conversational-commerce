package de.hybris.platform.servicelayer.util;

/** LOCAL STUB of the SAP class, for engine tests only. */
public final class ServicesUtil
{
    private ServicesUtil()
    {
    }

    public static void validateParameterNotNull(final Object parameter, final String nullMessage)
    {
        if (parameter == null)
        {
            throw new IllegalArgumentException(nullMessage);
        }
    }
}
