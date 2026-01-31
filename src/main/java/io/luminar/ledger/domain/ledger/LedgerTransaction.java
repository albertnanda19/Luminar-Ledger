package io.luminar.ledger.domain.ledger;

import io.luminar.ledger.domain.common.DomainException;
import io.luminar.ledger.domain.common.ReferenceKey;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LedgerTransaction {
	private final UUID id;
	private final Instant occurredAt;
	private final ReferenceKey referenceKey;
	private final List<LedgerEntry> entries;

	public LedgerTransaction(UUID id, Instant occurredAt, ReferenceKey referenceKey, List<LedgerEntry> entries) {
		if (id == null) {
			throw new DomainException("LedgerTransaction.id is required");
		}
		if (occurredAt == null) {
			throw new DomainException("LedgerTransaction.occurredAt is required");
		}
		if (referenceKey == null) {
			throw new DomainException("LedgerTransaction.referenceKey is required");
		}
		if (entries == null) {
			throw new DomainException("LedgerTransaction.entries is required");
		}

		List<LedgerEntry> snapshot = List.copyOf(entries);
		LedgerPolicy.validateTransaction(snapshot);

		this.id = id;
		this.occurredAt = occurredAt;
		this.referenceKey = referenceKey;
		this.entries = snapshot;
	}

	public UUID id() {
		return id;
	}

	public Instant occurredAt() {
		return occurredAt;
	}

	public ReferenceKey referenceKey() {
		return referenceKey;
	}

	public List<LedgerEntry> entries() {
		return entries;
	}
}
