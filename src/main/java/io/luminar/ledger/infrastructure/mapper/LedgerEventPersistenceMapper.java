package io.luminar.ledger.infrastructure.mapper;

import io.luminar.ledger.domain.ledger.event.LedgerTransactionRecordedEvent;
import io.luminar.ledger.infrastructure.persistence.ledger.LedgerEventEntity;

public final class LedgerEventPersistenceMapper {
	private LedgerEventPersistenceMapper() {
	}

	public static LedgerEventEntity toEntity(LedgerTransactionRecordedEvent event) {
		return new LedgerEventEntity(
			event.eventId(),
			event.aggregateType(),
			event.aggregateId(),
			event.sequenceNumber(),
			event.eventType(),
			event.referenceId(),
			event.correlationId(),
			event.payload(),
			event.occurredAt()
		);
	}
}
