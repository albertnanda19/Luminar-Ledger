package io.luminar.ledger.infrastructure.mapper;

import io.luminar.ledger.domain.account.Account;
import io.luminar.ledger.domain.account.AccountId;
import io.luminar.ledger.domain.account.AccountStatus;
import io.luminar.ledger.domain.account.Currency;
import io.luminar.ledger.infrastructure.persistence.account.AccountBalanceEntity;
import io.luminar.ledger.infrastructure.persistence.account.AccountEntity;
import io.luminar.ledger.infrastructure.persistence.account.AccountStatusEntity;

import java.math.BigDecimal;

public final class AccountPersistenceMapper {
	private AccountPersistenceMapper() {
	}

	public static AccountEntity toEntity(Account account) {
		return new AccountEntity(
				account.id().value(),
				account.currency().code(),
				toEntityStatus(account.status())
		);
	}

	public static Account toDomain(AccountEntity entity) {
		return Account.rehydrate(
				new AccountId(entity.getId()),
				new Currency(entity.getCurrency()),
				toDomainStatus(entity.getStatus())
		);
	}

	public static BigDecimal toBalance(AccountBalanceEntity entity) {
		return entity.getBalance();
	}

	private static AccountStatusEntity toEntityStatus(AccountStatus status) {
		return switch (status) {
			case ACTIVE -> AccountStatusEntity.ACTIVE;
			case SUSPENDED -> AccountStatusEntity.FROZEN;
			case CLOSED -> throw new IllegalArgumentException("AccountStatus.CLOSED is not representable in persistence");
		};
	}

	private static AccountStatus toDomainStatus(AccountStatusEntity status) {
		return switch (status) {
			case ACTIVE -> AccountStatus.ACTIVE;
			case FROZEN -> AccountStatus.SUSPENDED;
		};
	}
}
