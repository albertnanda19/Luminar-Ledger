package io.luminar.ledger.service;

import java.time.Instant;
import java.util.UUID;

public record PostedTransaction(
		UUID transactionId,
		String referenceKey,
		Instant postedAt
) {
}
