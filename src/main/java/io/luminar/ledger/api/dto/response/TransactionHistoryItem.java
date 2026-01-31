package io.luminar.ledger.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class TransactionHistoryItem {
	public enum EntryType {
		DEBIT,
		CREDIT
	}

	private final UUID transactionId;
	private final String referenceKey;
	private final EntryType entryType;
	private final BigDecimal amount;
	private final Instant postedAt;

	public TransactionHistoryItem(UUID transactionId, String referenceKey, EntryType entryType, BigDecimal amount,
			Instant postedAt) {
		this.transactionId = Objects.requireNonNull(transactionId, "TransactionHistoryItem.transactionId is required");
		this.referenceKey = Objects.requireNonNull(referenceKey, "TransactionHistoryItem.referenceKey is required");
		this.entryType = Objects.requireNonNull(entryType, "TransactionHistoryItem.entryType is required");
		this.amount = Objects.requireNonNull(amount, "TransactionHistoryItem.amount is required");
		this.postedAt = Objects.requireNonNull(postedAt, "TransactionHistoryItem.postedAt is required");
	}

	public UUID getTransactionId() {
		return transactionId;
	}

	public String getReferenceKey() {
		return referenceKey;
	}

	public EntryType getEntryType() {
		return entryType;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public Instant getPostedAt() {
		return postedAt;
	}
}
