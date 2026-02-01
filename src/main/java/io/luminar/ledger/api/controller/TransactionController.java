package io.luminar.ledger.api.controller;

import io.luminar.ledger.api.dto.request.PostTransactionRequest;
import io.luminar.ledger.api.dto.response.PostTransactionResponse;
import io.luminar.ledger.application.transaction.TransactionApplicationService;
import io.luminar.ledger.application.transaction.command.PostTransactionCommand;
import io.luminar.ledger.service.PostedTransaction;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
	private final TransactionApplicationService transactionApplicationService;

	public TransactionController(TransactionApplicationService transactionApplicationService) {
		this.transactionApplicationService = Objects.requireNonNull(transactionApplicationService);
	}

	@PostMapping
	public PostTransactionResponse post(@Valid @RequestBody PostTransactionRequest request) {
		PostedTransaction posted = Objects.requireNonNull(
				transactionApplicationService.post(toCommand(request)),
				"TransactionApplicationService.post returned null");
		return new PostTransactionResponse(
				posted.transactionId(),
				posted.referenceKey(),
				posted.postedAt());
	}

	private static PostTransactionCommand toCommand(PostTransactionRequest request) {
		List<PostTransactionCommand.Entry> entries = request.entries().stream()
				.map(e -> new PostTransactionCommand.Entry(
						e.accountId(),
						PostTransactionCommand.EntryType.valueOf(e.type().name()),
						e.amount()))
				.toList();

		return new PostTransactionCommand(request.referenceKey(), entries);
	}
}
