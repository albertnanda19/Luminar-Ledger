package io.luminar.ledger.api.dto.response;

import java.util.UUID;

public record CreateAccountResponse(
		UUID accountId,
		String code
) {
}
