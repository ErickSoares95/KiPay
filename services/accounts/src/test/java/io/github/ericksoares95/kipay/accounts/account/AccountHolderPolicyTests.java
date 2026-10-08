package io.github.ericksoares95.kipay.accounts.account;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountHolderPolicyTests {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private static AccountHolderPolicy policyAt(String instant) {
        return new AccountHolderPolicy(Clock.fixed(Instant.parse(instant), SAO_PAULO));
    }

    @Test
    @DisplayName("na véspera do 18º aniversário o titular é recusado")
    void eveOfEighteenthBirthdayIsRejected() {
        AccountHolderPolicy policy = policyAt("2026-06-14T15:00:00Z");

        assertThatThrownBy(() -> policy.requireMinimumAge(LocalDate.of(2008, 6, 15)))
                .isInstanceOf(UnderageHolderException.class);
    }

    @Test
    @DisplayName("no dia do 18º aniversário o titular é aceito")
    void eighteenthBirthdayIsAccepted() {
        AccountHolderPolicy policy = policyAt("2026-06-15T15:00:00Z");

        assertThatCode(() -> policy.requireMinimumAge(LocalDate.of(2008, 6, 15))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("quem nasceu em 29/02 e faz 18 anos num ano não bissexto é recusado em 28/02")
    void leapDayBornIsRejectedOnFebruary28() {
        AccountHolderPolicy policy = policyAt("2026-02-28T15:00:00Z");

        assertThatThrownBy(() -> policy.requireMinimumAge(LocalDate.of(2008, 2, 29)))
                .isInstanceOf(UnderageHolderException.class);
    }

    @Test
    @DisplayName("quem nasceu em 29/02 e faz 18 anos num ano não bissexto é aceito em 01/03")
    void leapDayBornIsAcceptedOnMarch1() {
        AccountHolderPolicy policy = policyAt("2026-03-01T15:00:00Z");

        assertThatCode(() -> policy.requireMinimumAge(LocalDate.of(2008, 2, 29))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a data civil é a de São Paulo: às 01:00 UTC do dia seguinte ainda é a véspera e o titular é recusado")
    void civilDateFollowsSaoPauloNotUtc() {
        // 2026-06-15T01:00Z is still 2026-06-14 22:00 in Sao Paulo (UTC-3).
        AccountHolderPolicy policy = policyAt("2026-06-15T01:00:00Z");

        assertThatThrownBy(() -> policy.requireMinimumAge(LocalDate.of(2008, 6, 15)))
                .isInstanceOf(UnderageHolderException.class);
    }

    @Test
    @DisplayName("a mensagem da exceção de menor de idade não traz dados pessoais")
    void underageExceptionMessageHasNoPersonalData() {
        AccountHolderPolicy policy = policyAt("2026-06-14T15:00:00Z");

        assertThatThrownBy(() -> policy.requireMinimumAge(LocalDate.of(2008, 6, 15)))
                .hasMessageNotContaining("2008");
    }
}
