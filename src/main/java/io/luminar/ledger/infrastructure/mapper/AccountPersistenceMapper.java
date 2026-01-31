package io.luminar.ledger.infrastructure.mapper;

import io.luminar.ledger.domain.account.Account;
import io.luminar.ledger.domain.account.AccountId;
import io.luminar.ledger.domain.account.AccountStatus;
import io.luminar.ledger.domain.account.AccountType;
import io.luminar.ledger.domain.account.Currency;
import io.luminar.ledger.infrastructure.persistence.account.AccountBalanceEntity;
import io.luminar.ledger.infrastructure.persistence.account.AccountEntity;
import io.luminar.ledger.infrastructure.persistence.account.AccountStatusEntity;
import io.luminar.ledger.infrastructure.persistence.account.AccountTypeEntity;

import java.math.BigDecimal;

public final class AccountPersistenceMapper {
	private AccountPersistenceMapper() {
	}

	public static AccountEntity toEntity(Account account) {
		return new AccountEntity(
				account.id().value(),
				account.code(),
				account.name(),
				toEntityType(account.type()),
				account.currency().code(),
				toEntityStatus(account.status()),
				account.frozenAt(),
				account.closedAt(),
				account.statusChangedAt(),
				account.statusReason());
	}

	public static Account toDomain(AccountEntity entity) {
		return Account.rehydrate(
				new AccountId(entity.getId()),
				entity.getCode(),
				entity.getName(),
				toDomainType(entity.getType()),
				new Currency(entity.getCurrency()),
				toDomainStatus(entity.getStatus()),
				entity.getFrozenAt(),
				entity.getClosedAt(),
				entity.getStatusChangedAt(),
				entity.getStatusReason());
	}

	public static BigDecimal toBalance(AccountBalanceEntity entity) {
		return entity.getBalance();
	}

	private static AccountStatusEntity toEntityStatus(AccountStatus status) {
		return switch (status) {
			case ACTIVE -> AccountStatusEntity.ACTIVE;
			case FROZEN -> AccountStatusEntity.FROZEN;
			case CLOSED -> AccountStatusEntity.CLOSED;
		};
	}

	private static AccountStatus toDomainStatus(AccountStatusEntity status) {
		return switch (status) {
			case ACTIVE -> AccountStatus.ACTIVE;
			case FROZEN -> AccountStatus.FROZEN;
			case CLOSED -> AccountStatus.CLOSED;
		};
	}

	private static AccountTypeEntity toEntityType(AccountType type) {
		return switch (type) {
			case ASSET -> AccountTypeEntity.ASSET;
			case LIABILITY -> AccountTypeEntity.LIABILITY;
			case EQUITY -> AccountTypeEntity.EQUITY;
			case REVENUE -> AccountTypeEntity.REVENUE;
			case EXPENSE -> AccountTypeEntity.EXPENSE;
		};
	}

	private static AccountType toDomainType(AccountTypeEntity type) {
		return switch (type) {
			case ASSET -> AccountType.ASSET;
			case LIABILITY -> AccountType.LIABILITY;
			case EQUITY -> AccountType.EQUITY;
			case REVENUE -> AccountType.REVENUE;
			case EXPENSE -> AccountType.EXPENSE;
		};
	}
}
