package com.aisa.provider.gateway;

import com.aisa.commons.error.ErrorCodes;
import com.aisa.provider.contract.UniformRequest;
import com.aisa.provider.model.ProviderType;

import java.util.List;

/**
 * Thrown when the selected provider and every configured fallback are classified as unavailable
 * (Requirement 20.7).
 *
 * <p>The gateway returns this to the requesting agent <em>without altering the agent's input
 * data</em>: the original {@link UniformRequest} is carried on the exception by reference so the
 * caller can confirm its input is unchanged and safely retry later. The stable error code is
 * {@link ErrorCodes#PROVIDER_UNAVAILABLE}.
 */
public class ProviderUnavailableException extends RuntimeException {

    /** Stable, client-safe error code (shared across services). */
    public static final String ERROR_CODE = ErrorCodes.PROVIDER_UNAVAILABLE;

    private final transient UniformRequest originalRequest;
    private final transient List<ProviderType> attemptedProviders;

    public ProviderUnavailableException(UniformRequest originalRequest,
                                        List<ProviderType> attemptedProviders) {
        super(buildMessage(attemptedProviders));
        this.originalRequest = originalRequest;
        this.attemptedProviders = List.copyOf(attemptedProviders);
    }

    private static String buildMessage(List<ProviderType> attempted) {
        if (attempted == null || attempted.isEmpty()) {
            return "No AI provider is available to serve the request";
        }
        return "All AI providers are unavailable after attempting: " + attempted;
    }

    /**
     * @return the caller's original, unaltered request (Req 20.7).
     */
    public UniformRequest getOriginalRequest() {
        return originalRequest;
    }

    /**
     * @return the providers that were attempted before exhaustion, in attempt order.
     */
    public List<ProviderType> getAttemptedProviders() {
        return attemptedProviders;
    }
}
