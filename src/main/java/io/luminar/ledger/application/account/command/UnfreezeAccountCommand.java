package io.luminar.ledger.application.account.command;

import java.util.UUID;

public final class UnfreezeAccountCommand {
	private final UUID accountId;
	private final String reason;

	public UnfreezeAccountCommand(UUID accountId, String reason) {
		if (accountId == null) {
			throw new IllegalArgumentException("UnfreezeAccountCommand.accountId is required");
		}
		if (reason == null || reason.trim().isEmpty()) {
			throw new IllegalArgumentException("UnfreezeAccountCommand.reason is required");
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
