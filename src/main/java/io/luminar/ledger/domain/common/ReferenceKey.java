package io.luminar.ledger.domain.common;

public record ReferenceKey(String value) {
	public ReferenceKey {
		if (value == null) {
			throw new DomainException("ReferenceKey.value is required");
		}

		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			throw new DomainException("ReferenceKey.value must not be blank");
		}

		value = trimmed;
	}
}
