package com.aisa.provider.gateway;

import com.aisa.provider.client.ProviderRegistry;
import com.aisa.provider.contract.AiProvider;
import com.aisa.provider.contract.TokenUsage;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the routing facade covering timeout clamping, the 3-consecutive-error
 * unavailability trip, fallback ordering, usage recording, and the all-exhausted
 * provider-unavailable error preserving the caller's input (Requirements 20.5–20.8).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiProviderGatewayTest {

    @Mock
    private ProviderSelectionService selectionService;
    @Mock
    private ProviderRegistry providerRegistry;
    @Mock
    private ProviderConfigRepository configRepository;
    @Mock
    private ProviderUsageRecordRepository usageRepository;

    private ProviderHealthTracker healthTracker;
    private GatewayProperties properties;
    private AiProviderGateway gateway;

    private static final UniformRequest REQUEST = UniformRequest.ofPrompt("design a food delivery app");

    @BeforeEach
    void setUp() {
        healthTracker = new ProviderHealthTracker();
        properties = new GatewayProperties();
        gateway = new AiProviderGateway(selectionService, providerRegistry, configRepository,
                usageRepository, healthTracker, properties);
        // Default: no provider has a stored config (so timeout resolves to the 30s default) and
        // there are no fallbacks unless a test overrides it.
        when(configRepository.findByProvider(any())).thenReturn(Optional.empty());
        when(configRepository.findByConfiguredTrueOrderByFallbackPriorityAsc()).thenReturn(List.of());
    }

    // ---- Timeout clamping (Req 20.5) ----------------------------------------------------------

    @Test
    void clampTimeoutSecondsDefaultsToThirtyWhenAbsent() {
        assertThat(AiProviderGateway.clampTimeoutSeconds(null)).isEqualTo(30);
    }

    @Test
    void clampTimeoutSecondsClampsBelowAndAboveRange() {
        assertThat(AiProviderGateway.clampTimeoutSeconds(0)).isEqualTo(1);
        assertThat(AiProviderGateway.clampTimeoutSeconds(-5)).isEqualTo(1);
        assertThat(AiProviderGateway.clampTimeoutSeconds(999)).isEqualTo(120);
    }

    @Test
    void clampTimeoutSecondsPassesThroughInRangeValue() {
        assertThat(AiProviderGateway.clampTimeoutSeconds(45)).isEqualTo(45);
    }

    @Test
    void resolveTimeoutUsesConfiguredValueOrDefault() {
        when(configRepository.findByProvider(ProviderType.OPENAI))
                .thenReturn(Optional.of(config(ProviderType.OPENAI, 5, null)));
        assertThat(gateway.resolveTimeoutSeconds(ProviderType.OPENAI)).isEqualTo(5);
        // Unconfigured provider falls back to the 30s default.
        assertThat(gateway.resolveTimeoutSeconds(ProviderType.GEMINI)).isEqualTo(30);
    }

    // ---- 3-consecutive-error trip (Req 20.5) --------------------------------------------------

    @Test
    void threeConsecutiveErrorsClassifyProviderUnavailable() {
        when(selectionService.currentSelection()).thenReturn(Optional.of(ProviderType.OPENAI));
        ErrorProvider primary = new ErrorProvider(ProviderType.OPENAI);
        when(providerRegistry.find(ProviderType.OPENAI)).thenReturn(Optional.of(primary));

        assertThatThrownBy(() -> gateway.complete(REQUEST))
                .isInstanceOf(ProviderUnavailableException.class);

        // The provider was attempted exactly the threshold number of times then tripped.
        assertThat(primary.calls).isEqualTo(3);
        assertThat(healthTracker.isUnavailable(ProviderType.OPENAI, 3)).isTrue();
    }

    // ---- Failover order (Req 20.6) ------------------------------------------------------------

    @Test
    void failsOverToFallbacksInPriorityOrder() {
        when(selectionService.currentSelection()).thenReturn(Optional.of(ProviderType.OPENAI));
        ErrorProvider primary = new ErrorProvider(ProviderType.OPENAI);
        FixedProvider firstFallback = new FixedProvider(ProviderType.GEMINI);
        FixedProvider secondFallback = new FixedProvider(ProviderType.CLAUDE);
        when(providerRegistry.find(ProviderType.OPENAI)).thenReturn(Optional.of(primary));
        when(providerRegistry.find(ProviderType.GEMINI)).thenReturn(Optional.of(firstFallback));
        when(providerRegistry.find(ProviderType.CLAUDE)).thenReturn(Optional.of(secondFallback));
        // Fallbacks returned in ascending priority order by the repository.
        when(configRepository.findByConfiguredTrueOrderByFallbackPriorityAsc()).thenReturn(List.of(
                config(ProviderType.GEMINI, 30, 1),
                config(ProviderType.CLAUDE, 30, 2)));

        UniformResponse response = gateway.complete(REQUEST);

        // The first fallback in priority order served the request; the second was never tried.
        assertThat(response.servedBy()).isEqualTo(ProviderType.GEMINI);
        assertThat(firstFallback.calls).isEqualTo(1);
        assertThat(secondFallback.calls).isEqualTo(0);
    }

    @Test
    void candidateListHonoursMaxThreeFallbacks() {
        properties.setMaxFallbacks(3);
        when(selectionService.currentSelection()).thenReturn(Optional.of(ProviderType.OPENAI));
        when(configRepository.findByConfiguredTrueOrderByFallbackPriorityAsc()).thenReturn(List.of(
                config(ProviderType.GEMINI, 30, 1),
                config(ProviderType.CLAUDE, 30, 2),
                config(ProviderType.LOCAL_LLM, 30, 3)));

        List<ProviderType> candidates = gateway.routingCandidates();

        // Primary + up to three fallbacks, primary first, fallbacks in priority order.
        assertThat(candidates).containsExactly(ProviderType.OPENAI, ProviderType.GEMINI,
                ProviderType.CLAUDE, ProviderType.LOCAL_LLM);
    }

    // ---- Usage recording (Req 20.8) -----------------------------------------------------------

    @Test
    void recordsUsageForServingProviderOnSuccess() {
        when(selectionService.currentSelection()).thenReturn(Optional.of(ProviderType.OPENAI));
        when(providerRegistry.find(ProviderType.OPENAI))
                .thenReturn(Optional.of(new FixedProvider(ProviderType.OPENAI)));

        gateway.complete(REQUEST, "corr-123");

        ArgumentCaptor<ProviderUsageRecord> captor = ArgumentCaptor.forClass(ProviderUsageRecord.class);
        verify(usageRepository).save(captor.capture());
        ProviderUsageRecord record = captor.getValue();
        assertThat(record.getProvider()).isEqualTo(ProviderType.OPENAI);
        assertThat(record.getOperation()).isEqualTo(ProviderOperation.COMPLETE);
        assertThat(record.getCorrelationId()).isEqualTo("corr-123");
        assertThat(record.isSuccess()).isTrue();
        assertThat(record.getServedAt()).isNotNull();
    }

    // ---- All exhausted: provider-unavailable error preserves input (Req 20.7) -----------------

    @Test
    void allProvidersUnavailableThrowsPreservingOriginalInput() {
        when(selectionService.currentSelection()).thenReturn(Optional.of(ProviderType.OPENAI));
        when(providerRegistry.find(ProviderType.OPENAI))
                .thenReturn(Optional.of(new ErrorProvider(ProviderType.OPENAI)));
        when(providerRegistry.find(ProviderType.GEMINI))
                .thenReturn(Optional.of(new ErrorProvider(ProviderType.GEMINI)));
        when(configRepository.findByConfiguredTrueOrderByFallbackPriorityAsc())
                .thenReturn(List.of(config(ProviderType.GEMINI, 30, 1)));

        assertThatThrownBy(() -> gateway.complete(REQUEST))
                .isInstanceOf(ProviderUnavailableException.class)
                .satisfies(ex -> {
                    ProviderUnavailableException pue = (ProviderUnavailableException) ex;
                    // The original input is preserved by reference and unchanged (Req 20.7).
                    assertThat(pue.getOriginalRequest()).isSameAs(REQUEST);
                    assertThat(pue.getOriginalRequest().messages()).isEqualTo(REQUEST.messages());
                    assertThat(pue.getAttemptedProviders())
                            .containsExactly(ProviderType.OPENAI, ProviderType.GEMINI);
                });
        // A failure usage record is still written for the primary attempt.
        verify(usageRepository).save(any(ProviderUsageRecord.class));
    }

    @Test
    void timeoutClassifiesUnavailableAndFailsOver() {
        when(selectionService.currentSelection()).thenReturn(Optional.of(ProviderType.OPENAI));
        // Primary times out: configured 1s, but the provider takes ~2s.
        when(configRepository.findByProvider(ProviderType.OPENAI))
                .thenReturn(Optional.of(config(ProviderType.OPENAI, 1, null)));
        when(providerRegistry.find(ProviderType.OPENAI))
                .thenReturn(Optional.of(new SlowProvider(ProviderType.OPENAI, 2000)));
        FixedProvider fallback = new FixedProvider(ProviderType.GEMINI);
        when(providerRegistry.find(ProviderType.GEMINI)).thenReturn(Optional.of(fallback));
        when(configRepository.findByConfiguredTrueOrderByFallbackPriorityAsc())
                .thenReturn(List.of(config(ProviderType.GEMINI, 30, 1)));

        UniformResponse response = gateway.complete(REQUEST);

        assertThat(response.servedBy()).isEqualTo(ProviderType.GEMINI);
        assertThat(healthTracker.isUnavailable(ProviderType.OPENAI, 3)).isTrue();
        assertThat(fallback.calls).isEqualTo(1);
    }

    @Test
    void successResetsConsecutiveErrorStreak() {
        // Two errors then a success should leave the provider available again.
        healthTracker.recordError(ProviderType.OPENAI);
        healthTracker.recordError(ProviderType.OPENAI);
        healthTracker.recordSuccess(ProviderType.OPENAI);
        assertThat(healthTracker.consecutiveErrors(ProviderType.OPENAI)).isZero();
        assertThat(healthTracker.isUnavailable(ProviderType.OPENAI, 3)).isFalse();
    }

    // ---- Test fixtures ------------------------------------------------------------------------

    private static ProviderConfig config(ProviderType type, int timeoutSeconds, Integer priority) {
        return new ProviderConfig(type, type.name(), "model", true, timeoutSeconds, priority);
    }

    /** Always succeeds, echoing which provider served the request. */
    private static final class FixedProvider implements AiProvider {
        private final ProviderType type;
        private int calls;

        private FixedProvider(ProviderType type) {
            this.type = type;
        }

        @Override
        public ProviderType providerType() {
            return type;
        }

        @Override
        public UniformResponse complete(UniformRequest request) {
            calls++;
            return new UniformResponse("ok from " + type, type, "STOP", TokenUsage.NONE);
        }

        @Override
        public Flux<UniformResponseChunk> stream(UniformRequest request) {
            calls++;
            return Flux.just(new UniformResponseChunk("ok", type, true));
        }
    }

    /** Always throws a transport-style error. */
    private static final class ErrorProvider implements AiProvider {
        private final ProviderType type;
        private int calls;

        private ErrorProvider(ProviderType type) {
            this.type = type;
        }

        @Override
        public ProviderType providerType() {
            return type;
        }

        @Override
        public UniformResponse complete(UniformRequest request) {
            calls++;
            throw new RuntimeException("transport error from " + type);
        }

        @Override
        public Flux<UniformResponseChunk> stream(UniformRequest request) {
            calls++;
            return Flux.error(new RuntimeException("transport error from " + type));
        }
    }

    /** Sleeps before responding, to exercise the request timeout. */
    private static final class SlowProvider implements AiProvider {
        private final ProviderType type;
        private final long sleepMillis;

        private SlowProvider(ProviderType type, long sleepMillis) {
            this.type = type;
            this.sleepMillis = sleepMillis;
        }

        @Override
        public ProviderType providerType() {
            return type;
        }

        @Override
        public UniformResponse complete(UniformRequest request) {
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new UniformResponse("late from " + type, type, "STOP", TokenUsage.NONE);
        }

        @Override
        public Flux<UniformResponseChunk> stream(UniformRequest request) {
            return Flux.just(new UniformResponseChunk("late", type, true));
        }
    }
}
