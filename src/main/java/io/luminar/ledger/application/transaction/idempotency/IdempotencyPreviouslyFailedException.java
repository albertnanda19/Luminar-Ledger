package io.luminar.ledger.application.transaction.idempotency;

public class IdempotencyPreviouslyFailedException extends RuntimeException {
	private final String referenceKey;

	public IdempotencyPreviouslyFailedException(String referenceKey) {
		super("Previous request failed for referenceKey: " + referenceKey);
		this.referenceKey = referenceKey;
	}

	public String referenceKey() {
		return referenceKey;
	}
}
