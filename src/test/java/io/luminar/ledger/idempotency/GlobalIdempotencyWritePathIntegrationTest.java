package io.luminar.ledger.idempotency;

import io.luminar.ledger.TestcontainersConfiguration;
import io.luminar.ledger.application.account.AccountApplicationService;
import io.luminar.ledger.application.account.command.CreateAccountCommand;
import io.luminar.ledger.application.transaction.TransactionApplicationService;
import io.luminar.ledger.application.transaction.command.PostTransactionCommand;
import io.luminar.ledger.application.transaction.idempotency.IdempotencyInProgressException;
import io.luminar.ledger.domain.account.AccountType;
import io.luminar.ledger.infrastructure.persistence.account.AccountJpaRepository;
import io.luminar.ledger.service.PostedTransaction;
import jakarta.persistence.EntityManager;
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
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"spring.task.scheduling.enabled=false",
		"ledger.idempotency.ttl-seconds=10"
})
@Testcontainers
class GlobalIdempotencyWritePathIntegrationTest {
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
	private AccountJpaRepository accountJpaRepository;

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private EntityManager entityManager;

	@Test
	void duplicateRequest_mustReturnSameResponse_andMustCommitSingleTransaction() {
		String runId = UUID.randomUUID().toString();
		UUID sourceAccountId = createAccount("SRC-" + runId);
		UUID targetAccountId = createAccount("TGT-" + runId);
		seedBalance(sourceAccountId, INITIAL_SOURCE_BALANCE);

		String referenceKey = "idem-dup-" + runId;
		PostTransactionCommand cmd = transfer(referenceKey, sourceAccountId, targetAccountId);

		PostedTransaction first = transactionApplicationService.post(cmd);
		PostedTransaction second = transactionApplicationService.post(cmd);

		assertEquals(first.transactionId(), second.transactionId());
		assertEquals(1L, countTransactions(referenceKey));

		String cached = stringRedisTemplate.opsForValue().get("idempotency::" + referenceKey);
		assertNotNull(cached);
		assertTrue(cached.contains("\"status\":\"COMPLETED\""));
	}

	@Test
	void parallelRequest_sameKey_oneMustSucceed_otherMustBeRejectedWhileInProgress() throws Exception {
		String runId = UUID.randomUUID().toString();
		UUID sourceAccountId = createAccount("SRC-" + runId);
		UUID targetAccountId = createAccount("TGT-" + runId);
		seedBalance(sourceAccountId, INITIAL_SOURCE_BALANCE);

		String referenceKey = "idem-parallel-" + runId;
		PostTransactionCommand cmd = transfer(referenceKey, sourceAccountId, targetAccountId);

		CountDownLatch lockAcquired = new CountDownLatch(1);
		CountDownLatch releaseLock = new CountDownLatch(1);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			executor.submit(() -> {
				TransactionTemplate tx = new TransactionTemplate(
						Objects.requireNonNull(transactionManager, "transactionManager is required"));
				tx.executeWithoutResult(status -> {
					accountJpaRepository.findByIdForUpdate(sourceAccountId).orElseThrow();
					lockAcquired.countDown();
					try {
						if (!releaseLock.await(10, TimeUnit.SECONDS)) {
							throw new IllegalStateException("releaseLock timeout");
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException(e);
					}
				});
			});

			if (!lockAcquired.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("lockAcquired timeout");
			}

			var firstFuture = executor.submit(() -> transactionApplicationService.post(cmd));

			waitUntilKeyContains(referenceKey, "IN_PROGRESS", Duration.ofSeconds(2));

			assertThrows(IdempotencyInProgressException.class, () -> transactionApplicationService.post(cmd));

			releaseLock.countDown();
			PostedTransaction posted = firstFuture.get(30, TimeUnit.SECONDS);
			assertNotNull(posted.transactionId());
		} finally {
			executor.shutdownNow();
		}
	}

	private void waitUntilKeyContains(String referenceKey, String status, Duration timeout) {
		String key = "idempotency::" + referenceKey;
		long deadlineNanos = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadlineNanos) {
			String json = stringRedisTemplate.opsForValue().get(key);
			if (json != null && json.contains("\"status\":\"" + status + "\"")) {
				return;
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
		}
		throw new IllegalStateException("Timed out waiting for idempotency status. key=" + key);
	}

	private long countTransactions(String referenceKey) {
		Number count = (Number) entityManager.createNativeQuery(
				"select count(*) from transactions where reference_key = :referenceKey")
				.setParameter("referenceKey", referenceKey)
				.getSingleResult();
		return count.longValue();
	}

	private UUID createAccount(String code) {
		UUID accountId = Objects.requireNonNull(accountApplicationService.create(new CreateAccountCommand(
				code,
				code,
				AccountType.ASSET,
				CURRENCY)), "AccountApplicationService.create returned null");
		return accountId;
	}

	private PostTransactionCommand transfer(String referenceKey, UUID sourceAccountId, UUID targetAccountId) {
		return new PostTransactionCommand(referenceKey, List.of(
				new PostTransactionCommand.Entry(sourceAccountId, PostTransactionCommand.EntryType.DEBIT, AMOUNT),
				new PostTransactionCommand.Entry(targetAccountId, PostTransactionCommand.EntryType.CREDIT, AMOUNT)));
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
