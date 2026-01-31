package io.luminar.ledger.domain.common;

public class DomainException extends RuntimeException {
	private final String code;

	public DomainException(String message) {
		this(null, message, null);
	}

	public DomainException(String message, Throwable cause) {
		this(null, message, cause);
	}

	protected DomainException(String code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public String code() {
		return code;
	}
}
