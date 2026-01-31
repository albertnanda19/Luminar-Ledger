package io.luminar.ledger.api.dto.response;

import java.util.List;
import java.util.Objects;

public final class AccountListResponse {
	private final List<AccountSummary> accounts;

	public AccountListResponse(List<AccountSummary> accounts) {
		this.accounts = List.copyOf(Objects.requireNonNull(accounts, "AccountListResponse.accounts is required"));
	}

	public List<AccountSummary> getAccounts() {
		return accounts;
	}
}
