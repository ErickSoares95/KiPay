package io.github.ericksoares95.kipay.accounts.account;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_holder_id", nullable = false)
    private AccountHolder accountHolder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Account() {
    }

    private Account(AccountHolder accountHolder, Instant openedAt) {
        this.accountHolder = accountHolder;
        this.status = AccountStatus.PENDING;
        this.openedAt = openedAt;
    }

    /** Creates an account in {@link AccountStatus#PENDING}; it only moves money after {@link #activate}. */
    public static Account open(AccountHolder accountHolder, Instant openedAt) {
        return new Account(accountHolder, openedAt);
    }

    /** Idempotent: on an already ACTIVE account nothing changes. */
    public void activate(Instant activatedAt) {
        if (status == AccountStatus.PENDING) {
            this.status = AccountStatus.ACTIVE;
            this.activatedAt = activatedAt;
        }
    }

    public boolean canMoveMoney() {
        return status == AccountStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public AccountHolder getAccountHolder() {
        return accountHolder;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Account[id=" + id + ", status=" + status + "]";
    }
}
