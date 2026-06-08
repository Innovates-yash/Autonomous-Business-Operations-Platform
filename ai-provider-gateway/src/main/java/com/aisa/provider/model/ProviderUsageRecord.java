package com.aisa.provider.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable record of a single routed request and which provider served it
 * (Requirement 20.8).
 *
 * <p>When the gateway completes routing a request, it records the provider that served it and
 * a timestamp, retained for a configurable period of at least 90 days. The {@code servedAt}
 * column is indexed so retention pruning and Admin queries by time range are efficient.
 */
@Entity
@Table(name = "provider_usage_record", indexes = {
        @Index(name = "idx_usage_served_at", columnList = "served_at"),
        @Index(name = "idx_usage_provider", columnList = "provider")
})
public class ProviderUsageRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    /** The provider that actually served the request (after any failover). */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private ProviderType provider;

    /** The uniform operation that was served. */
    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 16)
    private ProviderOperation operation;

    /** Correlation identifier propagated from the calling request, when available. */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    /** Whether the request ultimately produced a successful response. */
    @Column(name = "success", nullable = false)
    private boolean success;

    /** Instant the request completed routing (Req 20.8). */
    @Column(name = "served_at", nullable = false, updatable = false)
    private Instant servedAt;

    protected ProviderUsageRecord() {
        // JPA
    }

    public ProviderUsageRecord(ProviderType provider, ProviderOperation operation,
                               String correlationId, boolean success) {
        this.id = UUID.randomUUID().toString();
        this.provider = provider;
        this.operation = operation;
        this.correlationId = correlationId;
        this.success = success;
        this.servedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public ProviderType getProvider() {
        return provider;
    }

    public ProviderOperation getOperation() {
        return operation;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public boolean isSuccess() {
        return success;
    }

    public Instant getServedAt() {
        return servedAt;
    }
}
