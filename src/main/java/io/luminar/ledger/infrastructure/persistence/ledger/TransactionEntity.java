package io.luminar.ledger.infrastructure.persistence.ledger;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Access(AccessType.FIELD)
public class TransactionEntity {
	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "reference_key", nullable = false, length = 128, updatable = false)
	private String referenceKey;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "status", nullable = false, columnDefinition = "transaction_status")
	private TransactionStatusEntity status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected TransactionEntity() {
	}

	public TransactionEntity(UUID id, String referenceKey, TransactionStatusEntity status, Instant createdAt) {
		this.id = id;
		this.referenceKey = referenceKey;
		this.status = status;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public String getReferenceKey() {
		return referenceKey;
	}

	public TransactionStatusEntity getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
