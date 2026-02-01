package io.luminar.ledger.infrastructure.projection;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Repository
public class TransactionHistoryProjectionRepository {
	private final EntityManager entityManager;

	public TransactionHistoryProjectionRepository(EntityManager entityManager) {
		this.entityManager = Objects.requireNonNull(entityManager, "entityManager is required");
	}

	public boolean tryMarkEventProcessed(UUID eventId, String projectionType) {
		Objects.requireNonNull(eventId, "eventId is required");
		Objects.requireNonNull(projectionType, "projectionType is required");

		int inserted = entityManager.createNativeQuery(
				"insert into projection_event_dedup (event_id, projection_type) values (:eventId, :projectionType) " +
						"on conflict do nothing")
				.setParameter("eventId", eventId)
				.setParameter("projectionType", projectionType)
				.executeUpdate();

		return inserted == 1;
	}

	public boolean isEventProcessed(UUID eventId, String projectionType) {
		Objects.requireNonNull(eventId, "eventId is required");
		Objects.requireNonNull(projectionType, "projectionType is required");
		@SuppressWarnings("unchecked")
		boolean exists = !((java.util.List<Object>) entityManager.createNativeQuery(
				"select 1 from projection_event_dedup where event_id = :eventId and projection_type = :projectionType")
				.setParameter("eventId", eventId)
				.setParameter("projectionType", projectionType)
				.setMaxResults(1)
				.getResultList()).isEmpty();

		return exists;
	}

	public void insertProjectionRow(UUID eventId, UUID transactionId, String referenceKey, UUID accountId,
			String direction, BigDecimal amount, String currency, Instant occurredAt, long sequenceNumber,
			String correlationId) {
		Objects.requireNonNull(eventId, "eventId is required");
		Objects.requireNonNull(transactionId, "transactionId is required");
		Objects.requireNonNull(referenceKey, "referenceKey is required");
		Objects.requireNonNull(accountId, "accountId is required");
		Objects.requireNonNull(direction, "direction is required");
		Objects.requireNonNull(amount, "amount is required");
		Objects.requireNonNull(currency, "currency is required");
		Objects.requireNonNull(occurredAt, "occurredAt is required");
		Objects.requireNonNull(correlationId, "correlationId is required");

		entityManager.createNativeQuery(
				"insert into transaction_history_projection " +
						"(event_id, transaction_id, reference_key, account_id, direction, amount, currency, occurred_at, sequence_number, correlation_id) "
						+
						"values (:eventId, :transactionId, :referenceKey, :accountId, cast(:direction as entry_type), :amount, :currency, :occurredAt, :sequenceNumber, :correlationId) "
						+
						"on conflict do nothing")
				.setParameter("eventId", eventId)
				.setParameter("transactionId", transactionId)
				.setParameter("referenceKey", referenceKey)
				.setParameter("accountId", accountId)
				.setParameter("direction", direction)
				.setParameter("amount", amount)
				.setParameter("currency", currency)
				.setParameter("occurredAt", occurredAt)
				.setParameter("sequenceNumber", sequenceNumber)
				.setParameter("correlationId", correlationId)
				.executeUpdate();
	}
}
