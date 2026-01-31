package io.luminar.ledger.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateAccountRequest(
		@NotBlank String code,
		@NotBlank String name,
		@NotNull AccountType type,
		@NotBlank @Pattern(regexp = "(?i)[A-Z]{3}") String currency) {
	public enum AccountType {
		ASSET,
		LIABILITY,
		EQUITY,
		REVENUE,
		EXPENSE
	}
}
