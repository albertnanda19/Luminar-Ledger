package io.luminar.ledger.infrastructure.persistence.ledger;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction_entries")
@Access(AccessType.FIELD)
public class TransactionEntryEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "transaction_id", nullable = false, updatable = false)
	private UUID transactionId;

	@Column(name = "account_id", nullable = false, updatable = false)
	private UUID accountId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "entry_type", nullable = false, columnDefinition = "entry_type")
	private EntryTypeEntity entryType;

	@Column(name = "amount", nullable = false, precision = 20, scale = 6, updatable = false)
	private BigDecimal amount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected TransactionEntryEntity() {
	}

	public TransactionEntryEntity(UUID id, UUID transactionId, UUID accountId, EntryTypeEntity entryType,
			BigDecimal amount, Instant createdAt) {
		this.id = id;
		this.transactionId = transactionId;
		this.accountId = accountId;
		this.entryType = entryType;
		this.amount = amount;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTransactionId() {
		return transactionId;
	}

	public UUID getAccountId() {
		return accountId;
	}

	public EntryTypeEntity getEntryType() {
		return entryType;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
