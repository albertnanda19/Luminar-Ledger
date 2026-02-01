package io.luminar.ledger.application.transaction.idempotency;

import java.time.Instant;
import java.util.UUID;

public record IdempotencyRecord(
		String status,
		UUID transactionId,
		String referenceKey,
		Instant postedAt,
		Instant createdAt
) {
}
