package io.luminar.ledger.domain.ledger;

import io.luminar.ledger.domain.account.AccountId;
import io.luminar.ledger.domain.common.DomainException;

import java.math.BigDecimal;

public final class LedgerEntry {
	private final AccountId accountId;
	private final EntryType type;
	private final Money amount;

	public LedgerEntry(AccountId accountId, EntryType type, Money amount) {
		if (accountId == null) {
			throw new DomainException("LedgerEntry.accountId is required");
		}
		if (type == null) {
			throw new DomainException("LedgerEntry.type is required");
		}
		if (amount == null) {
			throw new DomainException("LedgerEntry.amount is required");
		}
		if (amount.amount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new DomainException("LedgerEntry.amount must be positive");
		}

		this.accountId = accountId;
		this.type = type;
		this.amount = amount;
	}

	public AccountId accountId() {
		return accountId;
	}

	public EntryType type() {
		return type;
	}

	public Money amount() {
		return amount;
	}
}
