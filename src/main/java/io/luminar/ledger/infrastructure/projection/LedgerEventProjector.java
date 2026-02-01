package io.luminar.ledger.infrastructure.projection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class LedgerEventProjector {
	public static final String TRANSACTION_HISTORY_PROJECTION_TYPE = "TRANSACTION_HISTORY";

	private static final Logger log = LoggerFactory.getLogger(LedgerEventProjector.class);
	private static final String LEDGER_TRANSACTION_RECORDED = "LEDGER_TRANSACTION_RECORDED";

	private final ProjectionCheckpointRepository checkpointRepository;
	private final LedgerEventPollingRepository ledgerEventPollingRepository;
	private final TransactionHistoryProjectionRepository projectionRepository;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactionTemplate;
	private final AtomicBoolean running;
	private final int batchSize;

	public LedgerEventProjector(
			ProjectionCheckpointRepository checkpointRepository,
			LedgerEventPollingRepository ledgerEventPollingRepository,
			TransactionHistoryProjectionRepository projectionRepository,
			ObjectMapper objectMapper,
			PlatformTransactionManager transactionManager,
			@Value("${ledger.projection.transaction-history.batch-size:200}") int batchSize) {
		this.checkpointRepository = Objects.requireNonNull(checkpointRepository, "checkpointRepository is required");
		this.ledgerEventPollingRepository = Objects.requireNonNull(ledgerEventPollingRepository,
				"ledgerEventPollingRepository is required");
		this.projectionRepository = Objects.requireNonNull(projectionRepository, "projectionRepository is required");
		this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
		this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager,
				"transactionManager is required"));
		this.running = new AtomicBoolean(false);
		this.batchSize = Math.max(1, batchSize);
	}

	@Scheduled(fixedDelayString = "${ledger.projection.transaction-history.fixed-delay-ms:200}")
	public void tick() {
		if (!running.compareAndSet(false, true)) {
			return;
		}

		try {
			Integer processed = transactionTemplate.execute(status -> projectBatch());
			if (processed != null && processed > 0) {
				log.debug("Projected {} ledger events into {}", processed, TRANSACTION_HISTORY_PROJECTION_TYPE);
			}
		} catch (RuntimeException e) {
			log.error("Ledger event projector failed. Batch will be retried.", e);
		} finally {
			running.set(false);
		}
	}

	public int projectOnce() {
		Integer processed = transactionTemplate.execute(status -> projectBatch());
		return processed == null ? 0 : processed;
	}

	private int projectBatch() {
		long last = checkpointRepository.lockAndGetLastSequenceNumber(TRANSACTION_HISTORY_PROJECTION_TYPE);
		var events = ledgerEventPollingRepository.fetchAfter(last, batchSize);
		if (events.isEmpty()) {
			return 0;
		}

		long max = last;
		int processed = 0;
		for (var event : events) {
			max = Math.max(max, event.globalSequence());
			if (LEDGER_TRANSACTION_RECORDED.equals(event.eventType())) {
				projectTransactionRecorded(event);
			}
			processed++;
		}

		checkpointRepository.updateLastSequenceNumber(TRANSACTION_HISTORY_PROJECTION_TYPE, max);
		return processed;
	}

	private void projectTransactionRecorded(LedgerEventPollingRepository.LedgerEventRow event) {
		try {
			if (projectionRepository.isEventProcessed(event.eventId(), TRANSACTION_HISTORY_PROJECTION_TYPE)) {
				return;
			}

			JsonNode root = objectMapper.readTree(event.payloadJson());

			UUID transactionId = UUID.fromString(requiredText(root, "transaction_id"));
			String referenceKey = requiredText(root, "reference_key");
			String currency = requiredText(root, "currency");

			JsonNode entries = root.get("entries");
			if (entries == null || !entries.isArray()) {
				throw new IllegalStateException("Ledger event payload entries must be an array");
			}

			Iterator<JsonNode> it = entries.elements();
			while (it.hasNext()) {
				JsonNode leg = it.next();
				UUID accountId = UUID.fromString(requiredText(leg, "account_id"));
				String direction = requiredText(leg, "entry_type");
				BigDecimal amount = new BigDecimal(requiredText(leg, "amount"));

				projectionRepository.insertProjectionRow(
						event.eventId(),
						transactionId,
						referenceKey,
						accountId,
						direction,
						amount,
						currency,
						event.occurredAt(),
						event.globalSequence(),
						event.correlationId());
			}

			projectionRepository.tryMarkEventProcessed(event.eventId(), TRANSACTION_HISTORY_PROJECTION_TYPE);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to project ledger event " + event.eventId(), e);
		}
	}

	private static String requiredText(JsonNode root, String fieldName) {
		JsonNode node = Objects.requireNonNull(root, "root is required").get(fieldName);
		if (node == null || node.isNull()) {
			throw new IllegalStateException("Ledger event payload missing field: " + fieldName);
		}
		String value = node.asText();
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalStateException("Ledger event payload field is blank: " + fieldName);
		}
		return value.trim();
	}
}
