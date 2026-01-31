package io.luminar.ledger.domain.account;

import io.luminar.ledger.domain.common.DomainException;

public record Currency(String code) {
	public Currency {
		if (code == null) {
			throw new DomainException("Currency.code is required");
		}

		String normalized = code.trim().toUpperCase();
		if (!normalized.matches("[A-Z]{3}")) {
			throw new DomainException("Currency.code must be a 3-letter ISO code");
		}

		code = normalized;
	}
}
