package io.luminar.ledger.api.controller;

import io.luminar.ledger.api.dto.response.AccountTransactionHistoryResponse;
import io.luminar.ledger.api.dto.response.TransactionHistoryItem;
import io.luminar.ledger.api.query.AccountTransactionHistoryQuery;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountTransactionHistoryController {
	private static final int DEFAULT_LIMIT = 50;
	private static final int MAX_LIMIT = 200;

	private final AccountTransactionHistoryQuery accountTransactionHistoryQuery;

	public AccountTransactionHistoryController(AccountTransactionHistoryQuery accountTransactionHistoryQuery) {
		this.accountTransactionHistoryQuery = Objects.requireNonNull(accountTransactionHistoryQuery,
				"AccountTransactionHistoryController.accountTransactionHistoryQuery is required");
	}

	@GetMapping("/{accountId}/transactions")
	public AccountTransactionHistoryResponse get(
			@PathVariable UUID accountId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(required = false) Integer limit) {
		Objects.requireNonNull(accountId, "accountId is required");

		int effectiveLimit = normalizeLimit(limit);
		List<TransactionHistoryItem> transactions = accountTransactionHistoryQuery
				.findByAccountId(accountId, from, to, effectiveLimit);

		return new AccountTransactionHistoryResponse(accountId, transactions);
	}

	private static int normalizeLimit(Integer limit) {
		if (limit == null) {
			return DEFAULT_LIMIT;
		}

		if (limit <= 0) {
			return DEFAULT_LIMIT;
		}

		return Math.min(limit, MAX_LIMIT);
	}
}
