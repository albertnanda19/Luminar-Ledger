package io.luminar.ledger.domain.account;

import io.luminar.ledger.domain.common.DomainException;

public final class AccountPolicy {
	private AccountPolicy() {
	}

	static void validate(AccountId id, String code, String name, AccountType type, Currency currency,
			AccountStatus status) {
		if (id == null) {
			throw new DomainException("Account.id is required");
		}
		if (code == null || code.trim().isEmpty()) {
			throw new DomainException("Account.code is required");
		}
		if (name == null || name.trim().isEmpty()) {
			throw new DomainException("Account.name is required");
		}
		if (type == null) {
			throw new DomainException("Account.type is required");
		}
		if (currency == null) {
			throw new DomainException("Account.currency is required");
		}
		if (status == null) {
			throw new DomainException("Account.status is required");
		}
	}
}
