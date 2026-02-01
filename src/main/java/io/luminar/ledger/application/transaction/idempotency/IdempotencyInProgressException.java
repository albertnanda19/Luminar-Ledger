package io.luminar.ledger.application.transaction.idempotency;

public class IdempotencyInProgressException extends RuntimeException {
	private final String referenceKey;

	public IdempotencyInProgressException(String referenceKey) {
		super("Request is already in progress for referenceKey: " + referenceKey);
		this.referenceKey = referenceKey;
	}

	public String referenceKey() {
		return referenceKey;
	}
}
