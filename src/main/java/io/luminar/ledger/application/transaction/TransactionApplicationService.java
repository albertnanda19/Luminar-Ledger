package io.luminar.ledger.application.transaction;

import io.luminar.ledger.application.transaction.command.PostTransactionCommand;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionEntity;
import io.luminar.ledger.infrastructure.persistence.ledger.TransactionJpaRepository;
import io.luminar.ledger.service.LedgerPostingService;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.LockSupport;

@Service
public class TransactionApplicationService {
	private static final int MAX_ATTEMPTS = 40;
	private static final String SERIALIZATION_FAILURE_SQLSTATE = "40001";

	private final TransactionJpaRepository transactionJpaRepository;
	private final LedgerPostingService ledgerPostingService;
	private final Semaphore postingConcurrency;

	public TransactionApplicationService(TransactionJpaRepository transactionJpaRepository,
			LedgerPostingService ledgerPostingService,
			DataSource dataSource) {
		this.transactionJpaRepository = Objects.requireNonNull(transactionJpaRepository);
		this.ledgerPostingService = Objects.requireNonNull(ledgerPostingService);
		this.postingConcurrency = new Semaphore(resolvePostingPermits(dataSource), true);
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public UUID post(PostTransactionCommand command) {
		Objects.requireNonNull(command, "PostTransactionCommand is required");
		acquirePostingPermit();
		try {
			Optional<TransactionEntity> existing = transactionJpaRepository.findByReferenceKey(command.referenceKey());
			if (existing.isPresent()) {
				return existing.get().getId();
			}

			RuntimeException last = null;
			for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
				try {
					return ledgerPostingService.post(command);
				} catch (RuntimeException e) {
					last = e;
					if (!isSerializationFailure(e) || attempt == MAX_ATTEMPTS) {
						throw e;
					}
					backoff(command.referenceKey(), attempt);
				}
			}

			throw Objects.requireNonNull(last, "Retry loop ended unexpectedly without exception");
		} finally {
			postingConcurrency.release();
		}
	}

	private void acquirePostingPermit() {
		try {
			postingConcurrency.acquire();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for posting permit", e);
		}
	}

	private static int resolvePostingPermits(DataSource dataSource) {
		Objects.requireNonNull(dataSource, "dataSource is required");
		if (dataSource instanceof HikariDataSource hikari) {
			int maxPool = hikari.getMaximumPoolSize();
			return Math.max(1, Math.min(16, maxPool / 4));
		}
		return 16;
	}

	private static boolean isSerializationFailure(Throwable t) {
		Throwable current = t;
		int depth = 0;
		while (current != null && depth < 50) {
			if (current instanceof SQLException sqlEx) {
				String state = sqlEx.getSQLState();
				if (SERIALIZATION_FAILURE_SQLSTATE.equals(state)) {
					return true;
				}
			}
			current = current.getCause();
			depth++;
		}
		return false;
	}

	private static void backoff(String referenceKey, int attempt) {
		Objects.requireNonNull(referenceKey, "referenceKey is required");
		int seed = 31 * referenceKey.hashCode() + attempt;
		long jitterMs = Math.floorMod(seed, 11);
		long exp = 1L << Math.min(7, Math.max(0, attempt - 1));
		long baseMs = Math.min(200L, 5L * exp);
		long sleepMs = Math.min(250L, baseMs + jitterMs);
		LockSupport.parkNanos(sleepMs * 1_000_000L);
	}
}
