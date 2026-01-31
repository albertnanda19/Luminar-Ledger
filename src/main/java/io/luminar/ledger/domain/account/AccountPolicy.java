package io.luminar.ledger.domain.account;

import io.luminar.ledger.domain.common.DomainException;

import java.time.Instant;

public final class AccountPolicy {
	private AccountPolicy() {
	}

	static void validate(AccountId id, String code, String name, AccountType type, Currency currency,
			AccountStatus status, Instant frozenAt, Instant closedAt, Instant statusChangedAt, String statusReason) {
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
		if (statusChangedAt == null) {
			throw new DomainException("Account.statusChangedAt is required");
		}
		if (statusReason == null || statusReason.trim().isEmpty()) {
			throw new DomainException("Account.statusReason is required");
		}

		switch (status) {
			case ACTIVE -> {
				if (closedAt != null) {
					throw new DomainException("Account.closedAt must be null when status is ACTIVE");
				}
				if (frozenAt != null) {
					throw new DomainException("Account.frozenAt must be null when status is ACTIVE");
				}
			}
			case FROZEN -> {
				if (frozenAt == null) {
					throw new DomainException("Account.frozenAt is required when status is FROZEN");
				}
				if (closedAt != null) {
					throw new DomainException("Account.closedAt must be null when status is FROZEN");
				}
			}
			case CLOSED -> {
				if (closedAt == null) {
					throw new DomainException("Account.closedAt is required when status is CLOSED");
				}
			}
		}
	}
}
