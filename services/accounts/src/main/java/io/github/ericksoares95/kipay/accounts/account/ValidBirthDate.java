package io.github.ericksoares95.kipay.accounts.account;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The text must be a present ISO-8601 date ({@code yyyy-MM-dd}) that is not after today's civil date in
 * {@code America/Sao_Paulo}, taken from the injected {@link java.time.Clock}. Null, blank, malformed and future
 * values are all rejected, and the value is never echoed in the message.
 */
@Documented
@Constraint(validatedBy = ValidBirthDateValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidBirthDate {

    String message() default "data de nascimento inválida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
