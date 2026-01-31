package io.luminar.ledger.domain.account;

import java.util.Objects;

public final class Account {
	private final AccountId id;
	private final Currency currency;
	private final AccountStatus status;

	private Account(AccountId id, Currency currency, AccountStatus status) {
		AccountPolicy.validate(id, currency, status);
		this.id = id;
		this.currency = currency;
		this.status = status;
	}

	public static Account open(AccountId id, Currency currency) {
		return new Account(id, currency, AccountStatus.ACTIVE);
	}

	public static Account rehydrate(AccountId id, Currency currency, AccountStatus status) {
		return new Account(id, currency, status);
	}

	public AccountId id() {
		return id;
	}

	public Currency currency() {
		return currency;
	}

	public AccountStatus status() {
		return status;
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
