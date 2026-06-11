package com.aisa.provider.gateway;

import com.aisa.provider.client.ProviderRegistry;
import com.aisa.provider.contract.AiProvider;
import com.aisa.provider.contract.UniformRequest;
import com.aisa.provider.contract.UniformResponse;
import com.aisa.provider.contract.UniformResponseChunk;
import com.aisa.provider.model.ProviderConfig;
import com.aisa.provider.model.ProviderOperation;
import com.aisa.provider.model.ProviderType;
import com.aisa.provider.model.ProviderUsageRecord;
import com.aisa.provider.repository.ProviderConfigRepository;
import com.aisa.provider.repository.ProviderUsageRecordRepository;
import com.aisa.provider.selection.ProviderSelectionService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The routing facade every agent and the chat service call to reach an AI provider
 * (Requirement 20.5–20.8).
 *
 * <p>It applies the selected provider's configured per-request timeout (clamped to the 1–120s
 * range, default 30s — Req 20.5), classifies a provider unavailable on a timeout or after
 * {@code consecutiveErrorThreshold} consecutive errors (Req 20.5), fails over to up to
 * {@code maxFallbacks} configured fallback providers in {@code fallbackPriority} order
 * (Req 20.6), and when no provider succeeds throws a {@link ProviderUnavailableException} that
 * preserves the caller's original input (Req 20.7). Every invocation records the provider that
 * served it (or the primary attempt on failure) plus a timestamp (Req 20.8).
 */
@Service
public class AiProviderGateway {

    private static final Logger log = LoggerFactory.getLogger(AiProviderGateway.class);
    private static final String CORRELATION_KEY = "correlationId";

    private final ProviderSelectionService selectionService;
    private final ProviderRegistry providerRegistry;
    private final ProviderConfigRepository configRepository;
    private final ProviderUsageRecordRepository usageRepository;
    private final ProviderHealthTracker healthTracker;
    private final GatewayProperties properties;

