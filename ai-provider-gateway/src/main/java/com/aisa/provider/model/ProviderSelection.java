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
 * The record of an Admin selecting the active AI provider (Requirement 20.2).
 *
 * <p>Each selection is persisted with the provider chosen, the Admin who made the choice, and
 * the instant it was saved, so the gateway can route all requests received after the selection
 * takes effect to the selected provider. Selections form an append-only history; the most
 * recent valid selection is the active one. A rejected selection (unconfigured provider) is
 * never written, preserving the previously selected provider (Requirement 20.3).
 */
@Entity
@Table(name = "provider_selection")
public class ProviderSelection {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    /** The provider made active by this selection. */
    @Enumerated(EnumType.STRING)
    @Column(name = "selected_provider", nullable = false, length = 32)
    private ProviderType selectedProvider;

    /** Identifier of the Admin who made the selection. */
    @Column(name = "selected_by", nullable = false, length = 36)
    private String selectedBy;

    /** Instant the selection was saved; selection takes effect within 5s (Req 20.2). */
    @Column(name = "selected_at", nullable = false, updatable = false)
    private Instant selectedAt;

    protected ProviderSelection() {
        // JPA
    }

    public ProviderSelection(ProviderType selectedProvider, String selectedBy) {
        this.id = UUID.randomUUID().toString();
        this.selectedProvider = selectedProvider;
        this.selectedBy = selectedBy;
        this.selectedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public ProviderType getSelectedProvider() {
        return selectedProvider;
    }

    public String getSelectedBy() {
        return selectedBy;
    }

    public Instant getSelectedAt() {
        return selectedAt;
    }
}
