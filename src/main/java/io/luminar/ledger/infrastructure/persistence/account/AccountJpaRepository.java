package io.luminar.ledger.infrastructure.persistence.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> {
	Optional<AccountEntity> findByCode(String code);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from AccountEntity a where a.id = :id")
	Optional<AccountEntity> findByIdForUpdate(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from AccountEntity a where a.id in :ids")
	List<AccountEntity> findByIdIn(@Param("ids") Collection<UUID> ids);
}
