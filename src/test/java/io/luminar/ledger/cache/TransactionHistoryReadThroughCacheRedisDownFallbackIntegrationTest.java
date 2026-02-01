package io.luminar.ledger.cache;

import io.luminar.ledger.TestcontainersConfiguration;
import io.luminar.ledger.application.account.AccountApplicationService;
import io.luminar.ledger.application.account.AccountTransactionHistoryReadService;
import io.luminar.ledger.application.account.command.CreateAccountCommand;
import io.luminar.ledger.application.transaction.TransactionApplicationService;
import io.luminar.ledger.application.transaction.command.PostTransactionCommand;
import io.luminar.ledger.domain.account.AccountType;
import io.luminar.ledger.infrastructure.projection.LedgerEventProjector;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class TransactionHistoryReadThroughCacheRedisDownFallbackIntegrationTest {
	private static final String CURRENCY = "USD";
	private static final BigDecimal INITIAL_SOURCE_BALANCE = new BigDecimal("1000.000000");
	private static final BigDecimal AMOUNT = new BigDecimal("10.000000");

	@SuppressWarnings("resource")
	private static final GenericContainer<?> redis = new GenericContainer<>(
			DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void registerRedisProperties(DynamicPropertyRegistry registry) {
		redis.start();
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
		registry.add("spring.data.redis.timeout", () -> "100ms");
	}

	@AfterAll
	static void stopRedis() {
		redis.stop();
	}

	@Autowired
	private AccountApplicationService accountApplicationService;

	@Autowired
	private TransactionApplicationService transactionApplicationService;

	@Autowired
	private LedgerEventProjector ledgerEventProjector;

	@Autowired
	private AccountTransactionHistoryReadService accountTransactionHistoryReadService;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void stopRedisBeforeTest() {
		if (redis.isRunning()) {
			redis.stop();
		}
	}

	@Test
	void redisDown_mustFallbackToDb_andRequestMustNotFail() {
		String runId = UUID.randomUUID().toString();
		UUID sourceAccountId = createAccount("SRC-" + runId);
		UUID targetAccountId = createAccount("TGT-" + runId);
		seedBalance(sourceAccountId, INITIAL_SOURCE_BALANCE);

		UUID txId = postTransferTransaction(sourceAccountId, targetAccountId, "tx-redis-down-" + runId);
		assertNotNull(txId);
		projectUntilCaughtUp();

		var result = accountTransactionHistoryReadService.findByAccountId(sourceAccountId, null, null, 0, 50);
		assertTrue(result.stream().anyMatch(i -> txId.equals(i.getTransactionId())));
	}

	private void projectUntilCaughtUp() {
		for (int i = 0; i < 10; i++) {
			int processed = ledgerEventProjector.projectOnce();
			if (processed == 0) {
				return;
			}
		}
		throw new IllegalStateException("Projector did not catch up within expected iterations");
	}

	private UUID createAccount(String code) {
		UUID accountId = Objects.requireNonNull(accountApplicationService.create(new CreateAccountCommand(
				code,
				code,
				AccountType.ASSET,
				CURRENCY)), "AccountApplicationService.create returned null");
		return accountId;
	}

	private UUID postTransferTransaction(UUID sourceAccountId, UUID targetAccountId, String referenceKey) {
		PostTransactionCommand cmd = new PostTransactionCommand(referenceKey, List.of(
				new PostTransactionCommand.Entry(sourceAccountId, PostTransactionCommand.EntryType.DEBIT, AMOUNT),
				new PostTransactionCommand.Entry(targetAccountId, PostTransactionCommand.EntryType.CREDIT, AMOUNT)));
		return transactionApplicationService.post(cmd);
	}

	private void seedBalance(UUID accountId, BigDecimal balance) {
		TransactionTemplate txTemplate = new TransactionTemplate(
				Objects.requireNonNull(transactionManager, "transactionManager is required"));
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
}
