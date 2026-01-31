package io.luminar.ledger.domain.account;

import io.luminar.ledger.domain.common.DomainException;

public final class AccountFrozenException extends DomainException {
	public AccountFrozenException(String message) {
		super("ACCOUNT_FROZEN", message, null);
	}
}
