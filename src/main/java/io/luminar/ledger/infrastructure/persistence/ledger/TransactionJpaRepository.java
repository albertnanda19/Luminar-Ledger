package io.luminar.ledger.infrastructure.persistence.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionJpaRepository extends JpaRepository<TransactionEntity, UUID> {
	Optional<TransactionEntity> findByReferenceKey(String referenceKey);
}
