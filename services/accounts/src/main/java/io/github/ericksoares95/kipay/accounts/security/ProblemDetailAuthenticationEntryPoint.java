package io.github.ericksoares95.kipay.accounts.security;

import java.io.IOException;
import java.net.URI;

import io.github.ericksoares95.kipay.accounts.error.ErrorCode;
import io.github.ericksoares95.kipay.accounts.error.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Answers {@code 401} as {@code ProblemDetail}. The body is the same for any cause (missing, expired, wrong audience,
 * wrong issuer or bad signature), so the reason is never revealed.
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String DETAIL = "Autenticação necessária.";

    private final JacksonJsonHttpMessageConverter converter;

    public ProblemDetailAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.converter = new JacksonJsonHttpMessageConverter(jsonMapper);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        ProblemDetail problem = ProblemDetails.of(HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_REQUIRED, DETAIL);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader("WWW-Authenticate", "Bearer");
        converter.write(problem, MediaType.APPLICATION_PROBLEM_JSON, new ServletServerHttpResponse(response));
    }
}
