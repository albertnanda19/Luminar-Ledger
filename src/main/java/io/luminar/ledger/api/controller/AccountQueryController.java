package io.luminar.ledger.api.controller;

import io.luminar.ledger.api.dto.response.AccountBalanceResponse;
import io.luminar.ledger.api.dto.response.AccountListResponse;
import io.luminar.ledger.api.dto.response.AccountSummary;
import io.luminar.ledger.api.dto.response.CURRENCY;
import io.luminar.ledger.domain.account.Account;
import io.luminar.ledger.infrastructure.mapper.AccountPersistenceMapper;
import io.luminar.ledger.infrastructure.persistence.account.AccountBalanceEntity;
import io.luminar.ledger.infrastructure.persistence.account.AccountBalanceJpaRepository;
import io.luminar.ledger.infrastructure.persistence.account.AccountEntity;
import io.luminar.ledger.infrastructure.persistence.account.AccountJpaRepository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountQueryController {
	private final AccountJpaRepository accountJpaRepository;
	private final AccountBalanceJpaRepository accountBalanceJpaRepository;

	public AccountQueryController(AccountJpaRepository accountJpaRepository,
			AccountBalanceJpaRepository accountBalanceJpaRepository) {
		this.accountJpaRepository = Objects.requireNonNull(accountJpaRepository);
		this.accountBalanceJpaRepository = Objects.requireNonNull(accountBalanceJpaRepository);
	}

	@GetMapping
	public AccountListResponse list() {
		List<AccountSummary> accounts = accountJpaRepository.findAll().stream()
				.map(AccountQueryController::toSummary)
				.toList();

		return new AccountListResponse(accounts);
	}

	@GetMapping("/{accountId}/balance")
	public AccountBalanceResponse getBalance(@PathVariable UUID accountId) {
		Objects.requireNonNull(accountId, "accountId is required");

		AccountEntity accountEntity = accountJpaRepository.findById(accountId).orElseThrow();
		AccountBalanceEntity balanceEntity = accountBalanceJpaRepository.findById(accountId).orElseThrow();

		return new AccountBalanceResponse(
				accountId,
				CURRENCY.fromCode(accountEntity.getCurrency()),
				balanceEntity.getBalance(),
				balanceEntity.getUpdatedAt());
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
