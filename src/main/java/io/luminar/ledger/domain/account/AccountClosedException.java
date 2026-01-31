package io.luminar.ledger.domain.account;

import io.luminar.ledger.domain.common.DomainException;

public final class AccountClosedException extends DomainException {
	public AccountClosedException(String message) {
		super("ACCOUNT_CLOSED", message, null);
	}
}
