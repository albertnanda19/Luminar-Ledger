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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"spring.task.scheduling.enabled=false",
		"ledger.cache.transaction-history.ttl-seconds=1"
})
@Testcontainers
class TransactionHistoryReadThroughCacheIntegrationTest {
	private static final String CURRENCY = "USD";
	private static final BigDecimal INITIAL_SOURCE_BALANCE = new BigDecimal("1000.000000");
	private static final BigDecimal AMOUNT = new BigDecimal("10.000000");

	@Container
	@SuppressWarnings("resource")
	private static final GenericContainer<?> redis = new GenericContainer<>(
			DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void registerRedisProperties(DynamicPropertyRegistry registry) {
		if (!redis.isRunning()) {
			redis.start();
		}
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
		registry.add("spring.data.redis.timeout", () -> "100ms");
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

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@BeforeEach
	void resetRedis() {
		try (var connection = Objects.requireNonNull(stringRedisTemplate.getConnectionFactory(),
				"connectionFactory is required").getConnection()) {
			connection.serverCommands().flushAll();
		}
	}

	@Test
	void cacheMiss_thenDbHit_thenCachePopulated_thenCacheHitDoesNotRequireDb() {
		String runId = UUID.randomUUID().toString();
		UUID sourceAccountId = createAccount("SRC-" + runId);
		UUID targetAccountId = createAccount("TGT-" + runId);
		seedBalance(sourceAccountId, INITIAL_SOURCE_BALANCE);

		UUID txId = postTransferTransaction(sourceAccountId, targetAccountId, "tx-cache-miss-" + runId);
		projectUntilCaughtUp();

		var first = accountTransactionHistoryReadService.findByAccountId(sourceAccountId, null, null, 0, 50);
		assertTrue(first.stream().anyMatch(i -> txId.equals(i.getTransactionId())));

		truncateProjectionOnly();

		var second = accountTransactionHistoryReadService.findByAccountId(sourceAccountId, null, null, 0, 50);
		assertEquals(first.size(), second.size());
		assertTrue(second.stream().anyMatch(i -> txId.equals(i.getTransactionId())));
	}

	@Test
	void paginationCacheKeyMustBeUnique_perPage() {
		String runId = UUID.randomUUID().toString();
		UUID sourceAccountId = createAccount("SRC-" + runId);
		UUID targetAccountId = createAccount("TGT-" + runId);
		seedBalance(sourceAccountId, INITIAL_SOURCE_BALANCE);

		UUID tx1 = postTransferTransaction(sourceAccountId, targetAccountId, "tx-page-1-" + runId);
		UUID tx2 = postTransferTransaction(sourceAccountId, targetAccountId, "tx-page-2-" + runId);
		assertNotNull(tx1);
		assertNotNull(tx2);
		projectUntilCaughtUp();

		List<io.luminar.ledger.api.dto.response.TransactionHistoryItem> page0 = accountTransactionHistoryReadService
				.findByAccountId(sourceAccountId, null, null, 0, 1);
		List<io.luminar.ledger.api.dto.response.TransactionHistoryItem> page1 = accountTransactionHistoryReadService
				.findByAccountId(sourceAccountId, null, null, 1, 1);
		assertEquals(1, page0.size());
		assertEquals(1, page1.size());

		truncateProjectionOnly();

		var cached0 = accountTransactionHistoryReadService.findByAccountId(sourceAccountId, null, null, 0, 1);
		var cached1 = accountTransactionHistoryReadService.findByAccountId(sourceAccountId, null, null, 1, 1);
		assertEquals(cached0.getFirst().getTransactionId(), page0.getFirst().getTransactionId());
		assertEquals(cached1.getFirst().getTransactionId(), page1.getFirst().getTransactionId());
		assertTrue(!cached0.getFirst().getTransactionId().equals(cached1.getFirst().getTransactionId()));
	}

	@Test
	void ttlExpiry_mustReloadFromDb() throws Exception {
		String runId = UUID.randomUUID().toString();
		UUID sourceAccountId = createAccount("SRC-" + runId);
		UUID targetAccountId = createAccount("TGT-" + runId);
		seedBalance(sourceAccountId, INITIAL_SOURCE_BALANCE);

		postTransferTransaction(sourceAccountId, targetAccountId, "tx-ttl-" + runId);
		projectUntilCaughtUp();

		var first = accountTransactionHistoryReadService.findByAccountId(sourceAccountId, null, null, 0, 50);
		assertTrue(first.size() >= 1);

		truncateProjectionOnly();

		Thread.sleep(1500L);

		var reloaded = accountTransactionHistoryReadService.findByAccountId(sourceAccountId, null, null, 0, 50);
		assertEquals(0, reloaded.size());
	}

	@Test
	void filterHashMustBeUnique_perFilterParameters() {
		String runId = UUID.randomUUID().toString();
		UUID sourceAccountId = createAccount("SRC-" + runId);
		UUID targetAccountId = createAccount("TGT-" + runId);
		seedBalance(sourceAccountId, INITIAL_SOURCE_BALANCE);

		postTransferTransaction(sourceAccountId, targetAccountId, "tx-filter-" + runId);
		projectUntilCaughtUp();

		var all = accountTransactionHistoryReadService.findByAccountId(sourceAccountId, null, null, 0, 50);
		assertTrue(all.size() >= 1);

		Instant futureFrom = Instant.now().plusSeconds(3_600);
		var empty = accountTransactionHistoryReadService.findByAccountId(sourceAccountId, futureFrom, null, 0, 50);
		assertEquals(0, empty.size());

		truncateProjectionOnly();

		var emptyCached = accountTransactionHistoryReadService.findByAccountId(sourceAccountId, futureFrom, null, 0,
				50);
		assertEquals(0, emptyCached.size());
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

	private void truncateProjectionOnly() {
		TransactionTemplate txTemplate = new TransactionTemplate(
				Objects.requireNonNull(transactionManager, "transactionManager is required"));
		txTemplate.executeWithoutResult(status -> {
			entityManager.createNativeQuery("truncate table transaction_history_projection").executeUpdate();
			entityManager.flush();
		});
	}
}
