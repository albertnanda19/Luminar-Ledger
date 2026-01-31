package io.luminar.ledger.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PostTransactionRequest(
		@NotBlank String referenceKey,
		@NotNull @Size(min = 2) @Valid List<TransactionEntryRequest> entries
) {
}
