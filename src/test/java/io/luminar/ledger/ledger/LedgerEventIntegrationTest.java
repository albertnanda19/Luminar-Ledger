package io.luminar.ledger.ledger;

import io.luminar.ledger.TestcontainersConfiguration;
import io.luminar.ledger.application.account.AccountApplicationService;
import io.luminar.ledger.application.account.command.CreateAccountCommand;
import io.luminar.ledger.application.transaction.TransactionApplicationService;
import io.luminar.ledger.application.transaction.command.PostTransactionCommand;
import io.luminar.ledger.domain.account.AccountType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LedgerEventIntegrationTest {
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

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void postingTransaction_mustEmitImmutableLedgerEvent_withPayloadMatchingEntries_andNoDuplicateOnRetry()
			throws Exception {
		String runId = UUID.randomUUID().toString();
		UUID sourceAccountId = createAccount("SRC-" + runId);
		UUID targetAccountId = createAccount("TGT-" + runId);
		seedBalance(sourceAccountId, INITIAL_SOURCE_BALANCE);

		String referenceKey = "tx-event-" + runId;
		PostTransactionCommand cmd = new PostTransactionCommand(referenceKey, List.of(
				new PostTransactionCommand.Entry(sourceAccountId, PostTransactionCommand.EntryType.DEBIT, AMOUNT),
				new PostTransactionCommand.Entry(targetAccountId, PostTransactionCommand.EntryType.CREDIT, AMOUNT)));

		UUID txId = transactionApplicationService.post(cmd).transactionId();
		assertNotNull(txId);

		Object[] eventRow = (Object[]) entityManager.createNativeQuery(
				"select aggregate_id, sequence_number, event_type, payload::text, occurred_at " +
						"from ledger_events where reference_id = :referenceId")
				.setParameter("referenceId", referenceKey)
				.getSingleResult();

		UUID aggregateId = (UUID) eventRow[0];
		Number sequenceNumber = (Number) eventRow[1];
		String eventType = (String) eventRow[2];
		String payloadJson = (String) eventRow[3];
		Instant occurredAt;
		Object occurredAtRawDb = eventRow[4];
		if (occurredAtRawDb instanceof java.sql.Timestamp ts) {
			occurredAt = ts.toInstant();
		} else if (occurredAtRawDb instanceof java.time.OffsetDateTime odt) {
			occurredAt = odt.toInstant();
		} else if (occurredAtRawDb instanceof Instant i) {
			occurredAt = i;
		} else {
			throw new IllegalStateException("Unexpected occurred_at type from DB: " +
					(occurredAtRawDb == null ? "null" : occurredAtRawDb.getClass().getName()));
		}

		assertEquals(txId, aggregateId);
		assertEquals(1L, sequenceNumber.longValue());
		assertEquals("LEDGER_TRANSACTION_RECORDED", eventType);

		@SuppressWarnings("unchecked")
		Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
		assertEquals(txId.toString(), payload.get("transaction_id"));
		assertEquals(referenceKey, payload.get("reference_key"));
		assertEquals(CURRENCY, payload.get("currency"));

		Object occurredAtRaw = payload.get("occurred_at");
		assertNotNull(occurredAtRaw);
		Instant payloadOccurredAt = Instant.parse(occurredAtRaw.toString());
		assertEquals(occurredAt, payloadOccurredAt);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> legs = (List<Map<String, Object>>) payload.get("entries");
		assertNotNull(legs);
		assertEquals(2, legs.size());

		Set<String> payloadLegs = new HashSet<>();
		for (Map<String, Object> leg : legs) {
			UUID accountId = UUID.fromString(leg.get("account_id").toString());
			String entryTypeLeg = leg.get("entry_type").toString();
			BigDecimal amountLeg = new BigDecimal(leg.get("amount").toString()).stripTrailingZeros();
			payloadLegs.add(accountId + "|" + entryTypeLeg + "|" + amountLeg.toPlainString());
		}

		@SuppressWarnings("unchecked")
		List<Object[]> dbRows = entityManager.createNativeQuery(
				"select account_id::text, entry_type::text, amount from transaction_entries where transaction_id = :txId")
				.setParameter("txId", txId)
				.getResultList();
		assertEquals(2, dbRows.size());

		Set<String> dbLegs = new HashSet<>();
		for (Object[] row : dbRows) {
			UUID accountId = UUID.fromString(row[0].toString());
			String entryTypeLeg = row[1].toString();
			BigDecimal amountLeg = ((BigDecimal) row[2]).stripTrailingZeros();
			dbLegs.add(accountId + "|" + entryTypeLeg + "|" + amountLeg.toPlainString());
		}

		assertEquals(dbLegs, payloadLegs);

		UUID txId2 = transactionApplicationService.post(cmd).transactionId();
		assertEquals(txId, txId2);

		Number eventCount = (Number) entityManager.createNativeQuery(
				"select count(*) from ledger_events where reference_id = :referenceId")
				.setParameter("referenceId", referenceKey)
				.getSingleResult();
		assertEquals(1L, eventCount.longValue());

		Number entryCount = (Number) entityManager.createNativeQuery(
				"select count(*) from transaction_entries where transaction_id = :txId")
				.setParameter("txId", txId)
				.getSingleResult();
		assertEquals(2L, entryCount.longValue());
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
}
