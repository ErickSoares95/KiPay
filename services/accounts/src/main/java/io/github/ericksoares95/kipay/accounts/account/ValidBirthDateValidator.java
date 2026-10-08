package io.github.ericksoares95.kipay.accounts.account;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidBirthDateValidator implements ConstraintValidator<ValidBirthDate, String> {

    static final String REQUIRED = "é obrigatória";
    static final String BAD_FORMAT = "deve estar no formato AAAA-MM-DD";
    static final String FUTURE = "não pode ser uma data futura";

    private final Clock clock;

    public ValidBirthDateValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return reject(context, REQUIRED);
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value);
        } catch (DateTimeException e) {
            return reject(context, BAD_FORMAT);
        }
        if (date.isAfter(AccountHolderPolicy.today(clock))) {
            return reject(context, FUTURE);
        }
        return true;
    }

    private static boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
