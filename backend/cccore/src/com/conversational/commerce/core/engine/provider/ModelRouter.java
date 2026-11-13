package com.conversational.commerce.core.engine.provider;

public interface ModelRouter
{
    class ModelUnavailableException extends RuntimeException
    {
        public ModelUnavailableException(final String message)
        {
            super(message);
        }
    }

    /** Walks route.&lt;task&gt;.provider/model then the fallback chain. Token counts are on the returned result. */
    ModelResult call(ModelRequest request);
}
