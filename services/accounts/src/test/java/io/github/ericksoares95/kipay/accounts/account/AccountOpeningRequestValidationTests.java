package io.github.ericksoares95.kipay.accounts.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountOpeningRequestValidationTests {

    // 2026-06-15 12:00 in Sao Paulo
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-15T15:00:00Z"),
            ZoneId.of("America/Sao_Paulo"));
    private static final ValidatorFactory FACTORY = Validation.byDefaultProvider().configure()
            .constraintValidatorFactory(new ClockAwareFactory()).buildValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    private static final String ADULT = "1990-01-01";
    private static final String CPF = "52998224725";

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    private static List<String> invalidFields(AccountOpeningRequest request) {
        Set<ConstraintViolation<AccountOpeningRequest>> violations = VALIDATOR.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString()).toList();
    }

    @Test
    @DisplayName("um pedido completo e com data de nascimento no passado não tem violações")
    void validRequestHasNoViolations() {
        assertThat(invalidFields(new AccountOpeningRequest("Ana Souza", CPF, ADULT))).isEmpty();
    }

    @Test
    @DisplayName("nome nulo é indicado como campo fullName")
    void nullNameIsReported() {
        assertThat(invalidFields(new AccountOpeningRequest(null, CPF, ADULT))).containsExactly("fullName");
    }

    @Test
    @DisplayName("nome vazio é indicado como campo fullName")
    void emptyNameIsReported() {
        assertThat(invalidFields(new AccountOpeningRequest("", CPF, ADULT))).containsExactly("fullName");
    }

    @Test
    @DisplayName("nome só com espaços é indicado como campo fullName")
    void blankNameIsReported() {
        assertThat(invalidFields(new AccountOpeningRequest("   ", CPF, ADULT))).containsExactly("fullName");
    }

    @Test
    @DisplayName("data de nascimento nula é indicada como campo birthDate")
    void nullBirthDateIsReported() {
        assertThat(invalidFields(new AccountOpeningRequest("Ana Souza", CPF, null))).containsExactly("birthDate");
    }

    @Test
    @DisplayName("data de nascimento vazia ou só com espaços é indicada como campo birthDate")
    void emptyBirthDateIsReported() {
        assertThat(invalidFields(new AccountOpeningRequest("Ana Souza", CPF, ""))).containsExactly("birthDate");
        assertThat(invalidFields(new AccountOpeningRequest("Ana Souza", CPF, "  "))).containsExactly("birthDate");
    }

    @Test
    @DisplayName("data de nascimento em formato inválido ou inexistente é indicada como campo birthDate, sem eco do valor")
    void malformedBirthDateIsReported() {
        for (String malformed : new String[] { "31/12/2000", "abc", "2000-02-30", "2000-13-01" }) {
            Set<ConstraintViolation<AccountOpeningRequest>> violations = VALIDATOR
                    .validate(new AccountOpeningRequest("Ana Souza", CPF, malformed));

            assertThat(violations).hasSize(1);
            ConstraintViolation<AccountOpeningRequest> violation = violations.iterator().next();
            assertThat(violation.getPropertyPath().toString()).isEqualTo("birthDate");
            assertThat(violation.getMessage()).doesNotContain(malformed);
        }
    }

    @Test
    @DisplayName("data de nascimento futura é indicada como campo birthDate")
    void futureBirthDateIsReported() {
        assertThat(invalidFields(new AccountOpeningRequest("Ana Souza", CPF, "2026-06-16")))
                .containsExactly("birthDate");
    }

    @Test
    @DisplayName("data de nascimento igual a hoje em São Paulo não é futura")
    void todayBirthDateIsNotFuture() {
        assertThat(invalidFields(new AccountOpeningRequest("Ana Souza", CPF, "2026-06-15"))).isEmpty();
    }

    @Test
    @DisplayName("vários campos com problema aparecem juntos nas violações")
    void severalInvalidFieldsAreAllReported() {
        assertThat(invalidFields(new AccountOpeningRequest("  ", CPF, "2030-01-01")))
                .containsExactlyInAnyOrder("fullName", "birthDate");
        assertThat(invalidFields(new AccountOpeningRequest(null, null, null)))
                .containsExactlyInAnyOrder("fullName", "birthDate");
        assertThat(invalidFields(new AccountOpeningRequest("  ", CPF, "abc")))
                .containsExactlyInAnyOrder("fullName", "birthDate");
    }

    @Test
    @DisplayName("birthDateValue devolve o LocalDate do pedido já validado")
    void birthDateValueReturnsParsedDate() {
        assertThat(new AccountOpeningRequest("Ana Souza", CPF, ADULT).birthDateValue())
                .isEqualTo(LocalDate.of(1990, 1, 1));
    }

    @Test
    @DisplayName("o toString do pedido não expõe dados pessoais")
    void toStringHasNoPersonalData() {
        String text = new AccountOpeningRequest("Ana Souza", CPF, ADULT).toString();

        assertThat(text).doesNotContain("Ana").doesNotContain(CPF).doesNotContain("1990");
    }

    @Test
    @DisplayName("o nome completo é guardado sem os espaços nas pontas")
    void fullNameIsStoredWithoutSurroundingSpaces() {
        AccountHolder holder = new AccountHolder(Cpf.of("529.982.247-25"), "sub-1", "  Ana  Souza \t",
                LocalDate.of(1990, 1, 1), "ana@example.com", Instant.parse("2026-06-15T15:00:00Z"));

        assertThat(holder.getFullName()).isEqualTo("Ana  Souza");
    }

    /** Builds validators with the fixed clock, as Spring does with constructor injection. */
    private static final class ClockAwareFactory implements ConstraintValidatorFactory {

        @Override
        @SuppressWarnings("unchecked")
        public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            if (key == ValidBirthDateValidator.class) {
                return (T) new ValidBirthDateValidator(CLOCK);
            }
            try {
                return key.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void releaseInstance(ConstraintValidator<?, ?> instance) {
        }
    }
}
