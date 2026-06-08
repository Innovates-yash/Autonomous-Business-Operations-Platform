package com.aisa.provider.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Configuration for a single AI provider the gateway can route to (Requirement 20.1).
 *
 * <p>A provider is considered "configured" — and therefore selectable (Requirement 20.2) —
 * only when a {@link ProviderConfig} row exists with {@link #configured} set to {@code true}.
 * Selecting a provider without such a row is rejected (Requirement 20.3).
 *
 * <p>The {@link #requestTimeoutSeconds} is the per-request timeout used for unavailability
 * classification and is constrained to the 1–120 second range with a default of 30
 * (Requirement 20.5). The {@link #fallbackPriority} orders fallback selection, lowest first
 * (Requirement 20.6).
 */
@Entity
@Table(name = "provider_config")
public class ProviderConfig {

    /** Lower bound of the configurable request timeout, in seconds (Requirement 20.5). */
    public static final int MIN_TIMEOUT_SECONDS = 1;
    /** Upper bound of the configurable request timeout, in seconds (Requirement 20.5). */
    public static final int MAX_TIMEOUT_SECONDS = 120;
    /** Default request timeout, in seconds (Requirement 20.5). */
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    /** The provider this configuration describes. Exactly one row per provider. */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, unique = true, length = 32)
    private ProviderType provider;

    /** Human-readable label shown to administrators. */
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    /** The model identifier the provider client should target (e.g. {@code gpt-4o}). */
    @Column(name = "model", nullable = false, length = 100)
    private String model;

    /** Whether this provider is fully configured and therefore selectable (Req 20.2/20.3). */
    @Column(name = "configured", nullable = false)
    private boolean configured;

    /** Per-request timeout used for unavailability classification (Req 20.5). */
    @Column(name = "request_timeout_seconds", nullable = false)
    private int requestTimeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    /** Fallback ordering, lowest first; {@code null} means not used as a fallback (Req 20.6). */
    @Column(name = "fallback_priority")
    private Integer fallbackPriority;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProviderConfig() {
        // JPA
    }

    public ProviderConfig(ProviderType provider, String displayName, String model,
                          boolean configured, int requestTimeoutSeconds, Integer fallbackPriority) {
        this.id = UUID.randomUUID().toString();
        this.provider = provider;
        this.displayName = displayName;
        this.model = model;
        this.configured = configured;
        setRequestTimeoutSeconds(requestTimeoutSeconds);
        this.fallbackPriority = fallbackPriority;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Set the request timeout, enforcing the configurable 1–120 second range (Req 20.5).
     *
     * @throws IllegalArgumentException if the value is outside the permitted range
     */
    public final void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        if (requestTimeoutSeconds < MIN_TIMEOUT_SECONDS || requestTimeoutSeconds > MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException(
                    "requestTimeoutSeconds must be between " + MIN_TIMEOUT_SECONDS
                            + " and " + MAX_TIMEOUT_SECONDS + " seconds");
        }
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public ProviderType getProvider() {
        return provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        touch();
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
        touch();
    }

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
        touch();
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public Integer getFallbackPriority() {
        return fallbackPriority;
    }

    public void setFallbackPriority(Integer fallbackPriority) {
        this.fallbackPriority = fallbackPriority;
        touch();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
