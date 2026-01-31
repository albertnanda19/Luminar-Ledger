package io.luminar.ledger.domain.ledger;

import io.luminar.ledger.domain.common.DomainException;

import java.math.BigDecimal;
import java.util.List;

public final class LedgerPolicy {
	private LedgerPolicy() {
	}

	static void validateTransaction(List<LedgerEntry> entries) {
		if (entries == null) {
			throw new DomainException("LedgerTransaction.entries is required");
		}
		if (entries.isEmpty()) {
			throw new DomainException("LedgerTransaction.entries must not be empty");
		}
		if (entries.size() < 2) {
			throw new DomainException("LedgerTransaction must have at least 2 entries");
		}

		LedgerEntry first = entries.getFirst();
		if (first == null) {
			throw new DomainException("LedgerTransaction.entries must not contain null");
		}

		BigDecimal debitTotal = BigDecimal.ZERO;
		BigDecimal creditTotal = BigDecimal.ZERO;
		var currency = first.amount().currency();

		for (LedgerEntry entry : entries) {
			if (entry == null) {
				throw new DomainException("LedgerTransaction.entries must not contain null");
			}
			if (!currency.equals(entry.amount().currency())) {
				throw new DomainException("LedgerTransaction must be single-currency");
			}

			switch (entry.type()) {
				case DEBIT -> debitTotal = debitTotal.add(entry.amount().amount());
				case CREDIT -> creditTotal = creditTotal.add(entry.amount().amount());
			}
		}
		if (debitTotal.compareTo(creditTotal) != 0) {
			throw new DomainException("LedgerTransaction is not balanced");
		}
	}
}
