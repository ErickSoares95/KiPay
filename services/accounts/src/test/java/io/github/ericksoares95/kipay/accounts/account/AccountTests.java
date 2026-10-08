package io.github.ericksoares95.kipay.accounts.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountTests {

    private static final Instant OPENED_AT = Instant.parse("2026-10-08T12:00:00Z");
    private static final Instant ACTIVATED_AT = Instant.parse("2026-10-08T12:00:05Z");

    private final AccountHolder holder = new AccountHolder(Cpf.of("529.982.247-25"), "sub-1", "Ana Souza",
            LocalDate.of(1990, 1, 1), "ana@example.com", OPENED_AT);

    @Test
    @DisplayName("conta recém-aberta fica PENDING e não movimenta dinheiro")
    void pendingAccountCannotMoveMoney() {
        Account account = Account.open(holder, OPENED_AT);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(account.canMoveMoney()).isFalse();
        assertThat(account.getActivatedAt()).isNull();
    }

    @Test
    @DisplayName("conta ACTIVE movimenta dinheiro")
    void activeAccountCanMoveMoney() {
        Account account = Account.open(holder, OPENED_AT);

        account.activate(ACTIVATED_AT);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.canMoveMoney()).isTrue();
        assertThat(account.getActivatedAt()).isEqualTo(ACTIVATED_AT);
    }

    @Test
    @DisplayName("conta CLOSED não movimenta dinheiro")
    void closedAccountCannotMoveMoney() throws Exception {
        Account account = Account.open(holder, OPENED_AT);
        Field status = Account.class.getDeclaredField("status");
        status.setAccessible(true);
        status.set(account, AccountStatus.CLOSED);

        assertThat(account.canMoveMoney()).isFalse();
    }

    @Test
    @DisplayName("activate repetido não muda a conta nem o activated_at")
    void repeatedActivateChangesNothing() {
        Account account = Account.open(holder, OPENED_AT);
        account.activate(ACTIVATED_AT);

        account.activate(ACTIVATED_AT.plusSeconds(60));

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getActivatedAt()).isEqualTo(ACTIVATED_AT);
    }

    @Test
    @DisplayName("toString do titular mostra o CPF mascarado e nenhum dado pessoal")
    void holderToStringIsMasked() {
        assertThat(holder.toString()).contains("***.982.247-**")
                .doesNotContain("529.982.247-25", "52998224725", "Ana", "ana@example.com", "sub-1");
    }
}
