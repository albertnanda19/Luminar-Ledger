package io.luminar.ledger.service;

import io.luminar.ledger.application.transaction.command.PostTransactionCommand;
import io.luminar.ledger.domain.account.AccountId;
import io.luminar.ledger.domain.account.Currency;
import io.luminar.ledger.domain.common.DomainException;
import io.luminar.ledger.domain.common.ReferenceKey;
import io.luminar.ledger.domain.ledger.EntryType;
import io.luminar.ledger.domain.ledger.LedgerEntry;
import io.luminar.ledger.domain.ledger.LedgerTransaction;
import io.luminar.ledger.domain.ledger.Money;
import io.luminar.ledger.infrastructure.mapper.LedgerPersistenceMapper;
import io.luminar.ledger.infrastructure.persistence.account.AccountBalanceEntity;
import io.luminar.ledger.infrastructure.persistence.account.AccountBalanceJpaRepository;
import io.luminar.ledger.infrastructure.persistence.account.AccountEntity;
import io.luminar.ledger.infrastructure.persistence.account.AccountJpaRepository;
import io.luminar.ledger.infrastructure.persistence.account.AccountStatusEntity;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionEntity;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionEntryEntity;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionEntryJpaRepository;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionJpaRepository;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionStatusEntity;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class LedgerPostingService {
	private final TransactionJpaRepository transactionJpaRepository;
	private final TransactionEntryJpaRepository transactionEntryJpaRepository;
	private final AccountJpaRepository accountJpaRepository;
	private final AccountBalanceJpaRepository accountBalanceJpaRepository;
	private final EntityManager entityManager;

	public LedgerPostingService(
			TransactionJpaRepository transactionJpaRepository,
			TransactionEntryJpaRepository transactionEntryJpaRepository,
			AccountJpaRepository accountJpaRepository,
			AccountBalanceJpaRepository accountBalanceJpaRepository,
			EntityManager entityManager) {
		this.transactionJpaRepository = Objects.requireNonNull(transactionJpaRepository);
		this.transactionEntryJpaRepository = Objects.requireNonNull(transactionEntryJpaRepository);
		this.accountJpaRepository = Objects.requireNonNull(accountJpaRepository);
		this.accountBalanceJpaRepository = Objects.requireNonNull(accountBalanceJpaRepository);
		this.entityManager = Objects.requireNonNull(entityManager);
	}

	@Transactional(isolation = Isolation.SERIALIZABLE)
	public UUID post(PostTransactionCommand command) {
		Objects.requireNonNull(command, "PostTransactionCommand is required");

		Optional<TransactionEntity> existing = transactionJpaRepository.findByReferenceKey(command.referenceKey());
		if (existing.isPresent()) {
			return existing.get().getId();
		}

		Set<UUID> accountIds = extractAccountIds(command.entries());
		List<AccountEntity> lockedAccounts = accountJpaRepository.findByIdIn(accountIds);
		validateAccounts(command, accountIds, lockedAccounts);

		Currency currency = new Currency(lockedAccounts.getFirst().getCurrency());
		LedgerTransaction domainTransaction = buildDomainTransaction(command, currency);

		TransactionEntity txEntity = LedgerPersistenceMapper.toTransactionEntity(domainTransaction,
				TransactionStatusEntity.POSTED);
		List<TransactionEntryEntity> entryEntities = LedgerPersistenceMapper
				.toTransactionEntryEntities(domainTransaction);

		try {
			transactionJpaRepository.save(Objects.requireNonNull(txEntity));
			transactionEntryJpaRepository.saveAll(Objects.requireNonNull(entryEntities));
		} catch (DataIntegrityViolationException e) {
			TransactionEntity concurrent = transactionJpaRepository.findByReferenceKey(command.referenceKey())
					.orElseThrow(() -> new DomainException("Transaction persistence failed", e));
			return concurrent.getId();
		}

		applyBalanceUpdates(domainTransaction.entries());
		return domainTransaction.id();
	}

	private static Set<UUID> extractAccountIds(List<PostTransactionCommand.Entry> entries) {
		Set<UUID> ids = new HashSet<>();
		for (PostTransactionCommand.Entry e : entries) {
			if (e == null) {
				throw new IllegalArgumentException("PostTransactionCommand.entries must not contain null");
			}
			ids.add(e.accountId());
		}
		return ids;
	}

	private static void validateAccounts(PostTransactionCommand command, Set<UUID> requestedAccountIds,
			List<AccountEntity> lockedAccounts) {
		if (lockedAccounts.size() != requestedAccountIds.size()) {
			throw new DomainException("One or more accounts do not exist");
		}

		String currency = lockedAccounts.getFirst().getCurrency();
		for (AccountEntity account : lockedAccounts) {
			if (account.getStatus() != AccountStatusEntity.ACTIVE) {
				throw new DomainException("Account is not ACTIVE: " + account.getId());
			}
			if (!currency.equals(account.getCurrency())) {
				throw new DomainException("Transaction accounts must be single-currency");
			}
		}

		if (command.entries().size() < 2) {
			throw new DomainException("LedgerTransaction must have at least 2 entries");
		}
	}

	private static LedgerTransaction buildDomainTransaction(PostTransactionCommand command, Currency currency) {
		List<LedgerEntry> entries = command.entries().stream()
				.map(e -> new LedgerEntry(
						new AccountId(e.accountId()),
						toDomainType(e.entryType()),
						new Money(currency, e.amount())))
				.toList();

		return new LedgerTransaction(
				UUID.randomUUID(),
				Instant.now(),
				new ReferenceKey(command.referenceKey()),
				entries);
	}

	private void applyBalanceUpdates(List<LedgerEntry> entries) {
		Map<UUID, BigDecimal> netChanges = aggregateNetChanges(entries);
		Set<UUID> accountIds = netChanges.keySet();
		Iterable<UUID> accountIdIterable = accountIds;
		List<AccountBalanceEntity> balances = accountBalanceJpaRepository
				.findAllById(Objects.requireNonNull(accountIdIterable));
		if (balances.size() != accountIds.size()) {
			throw new DomainException("Account balance record missing for one or more accounts");
		}

		for (Map.Entry<UUID, BigDecimal> change : netChanges.entrySet()) {
			int updated = entityManager.createQuery(
					"update AccountBalanceEntity b set b.balance = b.balance + :delta where b.accountId = :accountId")
					.setParameter("delta", change.getValue())
					.setParameter("accountId", change.getKey())
					.executeUpdate();
			if (updated != 1) {
				throw new DomainException("Account balance update failed for accountId: " + change.getKey());
			}
		}
	}

	private static Map<UUID, BigDecimal> aggregateNetChanges(List<LedgerEntry> entries) {
		Map<UUID, BigDecimal> changes = new HashMap<>();
		for (LedgerEntry entry : entries) {
			UUID accountId = entry.accountId().value();
			BigDecimal amount = entry.amount().amount();
			BigDecimal signed = switch (entry.type()) {
				case DEBIT -> amount.negate();
				case CREDIT -> amount;
			};
			changes.merge(accountId, signed, BigDecimal::add);
		}
		return changes;
	}

	private static EntryType toDomainType(PostTransactionCommand.EntryType entryType) {
		return switch (entryType) {
			case DEBIT -> EntryType.DEBIT;
			case CREDIT -> EntryType.CREDIT;
		};
	}
}
