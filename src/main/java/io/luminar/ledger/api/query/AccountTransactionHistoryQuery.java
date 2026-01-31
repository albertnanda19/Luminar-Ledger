package io.luminar.ledger.api.query;

import io.luminar.ledger.api.dto.response.TransactionHistoryItem;
import io.luminar.ledger.infrastructure.persistence.ledger.EntryTypeEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class AccountTransactionHistoryQuery {
	private final EntityManager entityManager;

	public AccountTransactionHistoryQuery(EntityManager entityManager) {
		this.entityManager = Objects.requireNonNull(entityManager, "AccountTransactionHistoryQuery.entityManager is required");
	}

	@Transactional(readOnly = true)
	public List<TransactionHistoryItem> findByAccountId(UUID accountId, Instant from, Instant to, int limit) {
		Objects.requireNonNull(accountId, "accountId is required");

		StringBuilder jpql = new StringBuilder();
		jpql.append("select t.id, t.referenceKey, e.entryType, e.amount, t.createdAt ");
		jpql.append("from TransactionEntryEntity e ");
		jpql.append("join TransactionEntity t on t.id = e.transactionId ");
		jpql.append("where e.accountId = :accountId ");

		if (from != null) {
			jpql.append("and t.createdAt >= :from ");
		}
		if (to != null) {
			jpql.append("and t.createdAt <= :to ");
		}

		jpql.append("order by t.createdAt asc, t.id asc, e.id asc");

		TypedQuery<Object[]> query = entityManager.createQuery(jpql.toString(), Object[].class);
		query.setParameter("accountId", accountId);
		if (from != null) {
			query.setParameter("from", from);
		}
		if (to != null) {
			query.setParameter("to", to);
		}
		query.setMaxResults(limit);

		List<Object[]> rows = query.getResultList();
		List<TransactionHistoryItem> result = new ArrayList<>(rows.size());

		for (Object[] row : rows) {
			UUID transactionId = (UUID) row[0];
			String referenceKey = (String) row[1];
			EntryTypeEntity entryType = (EntryTypeEntity) row[2];
			BigDecimal amount = (BigDecimal) row[3];
			Instant postedAt = (Instant) row[4];

			result.add(new TransactionHistoryItem(
					transactionId,
					referenceKey,
					toDtoEntryType(entryType),
					amount,
					postedAt));
		}

		return result;
	}

	private static TransactionHistoryItem.EntryType toDtoEntryType(EntryTypeEntity type) {
		return switch (Objects.requireNonNull(type, "entryType is required")) {
			case DEBIT -> TransactionHistoryItem.EntryType.DEBIT;
			case CREDIT -> TransactionHistoryItem.EntryType.CREDIT;
		};
	}
}
