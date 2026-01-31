package io.luminar.ledger.domain.account;

import io.luminar.ledger.domain.common.DomainException;

public final class AccountPolicy {
	private AccountPolicy() {
	}

	static void validate(AccountId id, Currency currency, AccountStatus status) {
		if (id == null) {
			throw new DomainException("Account.id is required");
		}
		if (currency == null) {
			throw new DomainException("Account.currency is required");
		}
		if (status == null) {
			throw new DomainException("Account.status is required");
		}
	}
}
