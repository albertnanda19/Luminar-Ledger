package io.luminar.ledger.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PostTransactionResponse(
		UUID transactionId,
		String referenceKey,
		Instant postedAt
) {
}
