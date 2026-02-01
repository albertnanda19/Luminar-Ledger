package io.luminar.ledger.infrastructure.projection;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class LedgerEventPollingRepository {
	private final EntityManager entityManager;

	public LedgerEventPollingRepository(EntityManager entityManager) {
		this.entityManager = Objects.requireNonNull(entityManager, "entityManager is required");
	}

	public List<LedgerEventRow> fetchAfter(long lastGlobalSequence, int limit) {
		@SuppressWarnings("unchecked")
		List<Object[]> rows = (List<Object[]>) entityManager.createNativeQuery(
				"select event_id, event_type, reference_id, correlation_id, payload::text, occurred_at, global_sequence "
						+
						"from ledger_events " +
						"where global_sequence > :lastGlobalSequence " +
						"order by global_sequence asc " +
						"limit :limit")
				.setParameter("lastGlobalSequence", lastGlobalSequence)
				.setParameter("limit", limit)
				.getResultList();

		return rows.stream()
				.map(row -> new LedgerEventRow(
						(UUID) row[0],
						(String) row[1],
						(String) row[2],
						(String) row[3],
						(String) row[4],
						toInstant(row[5]),
						((Number) row[6]).longValue()))
				.toList();
	}

	private static Instant toInstant(Object raw) {
		if (raw instanceof java.sql.Timestamp ts) {
			return ts.toInstant();
		}
		if (raw instanceof java.time.OffsetDateTime odt) {
			return odt.toInstant();
		}
		if (raw instanceof Instant i) {
			return i;
		}
		throw new IllegalStateException("Unexpected occurred_at type from DB: " +
				(raw == null ? "null" : raw.getClass().getName()));
	}

	public record LedgerEventRow(
			UUID eventId,
			String eventType,
			String referenceId,
			String correlationId,
			String payloadJson,
			Instant occurredAt,
			long globalSequence) {
		public LedgerEventRow {
			Objects.requireNonNull(eventId, "eventId is required");
			Objects.requireNonNull(eventType, "eventType is required");
			Objects.requireNonNull(referenceId, "referenceId is required");
			Objects.requireNonNull(correlationId, "correlationId is required");
			Objects.requireNonNull(payloadJson, "payloadJson is required");
			Objects.requireNonNull(occurredAt, "occurredAt is required");
		}
	}
}
