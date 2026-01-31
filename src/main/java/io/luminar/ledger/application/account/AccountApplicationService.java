package io.luminar.ledger.application.account;

import io.luminar.ledger.application.account.command.CloseAccountCommand;
import io.luminar.ledger.application.account.command.CreateAccountCommand;
import io.luminar.ledger.application.account.command.FreezeAccountCommand;
import io.luminar.ledger.application.account.command.UnfreezeAccountCommand;
import io.luminar.ledger.domain.account.Account;
import io.luminar.ledger.domain.account.AccountId;
import io.luminar.ledger.domain.account.Currency;
import io.luminar.ledger.infrastructure.mapper.AccountPersistenceMapper;
import io.luminar.ledger.infrastructure.persistence.account.AccountBalanceEntity;
import io.luminar.ledger.infrastructure.persistence.account.AccountBalanceJpaRepository;
import io.luminar.ledger.infrastructure.persistence.account.AccountEntity;
import io.luminar.ledger.infrastructure.persistence.account.AccountJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AccountApplicationService {
	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(6);

	private final AccountJpaRepository accountJpaRepository;
	private final AccountBalanceJpaRepository accountBalanceJpaRepository;

	public AccountApplicationService(AccountJpaRepository accountJpaRepository,
			AccountBalanceJpaRepository accountBalanceJpaRepository) {
		this.accountJpaRepository = Objects.requireNonNull(accountJpaRepository);
		this.accountBalanceJpaRepository = Objects.requireNonNull(accountBalanceJpaRepository);
	}

	@Transactional
	public UUID create(CreateAccountCommand command) {
		Objects.requireNonNull(command, "CreateAccountCommand is required");

		accountJpaRepository.findByCode(command.code()).ifPresent(existing -> {
			throw new ResponseStatusException(CONFLICT, "Account code already exists: " + command.code());
		});

		Account account = Account.open(
				new AccountId(UUID.randomUUID()),
				command.code(),
				command.name(),
				command.type(),
				new Currency(command.currency()));

		AccountEntity entity = Objects.requireNonNull(
				AccountPersistenceMapper.toEntity(account),
				"AccountPersistenceMapper.toEntity returned null");

		try {
			accountJpaRepository.save(entity);
			accountBalanceJpaRepository.save(new AccountBalanceEntity(account.id().value(), ZERO));
		} catch (DataIntegrityViolationException e) {
			throw new ResponseStatusException(CONFLICT, "Account code already exists: " + command.code(), e);
		}

		return account.id().value();
	}

	@Transactional
	public void freeze(FreezeAccountCommand command) {
		Objects.requireNonNull(command, "FreezeAccountCommand is required");
		AccountEntity entity = accountJpaRepository.findByIdForUpdate(command.accountId())
				.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Account not found: " + command.accountId()));
		Account current = AccountPersistenceMapper.toDomain(entity);
		Account updated = current.freeze(command.reason());
		applyLifecycle(entity, updated);
	}

	@Transactional
	public void unfreeze(UnfreezeAccountCommand command) {
		Objects.requireNonNull(command, "UnfreezeAccountCommand is required");
		AccountEntity entity = accountJpaRepository.findByIdForUpdate(command.accountId())
				.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Account not found: " + command.accountId()));
		Account current = AccountPersistenceMapper.toDomain(entity);
		Account updated = current.unfreeze(command.reason());
		applyLifecycle(entity, updated);
	}

	@Transactional
	public void close(CloseAccountCommand command) {
		Objects.requireNonNull(command, "CloseAccountCommand is required");
		AccountEntity entity = accountJpaRepository.findByIdForUpdate(command.accountId())
				.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Account not found: " + command.accountId()));
		Account current = AccountPersistenceMapper.toDomain(entity);
		Account updated = current.close(command.reason());
		applyLifecycle(entity, updated);
	}

	private static void applyLifecycle(AccountEntity entity, Account updated) {
		AccountEntity mapped = AccountPersistenceMapper.toEntity(updated);
		entity.setStatus(mapped.getStatus());
		entity.setFrozenAt(mapped.getFrozenAt());
		entity.setClosedAt(mapped.getClosedAt());
		entity.setStatusChangedAt(mapped.getStatusChangedAt());
		entity.setStatusReason(mapped.getStatusReason());
	}
}
