package io.luminar.ledger.infrastructure.persistence.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerEventJpaRepository extends JpaRepository<LedgerEventEntity, UUID> {
	Optional<LedgerEventEntity> findByReferenceId(String referenceId);
}
