package io.luminar.ledger.infrastructure.persistence.ledger;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_events")
@Access(AccessType.FIELD)
public class LedgerEventEntity {
	@Id
	@Column(name = "event_id", nullable = false, updatable = false)
	private UUID eventId;

	@Column(name = "aggregate_type", nullable = false, length = 32, updatable = false)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false, updatable = false)
	private UUID aggregateId;

	@Column(name = "sequence_number", nullable = false, updatable = false)
	private long sequenceNumber;

	@Column(name = "event_type", nullable = false, length = 128, updatable = false)
	private String eventType;

	@Column(name = "reference_id", nullable = false, length = 128, updatable = false)
	private String referenceId;

	@Column(name = "correlation_id", nullable = false, length = 128, updatable = false)
	private String correlationId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload", nullable = false, columnDefinition = "jsonb", updatable = false)
	private String payload;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	protected LedgerEventEntity() {
	}

	public LedgerEventEntity(UUID eventId, String aggregateType, UUID aggregateId, long sequenceNumber,
			String eventType, String referenceId, String correlationId, String payload, Instant occurredAt) {
		this.eventId = eventId;
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.sequenceNumber = sequenceNumber;
		this.eventType = eventType;
		this.referenceId = referenceId;
		this.correlationId = correlationId;
		this.payload = payload;
		this.occurredAt = occurredAt;
	}

	public UUID getEventId() {
		return eventId;
	}

	public String getAggregateType() {
		return aggregateType;
	}

	public UUID getAggregateId() {
		return aggregateId;
	}

	public long getSequenceNumber() {
		return sequenceNumber;
	}

	public String getEventType() {
		return eventType;
	}

	public String getReferenceId() {
		return referenceId;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public String getPayload() {
		return payload;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}
}
