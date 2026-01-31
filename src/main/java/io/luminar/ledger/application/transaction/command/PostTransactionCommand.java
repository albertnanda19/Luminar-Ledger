package io.luminar.ledger.application.transaction.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PostTransactionCommand {
	private final String referenceKey;
	private final List<Entry> entries;

	public PostTransactionCommand(String referenceKey, List<Entry> entries) {
		if (referenceKey == null || referenceKey.trim().isEmpty()) {
			throw new IllegalArgumentException("PostTransactionCommand.referenceKey is required");
		}
		if (entries == null || entries.isEmpty()) {
			throw new IllegalArgumentException("PostTransactionCommand.entries is required");
		}

		this.referenceKey = referenceKey.trim();
		this.entries = List.copyOf(entries);
	}

	public String referenceKey() {
		return referenceKey;
	}

	public List<Entry> entries() {
		return entries;
	}

	public enum EntryType {
		DEBIT,
		CREDIT
	}

	public record Entry(UUID accountId, EntryType entryType, BigDecimal amount) {
		public Entry {
			Objects.requireNonNull(accountId, "PostTransactionCommand.Entry.accountId is required");
			Objects.requireNonNull(entryType, "PostTransactionCommand.Entry.entryType is required");
			Objects.requireNonNull(amount, "PostTransactionCommand.Entry.amount is required");
		}
	}
}
