package io.github.ericksoares95.kipay.accounts.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.ericksoares95.kipay.accounts.account.UnderageHolderException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import tools.jackson.core.JacksonException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    static final String VALIDATION_DETAIL = "Um ou mais campos são inválidos.";
    static final String UNDERAGE_DETAIL = "O titular não atinge a idade mínima para abrir uma conta.";
    static final String UNREADABLE_FIELD_MESSAGE = "valor inválido ou em formato inesperado";
    static final String UNREADABLE_BODY_MESSAGE = "corpo da requisição ilegível";
    static final String UNAVAILABLE_DETAIL = "Serviço temporariamente indisponível. Tente novamente em instantes.";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(fieldEntry(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        return handleExceptionInternal(ex, validationProblem(errors), headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String parameter = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String field = error instanceof FieldError fieldError ? fieldError.getField() : parameter;
                errors.add(fieldEntry(field, error.getDefaultMessage()));
            }
        });
        return handleExceptionInternal(ex, validationProblem(errors), headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * A body that cannot be deserialized (for example a malformed {@code birthDate}) is a validation error too.
     * Only the property name taken from the Jackson path is reported; the offending value is never echoed (LGPD).
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        if (ex.getCause() instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
            String property = jackson.getPath().getLast().getPropertyName();
            errors.add(fieldEntry(property, UNREADABLE_FIELD_MESSAGE));
        } else {
            errors.add(fieldEntry("body", UNREADABLE_BODY_MESSAGE));
        }
        return handleExceptionInternal(ex, validationProblem(errors), headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler({ DataAccessResourceFailureException.class, QueryTimeoutException.class,
            CannotCreateTransactionException.class })
    ResponseEntity<Object> handleDatabaseUnavailable(Exception ex, WebRequest request) {
        // Only the exception type is logged: the driver message may carry connection details.
        log.warn("Database unavailable: {}", ex.getClass().getSimpleName());
        ProblemDetail problem = ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE,
                UNAVAILABLE_DETAIL);
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    @ExceptionHandler(UnderageHolderException.class)
    ResponseEntity<Object> handleUnderageHolder(UnderageHolderException ex, WebRequest request) {
        // No name, CPF or birth date in the body: the client already knows what it sent.
        ProblemDetail problem = ProblemDetails.of(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.ACCOUNT_HOLDER_UNDERAGE,
                UNDERAGE_DETAIL);
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.UNPROCESSABLE_CONTENT, request);
    }

    private static ProblemDetail validationProblem(List<Map<String, String>> errors) {
        ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                VALIDATION_DETAIL);
        // Field name and constraint message only: the rejected value is never echoed (LGPD).
        problem.setProperty("errors", errors);
        return problem;
    }

    private static Map<String, String> fieldEntry(String field, String message) {
        return Map.of("field", field == null ? "" : field, "message", message == null ? "" : message);
    }
}
