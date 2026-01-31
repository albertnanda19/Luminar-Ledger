package io.luminar.ledger.infrastructure.persistence.account;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_balances")
@Access(AccessType.FIELD)
public class AccountBalanceEntity {
	@Id
	@Column(name = "account_id", nullable = false, updatable = false)
	private UUID accountId;

	@Column(name = "balance", nullable = false, precision = 20, scale = 6)
	private BigDecimal balance;

	@Column(name = "updated_at", nullable = false, insertable = false)
	private Instant updatedAt;

	protected AccountBalanceEntity() {
	}

	public AccountBalanceEntity(UUID accountId, BigDecimal balance) {
		this.accountId = accountId;
		this.balance = balance;
	}

	public UUID getAccountId() {
		return accountId;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
