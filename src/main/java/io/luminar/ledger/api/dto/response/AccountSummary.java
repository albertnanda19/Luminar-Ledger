package io.luminar.ledger.api.dto.response;

import io.luminar.ledger.domain.account.AccountStatus;
import io.luminar.ledger.domain.account.AccountType;

import java.util.Objects;
import java.util.UUID;

public final class AccountSummary {
	private final UUID accountId;
	private final String code;
	private final String name;
	private final AccountType type;
	private final String currency;
	private final AccountStatus status;

	public AccountSummary(UUID accountId, String code, String name, AccountType type, String currency,
			AccountStatus status) {
		this.accountId = Objects.requireNonNull(accountId, "AccountSummary.accountId is required");
		this.code = Objects.requireNonNull(code, "AccountSummary.code is required");
		this.name = Objects.requireNonNull(name, "AccountSummary.name is required");
		this.type = Objects.requireNonNull(type, "AccountSummary.type is required");
		this.currency = Objects.requireNonNull(currency, "AccountSummary.currency is required");
		this.status = Objects.requireNonNull(status, "AccountSummary.status is required");
	}

	public UUID getAccountId() {
		return accountId;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public AccountType getType() {
		return type;
	}

	public String getCurrency() {
		return currency;
	}

	public AccountStatus getStatus() {
		return status;
	}
}
