package io.github.ericksoares95.kipay.accounts.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.ericksoares95.kipay.accounts.account.InvalidCpfException.Reason;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CpfTests {

    private static final String VALID = "529.982.247-25";
    private static final String VALID_DIGITS = "52998224725";

    @Test
    @DisplayName("um CPF válido com pontuação é aceito e normalizado para 11 dígitos")
    void acceptsValidCpfWithPunctuation() {
        assertThat(Cpf.of(VALID).value()).isEqualTo(VALID_DIGITS);
    }

    @Test
    @DisplayName("o mesmo CPF com e sem pontuação é igual por valor")
    void sameCpfWithAndWithoutPunctuationIsEqual() {
        Cpf punctuated = Cpf.of(VALID);
        Cpf plain = Cpf.of(VALID_DIGITS);

        assertThat(punctuated).isEqualTo(plain).hasSameHashCodeAs(plain);
    }

    @ParameterizedTest(name = "dígitos verificadores incorretos em {0}")
    @ValueSource(strings = { "529.982.247-26", "529.982.247-35", "52998224715", "111.444.777-36" })
    @DisplayName("dígitos verificadores incorretos são rejeitados com o motivo CHECK_DIGITS")
    void rejectsWrongCheckDigits(String raw) {
        assertThatThrownBy(() -> Cpf.of(raw))
                .isInstanceOfSatisfying(InvalidCpfException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.CHECK_DIGITS));
    }

    @ParameterizedTest(name = "dígito repetido em {0}")
    @ValueSource(strings = { "000.000.000-00", "111.111.111-11", "99999999999" })
    @DisplayName("CPF com um único dígito repetido é rejeitado com o motivo CHECK_DIGITS")
    void rejectsRepeatedDigits(String raw) {
        assertThatThrownBy(() -> Cpf.of(raw))
                .isInstanceOfSatisfying(InvalidCpfException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.CHECK_DIGITS));
    }

    @ParameterizedTest(name = "formato inválido: [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "5299822472", "529982247250", "529.982.247-2A", "abcdefghijk" })
    @DisplayName("CPF nulo, vazio, com 10 ou 12 dígitos ou com letras é rejeitado com o motivo FORMAT")
    void rejectsBadFormat(String raw) {
        assertThatThrownBy(() -> Cpf.of(raw))
                .isInstanceOfSatisfying(InvalidCpfException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.FORMAT));
    }

    @Test
    @DisplayName("masked e toString devolvem o CPF mascarado")
    void maskedAndToStringAreMasked() {
        Cpf cpf = Cpf.of(VALID);

        assertThat(cpf.masked()).isEqualTo("***.982.247-**");
        assertThat(cpf.toString()).isEqualTo("***.982.247-**");
        assertThat(cpf.toString()).doesNotContain(VALID_DIGITS);
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = { "529.982.247-26", "111.111.111-11", "529982247" })
    @DisplayName("a mensagem da exceção não contém o CPF informado")
    void exceptionMessageDoesNotLeakCpf(String raw) {
        assertThatThrownBy(() -> Cpf.of(raw))
                .isInstanceOf(InvalidCpfException.class)
                .message().doesNotContain(raw).doesNotContain(raw.replaceAll("[.-]", ""));
    }
}
