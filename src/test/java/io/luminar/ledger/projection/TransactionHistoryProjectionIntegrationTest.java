package io.luminar.ledger.projection;

import io.luminar.ledger.TestcontainersConfiguration;
import io.luminar.ledger.application.account.AccountApplicationService;
import io.luminar.ledger.application.account.command.CreateAccountCommand;
import io.luminar.ledger.application.transaction.TransactionApplicationService;
import io.luminar.ledger.application.transaction.command.PostTransactionCommand;
import io.luminar.ledger.domain.account.AccountType;
import io.luminar.ledger.infrastructure.projection.LedgerEventProjector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.task.scheduling.enabled=false")
class TransactionHistoryProjectionIntegrationTest {
	private static final String CURRENCY = "USD";
	private static final BigDecimal INITIAL_SOURCE_BALANCE = new BigDecimal("1000.000000");
	private static final BigDecimal AMOUNT = new BigDecimal("10.000000");

	@Autowired
	private AccountApplicationService accountApplicationService;

	@Autowired
	private TransactionApplicationService transactionApplicationService;

	@Autowired
	private LedgerEventProjector ledgerEventProjector;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void transactionHistoryProjection_mustBeAsyncIdempotentRestartSafe_apiReadsFromProjection_andReplayDeterministic()
			throws Exception {
		String runId = UUID.randomUUID().toString();
		UUID sourceAccountId = createAccount("SRC-" + runId);
		UUID targetAccountId = createAccount("TGT-" + runId);
		seedBalance(sourceAccountId, INITIAL_SOURCE_BALANCE);

		String referenceKey = "tx-proj-" + runId;
		PostTransactionCommand cmd = new PostTransactionCommand(referenceKey, List.of(
				new PostTransactionCommand.Entry(sourceAccountId, PostTransactionCommand.EntryType.DEBIT, AMOUNT),
				new PostTransactionCommand.Entry(targetAccountId, PostTransactionCommand.EntryType.CREDIT, AMOUNT)));

		UUID txId = transactionApplicationService.post(cmd).transactionId();
		assertNotNull(txId);

		projectUntilCaughtUp();

		assertEquals(2L, countProjectionRows(txId));

		ledgerEventProjector.projectOnce();
		assertEquals(2L, countProjectionRows(txId));

		resetCheckpointOnly();
		ledgerEventProjector.projectOnce();
		assertEquals(2L, countProjectionRows(txId));

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/v1/accounts/{accountId}/transactions?limit=50",
				String.class,
				sourceAccountId);
		assertEquals(200, response.getStatusCode().value());

		JsonNode body = objectMapper.readTree(Objects.requireNonNull(response.getBody(), "response body is required"));
		assertEquals(sourceAccountId.toString(), body.get("accountId").asText());
		JsonNode transactions = body.get("transactions");
		assertNotNull(transactions);
		assertTrue(transactions.isArray());
		assertTrue(transactions.size() >= 1);

		JsonNode first = transactions.get(0);
		assertEquals(txId.toString(), first.get("transactionId").asText());
		assertEquals(referenceKey, first.get("referenceKey").asText());
		assertEquals("DEBIT", first.get("entryType").asText());
		assertEquals(0, AMOUNT.compareTo(first.get("amount").decimalValue()));
		assertNotNull(first.get("postedAt"));

		Set<String> beforeReplay = loadProjectionSignature(txId);

		replay();
		projectUntilCaughtUp();

		assertEquals(2L, countProjectionRows(txId));
		Set<String> afterReplay = loadProjectionSignature(txId);
		assertEquals(beforeReplay, afterReplay);
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

	private long countProjectionRows(UUID txId) {
		Number count = (Number) entityManager.createNativeQuery(
				"select count(*) from transaction_history_projection where transaction_id = :txId")
				.setParameter("txId", txId)
				.getSingleResult();
		return count.longValue();
	}

	private Set<String> loadProjectionSignature(UUID txId) {
		@SuppressWarnings("unchecked")
		List<Object[]> rows = (List<Object[]>) entityManager.createNativeQuery(
				"select account_id::text, direction::text, amount, currency, occurred_at::text, sequence_number " +
						"from transaction_history_projection where transaction_id = :txId")
				.setParameter("txId", txId)
				.getResultList();

		Set<String> signature = new HashSet<>();
		for (Object[] r : rows) {
			signature.add(r[0] + "|" + r[1] + "|" + r[2] + "|" + r[3] + "|" + r[4] + "|" + r[5]);
		}
		return signature;
	}

	private void resetCheckpointOnly() {
		TransactionTemplate txTemplate = new TransactionTemplate(
				Objects.requireNonNull(transactionManager, "transactionManager is required"));
		txTemplate.executeWithoutResult(status -> {
			entityManager.createNativeQuery(
					"update projection_checkpoints set last_sequence_number = 0 where projection_type = :projectionType")
					.setParameter("projectionType", LedgerEventProjector.TRANSACTION_HISTORY_PROJECTION_TYPE)
					.executeUpdate();
			entityManager.flush();
		});
	}

	private void replay() {
		TransactionTemplate txTemplate = new TransactionTemplate(
				Objects.requireNonNull(transactionManager, "transactionManager is required"));
		txTemplate.executeWithoutResult(status -> {
			entityManager.createNativeQuery("truncate table transaction_history_projection").executeUpdate();
			entityManager.createNativeQuery("truncate table projection_event_dedup").executeUpdate();
			entityManager.createNativeQuery(
					"delete from projection_checkpoints where projection_type = :projectionType")
					.setParameter("projectionType", LedgerEventProjector.TRANSACTION_HISTORY_PROJECTION_TYPE)
					.executeUpdate();
			entityManager.flush();
		});
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
