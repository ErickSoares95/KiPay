package io.github.ericksoares95.kipay.accounts.account;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to open an account. The CPF is only declared here: it is validated by {@link Cpf} (D3 order). The birth
 * date travels as text so that a malformed value is reported by {@link ValidBirthDate} together with the other
 * invalid fields. Personal data stays out of {@code toString()}.
 */
public record AccountOpeningRequest(
        @NotBlank String fullName,
        String cpf,
        @ValidBirthDate String birthDate) {

    /** The parsed birth date. Only call after the request passed validation. */
    public LocalDate birthDateValue() {
        return LocalDate.parse(birthDate);
    }

    @Override
    public String toString() {
        return "AccountOpeningRequest[redacted]";
    }
}
