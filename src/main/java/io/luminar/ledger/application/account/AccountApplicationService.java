package io.luminar.ledger.application.account;

import io.luminar.ledger.application.account.command.CreateAccountCommand;
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
}
