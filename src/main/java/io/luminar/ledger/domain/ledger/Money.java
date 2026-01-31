package io.luminar.ledger.domain.ledger;

import io.luminar.ledger.domain.account.Currency;
import io.luminar.ledger.domain.common.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class Money {
	private final Currency currency;
	private final BigDecimal amount;

	public Money(Currency currency, BigDecimal amount) {
		if (currency == null) {
			throw new DomainException("Money.currency is required");
		}
		if (amount == null) {
			throw new DomainException("Money.amount is required");
		}

		BigDecimal normalized = amount.stripTrailingZeros();
		if (normalized.scale() < 0) {
			normalized = normalized.setScale(0, RoundingMode.UNNECESSARY);
		}
		if (normalized.signum() < 0) {
			throw new DomainException("Money.amount must be non-negative");
		}

		this.currency = currency;
		this.amount = normalized;
	}

	public static Money zero(Currency currency) {
		return new Money(currency, BigDecimal.ZERO);
	}

	public Currency currency() {
		return currency;
	}

	public BigDecimal amount() {
		return amount;
	}

	public Money plus(Money other) {
		requireSameCurrency(other);
		return new Money(currency, amount.add(other.amount));
	}

	private void requireSameCurrency(Money other) {
		if (other == null) {
			throw new DomainException("Money operand is required");
		}
		if (!currency.equals(other.currency)) {
			throw new DomainException("Currency mismatch");
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Money money)) {
			return false;
		}
		return Objects.equals(currency, money.currency) && amount.compareTo(money.amount) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(currency, amount.stripTrailingZeros());
	}
}
