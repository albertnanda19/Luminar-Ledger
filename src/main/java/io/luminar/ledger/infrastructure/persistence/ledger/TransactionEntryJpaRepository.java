package io.luminar.ledger.infrastructure.persistence.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionEntryJpaRepository extends JpaRepository<TransactionEntryEntity, UUID> {
}
