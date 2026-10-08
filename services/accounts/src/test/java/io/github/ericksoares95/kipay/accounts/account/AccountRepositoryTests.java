package io.github.ericksoares95.kipay.accounts.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AccountRepositoryTests.PostgresConfiguration.class)
class AccountRepositoryTests {

    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    private static final String CPF_A = "52998224725";
    private static final String CPF_B = "11144477735";

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfiguration {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:17.11-alpine"));
        }
    }

    private final AccountHolderRepository holders;
    private final AccountRepository accounts;
    private final JdbcClient jdbc;

    @Autowired
    AccountRepositoryTests(AccountHolderRepository holders, AccountRepository accounts, JdbcClient jdbc) {
        this.holders = holders;
        this.accounts = accounts;
        this.jdbc = jdbc;
    }

    private AccountHolder holder(String cpf, String subject) {
        return new AccountHolder(new Cpf(cpf), subject, "Ana Souza", LocalDate.of(1990, 1, 1), "ana@example.com", NOW);
    }

    @Test
    @DisplayName("titular e conta são gravados com id UUID v7, CPF em CHAR(11) e conta PENDING")
    void persistsHolderAndAccount() {
        AccountHolder holder = holders.saveAndFlush(holder(CPF_A, "sub-1"));
        Account account = accounts.saveAndFlush(Account.open(holder, NOW));

        assertThat(holder.getId().version()).isEqualTo(7);
        assertThat(account.getId().version()).isEqualTo(7);
        assertThat(jdbc.sql("SELECT cpf FROM account_holders WHERE id = ?").param(holder.getId())
                .query(String.class).single()).isEqualTo(CPF_A);
        assertThat(jdbc.sql("SELECT status FROM accounts WHERE id = ?").param(account.getId())
                .query(String.class).single()).isEqualTo("PENDING");
        assertThat(account.getVersion()).isZero();
    }

    @Test
    @DisplayName("o CPF duplicado viola uk_account_holders_cpf")
    void duplicateCpfViolatesConstraint() {
        holders.saveAndFlush(holder(CPF_A, "sub-1"));

        assertThatThrownBy(() -> holders.saveAndFlush(holder(CPF_A, "sub-2")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_account_holders_cpf");
    }

    @Test
    @DisplayName("a identidade duplicada com outro CPF viola uk_account_holders_owner_subject")
    void duplicateOwnerSubjectViolatesConstraint() {
        holders.saveAndFlush(holder(CPF_A, "sub-1"));

        assertThatThrownBy(() -> holders.saveAndFlush(holder(CPF_B, "sub-1")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_account_holders_owner_subject");
    }

    @Test
    @DisplayName("uma segunda conta não encerrada para o mesmo titular viola uk_accounts_open_per_holder")
    void secondOpenAccountViolatesPartialIndex() {
        AccountHolder holder = holders.saveAndFlush(holder(CPF_A, "sub-1"));
        accounts.saveAndFlush(Account.open(holder, NOW));

        assertThatThrownBy(() -> accounts.saveAndFlush(Account.open(holder, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_accounts_open_per_holder");
    }

    @Test
    @DisplayName("uma segunda conta é aceita quando a primeira está CLOSED")
    void secondAccountAcceptedWhenFirstIsClosed() {
        AccountHolder holder = holders.saveAndFlush(holder(CPF_A, "sub-1"));
        jdbc.sql("""
                INSERT INTO accounts (id, account_holder_id, status, opened_at, version)
                VALUES (?, ?, 'CLOSED', ?, 0)
                """).param(UuidV7.generate()).param(holder.getId())
                .param(java.sql.Timestamp.from(NOW)).update();

        Account second = accounts.saveAndFlush(Account.open(holder, NOW));

        assertThat(accounts.findById(second.getId())).isPresent();
        assertThat(jdbc.sql("SELECT count(*) FROM accounts WHERE account_holder_id = ?").param(holder.getId())
                .query(Long.class).single()).isEqualTo(2L);
    }

    @Test
    @DisplayName("o banco recusa status fora de PENDING, ACTIVE e CLOSED")
    void unknownStatusViolatesCheck() {
        AccountHolder holder = holders.saveAndFlush(holder(CPF_A, "sub-1"));

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO accounts (id, account_holder_id, status, opened_at, version)
                VALUES (?, ?, 'BLOCKED', ?, 0)
                """).param(UUID.randomUUID()).param(holder.getId()).param(java.sql.Timestamp.from(NOW)).update())
                .hasMessageContaining("ck_accounts_status");
    }
}
