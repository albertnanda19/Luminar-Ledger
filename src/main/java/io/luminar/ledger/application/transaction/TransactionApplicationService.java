package io.luminar.ledger.application.transaction;

import io.luminar.ledger.application.transaction.command.PostTransactionCommand;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionEntity;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionJpaRepository;
import io.luminar.ledger.service.LedgerPostingService;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionApplicationService {
	private final TransactionJpaRepository transactionJpaRepository;
	private final LedgerPostingService ledgerPostingService;

	public TransactionApplicationService(TransactionJpaRepository transactionJpaRepository, LedgerPostingService ledgerPostingService) {
		this.transactionJpaRepository = Objects.requireNonNull(transactionJpaRepository);
		this.ledgerPostingService = Objects.requireNonNull(ledgerPostingService);
	}

	public UUID post(PostTransactionCommand command) {
		Objects.requireNonNull(command, "PostTransactionCommand is required");

		Optional<TransactionEntity> existing = transactionJpaRepository.findByReferenceKey(command.referenceKey());
		if (existing.isPresent()) {
			return existing.get().getId();
		}

		return ledgerPostingService.post(command);
	}
}
