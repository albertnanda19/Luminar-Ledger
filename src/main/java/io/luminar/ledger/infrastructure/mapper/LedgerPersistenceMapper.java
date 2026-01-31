package io.luminar.ledger.infrastructure.mapper;

import io.luminar.ledger.domain.account.AccountId;
import io.luminar.ledger.domain.account.Currency;
import io.luminar.ledger.domain.common.ReferenceKey;
import io.luminar.ledger.domain.ledger.EntryType;
import io.luminar.ledger.domain.ledger.LedgerEntry;
import io.luminar.ledger.domain.ledger.LedgerTransaction;
import io.luminar.ledger.domain.ledger.Money;
import io.luminar.ledger.infrastructure.persistence.ledger.EntryTypeEntity;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionEntity;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionEntryEntity;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionStatusEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LedgerPersistenceMapper {
	private LedgerPersistenceMapper() {
	}

	public static TransactionEntity toTransactionEntity(LedgerTransaction transaction, TransactionStatusEntity status) {
		return new TransactionEntity(
				transaction.id(),
				transaction.referenceKey().value(),
				status,
				transaction.occurredAt()
		);
	}

	public static List<TransactionEntryEntity> toTransactionEntryEntities(LedgerTransaction transaction) {
		List<TransactionEntryEntity> result = new ArrayList<>(transaction.entries().size());
		for (LedgerEntry entry : transaction.entries()) {
			result.add(toTransactionEntryEntity(transaction.id(), transaction.occurredAt(), entry));
		}
		return result;
	}

	public static TransactionEntryEntity toTransactionEntryEntity(UUID transactionId, java.time.Instant occurredAt, LedgerEntry entry) {
		return new TransactionEntryEntity(
				null,
				transactionId,
				entry.accountId().value(),
				toEntityType(entry.type()),
				entry.amount().amount(),
				occurredAt
		);
	}

	public static LedgerEntry toDomainEntry(TransactionEntryEntity entity, Currency currency) {
		return new LedgerEntry(
				new AccountId(entity.getAccountId()),
				toDomainType(entity.getEntryType()),
				new Money(currency, entity.getAmount())
		);
	}

	public static LedgerTransaction toDomainTransaction(TransactionEntity entity, List<TransactionEntryEntity> entries, Currency currency) {
		List<LedgerEntry> domainEntries = new ArrayList<>(entries.size());
		for (TransactionEntryEntity e : entries) {
			domainEntries.add(toDomainEntry(e, currency));
		}
		return new LedgerTransaction(
				entity.getId(),
				entity.getCreatedAt(),
				new ReferenceKey(entity.getReferenceKey()),
				domainEntries
		);
	}

	private static EntryTypeEntity toEntityType(EntryType type) {
		return switch (type) {
			case DEBIT -> EntryTypeEntity.DEBIT;
			case CREDIT -> EntryTypeEntity.CREDIT;
		};
	}

	private static EntryType toDomainType(EntryTypeEntity type) {
		return switch (type) {
			case DEBIT -> EntryType.DEBIT;
			case CREDIT -> EntryType.CREDIT;
		};
	}
}
