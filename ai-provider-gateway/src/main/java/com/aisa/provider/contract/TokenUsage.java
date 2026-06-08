package com.aisa.provider.contract;

/**
 * Provider-agnostic token accounting for a completed request. Counts are best-effort and
 * default to zero when a provider does not report them.
 *
 * @param promptTokens     tokens consumed by the request prompt
 * @param completionTokens tokens produced in the response
 */
public record TokenUsage(long promptTokens, long completionTokens) {

    public static final TokenUsage NONE = new TokenUsage(0L, 0L);

    /** @return total tokens (prompt + completion). */
    public long totalTokens() {
        return promptTokens + completionTokens;
    }
}
