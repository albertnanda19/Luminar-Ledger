package io.luminar.ledger.api.query;

import io.luminar.ledger.api.dto.response.TransactionHistoryItem;
import jakarta.persistence.EntityManager;
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
		this.entityManager = Objects.requireNonNull(entityManager,
				"AccountTransactionHistoryQuery.entityManager is required");
	}

	@Transactional(readOnly = true)
	public List<TransactionHistoryItem> findByAccountId(UUID accountId, Instant from, Instant to, int page, int size) {
		Objects.requireNonNull(accountId, "accountId is required");
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, size);
		int offset = Math.multiplyExact(safePage, safeSize);

		StringBuilder sql = new StringBuilder();
		sql.append("select p.transaction_id, p.reference_key, p.direction::text, p.amount, p.occurred_at ");
		sql.append("from transaction_history_projection p ");
		sql.append("where p.account_id = :accountId ");

		if (from != null) {
			sql.append("and p.occurred_at >= :from ");
		}
		if (to != null) {
			sql.append("and p.occurred_at <= :to ");
		}

		sql.append("order by p.occurred_at asc, p.sequence_number asc, p.transaction_id asc, p.direction asc");

		var query = entityManager.createNativeQuery(sql.toString())
				.setParameter("accountId", accountId)
				.setFirstResult(offset)
				.setMaxResults(safeSize);
		if (from != null) {
			query.setParameter("from", from);
		}
		if (to != null) {
			query.setParameter("to", to);
		}

		@SuppressWarnings("unchecked")
		List<Object[]> rows = (List<Object[]>) query.getResultList();
		List<TransactionHistoryItem> result = new ArrayList<>(rows.size());

		for (Object[] row : rows) {
			UUID transactionId = (UUID) row[0];
			String referenceKey = (String) row[1];
			String entryType = (String) row[2];
			BigDecimal amount = (BigDecimal) row[3];
			Instant postedAt;
			Object postedAtRaw = row[4];
			if (postedAtRaw instanceof java.sql.Timestamp ts) {
				postedAt = ts.toInstant();
			} else if (postedAtRaw instanceof java.time.OffsetDateTime odt) {
				postedAt = odt.toInstant();
			} else if (postedAtRaw instanceof Instant i) {
				postedAt = i;
			} else {
				throw new IllegalStateException("Unexpected occurred_at type from DB: " +
						(postedAtRaw == null ? "null" : postedAtRaw.getClass().getName()));
			}

			result.add(new TransactionHistoryItem(
					transactionId,
					referenceKey,
					toDtoEntryType(entryType),
					amount,
					postedAt));
		}

		return result;
	}

	private static TransactionHistoryItem.EntryType toDtoEntryType(String type) {
		String normalized = Objects.requireNonNull(type, "entryType is required").trim();
		return switch (normalized) {
			case "DEBIT" -> TransactionHistoryItem.EntryType.DEBIT;
			case "CREDIT" -> TransactionHistoryItem.EntryType.CREDIT;
			default -> throw new IllegalArgumentException("Unknown entryType: " + normalized);
		};
	}
}
