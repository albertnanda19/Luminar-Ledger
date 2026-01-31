package io.luminar.ledger.domain.account;

import java.util.Objects;

public final class Account {
	private final AccountId id;
	private final String code;
	private final String name;
	private final AccountType type;
	private final Currency currency;
	private final AccountStatus status;

	private Account(AccountId id, String code, String name, AccountType type, Currency currency, AccountStatus status) {
		AccountPolicy.validate(id, code, name, type, currency, status);
		this.id = id;
		this.code = code;
		this.name = name;
		this.type = type;
		this.currency = currency;
		this.status = status;
	}

	public static Account open(AccountId id, String code, String name, AccountType type, Currency currency) {
		return new Account(id, code, name, type, currency, AccountStatus.ACTIVE);
	}

	public static Account rehydrate(AccountId id, String code, String name, AccountType type, Currency currency,
			AccountStatus status) {
		return new Account(id, code, name, type, currency, status);
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
