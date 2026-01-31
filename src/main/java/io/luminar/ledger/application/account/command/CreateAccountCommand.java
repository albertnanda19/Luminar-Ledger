package io.luminar.ledger.application.account.command;

import io.luminar.ledger.domain.account.AccountType;

import java.util.Objects;

public final class CreateAccountCommand {
	private final String code;
	private final String name;
	private final AccountType type;
	private final String currency;

	public CreateAccountCommand(String code, String name, AccountType type, String currency) {
		if (code == null || code.trim().isEmpty()) {
			throw new IllegalArgumentException("CreateAccountCommand.code is required");
		}
		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("CreateAccountCommand.name is required");
		}
		this.type = Objects.requireNonNull(type, "CreateAccountCommand.type is required");
		if (currency == null || currency.trim().isEmpty()) {
			throw new IllegalArgumentException("CreateAccountCommand.currency is required");
		}

		this.code = code.trim();
		this.name = name.trim();
		this.currency = currency.trim();
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

	public String currency() {
		return currency;
	}
}
