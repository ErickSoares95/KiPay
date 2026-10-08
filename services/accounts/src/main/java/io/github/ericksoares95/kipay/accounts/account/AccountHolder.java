package io.github.ericksoares95.kipay.accounts.account;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_holders")
public class AccountHolder {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Convert(converter = CpfConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "cpf", nullable = false, length = 11, columnDefinition = "char(11)")
    private Cpf cpf;

    @Column(name = "owner_subject", nullable = false)
    private String ownerSubject;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AccountHolder() {
    }

    public AccountHolder(Cpf cpf, String ownerSubject, String fullName, LocalDate birthDate, String email,
            Instant createdAt) {
        this.cpf = cpf;
        this.ownerSubject = ownerSubject;
        // Single normalization point: the name is kept as informed, without leading/trailing spaces.
        this.fullName = fullName == null ? null : fullName.strip();
        this.birthDate = birthDate;
        this.email = email;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public Cpf getCpf() {
        return cpf;
    }

    public String getOwnerSubject() {
        return ownerSubject;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Personal data (name, e-mail, subject) is left out on purpose; the CPF is masked. */
    @Override
    public String toString() {
        return "AccountHolder[id=" + id + ", cpf=" + cpf + "]";
    }
}
