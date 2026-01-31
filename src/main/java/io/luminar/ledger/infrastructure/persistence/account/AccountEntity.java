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

	@Column(name = "code", nullable = false, length = 64, updatable = false)
	private String code;

	@Column(name = "name", nullable = false, length = 128)
	private String name;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "type", nullable = false, columnDefinition = "account_type", updatable = false)
	private AccountTypeEntity type;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "status", nullable = false, columnDefinition = "account_status")
	private AccountStatusEntity status;

	@Column(name = "frozen_at")
	private Instant frozenAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	@Column(name = "status_changed_at", nullable = false)
	private Instant statusChangedAt;

	@Column(name = "status_reason", nullable = false, length = 256)
	private String statusReason;

	@Column(name = "created_at", nullable = false, updatable = false, insertable = false)
	private Instant createdAt;

	protected AccountEntity() {
	}

	public AccountEntity(UUID id, String code, String name, AccountTypeEntity type, String currency,
			AccountStatusEntity status, Instant frozenAt, Instant closedAt, Instant statusChangedAt,
			String statusReason) {
		this.id = id;
		this.code = code;
		this.name = name;
		this.type = type;
		this.currency = currency;
		this.status = status;
		this.frozenAt = frozenAt;
		this.closedAt = closedAt;
		this.statusChangedAt = statusChangedAt;
		this.statusReason = statusReason;
	}

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public AccountTypeEntity getType() {
		return type;
	}

	public String getCurrency() {
		return currency;
	}

	public AccountStatusEntity getStatus() {
		return status;
	}

	public void setStatus(AccountStatusEntity status) {
		this.status = status;
	}

	public Instant getFrozenAt() {
		return frozenAt;
	}

	public void setFrozenAt(Instant frozenAt) {
		this.frozenAt = frozenAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	public void setClosedAt(Instant closedAt) {
		this.closedAt = closedAt;
	}

	public Instant getStatusChangedAt() {
		return statusChangedAt;
	}

	public void setStatusChangedAt(Instant statusChangedAt) {
		this.statusChangedAt = statusChangedAt;
	}

	public String getStatusReason() {
		return statusReason;
	}

	public void setStatusReason(String statusReason) {
		this.statusReason = statusReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
