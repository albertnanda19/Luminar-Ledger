package io.luminar.ledger.domain.account;

import io.luminar.ledger.domain.common.DomainException;

import java.time.Instant;
import java.util.Objects;

public final class Account {
	private final AccountId id;
	private final String code;
	private final String name;
	private final AccountType type;
	private final Currency currency;
	private final AccountStatus status;
	private final Instant frozenAt;
	private final Instant closedAt;
	private final Instant statusChangedAt;
	private final String statusReason;

	private Account(AccountId id, String code, String name, AccountType type, Currency currency, AccountStatus status,
			Instant frozenAt, Instant closedAt, Instant statusChangedAt, String statusReason) {
		AccountPolicy.validate(id, code, name, type, currency, status, frozenAt, closedAt, statusChangedAt,
				statusReason);
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

	public static Account open(AccountId id, String code, String name, AccountType type, Currency currency) {
		Instant now = Instant.now();
		return new Account(id, code, name, type, currency, AccountStatus.ACTIVE, null, null, now, "ACCOUNT_OPENED");
	}

	public static Account rehydrate(AccountId id, String code, String name, AccountType type, Currency currency,
			AccountStatus status, Instant frozenAt, Instant closedAt, Instant statusChangedAt, String statusReason) {
		return new Account(id, code, name, type, currency, status, frozenAt, closedAt, statusChangedAt, statusReason);
	}

	public AccountId id() {
		return id;
	}

	public String code() {
		return code;
	}

	public String name() {
		return name;
	}

	public AccountType type() {
		return type;
	}

	public Currency currency() {
		return currency;
	}

	public AccountStatus status() {
		return status;
	}

	public Instant frozenAt() {
		return frozenAt;
	}

	public Instant closedAt() {
		return closedAt;
	}

	public Instant statusChangedAt() {
		return statusChangedAt;
	}

	public String statusReason() {
		return statusReason;
	}

	public void assertPostingAllowed() {
		switch (status) {
			case ACTIVE -> {
			}
			case FROZEN -> throw new AccountFrozenException("Account is FROZEN: " + id.value());
			case CLOSED -> throw new AccountClosedException("Account is CLOSED: " + id.value());
		}
	}

	public Account freeze(String reason) {
		Objects.requireNonNull(reason, "reason is required");
		if (reason.trim().isEmpty()) {
			throw new DomainException("Account.freeze reason is required");
		}
		if (status == AccountStatus.CLOSED) {
			throw new DomainException("Account is CLOSED");
		}
		if (status == AccountStatus.FROZEN) {
			throw new DomainException("Account is already FROZEN");
		}

		Instant now = Instant.now();
		return new Account(id, code, name, type, currency, AccountStatus.FROZEN, now, null, now, reason.trim());
	}

	public Account unfreeze(String reason) {
		Objects.requireNonNull(reason, "reason is required");
		if (reason.trim().isEmpty()) {
			throw new DomainException("Account.unfreeze reason is required");
		}
		if (status == AccountStatus.CLOSED) {
			throw new DomainException("Account is CLOSED");
		}
		if (status != AccountStatus.FROZEN) {
			throw new DomainException("Account is not FROZEN");
		}

		Instant now = Instant.now();
		return new Account(id, code, name, type, currency, AccountStatus.ACTIVE, null, null, now, reason.trim());
	}

	public Account close(String reason) {
		Objects.requireNonNull(reason, "reason is required");
		if (reason.trim().isEmpty()) {
			throw new DomainException("Account.close reason is required");
		}
		if (status == AccountStatus.CLOSED) {
			throw new DomainException("Account is already CLOSED");
		}

		Instant now = Instant.now();
		return new Account(id, code, name, type, currency, AccountStatus.CLOSED, frozenAt, now, now, reason.trim());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Account that)) {
			return false;
		}
		return Objects.equals(id, that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
