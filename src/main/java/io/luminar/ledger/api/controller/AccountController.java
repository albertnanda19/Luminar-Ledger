package io.luminar.ledger.api.controller;

import io.luminar.ledger.api.dto.request.CreateAccountRequest;
import io.luminar.ledger.api.dto.response.CreateAccountResponse;
import io.luminar.ledger.application.account.AccountApplicationService;
import io.luminar.ledger.application.account.command.CreateAccountCommand;
import io.luminar.ledger.domain.account.AccountType;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
	private final AccountApplicationService accountApplicationService;

	public AccountController(AccountApplicationService accountApplicationService) {
		this.accountApplicationService = Objects.requireNonNull(accountApplicationService);
	}

	@PostMapping
	public CreateAccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
		CreateAccountCommand command = new CreateAccountCommand(
				request.code(),
				request.name(),
				AccountType.valueOf(request.type().name()),
				request.currency());

		UUID accountId = accountApplicationService.create(command);
		return new CreateAccountResponse(accountId, command.code());
	}
}
