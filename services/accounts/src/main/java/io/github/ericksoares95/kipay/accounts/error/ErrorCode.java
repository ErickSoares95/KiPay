package io.github.ericksoares95.kipay.accounts.error;

/**
 * Stable business error codes returned in the {@code code} property of every {@code ProblemDetail}.
 */
public enum ErrorCode {
    VALIDATION_ERROR,
    SERVICE_UNAVAILABLE,
    AUTHENTICATION_REQUIRED
}
