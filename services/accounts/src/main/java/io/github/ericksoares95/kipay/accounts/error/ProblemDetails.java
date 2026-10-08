package io.github.ericksoares95.kipay.accounts.error;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Single place that builds the RFC 9457 body, shared by the exception handler and the security entry point,
 * so the format never diverges.
 */
public final class ProblemDetails {

    public static final String CODE_PROPERTY = "code";

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatusCode status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE_PROPERTY, code.name());
        return problem;
    }
}
