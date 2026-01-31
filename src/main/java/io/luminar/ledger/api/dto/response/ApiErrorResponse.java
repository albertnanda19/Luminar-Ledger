package io.luminar.ledger.api.dto.response;

public record ApiErrorResponse(
		String code,
		String message
) {
}
