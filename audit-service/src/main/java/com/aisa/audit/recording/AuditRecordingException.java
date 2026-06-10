package com.aisa.audit.recording;

/**
 * Raised when an audit event cannot be durably recorded after exhausting every
 * permitted retry (Req 23.2). The caller translates this into rejection of the
 * originating action.
 */
public class AuditRecordingException extends RuntimeException {

    private final int attempts;

    public AuditRecordingException(int attempts, String message, Throwable cause) {
        super(message, cause);
        this.attempts = attempts;
    }

    /** Number of persistence attempts that were made before giving up. */
    public int getAttempts() {
        return attempts;
    }
}
