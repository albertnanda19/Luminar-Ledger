package io.luminar.ledger.application.transaction.idempotency;

import io.luminar.ledger.service.PostedTransaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Service
public class GlobalIdempotencyCache {
	private static final Logger log = LoggerFactory.getLogger(GlobalIdempotencyCache.class);

	private static final String KEY_PREFIX = "idempotency::";

	private static final String LUA_GET_OR_SET_IN_PROGRESS = """
			local key = KEYS[1]
			local inProgressValue = ARGV[1]
			local ttlSeconds = tonumber(ARGV[2])

			local existing = redis.call('GET', key)
			if not existing then
				local ok = redis.call('SET', key, inProgressValue, 'NX', 'EX', ttlSeconds)
				if ok then
					return ''
				end
				existing = redis.call('GET', key)
				if not existing then
					return ''
				end
			end

			local decodedOk, decoded = pcall(cjson.decode, existing)
			if decodedOk and decoded and decoded['status'] == 'FAILED' then
				redis.call('SET', key, inProgressValue, 'XX', 'EX', ttlSeconds)
				return ''
			end

			return existing
			""";

	private static final DefaultRedisScript<String> GET_OR_SET_SCRIPT;

	static {
		GET_OR_SET_SCRIPT = new DefaultRedisScript<>();
		GET_OR_SET_SCRIPT.setResultType(String.class);
		GET_OR_SET_SCRIPT.setScriptText(LUA_GET_OR_SET_IN_PROGRESS);
	}

	private final StringRedisTemplate stringRedisTemplate;
	private final ObjectMapper objectMapper;
	private final Duration ttl;

	public GlobalIdempotencyCache(
			StringRedisTemplate stringRedisTemplate,
			ObjectMapper objectMapper,
			@Value("${ledger.idempotency.ttl-seconds:600}") int ttlSeconds) {
		this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate,
				"GlobalIdempotencyCache.stringRedisTemplate is required");
		this.objectMapper = Objects.requireNonNull(objectMapper,
				"GlobalIdempotencyCache.objectMapper is required");
		int safeSeconds = Math.max(1, ttlSeconds);
		if (safeSeconds < 300 || safeSeconds > 900) {
			log.warn("Idempotency TTL is outside recommended range (300..900 seconds). ttlSeconds={}", safeSeconds);
		}
		this.ttl = Duration.ofSeconds(safeSeconds);
	}

	public PostedTransaction acquireOrReplayCompleted(String referenceKey) {
		String ref = Objects.requireNonNull(referenceKey, "referenceKey is required").trim();
		String key = KEY_PREFIX + ref;
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

		IdempotencyRecord inProgress = new IdempotencyRecord("IN_PROGRESS", null, ref, null, now);
		String inProgressJson;
		try {
			inProgressJson = objectMapper.writeValueAsString(inProgress);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to serialize idempotency IN_PROGRESS record", e);
		}

		String result;
		try {
			RedisScript<String> script = Objects.requireNonNull(GET_OR_SET_SCRIPT, "GET_OR_SET_SCRIPT is required");
			List<String> keys = Objects.requireNonNull(List.of(key), "keys is required");
			Duration localTtl = Objects.requireNonNull(ttl, "ttl is required");
			result = stringRedisTemplate.execute(script, keys, inProgressJson,
					String.valueOf(localTtl.toSeconds()));
		} catch (Exception e) {
			log.warn("Idempotency cache acquire failed. Falling back to DB. key={}", key, e);
			return null;
		}

		if (result == null || result.isBlank()) {
			return null;
		}

		IdempotencyRecord existing;
		try {
			existing = objectMapper.readValue(result, IdempotencyRecord.class);
		} catch (Exception e) {
			log.warn("Failed to deserialize idempotency record. Treating as IN_PROGRESS. key={}", key, e);
			throw new IdempotencyInProgressException(ref);
		}

		String status = existing.status();
		if (status == null || status.isBlank()) {
			throw new IdempotencyInProgressException(ref);
		}

		return switch (status) {
			case "IN_PROGRESS" -> throw new IdempotencyInProgressException(ref);
			case "FAILED" -> throw new IdempotencyInProgressException(ref);
			case "COMPLETED" -> {
				if (existing.transactionId() == null || existing.postedAt() == null) {
					throw new IdempotencyInProgressException(ref);
				}
				yield new PostedTransaction(existing.transactionId(), existing.referenceKey(), existing.postedAt());
			}
			default -> throw new IdempotencyInProgressException(ref);
		};
	}

	public void markCompleted(PostedTransaction postedTransaction) {
		Objects.requireNonNull(postedTransaction, "postedTransaction is required");
		String ref = Objects.requireNonNull(postedTransaction.referenceKey(), "referenceKey is required").trim();
		String key = KEY_PREFIX + ref;
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

		IdempotencyRecord completed = new IdempotencyRecord(
				"COMPLETED",
				postedTransaction.transactionId(),
				ref,
				postedTransaction.postedAt(),
				now);

		try {
			String json = Objects.requireNonNull(objectMapper.writeValueAsString(completed),
					"Failed to serialize COMPLETED idempotency record");
			stringRedisTemplate.opsForValue().set(key, json, Objects.requireNonNull(ttl, "ttl is required"));
		} catch (Exception e) {
			log.warn("Idempotency cache update to COMPLETED failed. key={}", key, e);
		}
	}

	public void markFailed(String referenceKey) {
		String ref = Objects.requireNonNull(referenceKey, "referenceKey is required").trim();
		String key = KEY_PREFIX + ref;
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

		IdempotencyRecord failed = new IdempotencyRecord("FAILED", null, ref, null, now);
		try {
			String json = Objects.requireNonNull(objectMapper.writeValueAsString(failed),
					"Failed to serialize FAILED idempotency record");
			stringRedisTemplate.opsForValue().set(key, json, Objects.requireNonNull(ttl, "ttl is required"));
		} catch (Exception e) {
			log.warn("Idempotency cache update to FAILED failed. key={}", key, e);
		}
	}
}
