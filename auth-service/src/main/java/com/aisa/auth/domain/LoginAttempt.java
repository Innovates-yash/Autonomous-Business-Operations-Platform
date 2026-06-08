package com.aisa.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * A record of a single authentication attempt, keyed by the submitted email so
 * that failures can be counted within a rolling 15-minute window to drive
 * account lockout (Requirement 1.11). Stored by email (not a user reference)
 * because attempts may target non-existent accounts.
 */
@Entity
@Table(
        name = "login_attempts",
        indexes = {
            @Index(name = "idx_login_attempts_email_time", columnList = "email, attempted_at")
        })
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "successful", nullable = false)
    private boolean successful;

    /** Source IP (IPv4 or IPv6); nullable when not captured. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    protected LoginAttempt() {
    }

    public LoginAttempt(String email, boolean successful, String ipAddress, Instant attemptedAt) {
        this.email = email;
        this.successful = successful;
        this.ipAddress = ipAddress;
        this.attemptedAt = attemptedAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public void setAttemptedAt(Instant attemptedAt) {
        this.attemptedAt = attemptedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LoginAttempt that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
