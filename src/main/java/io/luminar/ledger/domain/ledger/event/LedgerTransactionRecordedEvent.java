package io.luminar.ledger.domain.ledger.event;

import io.luminar.ledger.domain.common.DomainException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LedgerTransactionRecordedEvent(
		UUID eventId,
		String aggregateType,
		UUID aggregateId,
		long sequenceNumber,
		String eventType,
		String payload,
		Instant occurredAt,
		String correlationId,
		String referenceId
) {
	public LedgerTransactionRecordedEvent {
		if (eventId == null) {
			throw new DomainException("LedgerTransactionRecordedEvent.eventId is required");
		}
		if (aggregateType == null || aggregateType.trim().isEmpty()) {
			throw new DomainException("LedgerTransactionRecordedEvent.aggregateType is required");
		}
		if (aggregateId == null) {
			throw new DomainException("LedgerTransactionRecordedEvent.aggregateId is required");
		}
		if (sequenceNumber <= 0) {
			throw new DomainException("LedgerTransactionRecordedEvent.sequenceNumber must be positive");
		}
		if (eventType == null || eventType.trim().isEmpty()) {
			throw new DomainException("LedgerTransactionRecordedEvent.eventType is required");
		}
		Objects.requireNonNull(payload, "LedgerTransactionRecordedEvent.payload is required");
		if (occurredAt == null) {
			throw new DomainException("LedgerTransactionRecordedEvent.occurredAt is required");
		}
		if (correlationId == null || correlationId.trim().isEmpty()) {
			throw new DomainException("LedgerTransactionRecordedEvent.correlationId is required");
		}
		if (referenceId == null || referenceId.trim().isEmpty()) {
			throw new DomainException("LedgerTransactionRecordedEvent.referenceId is required");
		}

		aggregateType = aggregateType.trim();
		eventType = eventType.trim();
		correlationId = correlationId.trim();
		referenceId = referenceId.trim();
	}
}
