package io.luminar.ledger.application.account.command;

import java.util.UUID;

public final class CloseAccountCommand {
	private final UUID accountId;
	private final String reason;

	public CloseAccountCommand(UUID accountId, String reason) {
		if (accountId == null) {
			throw new IllegalArgumentException("CloseAccountCommand.accountId is required");
		}
		if (reason == null || reason.trim().isEmpty()) {
			throw new IllegalArgumentException("CloseAccountCommand.reason is required");
		}
		this.accountId = accountId;
		this.reason = reason.trim();
	}

	public UUID accountId() {
		return accountId;
	}

	public String reason() {
		return reason;
	}
}
