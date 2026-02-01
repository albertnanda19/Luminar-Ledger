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
import io.luminar.ledger.domain.ledger.event.LedgerTransactionRecordedEvent;
import io.luminar.ledger.infrastructure.mapper.AccountPersistenceMapper;
import io.luminar.ledger.infrastructure.mapper.LedgerEventPersistenceMapper;
import io.luminar.ledger.infrastructure.mapper.LedgerPersistenceMapper;
import io.luminar.ledger.infrastructure.persistence.account.AccountBalanceEntity;
import io.luminar.ledger.infrastructure.persistence.account.AccountBalanceJpaRepository;
import io.luminar.ledger.infrastructure.persistence.account.AccountEntity;
import io.luminar.ledger.infrastructure.persistence.account.AccountJpaRepository;
import io.luminar.ledger.infrastructure.persistence.account.AccountTypeEntity;
import io.luminar.ledger.infrastructure.persistence.ledger.LedgerEventEntity;
import io.luminar.ledger.infrastructure.persistence.ledger.LedgerEventJpaRepository;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionEntity;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionEntryEntity;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionEntryJpaRepository;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionJpaRepository;
import jakarta.persistence.EntityManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
	private final LedgerEventJpaRepository ledgerEventJpaRepository;
	private final AccountJpaRepository accountJpaRepository;
	private final AccountBalanceJpaRepository accountBalanceJpaRepository;
	private final EntityManager entityManager;
	private final ObjectMapper objectMapper;

	public LedgerPostingService(
			TransactionJpaRepository transactionJpaRepository,
			TransactionEntryJpaRepository transactionEntryJpaRepository,
			LedgerEventJpaRepository ledgerEventJpaRepository,
			AccountJpaRepository accountJpaRepository,
			AccountBalanceJpaRepository accountBalanceJpaRepository,
			EntityManager entityManager,
			ObjectMapper objectMapper) {
		this.transactionJpaRepository = Objects.requireNonNull(transactionJpaRepository);
		this.transactionEntryJpaRepository = Objects.requireNonNull(transactionEntryJpaRepository);
		this.ledgerEventJpaRepository = Objects.requireNonNull(ledgerEventJpaRepository);
		this.accountJpaRepository = Objects.requireNonNull(accountJpaRepository);
		this.accountBalanceJpaRepository = Objects.requireNonNull(accountBalanceJpaRepository);
		this.entityManager = Objects.requireNonNull(entityManager);
		this.objectMapper = Objects.requireNonNull(objectMapper);
	}

	@Transactional(isolation = Isolation.SERIALIZABLE)
	public PostedTransaction post(PostTransactionCommand command) {
		Objects.requireNonNull(command, "PostTransactionCommand is required");

		Optional<TransactionEntity> existing = transactionJpaRepository.findByReferenceKey(command.referenceKey());
		if (existing.isPresent()) {
			TransactionEntity entity = existing.get();
			return new PostedTransaction(entity.getId(), entity.getReferenceKey(), entity.getCreatedAt());
		}

		Set<UUID> accountIds = extractAccountIds(command.entries());
		List<AccountEntity> lockedAccounts = accountJpaRepository.findByIdIn(accountIds);
		validateAccounts(command, accountIds, lockedAccounts);

		Currency currency = new Currency(lockedAccounts.getFirst().getCurrency());
		UUID transactionId = UUID.randomUUID();
		Instant occurredAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
		LedgerTransaction domainTransaction = buildDomainTransaction(command, currency, transactionId, occurredAt);

		int inserted = entityManager.createNativeQuery(
				"insert into transactions (id, reference_key, status, created_at) " +
						"values (:id, :referenceKey, 'POSTED'::transaction_status, :createdAt) " +
						"on conflict (reference_key) do nothing")
				.setParameter("id", transactionId)
				.setParameter("referenceKey", command.referenceKey())
				.setParameter("createdAt", occurredAt)
				.executeUpdate();
		if (inserted == 0) {
			TransactionEntity concurrent = transactionJpaRepository.findByReferenceKey(command.referenceKey())
					.orElseThrow(() -> new DomainException("Transaction already exists but could not be loaded"));
			return new PostedTransaction(concurrent.getId(), concurrent.getReferenceKey(), concurrent.getCreatedAt());
		}

		LedgerTransactionRecordedEvent recordedEvent = buildRecordedEvent(domainTransaction, currency);
		LedgerEventEntity eventEntity = LedgerEventPersistenceMapper.toEntity(recordedEvent);
		List<TransactionEntryEntity> entryEntities = LedgerPersistenceMapper
				.toTransactionEntryEntities(domainTransaction);

		ledgerEventJpaRepository.save(Objects.requireNonNull(eventEntity));
		transactionEntryJpaRepository.saveAll(Objects.requireNonNull(entryEntities));

		Map<UUID, AccountTypeEntity> accountTypes = new HashMap<>();
		for (AccountEntity a : lockedAccounts) {
			accountTypes.put(a.getId(), a.getType());
		}
		applyBalanceUpdates(domainTransaction.entries(), accountTypes);
		return new PostedTransaction(domainTransaction.id(), command.referenceKey(), occurredAt);
	}

	private LedgerTransactionRecordedEvent buildRecordedEvent(LedgerTransaction transaction, Currency currency) {
		Objects.requireNonNull(transaction, "transaction is required");
		Objects.requireNonNull(currency, "currency is required");

		String payload = buildPayload(transaction, currency);
		String referenceId = transaction.referenceKey().value();
		String correlationId = referenceId;

		return new LedgerTransactionRecordedEvent(
				UUID.randomUUID(),
				"LEDGER",
				transaction.id(),
				1L,
				"LEDGER_TRANSACTION_RECORDED",
				payload,
				transaction.occurredAt(),
				correlationId,
				referenceId);
	}

	private String buildPayload(LedgerTransaction transaction, Currency currency) {
		LinkedHashMap<String, Object> root = new LinkedHashMap<>();
		root.put("transaction_id", transaction.id().toString());
		root.put("reference_key", transaction.referenceKey().value());
		root.put("occurred_at", transaction.occurredAt().toString());
		root.put("currency", currency.code());

		List<LinkedHashMap<String, Object>> legs = new ArrayList<>(transaction.entries().size());
		for (LedgerEntry e : transaction.entries()) {
			LinkedHashMap<String, Object> leg = new LinkedHashMap<>();
			leg.put("account_id", e.accountId().value());
			leg.put("entry_type", e.type().name());
			leg.put("amount", e.amount().amount().toPlainString());
			legs.add(leg);
		}
		root.put("entries", legs);

		try {
			return objectMapper.writeValueAsString(root);
		} catch (JsonProcessingException e) {
			throw new DomainException("Failed to serialize ledger event payload", e);
		}
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
			AccountPersistenceMapper.toDomain(account).assertPostingAllowed();
			if (!currency.equals(account.getCurrency())) {
				throw new DomainException("Transaction accounts must be single-currency");
			}
		}

		if (command.entries().size() < 2) {
			throw new DomainException("LedgerTransaction must have at least 2 entries");
		}
	}

	private static LedgerTransaction buildDomainTransaction(PostTransactionCommand command, Currency currency,
			UUID transactionId, Instant occurredAt) {
		List<LedgerEntry> entries = command.entries().stream()
				.map(e -> new LedgerEntry(
						new AccountId(e.accountId()),
						toDomainType(e.entryType()),
						new Money(currency, e.amount())))
				.toList();

		return new LedgerTransaction(
				transactionId,
				occurredAt,
				new ReferenceKey(command.referenceKey()),
				entries);
	}

	private void applyBalanceUpdates(List<LedgerEntry> entries, Map<UUID, AccountTypeEntity> accountTypes) {
		Map<UUID, BigDecimal> netChanges = aggregateNetChanges(entries);
		Set<UUID> accountIds = netChanges.keySet();
		List<AccountBalanceEntity> balances = accountBalanceJpaRepository.findByAccountIdIn(accountIds);
		if (balances.size() != accountIds.size()) {
			throw new DomainException("Account balance record missing for one or more accounts");
		}

		for (Map.Entry<UUID, BigDecimal> change : netChanges.entrySet()) {
			UUID accountId = change.getKey();
			BigDecimal delta = change.getValue();
			AccountTypeEntity type = accountTypes.get(accountId);
			if (type == null) {
				throw new DomainException("Account type missing for accountId: " + accountId);
			}

			int updated;
			if (delta.signum() < 0 && type == AccountTypeEntity.ASSET) {
				BigDecimal required = delta.negate();
				updated = entityManager.createQuery(
						"update AccountBalanceEntity b set b.balance = b.balance + :delta " +
								"where b.accountId = :accountId and b.balance >= :required")
						.setParameter("delta", delta)
						.setParameter("accountId", accountId)
						.setParameter("required", required)
						.executeUpdate();
				if (updated != 1) {
					throw new DomainException("Insufficient funds for accountId: " + accountId);
				}
				continue;
			}

			updated = entityManager.createQuery(
					"update AccountBalanceEntity b set b.balance = b.balance + :delta where b.accountId = :accountId")
					.setParameter("delta", delta)
					.setParameter("accountId", accountId)
					.executeUpdate();
			if (updated != 1) {
				throw new DomainException("Account balance update failed for accountId: " + accountId);
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
