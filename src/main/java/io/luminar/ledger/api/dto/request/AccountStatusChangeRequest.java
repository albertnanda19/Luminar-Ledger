package io.luminar.ledger.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AccountStatusChangeRequest(
		@NotBlank String reason
) {
}
