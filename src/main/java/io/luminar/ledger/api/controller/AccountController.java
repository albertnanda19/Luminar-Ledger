package io.luminar.ledger.api.controller;

import io.luminar.ledger.api.dto.request.AccountStatusChangeRequest;
import io.luminar.ledger.api.dto.request.CreateAccountRequest;
import io.luminar.ledger.api.dto.response.CreateAccountResponse;
import io.luminar.ledger.application.account.AccountApplicationService;
import io.luminar.ledger.application.account.command.CloseAccountCommand;
import io.luminar.ledger.application.account.command.CreateAccountCommand;
import io.luminar.ledger.application.account.command.FreezeAccountCommand;
import io.luminar.ledger.application.account.command.UnfreezeAccountCommand;
import io.luminar.ledger.domain.account.AccountType;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

	@PostMapping("/{accountId}/freeze")
	public ResponseEntity<Void> freeze(@PathVariable UUID accountId,
			@Valid @RequestBody AccountStatusChangeRequest request) {
		accountApplicationService.freeze(new FreezeAccountCommand(accountId, request.reason()));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{accountId}/unfreeze")
	public ResponseEntity<Void> unfreeze(@PathVariable UUID accountId,
			@Valid @RequestBody AccountStatusChangeRequest request) {
		accountApplicationService.unfreeze(new UnfreezeAccountCommand(accountId, request.reason()));
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{accountId}")
	public ResponseEntity<Void> close(@PathVariable UUID accountId,
			@Valid @RequestBody AccountStatusChangeRequest request) {
		accountApplicationService.close(new CloseAccountCommand(accountId, request.reason()));
		return ResponseEntity.noContent().build();
	}
}
