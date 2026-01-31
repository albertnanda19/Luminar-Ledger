package io.luminar.ledger.account;

import io.luminar.ledger.TestcontainersConfiguration;
import io.luminar.ledger.application.account.AccountApplicationService;
import io.luminar.ledger.application.account.command.CloseAccountCommand;
import io.luminar.ledger.application.account.command.CreateAccountCommand;
import io.luminar.ledger.application.account.command.FreezeAccountCommand;
import io.luminar.ledger.application.account.command.UnfreezeAccountCommand;
import io.luminar.ledger.application.transaction.TransactionApplicationService;
import io.luminar.ledger.application.transaction.command.PostTransactionCommand;
import io.luminar.ledger.domain.account.AccountClosedException;
import io.luminar.ledger.domain.account.AccountFrozenException;
import io.luminar.ledger.domain.account.AccountType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccountLifecycleIntegrationTest {
	private static final String CURRENCY = "USD";
	private static final BigDecimal INITIAL_SOURCE_BALANCE = new BigDecimal("1000.000000");
	private static final BigDecimal AMOUNT = new BigDecimal("10.000000");

	@Autowired
	private AccountApplicationService accountApplicationService;

	@Autowired
	private TransactionApplicationService transactionApplicationService;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void postingToFrozenAccount_mustBeRejected() {
		String runId = UUID.randomUUID().toString();
		UUID sourceAccountId = createAccount("SRC-" + runId);
		UUID targetAccountId = createAccount("TGT-" + runId);

		seedBalance(sourceAccountId, INITIAL_SOURCE_BALANCE);
		accountApplicationService.freeze(new FreezeAccountCommand(targetAccountId, "FRAUD_REVIEW"));

		PostTransactionCommand cmd = transfer("tx-frozen-" + runId, sourceAccountId, targetAccountId);
		assertThrows(AccountFrozenException.class, () -> transactionApplicationService.post(cmd));
	}

	@Test
	void postingToClosedAccount_mustBeRejected() {
		String runId = UUID.randomUUID().toString();
		UUID sourceAccountId = createAccount("SRC-" + runId);
		UUID targetAccountId = createAccount("TGT-" + runId);

		seedBalance(sourceAccountId, INITIAL_SOURCE_BALANCE);
		accountApplicationService.close(new CloseAccountCommand(targetAccountId, "COMPLIANCE_CLOSURE"));

		PostTransactionCommand cmd = transfer("tx-closed-" + runId, sourceAccountId, targetAccountId);
		assertThrows(AccountClosedException.class, () -> transactionApplicationService.post(cmd));
	}

	@Test
	void freezeThenUnfreeze_thenPostingWorks() {
		String runId = UUID.randomUUID().toString();
		UUID sourceAccountId = createAccount("SRC-" + runId);
		UUID targetAccountId = createAccount("TGT-" + runId);

		seedBalance(sourceAccountId, INITIAL_SOURCE_BALANCE);

		accountApplicationService.freeze(new FreezeAccountCommand(targetAccountId, "DISPUTE"));
		accountApplicationService.unfreeze(new UnfreezeAccountCommand(targetAccountId, "DISPUTE_RESOLVED"));

		PostTransactionCommand cmd = transfer("tx-unfrozen-" + runId, sourceAccountId, targetAccountId);
		UUID txId = transactionApplicationService.post(cmd);
		assertNotNull(txId);
	}

	@Test
	void closedAccountAlwaysRejectsPosting_evenAfterOtherActions() {
		String runId = UUID.randomUUID().toString();
		UUID sourceAccountId = createAccount("SRC-" + runId);
		UUID targetAccountId = createAccount("TGT-" + runId);

		seedBalance(sourceAccountId, INITIAL_SOURCE_BALANCE);
		accountApplicationService.freeze(new FreezeAccountCommand(targetAccountId, "HOLD"));
		accountApplicationService.close(new CloseAccountCommand(targetAccountId, "FINAL_CLOSURE"));

		PostTransactionCommand cmd = transfer("tx-closed-always-" + runId, sourceAccountId, targetAccountId);
		assertThrows(AccountClosedException.class, () -> transactionApplicationService.post(cmd));
	}

	private UUID createAccount(String code) {
		UUID accountId = Objects.requireNonNull(accountApplicationService.create(new CreateAccountCommand(
				code,
				code,
				AccountType.ASSET,
				CURRENCY)), "AccountApplicationService.create returned null");
		return accountId;
	}

	private void seedBalance(UUID accountId, BigDecimal balance) {
		TransactionTemplate txTemplate = new TransactionTemplate(
				Objects.requireNonNull(transactionManager, "PlatformTransactionManager is required"));
		txTemplate.executeWithoutResult(status -> {
			int updated = entityManager.createQuery(
						"update AccountBalanceEntity b set b.balance = :balance where b.accountId = :accountId")
					.setParameter("balance", balance)
					.setParameter("accountId", accountId)
					.executeUpdate();
			if (updated != 1) {
				throw new IllegalStateException("Failed to seed account balance");
			}
			entityManager.flush();
		});
	}

	private static PostTransactionCommand transfer(String referenceKey, UUID sourceAccountId, UUID targetAccountId) {
		return new PostTransactionCommand(referenceKey, List.of(
				new PostTransactionCommand.Entry(sourceAccountId, PostTransactionCommand.EntryType.DEBIT, AMOUNT),
				new PostTransactionCommand.Entry(targetAccountId, PostTransactionCommand.EntryType.CREDIT, AMOUNT)));
	}
}
