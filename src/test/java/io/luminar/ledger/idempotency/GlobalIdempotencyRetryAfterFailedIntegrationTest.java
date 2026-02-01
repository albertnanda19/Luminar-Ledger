package io.luminar.ledger.idempotency;

import io.luminar.ledger.TestcontainersConfiguration;
import io.luminar.ledger.application.account.AccountApplicationService;
import io.luminar.ledger.application.account.command.CreateAccountCommand;
import io.luminar.ledger.application.transaction.TransactionApplicationService;
import io.luminar.ledger.application.transaction.command.PostTransactionCommand;
import io.luminar.ledger.domain.account.AccountType;
import io.luminar.ledger.domain.common.DomainException;
import io.luminar.ledger.service.PostedTransaction;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"spring.task.scheduling.enabled=false",
		"ledger.idempotency.ttl-seconds=10"
})
@Testcontainers
class GlobalIdempotencyRetryAfterFailedIntegrationTest {
	private static final String CURRENCY = "USD";
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
	private PlatformTransactionManager transactionManager;

	@Autowired
	private EntityManager entityManager;

	@Test
	void retryAfterFailed_mustBeAbleToProceed_andMustPostExactlyOnce() {
		String runId = UUID.randomUUID().toString();
		UUID sourceAccountId = createAccount("SRC-" + runId);
		UUID targetAccountId = createAccount("TGT-" + runId);

		String referenceKey = "idem-failed-" + runId;
		PostTransactionCommand cmd = new PostTransactionCommand(referenceKey, List.of(
				new PostTransactionCommand.Entry(sourceAccountId, PostTransactionCommand.EntryType.DEBIT, AMOUNT),
				new PostTransactionCommand.Entry(targetAccountId, PostTransactionCommand.EntryType.CREDIT, AMOUNT)));

		assertThrows(DomainException.class, () -> transactionApplicationService.post(cmd));

		seedBalance(sourceAccountId, new BigDecimal("100.000000"));
		PostedTransaction posted = transactionApplicationService.post(cmd);
		assertNotNull(posted.transactionId());

		PostedTransaction posted2 = transactionApplicationService.post(cmd);
		assertEquals(posted.transactionId(), posted2.transactionId());
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
