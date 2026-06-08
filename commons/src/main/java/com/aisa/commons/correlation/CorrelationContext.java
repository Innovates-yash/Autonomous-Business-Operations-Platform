package com.aisa.commons.correlation;

/**
 * Holds the correlation identifier for the current request thread so that logs and
 * outbound calls can propagate a single, unchanged identifier end to end.
 *
 * <p>Supports correctness Property 22 (Correlation propagation) and Requirements 27.5–27.6.
 */
public final class CorrelationContext {

    /** Header and MDC key used to carry the correlation identifier across services. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationContext() {
    }

    public static void set(String correlationId) {
        CURRENT.set(correlationId);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
