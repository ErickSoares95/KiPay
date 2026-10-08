package io.github.ericksoares95.kipay.accounts.account;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountHolderRepository extends JpaRepository<AccountHolder, UUID> {
}
