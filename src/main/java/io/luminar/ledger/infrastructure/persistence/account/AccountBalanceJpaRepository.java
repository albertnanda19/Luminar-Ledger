package io.luminar.ledger.infrastructure.persistence.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AccountBalanceJpaRepository extends JpaRepository<AccountBalanceEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from AccountBalanceEntity b where b.accountId in :ids")
    List<AccountBalanceEntity> findByAccountIdIn(@Param("ids") Collection<UUID> ids);
}
