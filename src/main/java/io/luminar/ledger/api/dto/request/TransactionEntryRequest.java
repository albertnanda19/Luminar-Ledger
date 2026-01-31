package io.luminar.ledger.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionEntryRequest(
		@NotNull UUID accountId,
		@NotNull Type type,
		@NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount
) {
	public enum Type {
		DEBIT,
		CREDIT
	}
}
