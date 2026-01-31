package io.luminar.ledger.api.dto.response;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AccountTransactionHistoryResponse {
	private final UUID accountId;
	private final List<TransactionHistoryItem> transactions;

	public AccountTransactionHistoryResponse(UUID accountId, List<TransactionHistoryItem> transactions) {
		this.accountId = Objects.requireNonNull(accountId, "AccountTransactionHistoryResponse.accountId is required");
		this.transactions = List.copyOf(Objects.requireNonNull(transactions,
				"AccountTransactionHistoryResponse.transactions is required"));
	}

	public UUID getAccountId() {
		return accountId;
	}

	public List<TransactionHistoryItem> getTransactions() {
		return transactions;
	}
}