    /** Executor used to bound blocking {@code complete} calls by the configured timeout. */
    private final ExecutorService timeoutExecutor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "provider-gateway-timeout");
                t.setDaemon(true);
                return t;
            });

    public AiProviderGateway(ProviderSelectionService selectionService,
                             ProviderRegistry providerRegistry,
                             ProviderConfigRepository configRepository,
                             ProviderUsageRecordRepository usageRepository,
                             ProviderHealthTracker healthTracker,
                             GatewayProperties properties) {
        this.selectionService = selectionService;
        this.providerRegistry = providerRegistry;
        this.configRepository = configRepository;
        this.usageRepository = usageRepository;
        this.healthTracker = healthTracker;
        this.properties = properties;
    }

    @PreDestroy
    void shutdown() {
        timeoutExecutor.shutdownNow();
    }

    /**
     * Execute a blocking completion against the active provider with timeout, unavailability
     * detection, failover, and usage recording. Correlation id is taken from the logging MDC.
     */
    public UniformResponse complete(UniformRequest request) {
        return complete(request, MDC.get(CORRELATION_KEY));
    }

    /**
     * Execute a blocking completion (Requirement 20.5–20.8).
     *
     * @param request       the caller's request; never mutated
     * @param correlationId correlation id to stamp on the usage record (may be {@code null})
     * @return the response from the first provider that succeeds
     * @throws ProviderUnavailableException if the selected provider and every fallback are
     *                                      unavailable; the original {@code request} is preserved
     */
    public UniformResponse complete(UniformRequest request, String correlationId) {
        Objects.requireNonNull(request, "request");
        List<ProviderType> candidates = routingCandidates();

        for (ProviderType type : candidates) {
            Optional<AiProvider> client = providerRegistry.find(type);
            if (client.isEmpty()) {
                log.warn("No client registered for provider {}; skipping in failover", type);
                continue;
            }
            int timeoutSeconds = resolveTimeoutSeconds(type);
            Optional<UniformResponse> response =
                    attemptComplete(client.get(), request, timeoutSeconds);
            if (response.isPresent()) {
                recordUsage(type, ProviderOperation.COMPLETE, correlationId, true);
                return response.get();
            }
            log.warn("Provider {} classified unavailable; failing over", type);
        }

        // Selected provider and all fallbacks exhausted (Req 20.7): record the failed primary
        // attempt and return the provider-unavailable error with the input preserved.
        recordUsage(primaryOrNull(candidates), ProviderOperation.COMPLETE, correlationId, false);
        throw new ProviderUnavailableException(request, candidates);
    }

    /**
     * Execute a streaming completion with failover and usage recording (Req 20.5–20.8).
     * Correlation id is taken from the logging MDC.
     */
    public Flux<UniformResponseChunk> stream(UniformRequest request) {
        return stream(request, MDC.get(CORRELATION_KEY));
    }

    /**
     * Execute a streaming completion (Requirement 20.5–20.8). On a timeout or error the stream
     * fails over to the next configured fallback; when all are exhausted the returned
     * {@link Flux} terminates with a {@link ProviderUnavailableException} preserving the input.
     */
    public Flux<UniformResponseChunk> stream(UniformRequest request, String correlationId) {
        Objects.requireNonNull(request, "request");
        List<ProviderType> candidates = routingCandidates();
        return streamFromCandidate(request, candidates, 0, correlationId);
    }

    private Flux<UniformResponseChunk> streamFromCandidate(UniformRequest request,
                                                           List<ProviderType> candidates,
                                                           int index,
                                                           String correlationId) {
        if (index >= candidates.size()) {
            recordUsage(primaryOrNull(candidates), ProviderOperation.STREAM, correlationId, false);
            return Flux.error(new ProviderUnavailableException(request, candidates));
        }
        ProviderType type = candidates.get(index);
        Optional<AiProvider> client = providerRegistry.find(type);
        if (client.isEmpty()) {
            return streamFromCandidate(request, candidates, index + 1, correlationId);
        }
        int timeoutSeconds = resolveTimeoutSeconds(type);
        return client.get().stream(request)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnComplete(() -> {
                    healthTracker.recordSuccess(type);
                    recordUsage(type, ProviderOperation.STREAM, correlationId, true);
                })
                .onErrorResume(error -> {
                    classifyStreamFailure(type, error, timeoutSeconds);
                    log.warn("Streaming provider {} failed ({}); failing over", type, error.toString());
                    return streamFromCandidate(request, candidates, index + 1, correlationId);
                });
    }

    private void classifyStreamFailure(ProviderType type, Throwable error, int threshold) {
        if (error instanceof java.util.concurrent.TimeoutException
                || error.getClass().getSimpleName().contains("Timeout")) {
            healthTracker.markUnavailableOnTimeout(type, properties.getConsecutiveErrorThreshold());
        } else {
            healthTracker.recordError(type);
        }
    }

    /**
     * Attempt a single provider for a blocking completion. Retries within this call until the
     * provider succeeds or accumulates {@code consecutiveErrorThreshold} consecutive errors, and
     * fails over immediately on a timeout.
     *
     * @return the response, or empty when the provider is classified unavailable (failover)
     */
    private Optional<UniformResponse> attemptComplete(AiProvider client, UniformRequest request,
                                                      int timeoutSeconds) {
        ProviderType type = client.providerType();
        int threshold = properties.getConsecutiveErrorThreshold();
        for (int attempt = 1; attempt <= threshold; attempt++) {
            try {
                UniformResponse response = callWithTimeout(client, request, timeoutSeconds);
                healthTracker.recordSuccess(type);
                return Optional.of(response);
            } catch (TimeoutException te) {
                healthTracker.markUnavailableOnTimeout(type, threshold);
                log.warn("Provider {} timed out after {}s on attempt {}", type, timeoutSeconds, attempt);
                return Optional.empty();
            } catch (Exception ex) {
                int consecutive = healthTracker.recordError(type);
                log.warn("Provider {} error on attempt {} (consecutive={}): {}",
                        type, attempt, consecutive, ex.toString());
                if (consecutive >= threshold) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Invoke {@link AiProvider#complete(UniformRequest)} but abort if it exceeds
     * {@code timeoutSeconds} (Req 20.5).
     */
    private UniformResponse callWithTimeout(AiProvider client, UniformRequest request,
                                            int timeoutSeconds) throws Exception {
        Callable<UniformResponse> task = () -> client.complete(request);
        Future<UniformResponse> future = timeoutExecutor.submit(task);
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            throw te;
        } catch (java.util.concurrent.ExecutionException ee) {
            // Unwrap the underlying provider failure so it is classified as a transport error.
            Throwable cause = ee.getCause();
            if (cause instanceof Exception e) {
                throw e;
            }
            throw ee;
        }
    }

    /**
     * Build the ordered routing list: the selected provider first, then up to
     * {@code maxFallbacks} configured fallbacks in ascending {@code fallbackPriority} order
     * (Req 20.6). Providers without a fallback priority are not used as fallbacks.
     */
    List<ProviderType> routingCandidates() {
        ProviderType primary = selectionService.currentSelection().orElse(null);
        List<ProviderType> candidates = new ArrayList<>();
        if (primary != null) {
            candidates.add(primary);
        }
        final ProviderType primaryRef = primary;
        configRepository.findByConfiguredTrueOrderByFallbackPriorityAsc().stream()
                .filter(c -> c.getFallbackPriority() != null)
                .map(ProviderConfig::getProvider)
                .filter(t -> t != primaryRef)
                .distinct()
                .limit(properties.getMaxFallbacks())
                .forEach(candidates::add);
        return candidates;
    }

    /**
     * Resolve the per-request timeout for {@code provider}, clamped to the configurable 1–120s
     * range with a 30s default when the provider has no stored configuration (Req 20.5).
     */
    int resolveTimeoutSeconds(ProviderType provider) {
        Integer configured = configRepository.findByProvider(provider)
                .map(ProviderConfig::getRequestTimeoutSeconds)
                .orElse(null);
        return clampTimeoutSeconds(configured);
    }

    /**
     * Clamp a raw timeout to the permitted 1–120 second range, defaulting to 30 seconds when the
     * value is absent (Req 20.5).
     */
    static int clampTimeoutSeconds(Integer rawSeconds) {
        if (rawSeconds == null) {
            return ProviderConfig.DEFAULT_TIMEOUT_SECONDS;
        }
        if (rawSeconds < ProviderConfig.MIN_TIMEOUT_SECONDS) {
            return ProviderConfig.MIN_TIMEOUT_SECONDS;
        }
        if (rawSeconds > ProviderConfig.MAX_TIMEOUT_SECONDS) {
            return ProviderConfig.MAX_TIMEOUT_SECONDS;
        }
        return rawSeconds;
    }

    private ProviderType primaryOrNull(List<ProviderType> candidates) {
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private void recordUsage(ProviderType provider, ProviderOperation operation,
                             String correlationId, boolean success) {
        if (provider == null) {
            return;
        }
        try {
            usageRepository.save(new ProviderUsageRecord(provider, operation, correlationId, success));
        } catch (Exception e) {
            // Usage recording must never break the routed request (Req 20.8 is observational).
            log.warn("Failed to record provider usage for {}: {}", provider, e.toString());
        }
    }
}
