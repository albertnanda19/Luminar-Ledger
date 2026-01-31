package io.luminar.ledger.infrastructure.persistence.account;

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
@Table(name = "accounts")
@Access(AccessType.FIELD)
public class AccountEntity {
	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "status", nullable = false, columnDefinition = "account_status")
	private AccountStatusEntity status;

	@Column(name = "created_at", nullable = false, updatable = false, insertable = false)
	private Instant createdAt;

	protected AccountEntity() {
	}

	public AccountEntity(UUID id, String currency, AccountStatusEntity status) {
		this.id = id;
		this.currency = currency;
		this.status = status;
	}

	public UUID getId() {
		return id;
	}

	public String getCurrency() {
		return currency;
	}

	public AccountStatusEntity getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
