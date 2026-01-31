package io.luminar.ledger.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AccountBalanceResponse {
	private final UUID accountId;
	private final CURRENCY currency;
	private final BigDecimal balance;
	private final Instant asOf;

	public AccountBalanceResponse(UUID accountId, CURRENCY currency, BigDecimal balance, Instant asOf) {
		this.accountId = Objects.requireNonNull(accountId, "AccountBalanceResponse.accountId is required");
		this.currency = Objects.requireNonNull(currency, "AccountBalanceResponse.currency is required");
		this.balance = Objects.requireNonNull(balance, "AccountBalanceResponse.balance is required");
		this.asOf = Objects.requireNonNull(asOf, "AccountBalanceResponse.asOf is required");
	}

	public UUID getAccountId() {
		return accountId;
	}

	public CURRENCY getCurrency() {
		return currency;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public Instant getAsOf() {
		return asOf;
	}
}
