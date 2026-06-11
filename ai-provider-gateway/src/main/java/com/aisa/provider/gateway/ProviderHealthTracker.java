package com.aisa.provider.gateway;

import com.aisa.provider.model.ProviderType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks per-provider consecutive failures to classify a provider as unavailable
 * (Requirement 20.5).
 *
 * <p>A provider is classified <em>unavailable</em> when either:
 * <ul>
 *   <li>it returns transport/service errors on {@code threshold} consecutive attempts
 *       ({@link #recordError(ProviderType)} reaches the threshold), or</li>
 *   <li>a single attempt exceeds the configured request timeout
 *       ({@link #markUnavailableOnTimeout(ProviderType)}).</li>
 * </ul>
 *
 * <p>The consecutive-error counter is shared across requests, so errors accumulate until a
 * successful response resets it ({@link #recordSuccess(ProviderType)}). This lets a provider
 * recover automatically once it starts responding again. The tracker is thread-safe.
 */
@Component
public class ProviderHealthTracker {

    private final Map<ProviderType, AtomicInteger> consecutiveErrors = new EnumMap<>(ProviderType.class);

    /** Reset the failure streak for {@code provider} after a successful response. */
    public synchronized void recordSuccess(ProviderType provider) {
        counter(provider).set(0);
    }

    /**
     * Record a transport/service error for {@code provider}.
     *
     * @return the updated consecutive-error count
     */
    public synchronized int recordError(ProviderType provider) {
        return counter(provider).incrementAndGet();
    }

    /**
     * Force {@code provider} into the unavailable classification because an attempt timed out.
     * A single timeout is sufficient to classify the provider unavailable (Req 20.5), so the
     * counter is driven to the supplied threshold.
     */
    public synchronized void markUnavailableOnTimeout(ProviderType provider, int threshold) {
        counter(provider).set(Math.max(threshold, 1));
    }

    /**
     * @return {@code true} when {@code provider} has reached the consecutive-error
     *         {@code threshold} and is therefore classified unavailable.
     */
    public synchronized boolean isUnavailable(ProviderType provider, int threshold) {
        return counter(provider).get() >= threshold;
    }

    /** @return the current consecutive-error count for {@code provider}. */
    public synchronized int consecutiveErrors(ProviderType provider) {
        return counter(provider).get();
    }

    private AtomicInteger counter(ProviderType provider) {
        return consecutiveErrors.computeIfAbsent(provider, p -> new AtomicInteger(0));
    }
}
