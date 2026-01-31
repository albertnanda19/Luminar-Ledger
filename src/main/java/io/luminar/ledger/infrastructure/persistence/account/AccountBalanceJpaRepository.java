package io.luminar.ledger.infrastructure.persistence.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountBalanceJpaRepository extends JpaRepository<AccountBalanceEntity, UUID> {
}
