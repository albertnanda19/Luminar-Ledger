package io.luminar.ledger.concurrency;

import io.luminar.ledger.TestcontainersConfiguration;
import io.luminar.ledger.application.account.AccountApplicationService;
import io.luminar.ledger.application.account.command.CreateAccountCommand;
import io.luminar.ledger.application.transaction.TransactionApplicationService;
import io.luminar.ledger.application.transaction.command.PostTransactionCommand;
import io.luminar.ledger.domain.account.AccountType;
import io.luminar.ledger.infrastructure.persistence.account.AccountBalanceEntity;
import io.luminar.ledger.infrastructure.persistence.account.AccountBalanceJpaRepository;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionEntity;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LedgerConcurrencyTest {
	private static final String CURRENCY = "USD";
	private static final BigDecimal INITIAL_SOURCE_BALANCE = new BigDecimal("1000000.000000");
	private static final BigDecimal AMOUNT_PER_TX = new BigDecimal("10000.000000");

	@Autowired
	private AccountApplicationService accountApplicationService;

	@Autowired
	private TransactionApplicationService transactionApplicationService;

	@Autowired
	private AccountBalanceJpaRepository accountBalanceJpaRepository;

	@Autowired
	private TransactionJpaRepository transactionJpaRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void concurrentTransfersFromSameSourceAccount_mustNotCreateGhostMoney_andMustStayConsistent() throws Exception {
		int expectedSuccess = INITIAL_SOURCE_BALANCE.divideToIntegralValue(AMOUNT_PER_TX).intValueExact();
		int threadCount = expectedSuccess + 50;
		int poolSize = threadCount;

		String runId = UUID.randomUUID().toString();
		String baseReferenceKey = "concurrency-transfer-" + runId;

		UUID sourceAccountId = Objects.requireNonNull(accountApplicationService.create(new CreateAccountCommand(
				"SRC-" + runId,
				"Source " + runId,
				AccountType.ASSET,
				CURRENCY)), "AccountApplicationService.create returned null (source)");
		UUID targetAccountId = Objects.requireNonNull(accountApplicationService.create(new CreateAccountCommand(
				"TGT-" + runId,
				"Target " + runId,
				AccountType.ASSET,
				CURRENCY)), "AccountApplicationService.create returned null (target)");

		TransactionTemplate txTemplate = new TransactionTemplate(
				Objects.requireNonNull(transactionManager, "PlatformTransactionManager is required"));
		txTemplate.executeWithoutResult(status -> {
			int updated = entityManager.createQuery(
					"update AccountBalanceEntity b set b.balance = :balance where b.accountId = :accountId")
					.setParameter("balance", INITIAL_SOURCE_BALANCE)
					.setParameter("accountId", sourceAccountId)
					.executeUpdate();
			if (updated != 1) {
				throw new IllegalStateException("Failed to seed source balance");
			}
			entityManager.flush();
		});

		AccountBalanceEntity seededSource = accountBalanceJpaRepository.findById(sourceAccountId).orElseThrow();
		assertEquals(0, seededSource.getBalance().compareTo(INITIAL_SOURCE_BALANCE));

		List<String> referenceKeys = new ArrayList<>(threadCount);
		for (int i = 0; i < threadCount; i++) {
			referenceKeys.add(baseReferenceKey + "-" + i);
		}

		CountDownLatch ready = new CountDownLatch(threadCount);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threadCount);

		ConcurrentLinkedQueue<String> successReferenceKeys = new ConcurrentLinkedQueue<>();
		ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

		ExecutorService executor = Executors.newFixedThreadPool(poolSize);
		try {
			for (String referenceKey : referenceKeys) {
				executor.execute(() -> {
					ready.countDown();
					try {
						if (!start.await(30, TimeUnit.SECONDS)) {
							throw new IllegalStateException("Start latch timeout");
						}

						PostTransactionCommand command = new PostTransactionCommand(referenceKey, List.of(
								new PostTransactionCommand.Entry(sourceAccountId,
										PostTransactionCommand.EntryType.DEBIT,
										AMOUNT_PER_TX),
								new PostTransactionCommand.Entry(targetAccountId,
										PostTransactionCommand.EntryType.CREDIT,
										AMOUNT_PER_TX)));

						UUID txId = transactionApplicationService.post(command);
						assertNotNull(txId);
						successReferenceKeys.add(referenceKey);
					} catch (Throwable t) {
						failures.add(t);
					} finally {
						done.countDown();
					}
				});
			}

			assertTrue(ready.await(30, TimeUnit.SECONDS));
			start.countDown();
			assertTrue(done.await(120, TimeUnit.SECONDS));
		} finally {
			executor.shutdownNow();
			executor.awaitTermination(30, TimeUnit.SECONDS);
		}

		int successCount = successReferenceKeys.size();
		int failureCount = failures.size();
		assertEquals(expectedSuccess, successCount, () -> {
			StringBuilder sb = new StringBuilder();
			sb.append("Unexpected successCount. ");
			sb.append("successCount=").append(successCount).append(", expectedSuccess=").append(expectedSuccess);
			sb.append(", failureCount=").append(failureCount);
			int shown = 0;
			for (Throwable t : failures) {
				if (t == null) {
					continue;
				}
				if (shown >= 5) {
					break;
				}
				sb.append("\n").append(t.getClass().getName()).append(": ").append(t.getMessage());
				shown++;
			}
			return sb.toString();
		});

		Set<String> uniqueSucceeded = new HashSet<>(successReferenceKeys);
		assertEquals(successCount, uniqueSucceeded.size());

		for (String referenceKey : uniqueSucceeded) {
			TransactionEntity tx = transactionJpaRepository.findByReferenceKey(referenceKey).orElseThrow();
			assertEquals(referenceKey, tx.getReferenceKey());

			Number eventCount = (Number) entityManager.createNativeQuery(
					"select count(*) from ledger_events where reference_id = :referenceId")
					.setParameter("referenceId", referenceKey)
					.getSingleResult();
			assertEquals(1L, eventCount.longValue());

			Object[] row = (Object[]) entityManager.createNativeQuery(
					"select " +
							"count(*) as entry_count, " +
							"coalesce(sum(case when entry_type = 'DEBIT' then amount end), 0) as debit_total, " +
							"coalesce(sum(case when entry_type = 'CREDIT' then amount end), 0) as credit_total " +
							"from transaction_entries where transaction_id = :txId")
					.setParameter("txId", tx.getId())
					.getSingleResult();

			Number entryCount = (Number) row[0];
			BigDecimal debitTotal = (BigDecimal) row[1];
			BigDecimal creditTotal = (BigDecimal) row[2];

			assertEquals(2L, entryCount.longValue());
			assertEquals(0, debitTotal.compareTo(AMOUNT_PER_TX));
			assertEquals(0, creditTotal.compareTo(AMOUNT_PER_TX));

			Number sourceDebitCount = (Number) entityManager.createNativeQuery(
					"select count(*) from transaction_entries " +
							"where transaction_id = :txId and account_id = :accountId and entry_type = 'DEBIT' and amount = :amount")
					.setParameter("txId", tx.getId())
					.setParameter("accountId", sourceAccountId)
					.setParameter("amount", AMOUNT_PER_TX)
					.getSingleResult();
			assertEquals(1L, sourceDebitCount.longValue());

			Number targetCreditCount = (Number) entityManager.createNativeQuery(
					"select count(*) from transaction_entries " +
							"where transaction_id = :txId and account_id = :accountId and entry_type = 'CREDIT' and amount = :amount")
					.setParameter("txId", tx.getId())
					.setParameter("accountId", targetAccountId)
					.setParameter("amount", AMOUNT_PER_TX)
					.getSingleResult();
			assertEquals(1L, targetCreditCount.longValue());
		}

		for (String referenceKey : referenceKeys) {
			boolean shouldExist = uniqueSucceeded.contains(referenceKey);
			boolean exists = transactionJpaRepository.findByReferenceKey(referenceKey).isPresent();
			assertEquals(shouldExist, exists);
		}

		BigDecimal totalTransferred = AMOUNT_PER_TX.multiply(BigDecimal.valueOf(successCount));

		AccountBalanceEntity finalSource = accountBalanceJpaRepository.findById(sourceAccountId).orElseThrow();
		AccountBalanceEntity finalTarget = accountBalanceJpaRepository.findById(targetAccountId).orElseThrow();

		BigDecimal expectedFinalSource = INITIAL_SOURCE_BALANCE.subtract(totalTransferred);
		BigDecimal expectedFinalTarget = totalTransferred;

		assertEquals(0, finalSource.getBalance().compareTo(expectedFinalSource));
		assertEquals(0, finalTarget.getBalance().compareTo(expectedFinalTarget));

		assertTrue(finalSource.getBalance().compareTo(BigDecimal.ZERO) >= 0);
		assertTrue(finalTarget.getBalance().compareTo(BigDecimal.ZERO) >= 0);

		Object[] totals = (Object[]) entityManager.createNativeQuery(
				"select " +
						"coalesce(sum(case when e.entry_type = 'DEBIT' then e.amount end), 0) as debit_total, " +
						"coalesce(sum(case when e.entry_type = 'CREDIT' then e.amount end), 0) as credit_total " +
						"from transaction_entries e join transactions t on t.id = e.transaction_id " +
						"where t.reference_key like :prefix")
				.setParameter("prefix", baseReferenceKey + "-%")
				.getSingleResult();

		BigDecimal allDebit = (BigDecimal) totals[0];
		BigDecimal allCredit = (BigDecimal) totals[1];

		assertEquals(0, allDebit.compareTo(allCredit));
		assertEquals(0, allDebit.compareTo(totalTransferred));
		assertEquals(0, allCredit.compareTo(totalTransferred));
	}
}
