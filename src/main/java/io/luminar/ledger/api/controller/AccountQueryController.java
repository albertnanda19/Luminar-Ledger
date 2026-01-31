package io.luminar.ledger.api.controller;

import io.luminar.ledger.api.dto.response.AccountListResponse;
import io.luminar.ledger.api.dto.response.AccountSummary;
import io.luminar.ledger.domain.account.Account;
import io.luminar.ledger.infrastructure.mapper.AccountPersistenceMapper;
import io.luminar.ledger.infrastructure.persistence.account.AccountEntity;
import io.luminar.ledger.infrastructure.persistence.account.AccountJpaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountQueryController {
	private final AccountJpaRepository accountJpaRepository;

	public AccountQueryController(AccountJpaRepository accountJpaRepository) {
		this.accountJpaRepository = Objects.requireNonNull(accountJpaRepository);
	}

	@GetMapping
	public AccountListResponse list() {
		List<AccountSummary> accounts = accountJpaRepository.findAll().stream()
				.map(AccountQueryController::toSummary)
				.toList();

		return new AccountListResponse(accounts);
	}

	private static AccountSummary toSummary(AccountEntity entity) {
		Account account = Objects.requireNonNull(AccountPersistenceMapper.toDomain(entity),
				"AccountPersistenceMapper.toDomain returned null");
		return new AccountSummary(
				account.id().value(),
				account.code(),
				account.name(),
				account.type(),
				account.currency().code(),
				account.status());
	}
}
