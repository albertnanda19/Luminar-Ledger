package io.luminar.ledger.domain.account;

import io.luminar.ledger.domain.common.DomainException;

import java.util.UUID;

public record AccountId(UUID value) {
	public AccountId {
		if (value == null) {
			throw new DomainException("AccountId.value is required");
		}
	}
}
