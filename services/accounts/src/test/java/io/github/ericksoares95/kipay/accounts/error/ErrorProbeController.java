package io.github.ericksoares95.kipay.accounts.error;

import java.util.Map;

import io.github.ericksoares95.kipay.accounts.account.AccountOpeningRequest;
import io.github.ericksoares95.kipay.accounts.account.UnderageHolderException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Support controller that exists only in the test classpath. It is not component-scanned from production code and
 * must be imported explicitly by the tests that need a route to exercise validation, 503 and authentication.
 */
@RestController
@RequestMapping("/test-probe")
public class ErrorProbeController {

    public static final String DRIVER_MESSAGE = "connection to jdbc:postgresql://internal-host:5432/accounts refused";

    public record ProbeRequest(@NotBlank String name, @Pattern(regexp = "\\d{11}") String document) {
    }

    @PostMapping("/validation")
    Map<String, String> validation(@Valid @RequestBody ProbeRequest request) {
        return Map.of("status", "ok");
    }

    @PostMapping("/account-opening")
    Map<String, String> accountOpening(@Valid @RequestBody AccountOpeningRequest request) {
        return Map.of("status", "ok");
    }

    @GetMapping("/db-failure")
    Map<String, String> dbFailure() {
        throw new DataAccessResourceFailureException(DRIVER_MESSAGE);
    }

    @GetMapping("/underage")
    Map<String, String> underage() {
        throw new UnderageHolderException();
    }

    @GetMapping("/protected")
    Map<String, String> protectedRoute(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("sub", jwt.getSubject());
    }
}
