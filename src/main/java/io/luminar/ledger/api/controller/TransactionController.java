package io.luminar.ledger.api.controller;

import io.luminar.ledger.api.dto.request.PostTransactionRequest;
import io.luminar.ledger.api.dto.response.PostTransactionResponse;
import io.luminar.ledger.application.transaction.TransactionApplicationService;
import io.luminar.ledger.application.transaction.command.PostTransactionCommand;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionEntity;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionJpaRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
	private final TransactionApplicationService transactionApplicationService;
	private final TransactionJpaRepository transactionJpaRepository;

	public TransactionController(TransactionApplicationService transactionApplicationService,
			TransactionJpaRepository transactionJpaRepository) {
		this.transactionApplicationService = Objects.requireNonNull(transactionApplicationService);
		this.transactionJpaRepository = Objects.requireNonNull(transactionJpaRepository);
	}

	@PostMapping
	public PostTransactionResponse post(@Valid @RequestBody PostTransactionRequest request) {
		UUID transactionId = Objects.requireNonNull(
				transactionApplicationService.post(toCommand(request)),
				"TransactionApplicationService.post returned null");
		TransactionEntity persisted = transactionJpaRepository.findById(transactionId)
				.orElseThrow(() -> new IllegalStateException("Transaction not found after posting: " + transactionId));
		return new PostTransactionResponse(
				persisted.getId(),
				persisted.getReferenceKey(),
				persisted.getCreatedAt());
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
